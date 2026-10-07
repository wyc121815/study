package com.example.platform.conn.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.platform.conn.entity.MetricDefinition;

public interface MetricDefinitionRepository extends JpaRepository<MetricDefinition, Long> {

    boolean existsByName(String name);

    boolean existsByNameAndIdNot(String name, Long id);

    List<MetricDefinition> findAllByOrderByIdDesc();

    long countByStatus(String status);
}
