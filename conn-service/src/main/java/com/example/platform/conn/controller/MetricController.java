package com.example.platform.conn.controller;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.platform.common.core.api.Result;
import com.example.platform.conn.dto.MetricRequest;
import com.example.platform.conn.dto.MetricRankItem;
import com.example.platform.conn.dto.MetricResponse;
import com.example.platform.conn.dto.SqlQueryResponse;
import com.example.platform.conn.service.MetricService;

import jakarta.validation.Valid;

/**
 * 指标：所有登录用户可查看与执行，创建/修改/删除限创建人或管理员。
 */
@RestController
@RequestMapping("/api/metrics")
public class MetricController {

    private static final DateTimeFormatter FILE_STAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    private final MetricService service;

    public MetricController(MetricService service) {
        this.service = service;
    }

    @GetMapping
    public Result<List<MetricResponse>> list() {
        return Result.ok(service.list());
    }

    @GetMapping("/{id}")
    public Result<MetricResponse> get(@PathVariable Long id) {
        return Result.ok(service.get(id));
    }

    @PostMapping
    public Result<MetricResponse> create(@Valid @RequestBody MetricRequest request) {
        return Result.ok(service.create(request));
    }

    @PutMapping("/{id}")
    public Result<MetricResponse> update(@PathVariable Long id, @Valid @RequestBody MetricRequest request) {
        return Result.ok(service.update(id, request));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return Result.ok();
    }

    @PostMapping("/{id}/run")
    public Result<SqlQueryResponse> run(@PathVariable Long id,
                                        @RequestParam(required = false) Integer maxRows,
                                        @RequestParam(defaultValue = "false") boolean noCache) {
        return Result.ok(service.run(id, maxRows, noCache));
    }

    /** 指标热度排行（按被查询次数），次数来自 Redis。 */
    @GetMapping("/ranking")
    public Result<List<MetricRankItem>> ranking(@RequestParam(defaultValue = "10") int limit) {
        return Result.ok(service.ranking(limit));
    }

    /** 导出指标结果为 CSV，直接返回文件流。 */
    @PostMapping("/{id}/export")
    public ResponseEntity<byte[]> export(@PathVariable Long id,
                                         @RequestParam(required = false) Integer maxRows) {
        byte[] csv = service.exportCsv(id, maxRows);
        String filename = "metric-" + id + "-" + FILE_STAMP.format(LocalDateTime.now()) + ".csv";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .body(csv);
    }
}
