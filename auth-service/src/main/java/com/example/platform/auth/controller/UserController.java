package com.example.platform.auth.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.platform.auth.dto.ResetPasswordRequest;
import com.example.platform.auth.dto.UserCreateRequest;
import com.example.platform.auth.dto.UserInfo;
import com.example.platform.auth.dto.UserSummary;
import com.example.platform.auth.dto.UserUpdateRequest;
import com.example.platform.auth.service.AuthService;
import com.example.platform.auth.service.UserAdminService;
import com.example.platform.common.core.api.PageResult;
import com.example.platform.common.core.api.Result;
import com.example.platform.common.core.constant.Roles;
import com.example.platform.common.web.annotation.RequireRole;

import jakarta.validation.Valid;

/**
 * 用户信息查询与管理。
 *
 * <p>{@code GET /api/users/{id}} 供 conn-service 通过 Feign 调用（展示"创建人"昵称），
 * 因此不能加类级别 ADMIN 限制——增删改写接口逐个用 {@code @RequireRole} 声明。</p>
 */
@RestController
@RequestMapping("/api/users")
public class UserController {

    private final AuthService authService;
    private final UserAdminService userAdminService;

    public UserController(AuthService authService,
                          UserAdminService userAdminService) {
        this.authService = authService;
        this.userAdminService = userAdminService;
    }

    @GetMapping("/{id}")
    public Result<UserInfo> getById(@PathVariable Long id) {
        return Result.ok(authService.getById(id));
    }

    @GetMapping
    @RequireRole(Roles.ADMIN)
    public Result<PageResult<UserSummary>> list(
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        return Result.ok(userAdminService.list(keyword, page, size));
    }

    @PostMapping
    @RequireRole(Roles.ADMIN)
    public Result<UserSummary> create(@Valid @RequestBody UserCreateRequest request) {
        return Result.ok(userAdminService.create(request));
    }

    @PutMapping("/{id}")
    @RequireRole(Roles.ADMIN)
    public Result<UserSummary> update(@PathVariable Long id,
                                      @Valid @RequestBody UserUpdateRequest request) {
        return Result.ok(userAdminService.update(id, request));
    }

    @PostMapping("/{id}/password")
    @RequireRole(Roles.ADMIN)
    public Result<Void> resetPassword(@PathVariable Long id,
                                      @Valid @RequestBody ResetPasswordRequest request) {
        userAdminService.resetPassword(id, request);
        return Result.ok();
    }
}
