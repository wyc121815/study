package com.example.platform.conn.service;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.example.platform.common.core.api.ErrorCode;
import com.example.platform.common.core.api.PageResult;
import com.example.platform.common.core.constant.Roles;
import com.example.platform.common.core.exception.BusinessException;
import com.example.platform.common.security.LoginUser;
import com.example.platform.common.web.context.UserContext;
import com.example.platform.conn.dto.ExportRequest;
import com.example.platform.conn.dto.ExportTaskResponse;
import com.example.platform.conn.dto.SqlQueryResponse;
import com.example.platform.conn.entity.DbConnection;
import com.example.platform.conn.entity.ExportTask;
import com.example.platform.conn.entity.MetricDefinition;
import com.example.platform.conn.entity.QueryHistory;
import com.example.platform.conn.queue.ExportTaskSender;
import com.example.platform.conn.repository.DbConnectionRepository;
import com.example.platform.conn.repository.ExportTaskRepository;
import com.example.platform.conn.repository.MetricDefinitionRepository;

/**
 * 批量导出：请求落库 + 投队列，真正跑 SQL 的是消费者。
 *
 * <p>为什么要异步：一次导出可能扫几百万行、跑十几秒，同步占着一个 Tomcat 线程和一个
 * 数据库连接，几个人同时点导出就把服务拖住了。投进队列后，消费端的并发数就是
 * 对数据库的并发上限，请求侧只是快速落库。</p>
 *
 * <p>队列不可用（Redis/Kafka 挂了、或者显式关掉异步）时自动降级为同步执行，
 * 功能不丢，只是慢。</p>
 */
@Service
public class ExportTaskService {

    private static final Logger log = LoggerFactory.getLogger(ExportTaskService.class);

    private static final int MAX_PAGE_SIZE = 100;
    private static final DateTimeFormatter FILE_STAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    private final ExportTaskRepository repository;
    private final DbConnectionRepository connectionRepository;
    private final MetricDefinitionRepository metricRepository;
    private final SqlStatementGuard guard;
    private final SqlQueryService queryService;
    private final ExportTaskSender sender;
    private final TransactionTemplate transactionTemplate;

    private final boolean asyncEnabled;
    private final long ttlMinutes;
    private final long stuckMinutes;
    private final boolean allowWrite;

    public ExportTaskService(ExportTaskRepository repository,
                             DbConnectionRepository connectionRepository,
                             MetricDefinitionRepository metricRepository,
                             SqlStatementGuard guard,
                             SqlQueryService queryService,
                             ExportTaskSender sender,
                             PlatformTransactionManager transactionManager,
                             @Value("${app.export.async-enabled:true}") boolean asyncEnabled,
                             @Value("${app.export.ttl-minutes:120}") long ttlMinutes,
                             @Value("${app.export.queue.stuck-minutes:5}") long stuckMinutes,
                             @Value("${app.query.allow-write:false}") boolean allowWrite) {
        this.repository = repository;
        this.connectionRepository = connectionRepository;
        this.metricRepository = metricRepository;
        this.guard = guard;
        this.queryService = queryService;
        this.sender = sender;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.asyncEnabled = asyncEnabled;
        this.ttlMinutes = Math.max(ttlMinutes, 1);
        this.stuckMinutes = Math.max(stuckMinutes, 1);
        this.allowWrite = allowWrite;
    }

