package com.example.platform.conn.queue;

import java.util.concurrent.TimeUnit;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/**
 * Kafka 版投递：{@code app.export.queue.type=kafka} 时启用。
 *
 * <p>消息键用连接 ID，同一个库的导出任务落进同一分区；把消费者并发数设成
 * 每库允许的并发上限，就自然形成了"每个数据源最多 N 个导出在跑"。</p>
 *
 * <p>发送用同步等待，是为了在 broker 不可用时能立刻发现并降级为同步导出，
 * 而不是把任务悄悄丢掉。</p>
 */
@Component
@ConditionalOnProperty(prefix = "app.export.queue", name = "type", havingValue = "kafka")
public class KafkaExportSender implements ExportTaskSender {

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final String topic;

    public KafkaExportSender(KafkaTemplate<String, String> kafkaTemplate,
                             @Value("${app.export.queue.topic:query-export-tasks}") String topic) {
        this.kafkaTemplate = kafkaTemplate;
        this.topic = topic;
    }

    @Override
    public void send(String taskId, String partitionKey) {
        try {
            kafkaTemplate.send(topic, partitionKey == null ? taskId : partitionKey, taskId)
                    .get(3, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("投递 Kafka 被中断", e);
        } catch (Exception e) {
            throw new IllegalStateException("投递 Kafka 失败: " + e.getMessage(), e);
        }
    }
}
