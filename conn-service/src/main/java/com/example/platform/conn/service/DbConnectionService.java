package com.example.platform.conn.service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.platform.common.core.api.ErrorCode;
import com.example.platform.common.core.api.PageResult;
import com.example.platform.common.core.api.Result;
import com.example.platform.common.core.exception.BusinessException;
import com.example.platform.common.core.util.AesCipher;
import com.example.platform.common.web.context.UserContext;
import com.example.platform.conn.client.UserClient;
import com.example.platform.conn.dto.ConnectionRequest;
import com.example.platform.conn.dto.ConnectionResponse;
import com.example.platform.conn.dto.ConnectionTestRequest;
import com.example.platform.conn.dto.ConnectionTestResult;
import com.example.platform.conn.dto.UserInfoDto;
import com.example.platform.conn.entity.DbConnection;
import com.example.platform.conn.enums.DbType;
import com.example.platform.conn.repository.DbConnectionRepository;

@Service
public class DbConnectionService {

    private static final Logger log = LoggerFactory.getLogger(DbConnectionService.class);

    private static final int MAX_PAGE_SIZE = 100;

    private final DbConnectionRepository repository;
    private final AesCipher cipher;
    private final ConnectionTester tester;
    private final UserClient userClient;

    public DbConnectionService(DbConnectionRepository repository,
                               AesCipher cipher,
                               ConnectionTester tester,
                               UserClient userClient) {
        this.repository = repository;
        this.cipher = cipher;
        this.tester = tester;
        this.userClient = userClient;
    }

    @Transactional(readOnly = true)
    public PageResult<ConnectionResponse> list(String keyword, String dbType, int page, int size) {
        int safePage = Math.max(page, 1);
        int safeSize = Math.clamp(size, 1, MAX_PAGE_SIZE);

        Page<DbConnection> result = repository.search(
                keyword == null ? "" : keyword.trim(),
                dbType == null ? "" : dbType.trim().toUpperCase(),
                PageRequest.of(safePage - 1, safeSize, Sort.by(Sort.Direction.DESC, "id")));

        Map<Long, String> creatorNames = resolveCreatorNames(result.getContent());
        List<ConnectionResponse> list = result.getContent().stream()
                .map(entity -> toResponse(entity, creatorNames))
                .toList();
        return new PageResult<>(list, result.getTotalElements(), safePage, safeSize);
    }

    @Transactional(readOnly = true)
    public ConnectionResponse get(Long id) {
        DbConnection entity = require(id);
        return toResponse(entity, resolveCreatorNames(List.of(entity)));
    }

    @Transactional
    public ConnectionResponse create(ConnectionRequest request) {
        String name = request.name().trim();
        if (repository.existsByName(name)) {
            throw new BusinessException(ErrorCode.CONFLICT, "连接名称已存在: " + name);
        }
        if (request.password() == null || request.password().isEmpty()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "新建连接时密码不能为空");
        }

        DbConnection entity = new DbConnection();
        applyRequest(entity, request);
        entity.setPasswordCipher(cipher.encrypt(request.password()));
        entity.setStatus(DbConnection.STATUS_UNKNOWN);
        entity.setCreatedBy(UserContext.require().userId());
        repository.save(entity);
        log.info("新建数据库连接: id={}, name={}", entity.getId(), entity.getName());
        return toResponse(entity, Map.of());
    }

    @Transactional
    public ConnectionResponse update(Long id, ConnectionRequest request) {
        DbConnection entity = require(id);
        String name = request.name().trim();
        if (repository.existsByNameAndIdNot(name, id)) {
            throw new BusinessException(ErrorCode.CONFLICT, "连接名称已存在: " + name);
        }

        applyRequest(entity, request);
        // 密码留空表示沿用旧密码
        if (request.password() != null && !request.password().isEmpty()) {
            entity.setPasswordCipher(cipher.encrypt(request.password()));
        }
        repository.save(entity);
        log.info("更新数据库连接: id={}, name={}", entity.getId(), entity.getName());
        return toResponse(entity, resolveCreatorNames(List.of(entity)));
    }

    @Transactional
    public void delete(Long id) {
        DbConnection entity = require(id);
        repository.delete(entity);
        log.info("删除数据库连接: id={}, name={}", id, entity.getName());
    }

    /** 用请求里的配置直接试连，不落库。表单上"测试连接"按钮走这里。 */
    public ConnectionTestResult test(ConnectionTestRequest request) {
        DbType dbType = DbType.of(request.dbType());
        String password = request.password();
        if ((password == null || password.isEmpty()) && request.id() != null) {
            password = decryptPassword(require(request.id()));
        }
        return tester.test(dbType, request.host().trim(), request.port(), request.databaseName(),
                request.username().trim(), password, request.params());
    }

    /** 对已保存的连接试连，并把结果写回记录。 */
    @Transactional
    public ConnectionTestResult testSaved(Long id) {
        DbConnection entity = require(id);
        DbType dbType = DbType.of(entity.getDbType());
        ConnectionTestResult result = tester.test(dbType, entity.getHost(), entity.getPort(),
                entity.getDatabaseName(), entity.getUsername(), decryptPassword(entity), entity.getParams());
        entity.markTestResult(result.success(), result.message());
        repository.save(entity);
        return result;
    }

    private DbConnection require(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "连接不存在: " + id));
    }

    private void applyRequest(DbConnection entity, ConnectionRequest request) {
        DbType dbType = DbType.of(request.dbType());
        entity.setName(request.name().trim());
        entity.setDbType(dbType.name());
        entity.setHost(request.host().trim());
        entity.setPort(request.port() == null ? dbType.defaultPort() : request.port());
        entity.setDatabaseName(trimToNull(request.databaseName()));
        entity.setUsername(request.username().trim());
        entity.setParams(trimToNull(request.params()));
        entity.setRemark(trimToNull(request.remark()));
        entity.setQueryEnabled(request.queryEnabled() == null || request.queryEnabled());
    }

    private String decryptPassword(DbConnection entity) {
        try {
            return cipher.decrypt(entity.getPasswordCipher());
        } catch (RuntimeException e) {
            log.error("解密连接密码失败: id={}", entity.getId(), e);
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "密码解密失败，请重新保存该连接");
        }
    }

    private ConnectionResponse toResponse(DbConnection entity, Map<Long, String> creatorNames) {
        String plainPassword;
        try {
            plainPassword = cipher.decrypt(entity.getPasswordCipher());
        } catch (RuntimeException e) {
            plainPassword = null;
        }
        return ConnectionResponse.from(entity, plainPassword, creatorNames.get(entity.getCreatedBy()));
    }

    /**
     * 通过 Feign 从 auth-service 取创建人昵称。
     * auth-service 不可用时只影响昵称展示，不影响列表本身。
     */
    private Map<Long, String> resolveCreatorNames(List<DbConnection> connections) {
        Set<Long> ids = connections.stream()
                .map(DbConnection::getCreatedBy)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (ids.isEmpty()) {
            return Map.of();
        }

        Map<Long, String> names = new HashMap<>();
        for (Long id : ids) {
            try {
                Result<UserInfoDto> result = userClient.getById(id);
                if (result != null && result.isSuccess() && result.getData() != null) {
                    UserInfoDto user = result.getData();
                    names.put(id, user.nickname() != null && !user.nickname().isBlank()
                            ? user.nickname() : user.username());
                }
            } catch (Exception e) {
                log.warn("调用 auth-service 获取用户信息失败: userId={}, error={}", id, e.getMessage());
            }
        }
        return names;
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
