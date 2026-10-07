package com.example.platform.conn.queue;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import com.example.platform.conn.service.ExportTaskService;

/**
 * Kafka 消费者。ACK 模式用默认的批量提交，重复消费由任务表的 CAS 认领去重。
 */
@Component
@ConditionalOnProperty(prefix = "app.export.queue", name = "type", havingValue = "kafka")
public class KafkaExportListener {

    private static final Logger log = LoggerFactory.getLogger(KafkaExportListener.class);

    private final ExportTaskService exportTaskService;

    public KafkaExportListener(ExportTaskService exportTaskService) {
        this.exportTaskService = exportTaskService;
    }

    @KafkaListener(topics = "${app.export.queue.topic:query-export-tasks}",
            groupId = "${app.export.queue.consumer-group:conn-service-export}")
    public void onMessage(String taskId) {
        try {
            exportTaskService.handle(taskId);
        } catch (RuntimeException e) {
            log.warn("处理导出任务异常，交给兜底重投: taskId={}, error={}", taskId, e.getMessage());
        }
    }
}
