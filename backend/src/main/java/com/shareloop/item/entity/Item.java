package com.shareloop.item.entity;

import com.shareloop.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import org.hibernate.annotations.SQLRestriction;

/**
 * Entity bảng items - lát cắt nội dung và trạng thái cơ bản (SRS Final Report 09/11, mục 3.1.5).
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

    @Column(name = "reference_price")
    private Long referencePrice;

    @Column(name = "review_submitted_at")
    private Instant reviewSubmittedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private ItemStatus status = ItemStatus.PENDING_REVIEW;

    // Bài do Admin ẩn: chỉ nút "Hiện lại" của Admin mới hiện lại được, các đường tự động bỏ qua bài có cờ này.
    @Column(name = "hidden_by_admin", nullable = false)
    private boolean hiddenByAdmin = false;

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

    public Long getReferencePrice() {
        return referencePrice;
    }

    public void setReferencePrice(long referencePrice) {
        this.referencePrice = referencePrice;
    }

    public Instant getReviewSubmittedAt() {
        return reviewSubmittedAt;
    }

    public ItemStatus getStatus() {
        return status;
    }

    @Column(name = "exchange_shipping_hold_request_id")
    private Long exchangeShippingHoldRequestId;

    @Column(name = "exchange_shipping_restore_visibility", nullable = false)
    private boolean exchangeShippingRestoreVisibility;

    public boolean holdForExchangeShipping(long requestId) {
        if (exchangeShippingHoldRequestId != null || (status != ItemStatus.RESERVED && status != ItemStatus.APPROVED)) {
            return false;
        }
        exchangeShippingRestoreVisibility = isActive();
        exchangeShippingHoldRequestId = requestId;
        super.deactivate();
        return true;
    }

    public boolean releaseExchangeShippingHold(long requestId) {
        if (!java.util.Objects.equals(exchangeShippingHoldRequestId, requestId)) {
            return false;
        }
        exchangeShippingHoldRequestId = null;
        if (exchangeShippingRestoreVisibility && !hiddenByAdmin && status == ItemStatus.APPROVED) {
            super.activate();
        }
        exchangeShippingRestoreVisibility = false;
        return true;
    }

    public boolean isHeldForExchangeShipping(long requestId) {
        return java.util.Objects.equals(exchangeShippingHoldRequestId, requestId);
    }

    @Override
    public void activate() {
        if (exchangeShippingHoldRequestId == null) {
            super.activate();
        }
    }

    @Override
    public void deactivate() {
        if (exchangeShippingHoldRequestId != null) {
            exchangeShippingRestoreVisibility = false;
        }
        super.deactivate();
    }

    public boolean isHiddenByAdmin() {
        return hiddenByAdmin;
    }

    /** Admin ẩn bài: tắt hiển thị và gắn cờ để các đường tự động không hiện lại. */
    public void hideByAdmin() {
        deactivate();
        hiddenByAdmin = true;
    }

    /** Admin hiện lại bài: bật hiển thị và xoá cờ. */
    public void unhideByAdmin() {
        activate();
        hiddenByAdmin = false;
    }

    public void approve() {
        transitionTo(ItemStatus.APPROVED);
    }

    public void submitEdit(
            Long categoryId,
            Long areaId,
            OfferType offerType,
            String title,
            String description,
            String desiredItem,
            ItemCondition condition,
            String defectNote,
            String brand) {
        if (status != ItemStatus.PENDING_REVIEW) {
            transitionTo(ItemStatus.PENDING_REVIEW);
        }
        this.reviewSubmittedAt = Instant.now();
        this.categoryId = categoryId;
        this.areaId = areaId;
        this.offerType = offerType;
        this.title = title;
        this.description = description;
        this.desiredItem = desiredItem;
        this.condition = condition;
        this.defectNote = defectNote;
        this.brand = brand;
    }

    public void transitionTo(ItemStatus target) {
        if (!ItemStatus.canTransition(status, target)) {
            throw new IllegalStateException("Invalid item status transition: " + status + " -> " + target);
        }
        status = target;
    }

    public void reserve() {
        transitionTo(ItemStatus.RESERVED);
    }

    public void release() {
        transitionTo(ItemStatus.APPROVED);
    }

    public void markTraded() {
        transitionTo(ItemStatus.TRADED);
    }

    public void markExpired() {
        transitionTo(ItemStatus.EXPIRED);
    }

    /**
     * Chuyển đổi hình thức Cho ↔ Trao đổi khi chọn người nhận (CD-02, CD-04, CD-06).
     *
     * <p>Khi chuyển sang SWAP, nếu chưa có desiredItem thì gán giá trị mặc định để thoả mãn check constraint CSDL.
     *
     * @param newOfferType loại mới (GIVE hoặc SWAP)
     */
    public void convertOfferType(OfferType newOfferType) {
        if (newOfferType == null) {
            throw new IllegalArgumentException("OfferType cannot be null");
        }
        this.offerType = newOfferType;
        if (newOfferType == OfferType.SWAP && (this.desiredItem == null || this.desiredItem.isBlank())) {
            this.desiredItem = "Trao đổi theo yêu cầu đã chọn";
        }
    }
}
