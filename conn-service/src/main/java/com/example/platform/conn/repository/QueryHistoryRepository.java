package com.example.platform.conn.repository;

import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import com.example.platform.conn.entity.QueryHistory;

public interface QueryHistoryRepository extends JpaRepository<QueryHistory, Long> {

    Page<QueryHistory> findByUserIdOrderByIdDesc(Long userId, Pageable pageable);

    Page<QueryHistory> findAllByOrderByIdDesc(Pageable pageable);

    Optional<QueryHistory> findByIdAndUserId(Long id, Long userId);

    void deleteByUserId(Long userId);
}
