package com.shareloop.common.entity;

import jakarta.persistence.Column;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.MappedSuperclass;
import java.time.Instant;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/**
 * Cột audit tối thiểu cho entity lát cắt của bảng dùng chung (users, items): chỉ map updated_at, updated_by để nhiều
 * entity cùng bảng không map trùng cột.
 */
@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
public abstract class SliceAudit {

    @LastModifiedDate
    @Column(name = "updated_at")
    private Instant updatedAt;

    @LastModifiedBy
    @Column(name = "updated_by")
    private Long updatedBy;

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public Long getUpdatedBy() {
        return updatedBy;
    }
}
