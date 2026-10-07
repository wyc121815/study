package com.example.platform.conn.queue;

/**
 * 把导出任务投递到队列。实现按 {@code app.export.queue.type} 选择
 * （默认 Redis Streams，可选 Kafka）。
 */
public interface ExportTaskSender {

    /**
     * @param taskId       任务号
     * @param partitionKey 分区键，用连接 ID —— 同一个数据源的任务落在同一分区，
     *                     消费者并发数就成了天然的"每库并发上限"
     */
    void send(String taskId, String partitionKey);
}
