package com.example.platform.conn.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.platform.common.core.api.ErrorCode;
import com.example.platform.common.core.api.PageResult;
import com.example.platform.common.core.constant.Roles;
import com.example.platform.common.core.exception.BusinessException;
import com.example.platform.common.security.LoginUser;
import com.example.platform.common.web.context.UserContext;
import com.example.platform.conn.dto.QueryHistoryItem;
import com.example.platform.conn.entity.QueryHistory;
import com.example.platform.conn.repository.QueryHistoryRepository;

/**
 * 查询历史的读写。写入失败绝不能影响查询本身，因此记录失败只打日志。
 */
@Service
public class QueryHistoryService {

    private static final Logger log = LoggerFactory.getLogger(QueryHistoryService.class);

    private static final int MAX_PAGE_SIZE = 100;
    private static final int MAX_SQL_LENGTH = 20_000;

    private final QueryHistoryRepository repository;

    public QueryHistoryService(QueryHistoryRepository repository) {
        this.repository = repository;
    }

    /** 记一条历史；任何异常都吞掉，只记日志。 */
    public void record(Long userId, String username, Long connectionId, String connectionName,
                       String sql, String source, String statementType,
                       Integer rowCount, Long elapsedMs, boolean success, String message) {
        try {
            QueryHistory entity = new QueryHistory();
            entity.setUserId(userId);
            entity.setUsername(username);
            entity.setConnectionId(connectionId);
            entity.setConnectionName(connectionName);
            entity.setSqlText(truncate(sql));
            entity.setSource(source == null ? QueryHistory.SOURCE_ADHOC : source);
            entity.setStatementType(statementType);
            entity.setRowCount(rowCount);
            entity.setElapsedMs(elapsedMs);
            entity.setSuccess(success);
            entity.setMessage(message);
            repository.save(entity);
        } catch (RuntimeException e) {
            log.warn("写入查询历史失败: userId={}, connectionId={}, error={}", userId, connectionId, e.getMessage());
        }
    }

    /** {@code scope=all} 只有 ADMIN 可用，默认只看自己的。 */
    @Transactional(readOnly = true)
    public PageResult<QueryHistoryItem> page(String scope, int page, int size) {
        LoginUser user = UserContext.require();
        int safePage = Math.max(page, 1);
        int safeSize = Math.clamp(size, 1, MAX_PAGE_SIZE);
        Pageable pageable = PageRequest.of(safePage - 1, safeSize);

        Page<QueryHistory> result = isAllScope(scope, user)
                ? repository.findAllByOrderByIdDesc(pageable)
                : repository.findByUserIdOrderByIdDesc(user.userId(), pageable);

        return new PageResult<>(result.getContent().stream().map(QueryHistoryItem::from).toList(),
                result.getTotalElements(), safePage, safeSize);
    }

    @Transactional
    public void delete(Long id) {
        LoginUser user = UserContext.require();
        QueryHistory entity = repository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "历史记录不存在"));
        if (!Roles.isAdmin(user.role()) && !user.userId().equals(entity.getUserId())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "只能删除自己的查询记录");
        }
        repository.delete(entity);
    }

    @Transactional
    public void clear(String scope) {
        LoginUser user = UserContext.require();
        if (isAllScope(scope, user)) {
            repository.deleteAllInBatch();
        } else {
            repository.deleteByUserId(user.userId());
        }
    }

    private static boolean isAllScope(String scope, LoginUser user) {
        return "all".equalsIgnoreCase(scope) && Roles.isAdmin(user.role());
    }

    private static String truncate(String sql) {
        if (sql == null) {
            return "";
        }
        return sql.length() <= MAX_SQL_LENGTH ? sql : sql.substring(0, MAX_SQL_LENGTH);
    }
}
