package com.example.platform.conn.queue;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.connection.stream.Consumer;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.connection.stream.StreamOffset;
import org.springframework.data.redis.connection.stream.StreamReadOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import com.example.platform.conn.service.ExportTaskService;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;

/**
 * Redis Streams 消费者：阻塞读 + 处理后 ACK。
 *
 * <p>消费者名带 UUID，多实例各读各的，同一个消息只会被组内一个消费者拿到。
 * 处理失败也 ACK——重试由数据库侧的"卡住重投"兜底，避免消息在 pending 里无限重放。</p>
 */
@Component
@ConditionalOnProperty(prefix = "app.export.queue", name = "type", havingValue = "redis", matchIfMissing = true)
public class RedisStreamExportWorker {

    private static final Logger log = LoggerFactory.getLogger(RedisStreamExportWorker.class);

    private final StringRedisTemplate redis;
    private final ExportTaskService exportTaskService;
    private final String stream;
    private final String group;
    private final String consumerName = "conn-" + UUID.randomUUID().toString().substring(0, 8);

    private volatile boolean running = true;
    private Thread worker;

    public RedisStreamExportWorker(StringRedisTemplate redis,
                                   ExportTaskService exportTaskService,
                                   @Value("${app.export.queue.stream:query:export:tasks}") String stream,
                                   @Value("${app.export.queue.consumer-group:conn-service-export}") String group) {
        this.redis = redis;
        this.exportTaskService = exportTaskService;
        this.stream = stream;
        this.group = group;
    }

    @PostConstruct
    void start() {
        createGroupIfAbsent();
        worker = new Thread(this::loop, "export-stream-worker");
        worker.setDaemon(true);
        worker.start();
        log.info("导出队列已启动: stream={}, group={}, consumer={}", stream, group, consumerName);
    }

    @PreDestroy
    void stop() {
        running = false;
        if (worker != null) {
            worker.interrupt();
        }
    }

    private void createGroupIfAbsent() {
        try {
            redis.opsForStream().createGroup(stream, ReadOffset.from("0"), group);
        } catch (Exception e) {
            // 已存在会报 BUSYGROUP，属于正常情况
            log.debug("消费组已存在或创建失败: stream={}, group={}, reason={}", stream, group, e.getMessage());
        }
    }

    private void loop() {
        while (running) {
            try {
                List<MapRecord<String, Object, Object>> records = redis.opsForStream().read(
                        Consumer.from(group, consumerName),
                        StreamReadOptions.empty().count(1).block(Duration.ofSeconds(2)),
                        StreamOffset.create(stream, ReadOffset.lastConsumed()));
                if (records == null || records.isEmpty()) {
                    continue;
                }
                records.forEach(this::process);
            } catch (Exception e) {
                if (running) {
                    log.warn("读取导出队列失败，1 秒后重试: {}", e.getMessage());
                    sleep();
                }
            }
        }
    }

    private void process(MapRecord<String, Object, Object> record) {
        Object taskId = record.getValue().get("taskId");
        try {
            if (taskId != null) {
                exportTaskService.handle(taskId.toString());
            }
        } catch (RuntimeException e) {
            log.warn("处理导出任务异常，交给兜底重投: taskId={}, error={}", taskId, e.getMessage());
        } finally {
            try {
                redis.opsForStream().acknowledge(stream, group, record.getId());
            } catch (Exception e) {
                log.warn("ACK 导出任务失败: taskId={}, error={}", taskId, e.getMessage());
            }
        }
    }

    private void sleep() {
        try {
            Thread.sleep(1_000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            running = false;
        }
    }
}
