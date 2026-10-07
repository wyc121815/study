package com.example.platform.conn.service;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.platform.common.core.api.ErrorCode;
import com.example.platform.common.core.api.Result;
import com.example.platform.common.core.constant.Roles;
import com.example.platform.common.core.exception.BusinessException;
import com.example.platform.common.security.LoginUser;
import com.example.platform.common.web.context.UserContext;
import com.example.platform.conn.client.UserClient;
import com.example.platform.conn.dto.MetricRequest;
import com.example.platform.conn.dto.MetricResponse;
import com.example.platform.conn.dto.SqlQueryResponse;
import com.example.platform.conn.dto.UserInfoDto;
import com.example.platform.conn.entity.DbConnection;
import com.example.platform.conn.entity.MetricDefinition;
import com.example.platform.conn.entity.QueryHistory;
import com.example.platform.conn.enums.DbType;
import com.example.platform.conn.repository.DbConnectionRepository;
import com.example.platform.conn.repository.MetricDefinitionRepository;

/**
 * 指标的增删改查与执行。
 *
 * <p>指标是共享资产：所有登录用户都能看、能跑；只有创建人或管理员能改、能删。
 * 执行时复用 {@link SqlQueryService}，因此只读、行数与超时护栏完全一致。</p>
 */
@Service
public class MetricService {

    private static final Logger log = LoggerFactory.getLogger(MetricService.class);

    private final MetricDefinitionRepository repository;
    private final DbConnectionRepository connectionRepository;
    private final SqlStatementGuard guard;
    private final SqlQueryService queryService;
    private final UserClient userClient;
    private final boolean allowWrite;

    public MetricService(MetricDefinitionRepository repository,
                         DbConnectionRepository connectionRepository,
                         SqlStatementGuard guard,
                         SqlQueryService queryService,
                         UserClient userClient,
                         @Value("${app.query.allow-write:false}") boolean allowWrite) {
        this.repository = repository;
        this.connectionRepository = connectionRepository;
        this.guard = guard;
        this.queryService = queryService;
        this.userClient = userClient;
        this.allowWrite = allowWrite;
    }

    @Transactional(readOnly = true)
    public List<MetricResponse> list() {
        List<MetricDefinition> metrics = repository.findAllByOrderByIdDesc();
        Map<Long, DbConnection> datasources = loadDatasources(metrics);
        Map<Long, String> creators = resolveCreatorNames(metrics);
        return metrics.stream().map(m -> toResponse(m, datasources, creators)).toList();
    }

    @Transactional(readOnly = true)
    public MetricResponse get(Long id) {
        MetricDefinition metric = require(id);
        return toResponse(metric, loadDatasources(List.of(metric)), resolveCreatorNames(List.of(metric)));
    }

    @Transactional
    public MetricResponse create(MetricRequest request) {
        String name = request.name().trim();
        if (repository.existsByName(name)) {
            throw new BusinessException(ErrorCode.CONFLICT, "指标名称已存在: " + name);
        }
        DbConnection datasource = requireQueryableDatasource(request.datasourceId());
        // 保存时就校验一次，避免把明显跑不通的语句存成指标
        guard.validate(request.sql(), allowWrite);

        MetricDefinition metric = new MetricDefinition();
        metric.setName(name);
        metric.setDescription(trimToNull(request.description()));
        metric.setDatasourceId(datasource.getId());
        metric.setSqlText(request.sql().trim());
        metric.setStatus(MetricDefinition.STATUS_ENABLED);
        metric.setCreatedBy(UserContext.require().userId());
        repository.save(metric);
        log.info("新建指标: id={}, name={}", metric.getId(), metric.getName());
        return toResponse(metric, Map.of(datasource.getId(), datasource), Map.of());
    }

