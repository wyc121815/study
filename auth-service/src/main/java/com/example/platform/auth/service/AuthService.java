package com.example.platform.auth.service;

import java.time.Duration;
import java.time.LocalDateTime;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.platform.auth.config.AuthProperties;
import com.example.platform.auth.dto.ChangePasswordRequest;
import com.example.platform.auth.dto.LoginRequest;
import com.example.platform.auth.dto.LoginResponse;
import com.example.platform.auth.dto.UserInfo;
import com.example.platform.auth.entity.RefreshToken;
import com.example.platform.auth.entity.SysUser;
import com.example.platform.auth.repository.SysUserRepository;
import com.example.platform.common.core.api.ErrorCode;
import com.example.platform.common.core.exception.BusinessException;
import com.example.platform.common.security.JwtService;
import com.example.platform.common.security.LoginUser;

@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private final SysUserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final RefreshTokenService refreshTokenService;
    private final TokenRevocationService tokenRevocationService;
    private final AuthProperties authProperties;

    /**
     * 用户不存在时用来"陪跑"一次 bcrypt 校验的假哈希，避免通过响应时间
     * 区分"用户不存在"和"密码错误"，堵住时序侧信道的账号枚举。
     */
    private final String dummyPasswordHash;

    public AuthService(SysUserRepository userRepository,
                       PasswordEncoder passwordEncoder,
                       JwtService jwtService,
                       RefreshTokenService refreshTokenService,
                       TokenRevocationService tokenRevocationService,
                       AuthProperties authProperties) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.refreshTokenService = refreshTokenService;
        this.tokenRevocationService = tokenRevocationService;
        this.authProperties = authProperties;
        this.dummyPasswordHash = passwordEncoder.encode("dummy-password-for-timing");
    }

    public LoginResponse login(LoginRequest request) {
        return login(request, null, null);
    }

    /**
     * 故意不加 {@code @Transactional}：失败计数要在抛异常前落库，而 RuntimeException
     * 会回滚最外层事务。这里让每次 save 各自成事务（Spring Data 默认行为），
     * 保证"锁定次数"和"吊销令牌"这类副作用不会被随后的异常抹掉。
     */
    public LoginResponse login(LoginRequest request, String clientIp, String userAgent) {
        LocalDateTime now = LocalDateTime.now();
        SysUser user = userRepository.findByUsername(request.username()).orElse(null);

        // 用户不存在：同样做一次 bcrypt 比较再报错，保持耗时一致
        if (user == null) {
            passwordEncoder.matches(request.password(), dummyPasswordHash);
            throw new BusinessException(ErrorCode.LOGIN_FAILED);
        }

        if (user.isLockedAt(now)) {
            log.warn("账号处于锁定期，拒绝登录: username={}", user.getUsername());
            throw new BusinessException(ErrorCode.ACCOUNT_LOCKED);
        }

        if (!passwordEncoder.matches(request.password(), user.getPassword())) {
            user.registerFailedAttempt(authProperties.getMaxFailedAttempts(),
                    Duration.ofMinutes(authProperties.getLockMinutes()), now);
            userRepository.save(user);
            if (user.isLockedAt(now)) {
                log.warn("连续登录失败达到阈值，账号已锁定: username={}", user.getUsername());
                throw new BusinessException(ErrorCode.ACCOUNT_LOCKED);
            }
            log.warn("登录失败，密码不匹配: username={}", request.username());
            throw new BusinessException(ErrorCode.LOGIN_FAILED);
        }
        if (!user.isEnabled()) {
            throw new BusinessException(ErrorCode.ACCOUNT_DISABLED);
        }

        user.resetFailedAttempts();
        userRepository.save(user);

        log.info("登录成功: username={}, userId={}", user.getUsername(), user.getId());
        return issueTokens(user, clientIp, userAgent);
    }

    /**
     * 用刷新令牌换一对新令牌。旧刷新令牌立即作废（轮换）；若收到的是
     * 已作废的令牌，视为令牌泄露，吊销该用户全部会话。
     *
     * <p>同样不加外层事务：检测到重放时要先吊销全部会话，再报错，
     * 否则异常会把吊销动作一起回滚掉。</p>
     */
    public LoginResponse refresh(String rawRefreshToken, String clientIp, String userAgent) {
        RefreshToken stored = refreshTokenService.find(rawRefreshToken)
                .orElseThrow(() -> new BusinessException(ErrorCode.REFRESH_TOKEN_INVALID));

        if (stored.isRevoked()) {
            log.warn("检测到已作废的刷新令牌被重复使用，吊销该用户全部会话: userId={}", stored.getUserId());
            refreshTokenService.revokeAllForUser(stored.getUserId());
            throw new BusinessException(ErrorCode.REFRESH_TOKEN_INVALID);
        }
        if (stored.isExpired(LocalDateTime.now())) {
            throw new BusinessException(ErrorCode.REFRESH_TOKEN_INVALID);
        }

        SysUser user = userRepository.findById(stored.getUserId())
                .orElseThrow(() -> new BusinessException(ErrorCode.REFRESH_TOKEN_INVALID));
        if (!user.isEnabled()) {
            refreshTokenService.revokeAllForUser(user.getId());
            throw new BusinessException(ErrorCode.ACCOUNT_DISABLED);
        }

        String nextRefresh = refreshTokenService.rotate(stored, clientIp, userAgent);
        String accessToken = jwtService.issue(new LoginUser(user.getId(), user.getUsername(), user.getRole()));
        return new LoginResponse(accessToken, "Bearer", jwtService.getTtlSeconds(),
                nextRefresh, refreshTokenService.getTtlSeconds(), UserInfo.from(user));
    }

    /**
     * 登出：作废刷新令牌（库里）并把当前访问令牌拉黑（Redis），做到立即失效。
     * 两个参数都允许缺失，保证接口幂等。
     */
    public void logout(String rawRefreshToken, String accessToken) {
        refreshTokenService.find(rawRefreshToken).ifPresent(refreshTokenService::revoke);
        tokenRevocationService.revokeAccessToken(accessToken);
    }

    /** 修改当前用户密码，成功后吊销其全部刷新令牌，强制其它会话重新登录。 */
    @Transactional
    public void changePassword(Long userId, ChangePasswordRequest request) {
        SysUser user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "用户不存在"));

        if (!passwordEncoder.matches(request.currentPassword(), user.getPassword())) {
            log.warn("修改密码失败，当前密码不匹配: userId={}", userId);
            throw new BusinessException(ErrorCode.BAD_REQUEST, "当前密码不正确");
        }
        validatePasswordStrength(request.newPassword());
        if (passwordEncoder.matches(request.newPassword(), user.getPassword())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "新密码不能与当前密码相同");
        }

        user.setPassword(passwordEncoder.encode(request.newPassword()));
        userRepository.save(user);
        refreshTokenService.revokeAllForUser(userId);
        // 改密后旧访问令牌也应立即失效，不能只等它自然过期
        tokenRevocationService.revokeAllAccessTokens(userId);
        log.info("用户修改密码成功: userId={}", userId);
    }

    private LoginResponse issueTokens(SysUser user, String clientIp, String userAgent) {
        String accessToken = jwtService.issue(new LoginUser(user.getId(), user.getUsername(), user.getRole()));
        String refreshToken = refreshTokenService.issue(user.getId(), clientIp, userAgent);
        return new LoginResponse(accessToken, "Bearer", jwtService.getTtlSeconds(),
                refreshToken, refreshTokenService.getTtlSeconds(), UserInfo.from(user));
    }

    /** 密码强度：长度达标且同时包含字母和数字。 */
    private void validatePasswordStrength(String password) {
        int min = authProperties.getMinPasswordLength();
        boolean hasLetter = password.chars().anyMatch(Character::isLetter);
        boolean hasDigit = password.chars().anyMatch(Character::isDigit);
        if (password.length() < min || !hasLetter || !hasDigit) {
            throw new BusinessException(ErrorCode.WEAK_PASSWORD,
                    "密码至少 " + min + " 位，且需同时包含字母和数字");
        }
    }

    @Transactional(readOnly = true)
    public UserInfo getById(Long id) {
        return userRepository.findById(id)
                .map(UserInfo::from)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "用户不存在"));
    }
}
