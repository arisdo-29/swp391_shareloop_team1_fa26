package com.shareloop.request.entity;

import com.shareloop.common.entity.BaseEntity;
import com.shareloop.common.exception.BusinessException;
import com.shareloop.request.RequestErrorCode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import org.hibernate.annotations.SQLRestriction;

/**
 * Entity bảng requests (ERD mục 7.5, 7.11).
 * Quản lý toàn bộ vòng đời giao dịch xin đồ / đổi đồ.
 */
@Entity
@Table(name = "requests")
@SQLRestriction("is_deleted = false")
public class Request extends BaseEntity {

    @Column(name = "item_id", nullable = false)
    private Long itemId;

    @Column(name = "receiver_id", nullable = false)
    private Long receiverId;

    @Column(name = "offered_item_id")
    private Long offeredItemId;

    @Column(name = "type", nullable = false, length = 10)
    private String type; // 'GIVE' hoặc 'SWAP'

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private RequestStatus status = RequestStatus.PENDING;

    @Column(name = "delivery_method", length = 12)
    private String deliveryMethod;

    @Column(name = "meeting_place", length = 255)
    private String meetingPlace;

    @Column(name = "meeting_time")
    private Instant meetingTime;

    @Column(name = "donor_waiver_at")
    private Instant donorWaiverAt;

    @Column(name = "receiver_waiver_at")
    private Instant receiverWaiverAt;

    @Column(name = "donor_schedule_ok_at")
    private Instant donorScheduleOkAt;

    @Column(name = "receiver_schedule_ok_at")
    private Instant receiverScheduleOkAt;

    @Column(name = "contact_revealed_at")
    private Instant contactRevealedAt;

    @Column(name = "donor_handover_at")
    private Instant donorHandoverAt;

    @Column(name = "receiver_handover_at")
    private Instant receiverHandoverAt;

    @Column(name = "as_described")
    private Boolean asDescribed;

    @Column(name = "auto_confirmed", nullable = false)
    private boolean autoConfirmed = false;

    @Column(name = "chat_violations", nullable = false)
    private short chatViolations = 0;

