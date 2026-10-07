package com.example.platform.auth.controller;

import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.platform.auth.dto.ChangePasswordRequest;
import com.example.platform.auth.dto.LoginRequest;
import com.example.platform.auth.dto.LoginResponse;
import com.example.platform.auth.dto.LogoutRequest;
import com.example.platform.auth.dto.RefreshRequest;
import com.example.platform.auth.dto.UserInfo;
import com.example.platform.auth.service.AuthService;
import com.example.platform.common.core.api.Result;
import com.example.platform.common.security.LoginUser;
import com.example.platform.common.web.context.UserContext;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    /** 登录：网关的放行名单里有这个路径。 */
    @PostMapping("/login")
    public Result<LoginResponse> login(@Valid @RequestBody LoginRequest request,
                                       HttpServletRequest httpRequest) {
        return Result.ok(authService.login(request,
                clientIp(httpRequest), httpRequest.getHeader(HttpHeaders.USER_AGENT)));
    }

    /** 用刷新令牌换新令牌：网关的放行名单里有这个路径（访问令牌过期也能续期）。 */
    @PostMapping("/refresh")
    public Result<LoginResponse> refresh(@Valid @RequestBody RefreshRequest request,
                                         HttpServletRequest httpRequest) {
        return Result.ok(authService.refresh(request.refreshToken(),
                clientIp(httpRequest), httpRequest.getHeader(HttpHeaders.USER_AGENT)));
    }

    /** 登出：作废当前刷新令牌，服务端立即失效。 */
    @PostMapping("/logout")
    public Result<Void> logout(@RequestBody(required = false) LogoutRequest request) {
        if (request != null) {
            authService.logout(request.refreshToken(), request.accessToken());
        }
        return Result.ok();
    }

    /** 修改当前用户密码。 */
    @PostMapping("/password")
    public Result<Void> changePassword(@Valid @RequestBody ChangePasswordRequest request) {
        LoginUser current = UserContext.require();
        authService.changePassword(current.userId(), request);
        return Result.ok();
    }

    /** 当前登录用户，数据来自网关注入的请求头。 */
    @GetMapping("/me")
    public Result<UserInfo> me() {
        LoginUser current = UserContext.require();
        return Result.ok(authService.getById(current.userId()));
    }

    private static String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            int comma = forwarded.indexOf(',');
            return (comma > 0 ? forwarded.substring(0, comma) : forwarded).trim();
        }
        return request.getRemoteAddr();
    }
}
