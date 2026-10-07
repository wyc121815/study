package com.example.platform.conn.queue;

import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.StreamRecords;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * Redis Streams 版投递。选它是因为平台本来就依赖 Redis，
 * 消费组 + ACK + pending 列表已经能满足"至少一次 + 失败可见"的队列语义。
 */
@Component
@ConditionalOnProperty(prefix = "app.export.queue", name = "type", havingValue = "redis", matchIfMissing = true)
public class RedisStreamExportSender implements ExportTaskSender {

    private final StringRedisTemplate redis;
    private final String stream;

    public RedisStreamExportSender(StringRedisTemplate redis,
                                   @Value("${app.export.queue.stream:query:export:tasks}") String stream) {
        this.redis = redis;
        this.stream = stream;
    }

    @Override
    public void send(String taskId, String partitionKey) {
        MapRecord<String, String, String> record = StreamRecords
                .mapBacked(Map.of("taskId", taskId, "partitionKey", partitionKey == null ? "" : partitionKey))
                .withStreamKey(stream);
        redis.opsForStream().add(record);
    }
}
