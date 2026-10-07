package com.example.platform.conn.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.platform.conn.dto.DataSourceResponse;
import com.example.platform.conn.repository.DbConnectionRepository;

/**
 * 查询用数据源列表。是否可用于查询由 {@code db_connection.query_enabled} 决定，
 * 这是"管理"与"取数"两类用途的隔离开关。
 */
@Service
public class DataSourceService {

    private final DbConnectionRepository repository;

    public DataSourceService(DbConnectionRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public List<DataSourceResponse> list() {
        return repository.findByQueryEnabledTrueOrderByIdDesc().stream()
                .map(DataSourceResponse::from)
                .toList();
    }
}