    /** 提交导出任务。校验在入队前做掉，参数不对就没必要浪费一次队列往返。 */
    public ExportTaskResponse create(ExportRequest request) {
        LoginUser user = UserContext.require();
        Target target = resolveTarget(request);

        ExportTask task = new ExportTask();
        task.setTaskId(UUID.randomUUID().toString().replace("-", ""));
        task.setUserId(user.userId());
        task.setUsername(user.username());
        task.setConnectionId(target.connectionId());
        task.setConnectionName(target.connectionName());
        task.setSqlText(target.sql());
        task.setMaxRows(target.maxRows());
        task.setSource(target.source());
        task.setStatus(ExportTask.STATUS_PENDING);
        task.setExpiresAt(LocalDateTime.now().plusMinutes(ttlMinutes));
        repository.save(task);

        if (asyncEnabled) {
            try {
                sender.send(task.getTaskId(), String.valueOf(target.connectionId()));
                log.info("导出任务已入队: taskId={}, connectionId={}, userId={}",
                        task.getTaskId(), target.connectionId(), user.userId());
                return ExportTaskResponse.from(task);
            } catch (RuntimeException e) {
                log.warn("投递导出队列失败，降级为同步导出: taskId={}, error={}", task.getTaskId(), e.getMessage());
            }
        }

        handle(task.getTaskId());
        return repository.findByTaskId(task.getTaskId())
                .map(ExportTaskResponse::from)
                .orElseGet(() -> ExportTaskResponse.from(task));
    }

    /**
     * 消费端入口：先原子认领，再把 SQL 跑完写回结果。
     *
     * <p>队列是至少一次投递，同一条消息可能被投多次；认领这一步保证了同一任务
     * 只会被真正执行一次。</p>
     */
    public void handle(String taskId) {
        LocalDateTime now = LocalDateTime.now();
        Integer claimed = transactionTemplate.execute(status -> repository.claim(
                taskId, ExportTask.STATUS_PENDING, ExportTask.STATUS_RUNNING, now));
        if (claimed == null || claimed == 0) {
            log.debug("导出任务已被认领或已完成，跳过: taskId={}", taskId);
            return;
        }

        ExportTask task = repository.findByTaskId(taskId).orElse(null);
        if (task == null) {
            log.warn("导出任务不存在: taskId={}", taskId);
            return;
        }

        try {
            SqlQueryResponse result = queryService.execute(task.getConnectionId(), task.getSqlText(),
                    task.getMaxRows(), task.getSource(), task.getUserId(), task.getUsername());
            task.setContent(CsvFormatter.toCsvText(result));
            task.setFileName(fileName(task));
            task.setRowCount(result.rowCount());
            task.setStatus(ExportTask.STATUS_DONE);
            task.setMessage(result.truncated() ? "结果超过行数上限，已截断" : null);
            log.info("导出任务完成: taskId={}, rows={}, elapsed={}ms",
                    taskId, result.rowCount(), result.elapsedMillis());
        } catch (RuntimeException e) {
            task.setStatus(ExportTask.STATUS_FAILED);
            task.setMessage(e.getMessage());
            log.warn("导出任务失败: taskId={}, error={}", taskId, e.getMessage());
        } finally {
            LocalDateTime finished = LocalDateTime.now();
            task.setFinishedAt(finished);
            task.setExpiresAt(finished.plusMinutes(ttlMinutes));
            repository.save(task);
        }
    }

    public PageResult<ExportTaskResponse> list(String scope, int page, int size) {
        LoginUser user = UserContext.require();
        int safePage = Math.max(page, 1);
        int safeSize = Math.clamp(size, 1, MAX_PAGE_SIZE);
        Pageable pageable = PageRequest.of(safePage - 1, safeSize);

        Page<ExportTask> result = "all".equalsIgnoreCase(scope) && Roles.isAdmin(user.role())
                ? repository.findAllByOrderByIdDesc(pageable)
                : repository.findByUserIdOrderByIdDesc(user.userId(), pageable);

        return new PageResult<>(result.getContent().stream().map(ExportTaskResponse::from).toList(),
                result.getTotalElements(), safePage, safeSize);
    }

    public ExportTaskResponse get(String taskId) {
        return ExportTaskResponse.from(requireReadable(taskId));
    }

    public DownloadedCsv download(String taskId) {
        ExportTask task = requireReadable(taskId);
        if (!ExportTask.STATUS_DONE.equals(task.getStatus()) || task.getContent() == null) {
            throw new BusinessException(ErrorCode.CONFLICT, "任务尚未完成，当前状态：" + task.getStatus());
        }
        return new DownloadedCsv(task.getFileName(), task.getContent().getBytes(StandardCharsets.UTF_8));
    }

    public void delete(String taskId) {
        ExportTask task = requireReadable(taskId);
        repository.delete(task);
    }

