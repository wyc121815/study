package com.example.platform.conn.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.platform.common.core.api.Result;
import com.example.platform.conn.dto.DataSourceResponse;
import com.example.platform.conn.service.DataSourceService;

/**
 * 查询用数据源：只暴露标记为"可用于查询"的连接，所有登录用户都可读。
 *
 * <p>与 {@code /api/connections} 的区别：这里不返回管理字段（脱敏密码、
 * 测试状态等），也不包含任何未开放查询的连接。</p>
 */
@RestController
@RequestMapping("/api/datasources")
public class DataSourceController {

    private final DataSourceService service;

    public DataSourceController(DataSourceService service) {
        this.service = service;
    }

    @GetMapping
    public Result<List<DataSourceResponse>> list() {
        return Result.ok(service.list());
    }
}
