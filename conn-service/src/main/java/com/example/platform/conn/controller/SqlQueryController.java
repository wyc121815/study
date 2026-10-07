package com.example.platform.conn.controller;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.platform.common.core.api.PageResult;
import com.example.platform.common.core.api.Result;
import com.example.platform.conn.dto.QueryHistoryItem;
import com.example.platform.conn.dto.SqlQueryRequest;
import com.example.platform.conn.dto.SqlQueryResponse;
import com.example.platform.conn.entity.QueryHistory;
import com.example.platform.conn.service.QueryHistoryService;
import com.example.platform.conn.service.SqlQueryService;

import jakarta.validation.Valid;

/**
 * SQL 执行入口。默认只读，写操作需要服务端开启 {@code app.query.allow-write}。
 */
@RestController
@RequestMapping("/api/queries")
public class SqlQueryController {

    private static final DateTimeFormatter FILE_STAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    private final SqlQueryService service;
    private final QueryHistoryService historyService;

    public SqlQueryController(SqlQueryService service, QueryHistoryService historyService) {
        this.service = service;
        this.historyService = historyService;
    }

    @PostMapping("/execute")
    public Result<SqlQueryResponse> execute(@Valid @RequestBody SqlQueryRequest request) {
        return Result.ok(service.execute(request));
    }

    /** 导出 CSV：直接返回文件流，不走统一返回体。 */
    @PostMapping("/export")
    public ResponseEntity<byte[]> export(@Valid @RequestBody SqlQueryRequest request) {
        byte[] csv = service.exportCsv(request.connectionId(), request.sql(), request.maxRows(),
                QueryHistory.SOURCE_ADHOC);
        String filename = "query-" + FILE_STAMP.format(LocalDateTime.now()) + ".csv";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(new MediaType("text", "csv", java.nio.charset.StandardCharsets.UTF_8))
                .body(csv);
    }

    /** 查询历史：默认只看自己的，ADMIN 传 scope=all 可看全部。 */
    @GetMapping("/history")
    public Result<PageResult<QueryHistoryItem>> history(
            @RequestParam(defaultValue = "mine") String scope,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return Result.ok(historyService.page(scope, page, size));
    }

    @DeleteMapping("/history/{id}")
    public Result<Void> deleteHistory(@PathVariable Long id) {
        historyService.delete(id);
        return Result.ok();
    }

    @DeleteMapping("/history")
    public Result<Void> clearHistory(@RequestParam(defaultValue = "mine") String scope) {
        historyService.clear(scope);
        return Result.ok();
    }
}
