package com.example.platform.conn.dto;

import java.time.LocalDateTime;

import com.example.platform.conn.entity.ExportTask;

/**
 * 导出任务状态。不返回 CSV 正文，正文走下载接口。
 */
public record ExportTaskResponse(
        String taskId,
        String status,
        String connectionName,
        String username,
        String sql,
        Integer rowCount,
        String fileName,
        String message,
        boolean downloadable,
        LocalDateTime createdAt,
        LocalDateTime finishedAt,
        LocalDateTime expiresAt) {

    public static ExportTaskResponse from(ExportTask task) {
        return new ExportTaskResponse(
                task.getTaskId(),
                task.getStatus(),
                task.getConnectionName(),
                task.getUsername(),
                task.getSqlText(),
                task.getRowCount(),
                task.getFileName(),
                task.getMessage(),
                ExportTask.STATUS_DONE.equals(task.getStatus()) && task.getContent() != null,
                task.getCreatedAt(),
                task.getFinishedAt(),
                task.getExpiresAt());
    }
}