    @Transactional
    public MetricResponse update(Long id, MetricRequest request) {
        MetricDefinition metric = require(id);
        requireOwnerOrAdmin(metric);

        String name = request.name().trim();
        if (repository.existsByNameAndIdNot(name, id)) {
            throw new BusinessException(ErrorCode.CONFLICT, "指标名称已存在: " + name);
        }
        DbConnection datasource = requireQueryableDatasource(request.datasourceId());
        guard.validate(request.sql(), allowWrite);

        metric.setName(name);
        metric.setDescription(trimToNull(request.description()));
        metric.setDatasourceId(datasource.getId());
        metric.setSqlText(request.sql().trim());
        repository.save(metric);
        log.info("更新指标: id={}, name={}", metric.getId(), metric.getName());
        return toResponse(metric, Map.of(datasource.getId(), datasource), resolveCreatorNames(List.of(metric)));
    }

    @Transactional
    public void delete(Long id) {
        MetricDefinition metric = require(id);
        requireOwnerOrAdmin(metric);
        repository.delete(metric);
        log.info("删除指标: id={}, name={}", id, metric.getName());
    }

    /** 执行指标：指标本身带数据源，调用方只需给行数上限。 */
    public SqlQueryResponse run(Long id, Integer maxRows) {
        MetricDefinition metric = require(id);
        if (!MetricDefinition.STATUS_ENABLED.equals(metric.getStatus())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "指标「" + metric.getName() + "」已停用");
        }
        return queryService.execute(metric.getDatasourceId(), metric.getSqlText(), maxRows,
                QueryHistory.SOURCE_METRIC);
    }

    public byte[] exportCsv(Long id, Integer maxRows) {
        MetricDefinition metric = require(id);
        if (!MetricDefinition.STATUS_ENABLED.equals(metric.getStatus())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "指标「" + metric.getName() + "」已停用");
        }
        return queryService.exportCsv(metric.getDatasourceId(), metric.getSqlText(), maxRows,
                QueryHistory.SOURCE_METRIC);
    }

    private MetricDefinition require(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "指标不存在: " + id));
    }

    private void requireOwnerOrAdmin(MetricDefinition metric) {
        LoginUser user = UserContext.require();
        if (!Roles.isAdmin(user.role()) && !user.userId().equals(metric.getCreatedBy())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "只有创建人或管理员可以修改该指标");
        }
    }

    private DbConnection requireQueryableDatasource(Long datasourceId) {
        DbConnection datasource = connectionRepository.findById(datasourceId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "数据源不存在: " + datasourceId));
        if (!datasource.isQueryEnabled()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST,
                    "连接「" + datasource.getName() + "」未开放查询，不能作为指标数据源");
        }
        return datasource;
    }

    private MetricResponse toResponse(MetricDefinition metric,
                                      Map<Long, DbConnection> datasources,
                                      Map<Long, String> creators) {
        DbConnection datasource = datasources.get(metric.getDatasourceId());
        String label = null;
        if (datasource != null) {
            try {
                label = DbType.of(datasource.getDbType()).label();
            } catch (RuntimeException e) {
                label = datasource.getDbType();
            }
        }
        return new MetricResponse(
                metric.getId(),
                metric.getName(),
                metric.getDescription(),
                metric.getDatasourceId(),
                datasource == null ? null : datasource.getName(),
                label,
                metric.getSqlText(),
                metric.getStatus(),
                metric.getCreatedBy(),
                creators.get(metric.getCreatedBy()),
                metric.getCreatedAt(),
                metric.getUpdatedAt());
    }

    private Map<Long, DbConnection> loadDatasources(List<MetricDefinition> metrics) {
        Set<Long> ids = new LinkedHashSet<>();
        metrics.forEach(m -> {
            if (m.getDatasourceId() != null) {
                ids.add(m.getDatasourceId());
            }
        });
        Map<Long, DbConnection> map = new HashMap<>();
        connectionRepository.findAllById(ids).forEach(c -> map.put(c.getId(), c));
        return map;
    }

    /** 取创建人昵称，auth-service 不可用时只影响展示。 */
    private Map<Long, String> resolveCreatorNames(List<MetricDefinition> metrics) {
        Set<Long> ids = new LinkedHashSet<>();
        metrics.stream().map(MetricDefinition::getCreatedBy).filter(Objects::nonNull).forEach(ids::add);
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
