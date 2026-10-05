package com.shareloop.catalog.entity;

import com.shareloop.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import org.hibernate.annotations.SQLRestriction;

@Entity
@Table(name = "areas")
@SQLRestriction("is_deleted = false")
public class Area extends BaseEntity {

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "parent_id")
    private Long parentId;

    @Column(name = "level", nullable = false)
    private short level;

    protected Area() {}

    public Area(String name, Long parentId, short level) {
        this.name = name;
        this.parentId = parentId;
        this.level = level;
    }

    public String getName() {
        return name;
    }

    public Long getParentId() {
        return parentId;
    }

    public short getLevel() {
        return level;
    }
}
