package com.example.platform.conn.service;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

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
import com.example.platform.conn.dto.MetricRankItem;
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
    private final MetricCacheService cacheService;
    private final boolean allowWrite;

    public MetricService(MetricDefinitionRepository repository,
                         DbConnectionRepository connectionRepository,
                         SqlStatementGuard guard,
                         SqlQueryService queryService,
                         UserClient userClient,
                         MetricCacheService cacheService,
                         @Value("${app.query.allow-write:false}") boolean allowWrite) {
        this.repository = repository;
        this.connectionRepository = connectionRepository;
        this.guard = guard;
        this.queryService = queryService;
        this.userClient = userClient;
        this.cacheService = cacheService;
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

    /**
     * 执行指标：指标本身带数据源，调用方只需给行数上限。
     *
     * <p>命中 Redis 缓存就直接返回；未命中查库后写回缓存。缓存键里带了 SQL 的
     * 摘要，所以改了指标定义自然就换了一把新键，不需要额外做失效。</p>
     */
    public SqlQueryResponse run(Long id, Integer maxRows, boolean noCache) {
        MetricDefinition metric = require(id);
        if (!MetricDefinition.STATUS_ENABLED.equals(metric.getStatus())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "指标「" + metric.getName() + "」已停用");
        }

        String variant = cacheVariant(metric.getSqlText(), maxRows);
        if (!noCache) {
            Optional<SqlQueryResponse> cached = cacheService.find(id, variant);
            if (cached.isPresent()) {
                cacheService.recordRun(id);
                log.debug("指标命中缓存: id={}, name={}", id, metric.getName());
                return cached.get();
            }
        }

        SqlQueryResponse result = queryService.execute(metric.getDatasourceId(), metric.getSqlText(),
                maxRows, QueryHistory.SOURCE_METRIC);
        cacheService.recordRun(id);
        if (!noCache) {
            cacheService.save(id, variant, result);
        }
        return result;
    }

    public byte[] exportCsv(Long id, Integer maxRows) {
        MetricDefinition metric = require(id);
        if (!MetricDefinition.STATUS_ENABLED.equals(metric.getStatus())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "指标「" + metric.getName() + "」已停用");
        }
        return queryService.exportCsv(metric.getDatasourceId(), metric.getSqlText(), maxRows,
                QueryHistory.SOURCE_METRIC);
    }

    /**
     * 指标热度排行：次数存在 Redis ZSET 里，指标名从库里补。
     * 已经被删掉的指标会被跳过，不影响其余排行。
     */
    @Transactional(readOnly = true)
    public List<MetricRankItem> ranking(int limit) {
        int safeLimit = Math.clamp(limit, 1, 50);
        Map<Long, Long> usage = cacheService.usageMap(safeLimit);
        if (usage.isEmpty()) {
            return List.of();
        }
        List<MetricDefinition> metrics = repository.findAllById(usage.keySet());
        Map<Long, String> datasourceNames = new HashMap<>();
        loadDatasources(metrics).forEach((key, value) -> datasourceNames.put(key, value.getName()));

        return metrics.stream()
                .map(metric -> new MetricRankItem(metric.getId(), metric.getName(), metric.getDescription(),
                        datasourceNames.get(metric.getDatasourceId()), usage.get(metric.getId())))
                .sorted((left, right) -> Long.compare(right.runs(), left.runs()))
                .toList();
    }

    /** 缓存变体：SQL 摘要 + 行数上限，SQL 一改键就变，旧缓存自然过期。 */
    private static String cacheVariant(String sql, Integer maxRows) {
        return digest(sql) + ":rows=" + (maxRows == null ? "default" : maxRows);
    }

    private static String digest(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash, 0, 6);
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 一定存在，兜底走 hashCode，最差是缓存命中率下降
            return Integer.toHexString(value.hashCode());
        }
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
