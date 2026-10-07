package com.example.platform.conn.repository;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.example.platform.conn.entity.DbConnection;

public interface DbConnectionRepository extends JpaRepository<DbConnection, Long> {

    boolean existsByName(String name);

    boolean existsByNameAndIdNot(String name, Long id);

    /** 可用于 SQL / 指标查询的连接。 */
    List<DbConnection> findByQueryEnabledTrueOrderByIdDesc();

    /**
     * 用空串哨兵代替 null 判断，避免 Hibernate 对参数类型推断报错。
     */
    @Query("""
            select c from DbConnection c
            where (:keyword = '' or lower(c.name) like lower(concat('%', :keyword, '%'))
                   or lower(coalesce(c.host, '')) like lower(concat('%', :keyword, '%')))
              and (:dbType = '' or c.dbType = :dbType)
            """)
    Page<DbConnection> search(@Param("keyword") String keyword,
                              @Param("dbType") String dbType,
                              Pageable pageable);
}
