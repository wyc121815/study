package com.example.platform.auth.service;

import org.springframework.stereotype.Component;

import com.example.platform.auth.config.AuthProperties;
import com.example.platform.common.core.api.ErrorCode;
import com.example.platform.common.core.exception.BusinessException;

/**
 * 密码强度校验。改密和重置密码共用，避免两处规则漂移。
 */
@Component
public class PasswordPolicy {

    private final AuthProperties authProperties;

    public PasswordPolicy(AuthProperties authProperties) {
        this.authProperties = authProperties;
    }

    /** 密码强度：长度达标且同时包含字母和数字。 */
    public void validate(String password) {
        int min = authProperties.getMinPasswordLength();
        boolean hasLetter = password != null && password.chars().anyMatch(Character::isLetter);
        boolean hasDigit = password != null && password.chars().anyMatch(Character::isDigit);
        if (password == null || password.length() < min || !hasLetter || !hasDigit) {
            throw new BusinessException(ErrorCode.WEAK_PASSWORD,
                    "密码至少 " + min + " 位，且需同时包含字母和数字");
        }
    }
}
