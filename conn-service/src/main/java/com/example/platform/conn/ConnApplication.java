package com.example.platform.conn;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 业务服务：数据库连接配置的增删改查与连通性测试。
 */
@SpringBootApplication
@EnableFeignClients
@EnableScheduling
public class ConnApplication {

    public static void main(String[] args) {
        SpringApplication.run(ConnApplication.class, args);
    }
}
