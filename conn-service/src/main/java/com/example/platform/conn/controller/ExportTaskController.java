package com.example.platform.conn.controller;

import java.nio.charset.StandardCharsets;

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
import com.example.platform.conn.dto.ExportRequest;
import com.example.platform.conn.dto.ExportTaskResponse;
import com.example.platform.conn.service.ExportTaskService;

/**
 * 批量导出任务。提交即返回任务号，前端轮询状态、完成后下载，避免长查询占着请求线程。
 */
@RestController
@RequestMapping("/api/exports")
public class ExportTaskController {

    private final ExportTaskService service;

    public ExportTaskController(ExportTaskService service) {
        this.service = service;
    }

    @PostMapping
    public Result<ExportTaskResponse> create(@RequestBody ExportRequest request) {
        return Result.ok(service.create(request));
    }

    @GetMapping
    public Result<PageResult<ExportTaskResponse>> list(
            @RequestParam(defaultValue = "mine") String scope,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return Result.ok(service.list(scope, page, size));
    }

    @GetMapping("/{taskId}")
    public Result<ExportTaskResponse> get(@PathVariable String taskId) {
        return Result.ok(service.get(taskId));
    }

    @DeleteMapping("/{taskId}")
    public Result<Void> delete(@PathVariable String taskId) {
        service.delete(taskId);
        return Result.ok();
    }

    @GetMapping("/{taskId}/download")
    public ResponseEntity<byte[]> download(@PathVariable String taskId) {
        ExportTaskService.DownloadedCsv file = service.download(taskId);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + file.fileName() + "\"")
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .body(file.content());
    }
}
