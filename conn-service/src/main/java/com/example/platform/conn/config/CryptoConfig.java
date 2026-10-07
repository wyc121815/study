package com.example.platform.conn.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.example.platform.common.core.util.AesCipher;

@Configuration
public class CryptoConfig {

    @Bean
    public AesCipher aesCipher(@Value("${app.crypto.key}") String base64Key) {
        return new AesCipher(base64Key);
    }
}
