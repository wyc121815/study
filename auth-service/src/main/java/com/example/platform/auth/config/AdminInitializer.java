package com.example.platform.auth.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.example.platform.auth.entity.SysUser;
import com.example.platform.auth.repository.SysUserRepository;

/**
 * 首次启动时创建管理员账号，避免"数据库建好了但登不进去"。
 *
 * <p>生产环境请用环境变量 APP_ADMIN_USERNAME / APP_ADMIN_PASSWORD 覆盖默认值，
 * 并在首次登录后立即改密码。</p>
 */
@Configuration
public class AdminInitializer {

    private static final Logger log = LoggerFactory.getLogger(AdminInitializer.class);

    /** 仓库里的默认密码，生产环境沿用会被强制告警。 */
    private static final String DEFAULT_PASSWORD = "admin123";

    @Bean
    public ApplicationRunner initAdminUser(SysUserRepository userRepository,
                                           PasswordEncoder passwordEncoder,
                                           @Value("${app.init.admin-username:admin}") String username,
                                           @Value("${app.init.admin-password:admin123}") String password,
                                           @Value("${app.init.admin-nickname:超级管理员}") String nickname) {
        return args -> {
            if (userRepository.existsByUsername(username)) {
                return;
            }
            if (DEFAULT_PASSWORD.equals(password)) {
                log.warn("初始管理员仍在使用默认密码，生产环境请通过 APP_ADMIN_PASSWORD 覆盖并尽快修改");
            }
            SysUser admin = new SysUser();
            admin.setUsername(username);
            admin.setPassword(passwordEncoder.encode(password));
            admin.setNickname(nickname);
            admin.setRole("ADMIN");
            admin.setStatus(SysUser.STATUS_ENABLED);
            userRepository.save(admin);
            log.warn("已创建初始管理员账号: {}（请尽快修改默认密码）", username);
        };
    }
}