    /**
     * 兜底重投：队列消息丢了、实例被 kill、消费者卡死，任务就会一直停在
     * PENDING/RUNNING。这里定期把它们捡回来重新投递，靠认领 CAS 保证不会重复执行。
     */
    @Scheduled(fixedDelayString = "${app.export.queue.stuck-scan-ms:60000}")
    public void requeueStuck() {
        LocalDateTime threshold = LocalDateTime.now().minusMinutes(stuckMinutes);
        List<ExportTask> stuck = repository.findByStatusInAndUpdatedAtBefore(
                List.of(ExportTask.STATUS_PENDING, ExportTask.STATUS_RUNNING), threshold);
        for (ExportTask task : stuck) {
            try {
                task.setStatus(ExportTask.STATUS_PENDING);
                task.setUpdatedAt(LocalDateTime.now());
                task.setMessage("任务长时间未完成，已重新排队");
                repository.save(task);
                sender.send(task.getTaskId(), String.valueOf(task.getConnectionId()));
                log.warn("重投卡住的导出任务: taskId={}", task.getTaskId());
            } catch (RuntimeException e) {
                log.warn("重投导出任务失败: taskId={}, error={}", task.getTaskId(), e.getMessage());
            }
        }
    }

    /** 导出结果按 TTL 清理，避免 MEDIUMTEXT 越积越多。 */
    @Scheduled(fixedDelayString = "${app.export.cleanup-scan-ms:600000}")
    public void cleanupExpired() {
        Integer deleted = transactionTemplate.execute(status ->
                repository.deleteExpired(LocalDateTime.now()));
        if (deleted != null && deleted > 0) {
            log.info("清理过期导出结果: {} 条", deleted);
        }
    }

    private ExportTask requireReadable(String taskId) {
        LoginUser user = UserContext.require();
        ExportTask task = repository.findByTaskId(taskId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "导出任务不存在"));
        if (!Roles.isAdmin(user.role()) && !user.userId().equals(task.getUserId())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "只能查看自己的导出任务");
        }
        return task;
    }

    /** 把请求解析成"哪个库、跑哪条 SQL"。指标导出与查询台导出共用这一条路径。 */
    private Target resolveTarget(ExportRequest request) {
        if (request.metricId() != null) {
            MetricDefinition metric = metricRepository.findById(request.metricId())
                    .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "指标不存在"));
            if (!MetricDefinition.STATUS_ENABLED.equals(metric.getStatus())) {
                throw new BusinessException(ErrorCode.FORBIDDEN, "指标「" + metric.getName() + "」已停用");
            }
            DbConnection datasource = requireQueryable(metric.getDatasourceId());
            return new Target(datasource.getId(), datasource.getName(), metric.getSqlText(),
                    request.maxRows(), QueryHistory.SOURCE_METRIC);
        }

        if (request.connectionId() == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "请选择数据源");
        }
        if (request.sql() == null || request.sql().isBlank()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "SQL 不能为空");
        }
        DbConnection datasource = requireQueryable(request.connectionId());
        guard.validate(request.sql(), allowWrite);
        return new Target(datasource.getId(), datasource.getName(), request.sql(),
                request.maxRows(), QueryHistory.SOURCE_ADHOC);
    }

    private DbConnection requireQueryable(Long connectionId) {
        DbConnection datasource = connectionRepository.findById(connectionId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "连接不存在: " + connectionId));
        if (!datasource.isQueryEnabled()) {
            throw new BusinessException(ErrorCode.FORBIDDEN,
                    "连接「" + datasource.getName() + "」未开放查询");
        }
        return datasource;
    }

    private static String fileName(ExportTask task) {
        return "export-" + task.getTaskId().substring(0, 8) + "-"
                + FILE_STAMP.format(LocalDateTime.now()) + ".csv";
    }

    private record Target(Long connectionId, String connectionName, String sql,
                          Integer maxRows, String source) {
    }

    /** 下载用的载体。 */
    public record DownloadedCsv(String fileName, byte[] content) {
    }
}
