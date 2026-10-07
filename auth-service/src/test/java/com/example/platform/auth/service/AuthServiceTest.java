package com.example.platform.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.time.Duration;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.example.platform.auth.dto.LoginRequest;
import com.example.platform.auth.dto.LoginResponse;
import com.example.platform.auth.dto.ChangePasswordRequest;
import com.example.platform.auth.config.AuthProperties;
import com.example.platform.auth.entity.RefreshToken;
import com.example.platform.auth.entity.SysUser;
import com.example.platform.auth.repository.RefreshTokenRepository;
import com.example.platform.auth.repository.SysUserRepository;
import com.example.platform.common.core.api.ErrorCode;
import com.example.platform.common.core.exception.BusinessException;
import com.example.platform.common.security.JwtProperties;
import com.example.platform.common.security.JwtService;
import com.example.platform.common.security.LoginUser;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private SysUserRepository userRepository;

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    @Mock
    private StringRedisTemplate stringRedisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder(4);
    private AuthProperties authProperties;
    private JwtService jwtService;
    private AuthService authService;

    @BeforeEach
    void setUp() {
        JwtProperties jwtProperties = new JwtProperties();
        jwtProperties.setSecret("ZGV2LW9ubHktc2VjcmV0LWRvLW5vdC11c2UtaW4tcHJvZHVjdGlvbi0xMjM0NTY=");
        jwtProperties.setIssuer("conn-platform");
        jwtService = new JwtService(jwtProperties);

        authProperties = new AuthProperties();
        RefreshTokenService refreshTokenService = new RefreshTokenService(refreshTokenRepository, authProperties);
        lenient().when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        TokenRevocationService tokenRevocationService =
                new TokenRevocationService(stringRedisTemplate, jwtService, jwtProperties, authProperties);
        authService = new AuthService(userRepository, passwordEncoder, jwtService,
                refreshTokenService, tokenRevocationService, authProperties);
    }

    @Test
    void loginReturnsUsableToken() {
        when(userRepository.findByUsername("admin")).thenReturn(Optional.of(user("admin", "admin123", 1)));

        LoginResponse response = authService.login(new LoginRequest("admin", "admin123"));

        assertThat(response.token()).isNotBlank();
        assertThat(response.user().username()).isEqualTo("admin");
        // 令牌必须能被同一套 JwtService 解析回原用户
        LoginUser parsed = jwtService.parse(response.token());
        assertThat(parsed.username()).isEqualTo("admin");
        assertThat(parsed.role()).isEqualTo("ADMIN");
    }

    @Test
    void loginWithWrongPasswordIsRejected() {
        when(userRepository.findByUsername("admin")).thenReturn(Optional.of(user("admin", "admin123", 1)));

        assertThatThrownBy(() -> authService.login(new LoginRequest("admin", "nope")))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.LOGIN_FAILED);
    }

    @Test
    void loginWithUnknownUserLooksTheSameAsWrongPassword() {
        when(userRepository.findByUsername("ghost")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.login(new LoginRequest("ghost", "whatever")))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.LOGIN_FAILED);
    }

    @Test
    void disabledAccountCannotLogin() {
        when(userRepository.findByUsername("banned")).thenReturn(Optional.of(user("banned", "banned123", 0)));

        assertThatThrownBy(() -> authService.login(new LoginRequest("banned", "banned123")))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.ACCOUNT_DISABLED);
    }

    @Test
    void loginLocksAccountAfterTooManyFailures() {
        when(userRepository.findByUsername("admin")).thenReturn(Optional.of(user("admin", "admin123", 1)));

        for (int i = 0; i < authProperties.getMaxFailedAttempts() - 1; i++) {
            assertThatThrownBy(() -> authService.login(new LoginRequest("admin", "wrong")))
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(ErrorCode.LOGIN_FAILED);
        }
        assertThatThrownBy(() -> authService.login(new LoginRequest("admin", "wrong")))
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.ACCOUNT_LOCKED);
    }

    @Test
    void lockedAccountIsRejectedEvenWithCorrectPassword() {
        SysUser user = user("admin", "admin123", 1);
        user.setLockedUntil(LocalDateTime.now().plusMinutes(5));
        when(userRepository.findByUsername("admin")).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> authService.login(new LoginRequest("admin", "admin123")))
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.ACCOUNT_LOCKED);
    }

    @Test
    void loginIssuesRefreshTokenNextToAccessToken() {
        when(userRepository.findByUsername("admin")).thenReturn(Optional.of(user("admin", "admin123", 1)));

        LoginResponse response = authService.login(new LoginRequest("admin", "admin123"));

        assertThat(response.refreshToken()).isNotBlank();
        assertThat(response.refreshExpiresIn()).isPositive();
    }

    @Test
    void refreshRejectsRevokedTokenAndKillsWholeSession() {
        RefreshToken revoked = new RefreshToken();
        revoked.setUserId(1L);
        revoked.setExpiresAt(LocalDateTime.now().plusDays(1));
        revoked.revoke(LocalDateTime.now());
        when(refreshTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.of(revoked));

        assertThatThrownBy(() -> authService.refresh("stolen-token", "127.0.0.1", "junit"))
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.REFRESH_TOKEN_INVALID);
        verify(refreshTokenRepository).revokeAllForUser(eq(1L), any(LocalDateTime.class));
    }

    @Test
    void refreshRotatesUsableToken() {
        RefreshToken valid = new RefreshToken();
        valid.setUserId(1L);
        valid.setTokenHash("some-hash");
        valid.setExpiresAt(LocalDateTime.now().plusDays(1));
        when(refreshTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.of(valid));
        when(userRepository.findById(1L)).thenReturn(Optional.of(user("admin", "admin123", 1)));

        LoginResponse response = authService.refresh("valid-token", "127.0.0.1", "junit");

        assertThat(response.token()).isNotBlank();
        assertThat(valid.isRevoked()).isTrue();
    }

    @Test
    void changePasswordRejectsWrongCurrentPassword() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(user("admin", "admin123", 1)));

        assertThatThrownBy(() -> authService.changePassword(1L,
                new ChangePasswordRequest("nope", "newpass123")))
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.BAD_REQUEST);
    }

    @Test
    void changePasswordRejectsWeakPassword() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(user("admin", "admin123", 1)));

        assertThatThrownBy(() -> authService.changePassword(1L,
                new ChangePasswordRequest("admin123", "abcdefgh")))
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.WEAK_PASSWORD);
    }

    @Test
    void changePasswordRevokesOtherSessions() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(user("admin", "admin123", 1)));

        authService.changePassword(1L, new ChangePasswordRequest("admin123", "brand-new-9"));

        verify(refreshTokenRepository).revokeAllForUser(eq(1L), any(LocalDateTime.class));
        // 改密后还要在 Redis 里立一条用户级吊销水位，让旧访问令牌立即失效
        verify(valueOperations).set(anyString(), anyString(), any(Duration.class));
    }

    @Test
    void logoutRevokesRefreshTokenAndBlacklistsAccessToken() {
        String accessToken = jwtService.issue(new LoginUser(1L, "admin", "ADMIN"));
        RefreshToken stored = new RefreshToken();
        stored.setUserId(1L);
        stored.setExpiresAt(LocalDateTime.now().plusDays(1));
        when(refreshTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.of(stored));

        authService.logout("raw-refresh-token", accessToken);

        assertThat(stored.isRevoked()).isTrue();
        verify(valueOperations).set(anyString(), eq("1"), any(Duration.class));
    }

    private SysUser user(String username, String rawPassword, int status) {
        SysUser user = new SysUser();
        user.setId(1L);
        user.setUsername(username);
        user.setPassword(passwordEncoder.encode(rawPassword));
        user.setNickname("测试用户");
        user.setRole("ADMIN");
        user.setStatus(status);
        return user;
    }
}
