package com.example.platform.auth.repository;

import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.example.platform.auth.entity.SysUser;

public interface SysUserRepository extends JpaRepository<SysUser, Long> {

    Optional<SysUser> findByUsername(String username);

    boolean existsByUsername(String username);

    /** 用空串哨兵代替 null 判断，避免 Hibernate 对参数类型推断报错。 */
    @Query("""
            select u from SysUser u
            where (:keyword = '' or lower(u.username) like lower(concat('%', :keyword, '%'))
                   or lower(u.nickname) like lower(concat('%', :keyword, '%')))
            """)
    Page<SysUser> search(@Param("keyword") String keyword, Pageable pageable);

    /**
     * 统计某个角色的启用用户数。
     *
     * <p>用 {@code lower()} 显式做大小写无关比较，不依赖数据库排序规则——
     * MySQL 默认不区分大小写，换成别的库或改了 collation 就会漏判。</p>
     */
    @Query("""
            select count(u) from SysUser u
            where lower(u.role) = lower(:role) and u.status = :status
            """)
    long countByRoleIgnoreCase(@Param("role") String role, @Param("status") Integer status);
}
