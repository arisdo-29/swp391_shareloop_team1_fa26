package com.shareloop.catalog.entity;

import com.shareloop.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import org.hibernate.annotations.SQLRestriction;

/** Bảng item_categories. Id và 6 cột chuẩn đã có ở BaseEntity; ở đây chỉ map cột nghiệp vụ. */
@Entity
@Table(name = "item_categories")
@SQLRestriction("is_deleted = false") // bản ghi xoá mềm tự bị loại khỏi mọi truy vấn
public class ItemCategory extends BaseEntity {

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    // Giữa các bảng cùng module vẫn chỉ lưu id, không @ManyToOne.
    @Column(name = "parent_id")
    private Long parentId;

    @Column(name = "is_restricted", nullable = false)
    private boolean restricted;

    @Column(name = "is_boostable", nullable = false)
    private boolean boostable = true;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    protected ItemCategory() {}

    public ItemCategory(String name, Long parentId, boolean restricted, boolean boostable, int sortOrder) {
        this.name = name;
        this.parentId = parentId;
        this.restricted = restricted;
        this.boostable = boostable;
        this.sortOrder = sortOrder;
    }

    public String getName() {
        return name;
    }

    public Long getParentId() {
        return parentId;
    }

    public boolean isRestricted() {
        return restricted;
    }

    public boolean isBoostable() {
        return boostable;
    }

    public int getSortOrder() {
        return sortOrder;
    }
}