    @Column(name = "reserved_at")
    private Instant reservedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "cancel_reason", length = 100)
    private String cancelReason;

    protected Request() {}

    public Request(Long itemId, Long receiverId, Long offeredItemId, String type) {
        this.itemId = itemId;
        this.receiverId = receiverId;
        this.offeredItemId = offeredItemId;
        this.type = type;
        this.status = RequestStatus.PENDING;
    }

    /**
     * Chuyển trạng thái yêu cầu theo sơ đồ vòng đời (ERD 5.1).
     *
     * @param nextStatus trạng thái đích
     * @throws BusinessException nếu bước chuyển không hợp lệ
     */
    public void transitionTo(RequestStatus nextStatus) {
        if (!RequestStatus.canTransition(this.status, nextStatus)) {
            throw new BusinessException(RequestErrorCode.REQUEST_INVALID_STATE_TRANSITION);
        }
        this.status = nextStatus;
    }

    /**
     * Huỷ yêu cầu khi còn PENDING (BR-R04).
     */
    public void cancel(String reason) {
        transitionTo(RequestStatus.CANCELLED);
        this.cancelReason = reason;
    }

    /**
     * Chủ bài chọn người nhận (FR-66, BR-D01). Chuyển sang RESERVED và ghi nhận reserved_at.
     */
    public void select(Instant now) {
        transitionTo(RequestStatus.RESERVED);
        this.reservedAt = now;
    }

    /**
     * Tự động từ chối yêu cầu (BR-D01). Chuyển sang REJECTED.
     */
    public void reject() {
        transitionTo(RequestStatus.REJECTED);
    }

    /**
     * Đề xuất hoặc cập nhật lịch hẹn giao nhận (FR-79, FR-80, BR-T01, BR-T02).
     *
     * <p>Chuyển sang AWAITING_LOGISTICS nếu đang là RESERVED.
     * Bên đề xuất tự động được ghi nhận đồng ý lịch mới, bên còn lại phải xác nhận lại.
     */
    public void proposeSchedule(
            String deliveryMethod,
            String meetingPlace,
            Instant meetingTime,
            boolean isDonor,
            boolean agreeToWaiver,
            Instant now) {
        if (this.status == RequestStatus.RESERVED) {
            transitionTo(RequestStatus.AWAITING_LOGISTICS);
        } else if (this.status != RequestStatus.AWAITING_LOGISTICS) {
            throw new BusinessException(RequestErrorCode.REQUEST_SCHEDULE_LOCKED);
        }

        this.deliveryMethod = deliveryMethod;
        this.meetingPlace = meetingPlace;
        this.meetingTime = meetingTime;

        boolean isShipping = "SHIPPING".equalsIgnoreCase(deliveryMethod);
        if (isDonor) {
            this.donorScheduleOkAt = now;
            this.receiverScheduleOkAt = null;
            if (isShipping && agreeToWaiver) {
                this.donorWaiverAt = now;
            }
        } else {
            this.receiverScheduleOkAt = now;
            this.donorScheduleOkAt = null;
            if (isShipping && agreeToWaiver) {
                this.receiverWaiverAt = now;
            }
        }
    }

    /**
     * Xác nhận lịch hẹn giao nhận và cam kết trách nhiệm (FR-80, FR-81, BR-T03, BR-T05).
     *
     * <p>Khi cả hai bên đều đã đồng ý lịch (và cam kết trách nhiệm nếu là SHIPPING),
     * Request chuyển sang LOGISTICS_CONFIRMED và ghi nhận contactRevealedAt.
     */
    public void confirmLogistics(boolean isDonor, boolean agreeToWaiver, Instant now) {
        if (this.status != RequestStatus.AWAITING_LOGISTICS) {
            throw new BusinessException(RequestErrorCode.REQUEST_INVALID_STATE_TRANSITION);
        }
        if (this.meetingTime == null || this.deliveryMethod == null) {
            throw new BusinessException(RequestErrorCode.REQUEST_SCHEDULE_NOT_SET);
        }

        boolean isShipping = "SHIPPING".equalsIgnoreCase(this.deliveryMethod);
        if (isShipping) {
            boolean alreadyWaived = isDonor ? (this.donorWaiverAt != null) : (this.receiverWaiverAt != null);
            if (!agreeToWaiver && !alreadyWaived) {
                throw new BusinessException(RequestErrorCode.REQUEST_WAIVER_REQUIRED);
            }
            if (agreeToWaiver) {
                if (isDonor) {
                    this.donorWaiverAt = now;
                } else {
                    this.receiverWaiverAt = now;
                }
            }
        }

        if (isDonor) {
            this.donorScheduleOkAt = now;
        } else {
            this.receiverScheduleOkAt = now;
        }

        boolean donorOk = this.donorScheduleOkAt != null && (!isShipping || this.donorWaiverAt != null);
        boolean receiverOk = this.receiverScheduleOkAt != null && (!isShipping || this.receiverWaiverAt != null);

        if (donorOk && receiverOk) {
            transitionTo(RequestStatus.LOGISTICS_CONFIRMED);
            this.contactRevealedAt = now;
        }
    }

    public Long getItemId() {
        return itemId;
    }

    public Long getReceiverId() {
        return receiverId;
    }

    public Long getOfferedItemId() {
        return offeredItemId;
    }

    public String getType() {
        return type;
    }

    public RequestStatus getStatus() {
        return status;
    }

    public String getDeliveryMethod() {
        return deliveryMethod;
    }

    public String getMeetingPlace() {
        return meetingPlace;
    }

    public Instant getMeetingTime() {
        return meetingTime;
    }

    public Instant getDonorWaiverAt() {
        return donorWaiverAt;
    }

    public Instant getReceiverWaiverAt() {
        return receiverWaiverAt;
    }

    public Instant getDonorScheduleOkAt() {
        return donorScheduleOkAt;
    }

    public Instant getReceiverScheduleOkAt() {
        return receiverScheduleOkAt;
    }

    public Instant getContactRevealedAt() {
        return contactRevealedAt;
    }

    public Instant getDonorHandoverAt() {
        return donorHandoverAt;
    }

    public Instant getReceiverHandoverAt() {
        return receiverHandoverAt;
    }

    public Boolean getAsDescribed() {
        return asDescribed;
    }

    public boolean isAutoConfirmed() {
        return autoConfirmed;
    }

    public short getChatViolations() {
        return chatViolations;
    }

    public Instant getReservedAt() {
        return reservedAt;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    public String getCancelReason() {
        return cancelReason;
    }
}
