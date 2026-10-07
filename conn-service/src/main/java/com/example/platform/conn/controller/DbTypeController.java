package com.example.platform.conn.controller;

import java.util.Arrays;
import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.platform.common.core.api.Result;
import com.example.platform.conn.dto.DbTypeInfo;
import com.example.platform.conn.enums.DbType;

@RestController
@RequestMapping("/api/db-types")
public class DbTypeController {

    @GetMapping
    public Result<List<DbTypeInfo>> list() {
        List<DbTypeInfo> types = Arrays.stream(DbType.values())
                .map(type -> new DbTypeInfo(type.name(), type.label(), type.defaultPort()))
                .toList();
        return Result.ok(types);
    }
}
