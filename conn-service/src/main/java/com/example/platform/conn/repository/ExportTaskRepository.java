package com.example.platform.conn.repository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.example.platform.conn.entity.ExportTask;

public interface ExportTaskRepository extends JpaRepository<ExportTask, Long> {

    Optional<ExportTask> findByTaskId(String taskId);

    Page<ExportTask> findByUserIdOrderByIdDesc(Long userId, Pageable pageable);

    Page<ExportTask> findAllByOrderByIdDesc(Pageable pageable);

    /**
     * 抢任务：只有把 PENDING 原子地改成 RUNNING 的那一次执行才算认领成功。
     *
     * <p>队列是至少一次投递，同一条消息可能被投多次，靠这个 CAS 保证只会跑一遍。</p>
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update ExportTask t
               set t.status = :running, t.updatedAt = :now
             where t.taskId = :taskId and t.status = :pending
            """)
    int claim(@Param("taskId") String taskId,
              @Param("pending") String pending,
              @Param("running") String running,
              @Param("now") LocalDateTime now);

    /** 卡住的未完成任务（消息丢了、实例被杀了），交给定时任务重投。 */
    List<ExportTask> findByStatusInAndUpdatedAtBefore(Collection<String> statuses, LocalDateTime before);

    @Modifying
    @Query("delete from ExportTask t where t.expiresAt < :before")
    int deleteExpired(@Param("before") LocalDateTime before);
}
