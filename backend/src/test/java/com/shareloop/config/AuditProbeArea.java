package com.shareloop.config;

import com.shareloop.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import org.hibernate.annotations.SQLRestriction;

/** Entity chỉ dùng trong test, map bảng areas để kiểm tra JPA Auditing. */
@Entity
@Table(name = "areas")
@SQLRestriction("is_deleted = false")
public class AuditProbeArea extends BaseEntity {

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "level", nullable = false)
    private short level;

    protected AuditProbeArea() {}

    public AuditProbeArea(String name, short level) {
        this.name = name;
        this.level = level;
    }
}
