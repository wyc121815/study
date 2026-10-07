package com.example.platform.auth.entity;

import java.time.LocalDateTime;
import java.time.Duration;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

/**
 * 平台用户。表结构由 deploy/mysql/init 下的脚本维护。
 */
@Entity
@Table(name = "sys_user")
public class SysUser {

    /** 1=启用，0=禁用。 */
    public static final int STATUS_ENABLED = 1;
    public static final int STATUS_DISABLED = 0;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "username", nullable = false, length = 64, unique = true)
    private String username;

    /** BCrypt 哈希，永远不返回给前端。 */
    @Column(name = "password", nullable = false, length = 100)
    private String password;

    @Column(name = "nickname", nullable = false, length = 64)
    private String nickname;

    @Column(name = "role", nullable = false, length = 32)
    private String role = "USER";

    @Column(name = "status", nullable = false)
    private Integer status = STATUS_ENABLED;

    /** 连续登录失败次数，登录成功后清零。 */
    @Column(name = "failed_attempts", nullable = false)
    private Integer failedAttempts = 0;

    /** 锁定截止时间，为空表示未锁定。 */
    @Column(name = "locked_until")
    private LocalDateTime lockedUntil;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    public boolean isEnabled() {
        return status != null && status == STATUS_ENABLED;
    }

    /** 当前是否处于临时锁定状态。 */
    public boolean isLockedAt(LocalDateTime now) {
        return lockedUntil != null && lockedUntil.isAfter(now);
    }

    /**
     * 记一次登录失败。达到阈值就锁定一段时间，并清零计数，锁定期满后重新计数。
     */
    public void registerFailedAttempt(int maxAttempts, Duration lockFor, LocalDateTime now) {
        int attempts = (failedAttempts == null ? 0 : failedAttempts) + 1;
        if (attempts >= maxAttempts) {
            this.failedAttempts = 0;
            this.lockedUntil = now.plus(lockFor);
        } else {
            this.failedAttempts = attempts;
        }
    }

    /** 登录成功后清掉失败计数与锁定。 */
    public void resetFailedAttempts() {
        this.failedAttempts = 0;
        this.lockedUntil = null;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getNickname() {
        return nickname;
    }

    public void setNickname(String nickname) {
        this.nickname = nickname;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public Integer getStatus() {
        return status;
    }

    public void setStatus(Integer status) {
        this.status = status;
    }

    public Integer getFailedAttempts() {
        return failedAttempts;
    }

    public void setFailedAttempts(Integer failedAttempts) {
        this.failedAttempts = failedAttempts;
    }

    public LocalDateTime getLockedUntil() {
        return lockedUntil;
    }

    public void setLockedUntil(LocalDateTime lockedUntil) {
        this.lockedUntil = lockedUntil;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
