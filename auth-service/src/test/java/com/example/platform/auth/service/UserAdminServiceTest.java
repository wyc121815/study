package com.example.platform.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.example.platform.auth.config.AuthProperties;
import com.example.platform.auth.dto.UserCreateRequest;
import com.example.platform.auth.dto.UserUpdateRequest;
import com.example.platform.auth.entity.SysUser;
import com.example.platform.auth.repository.SysUserRepository;
import com.example.platform.common.core.api.ErrorCode;
import com.example.platform.common.core.exception.BusinessException;
import com.example.platform.common.security.LoginUser;
import com.example.platform.common.web.context.UserContext;

/**
 * 重点盯住"角色大小写"这条线：库里存的是 {@code admin} 时，最后一名管理员的
 * 保护也必须生效，不能因为一次大小写差异就绕过。
 */
@ExtendWith(MockitoExtension.class)
class UserAdminServiceTest {

    @Mock
    private SysUserRepository userRepository;

    @Mock
    private RefreshTokenService refreshTokenService;

    @Mock
    private TokenRevocationService tokenRevocationService;

    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder(4);
    private UserAdminService service;

    @BeforeEach
    void setUp() {
        service = new UserAdminService(userRepository, passwordEncoder,
                new PasswordPolicy(new AuthProperties()), refreshTokenService, tokenRevocationService);
        // 当前登录用户是另一个管理员（id=1），用来测试"动别人"的场景
        UserContext.set(new LoginUser(1L, "admin", "ADMIN"));
    }

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    @Test
    void demotingLastAdminIsRejectedEvenWhenStoredRoleIsLowercase() {
        SysUser target = user(2L, " 管理员 ", "admin", SysUser.STATUS_ENABLED);
        when(userRepository.findById(2L)).thenReturn(Optional.of(target));
        when(userRepository.countByRoleIgnoreCase("ADMIN", SysUser.STATUS_ENABLED)).thenReturn(1L);

        assertThatThrownBy(() -> service.update(2L, new UserUpdateRequest("管理员", "USER", 1)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("至少需要保留一名启用状态的管理员");
        verify(userRepository, never()).save(target);
    }

    @Test
    void disablingLastAdminWithMixedCaseRoleIsRejected() {
        SysUser target = user(2L, "管理员", "Admin", SysUser.STATUS_ENABLED);
        when(userRepository.findById(2L)).thenReturn(Optional.of(target));
        when(userRepository.countByRoleIgnoreCase("ADMIN", SysUser.STATUS_ENABLED)).thenReturn(1L);

        assertThatThrownBy(() -> service.update(2L, new UserUpdateRequest("管理员", "Admin", 0)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("至少需要保留一名启用状态的管理员");
    }

    @Test
    void demotingAdminIsAllowedWhenAnotherActiveAdminExists() {
        SysUser target = user(2L, "管理员", "admin", SysUser.STATUS_ENABLED);
        when(userRepository.findById(2L)).thenReturn(Optional.of(target));
        when(userRepository.countByRoleIgnoreCase("ADMIN", SysUser.STATUS_ENABLED)).thenReturn(2L);

        service.update(2L, new UserUpdateRequest("管理员", "USER", 1));

        // 降级后角色被归一化成大写，并吊销该用户全部令牌
        assertThat(target.getRole()).isEqualTo("USER");
        verify(tokenRevocationService).revokeAllAccessTokens(2L);
        verify(refreshTokenService).revokeAllForUser(2L);
    }

    @Test
    void cannotChangeOwnRole() {
        SysUser self = user(1L, "admin", "admin", SysUser.STATUS_ENABLED);
        when(userRepository.findById(1L)).thenReturn(Optional.of(self));

        assertThatThrownBy(() -> service.update(1L, new UserUpdateRequest("admin", "USER", 1)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不能修改自己的角色");
    }

    @Test
    void createNormalizesLowercaseRoleToCanonical() {
        when(userRepository.existsByUsername("newadmin")).thenReturn(false);

        service.create(new UserCreateRequest("newadmin", "Abcd1234", "新管理员", "admin"));

        ArgumentCaptor<SysUser> captor = ArgumentCaptor.forClass(SysUser.class);
        verify(userRepository).save(captor.capture());
        assertThat(captor.getValue().getRole()).isEqualTo("ADMIN");
    }

    @Test
    void createRejectsUnknownRole() {
        when(userRepository.existsByUsername("boss")).thenReturn(false);

        assertThatThrownBy(() -> service.create(new UserCreateRequest("boss", "Abcd1234", "老板", "BOSS")))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.BAD_REQUEST);
        verify(userRepository, never()).save(any(SysUser.class));
    }

    private SysUser user(Long id, String nickname, String role, int status) {
        SysUser user = new SysUser();
        user.setId(id);
        user.setUsername("u" + id);
        user.setPassword(passwordEncoder.encode("Abcd1234"));
        user.setNickname(nickname);
        user.setRole(role);
        user.setStatus(status);
        return user;
    }
}
