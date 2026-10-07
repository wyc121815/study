package com.example.platform.conn.controller;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.platform.common.core.api.PageResult;
import com.example.platform.common.core.api.Result;
import com.example.platform.common.core.constant.Roles;
import com.example.platform.common.web.annotation.RequireRole;
import com.example.platform.conn.dto.ConnectionRequest;
import com.example.platform.conn.dto.ConnectionResponse;
import com.example.platform.conn.dto.ConnectionTestRequest;
import com.example.platform.conn.dto.ConnectionTestResult;
import com.example.platform.conn.service.DbConnectionService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/connections")
public class DbConnectionController {

    private final DbConnectionService service;

    public DbConnectionController(DbConnectionService service) {
        this.service = service;
    }

    @GetMapping
    public Result<PageResult<ConnectionResponse>> list(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String dbType,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        return Result.ok(service.list(keyword, dbType, page, size));
    }

    @GetMapping("/{id}")
    public Result<ConnectionResponse> get(@PathVariable Long id) {
        return Result.ok(service.get(id));
    }

    @PostMapping
    @RequireRole(Roles.ADMIN)
    public Result<ConnectionResponse> create(@Valid @RequestBody ConnectionRequest request) {
        return Result.ok(service.create(request));
    }

    @PutMapping("/{id}")
    @RequireRole(Roles.ADMIN)
    public Result<ConnectionResponse> update(@PathVariable Long id,
                                             @Valid @RequestBody ConnectionRequest request) {
        return Result.ok(service.update(id, request));
    }

    @DeleteMapping("/{id}")
    @RequireRole(Roles.ADMIN)
    public Result<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return Result.ok();
    }

    /** 表单上"测试连接"：可以带明文密码，不落库。 */
    @PostMapping("/test")
    public Result<ConnectionTestResult> test(@Valid @RequestBody ConnectionTestRequest request) {
        return Result.ok(service.test(request));
    }

    /** 对已保存的连接做一次真实探测，并把结果记录到该连接上。 */
    @PostMapping("/{id}/test")
    public Result<ConnectionTestResult> testSaved(@PathVariable Long id) {
        return Result.ok(service.testSaved(id));
    }
}
