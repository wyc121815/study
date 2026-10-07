package com.example.platform.auth.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.platform.auth.dto.ResetPasswordRequest;
import com.example.platform.auth.dto.UserCreateRequest;
import com.example.platform.auth.dto.UserSummary;
import com.example.platform.auth.dto.UserUpdateRequest;
import com.example.platform.auth.entity.SysUser;
import com.example.platform.auth.repository.SysUserRepository;
import com.example.platform.common.core.api.ErrorCode;
import com.example.platform.common.core.api.PageResult;
import com.example.platform.common.core.constant.Roles;
import com.example.platform.common.core.exception.BusinessException;
import com.example.platform.common.security.LoginUser;
import com.example.platform.common.web.context.UserContext;

/**
 * 用户管理（仅 ADMIN，鉴权在 Controller 上用 {@code @RequireRole} 声明）。
 *
 * <p>两条安全底线：不能把自己改残（改角色/禁用），不能把系统里最后一名
 * 启用状态的管理员降级或禁用。角色或状态一变就吊销该用户全部令牌，
 * 否则旧 JWT 里的角色还会继续生效到过期为止。</p>
 */
@Service
public class UserAdminService {

    private static final Logger log = LoggerFactory.getLogger(UserAdminService.class);

    private static final int MAX_PAGE_SIZE = 100;

    private final SysUserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final PasswordPolicy passwordPolicy;
    private final RefreshTokenService refreshTokenService;
    private final TokenRevocationService tokenRevocationService;

    public UserAdminService(SysUserRepository userRepository,
                            PasswordEncoder passwordEncoder,
                            PasswordPolicy passwordPolicy,
                            RefreshTokenService refreshTokenService,
                            TokenRevocationService tokenRevocationService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.passwordPolicy = passwordPolicy;
        this.refreshTokenService = refreshTokenService;
        this.tokenRevocationService = tokenRevocationService;
    }

    @Transactional(readOnly = true)
    public PageResult<UserSummary> list(String keyword, int page, int size) {
        int safePage = Math.max(page, 1);
        int safeSize = Math.clamp(size, 1, MAX_PAGE_SIZE);
        Page<SysUser> result = userRepository.search(
                keyword == null ? "" : keyword.trim(),
                PageRequest.of(safePage - 1, safeSize, Sort.by(Sort.Direction.ASC, "id")));
        return new PageResult<>(result.getContent().stream().map(UserSummary::from).toList(),
                result.getTotalElements(), safePage, safeSize);
    }

    @Transactional
    public UserSummary create(UserCreateRequest request) {
        String username = request.username().trim();
        if (userRepository.existsByUsername(username)) {
            throw new BusinessException(ErrorCode.CONFLICT, "登录名已存在: " + username);
        }
        String role = resolveRole(request.role());
        passwordPolicy.validate(request.password());

        SysUser user = new SysUser();
        user.setUsername(username);
        user.setPassword(passwordEncoder.encode(request.password()));
        user.setNickname(request.nickname().trim());
        user.setRole(role);
        user.setStatus(SysUser.STATUS_ENABLED);
        userRepository.save(user);
        log.info("创建用户: username={}, role={}, by={}", username, role, UserContext.require().userId());
        return UserSummary.from(user);
    }

    @Transactional
    public UserSummary update(Long id, UserUpdateRequest request) {
        SysUser user = require(id);
        LoginUser current = UserContext.require();

        String role = resolveRole(request.role());
        Integer status = request.status() == null ? user.getStatus() : request.status();
        if (status != SysUser.STATUS_ENABLED && status != SysUser.STATUS_DISABLED) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "状态只能是 1（启用）或 0（禁用）");
        }

        boolean roleChanged = !role.equals(Roles.normalize(user.getRole()));
        boolean statusChanged = !status.equals(user.getStatus());
        boolean self = current.userId().equals(id);

        if (self && roleChanged) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "不能修改自己的角色");
        }
        if (self && statusChanged && status == SysUser.STATUS_DISABLED) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "不能禁用自己的账号");
        }
        boolean losingAdmin = Roles.isAdmin(user.getRole())
                && ((roleChanged && !Roles.isAdmin(role))
                    || (statusChanged && status == SysUser.STATUS_DISABLED));
        if (losingAdmin) {
            ensureNotLastActiveAdmin(user);
        }

        user.setNickname(request.nickname().trim());
        user.setRole(role);
        user.setStatus(status);
        userRepository.save(user);

        if (roleChanged || statusChanged) {
            revokeAll(user.getId());
            log.info("用户角色/状态变更，已吊销其全部令牌: userId={}, role={}, status={}", id, role, status);
        }
        return UserSummary.from(user);
    }

    @Transactional
    public void resetPassword(Long id, ResetPasswordRequest request) {
        SysUser user = require(id);
        passwordPolicy.validate(request.newPassword());
        if (passwordEncoder.matches(request.newPassword(), user.getPassword())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "新密码不能与该用户当前密码相同");
        }
        user.setPassword(passwordEncoder.encode(request.newPassword()));
        userRepository.save(user);
        revokeAll(id);
        log.info("重置用户密码: userId={}, by={}", id, UserContext.require().userId());
    }

    private void ensureNotLastActiveAdmin(SysUser user) {
        if (user.isEnabled()
                && userRepository.countByRoleIgnoreCase(Roles.ADMIN, SysUser.STATUS_ENABLED) <= 1) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "系统至少需要保留一名启用状态的管理员");
        }
    }

    private void revokeAll(Long userId) {
        refreshTokenService.revokeAllForUser(userId);
        tokenRevocationService.revokeAllAccessTokens(userId);
    }

    private SysUser require(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "用户不存在: " + id));
    }

    private static String resolveRole(String role) {
        if (role == null || role.isBlank()) {
            return Roles.USER;
        }
        String normalized = Roles.normalize(role);
        if (normalized == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "角色只能是 ADMIN 或 USER");
        }
        return normalized;
    }
}
