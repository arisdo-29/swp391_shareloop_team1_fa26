package com.shareloop.item.entity;

import com.shareloop.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import org.hibernate.annotations.SQLRestriction;

/**
 * Entity bảng items - lát cắt nội dung và trạng thái cơ bản (Lộ trình 4.4, SRS v10).
 * Cột phí, AI, review thuộc module khác nên không map ở đây.
 */
@Entity
@Table(name = "items")
@SQLRestriction("is_deleted = false")
public class Item extends BaseEntity {

    @Column(name = "donor_id", nullable = false)
    private Long donorId;

    @Column(name = "category_id", nullable = false)
    private Long categoryId;

    @Column(name = "area_id", nullable = false)
    private Long areaId;

    @Enumerated(EnumType.STRING)
    @Column(name = "offer_type", nullable = false, length = 10)
    private OfferType offerType;

    @Column(name = "title", nullable = false, length = 150)
    private String title;

    @Column(name = "description", nullable = false, columnDefinition = "TEXT")
    private String description;

    @Column(name = "desired_item", columnDefinition = "TEXT")
    private String desiredItem;

    @Enumerated(EnumType.STRING)
    @Column(name = "condition", nullable = false, length = 12)
    private ItemCondition condition;

    @Column(name = "defect_note", columnDefinition = "TEXT")
    private String defectNote;

    @Column(name = "brand", length = 80)
    private String brand;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private ItemStatus status = ItemStatus.PENDING_REVIEW;

    protected Item() {}

    public Item(
            Long donorId,
            Long categoryId,
            Long areaId,
            OfferType offerType,
            String title,
            String description,
            String desiredItem,
            ItemCondition condition,
            String defectNote,
            String brand) {
        this.donorId = donorId;
        this.categoryId = categoryId;
        this.areaId = areaId;
        this.offerType = offerType;
        this.title = title;
        this.description = description;
        this.desiredItem = desiredItem;
        this.condition = condition;
        this.defectNote = defectNote;
        this.brand = brand;
        this.status = ItemStatus.PENDING_REVIEW;
    }

    public Long getDonorId() {
        return donorId;
    }

    public Long getCategoryId() {
        return categoryId;
    }

    public Long getAreaId() {
        return areaId;
    }

    public OfferType getOfferType() {
        return offerType;
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    public String getDesiredItem() {
        return desiredItem;
    }

    public ItemCondition getCondition() {
        return condition;
    }

    public String getDefectNote() {
        return defectNote;
    }

    public String getBrand() {
        return brand;
    }

    public ItemStatus getStatus() {
        return status;
    }

    public void approve() {
        this.status = ItemStatus.APPROVED;
    }
}
