package com.example.platform.auth.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.platform.auth.dto.UserInfo;
import com.example.platform.auth.repository.SysUserRepository;
import com.example.platform.auth.service.AuthService;
import com.example.platform.common.core.api.Result;
import com.example.platform.common.core.constant.Roles;
import com.example.platform.common.web.annotation.RequireRole;

/**
 * 用户信息查询，供 conn-service 通过 Feign 调用（展示“创建人”昵称）。
 */
@RestController
@RequestMapping("/api/users")
public class UserController {

    private final AuthService authService;
    private final SysUserRepository userRepository;

    public UserController(AuthService authService, SysUserRepository userRepository) {
        this.authService = authService;
        this.userRepository = userRepository;
    }

    @GetMapping("/{id}")
    public Result<UserInfo> getById(@PathVariable Long id) {
        return Result.ok(authService.getById(id));
    }

    @GetMapping
    @RequireRole(Roles.ADMIN)
    public Result<List<UserInfo>> list() {
        List<UserInfo> users = userRepository.findAll().stream().map(UserInfo::from).toList();
        return Result.ok(users);
    }
}
