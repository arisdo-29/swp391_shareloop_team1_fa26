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
 * Entity bảng requests.
 * Quản lý toàn bộ vòng đời giao dịch xin đồ / đổi đồ.
 */
@Entity
@Table(name = "requests")
@SQLRestriction("is_deleted = false")
public class Request extends BaseEntity {

    // Tiền tố ghi vào cancel_reason để người xem biết ai huỷ (không có cột riêng cho người huỷ)
    public static final String CANCEL_BY_DONOR = "[Chủ bài] ";
    public static final String CANCEL_BY_RECEIVER = "[Người nhận] ";
    public static final String CANCEL_BY_SYSTEM = "[Hệ thống] ";
    public static final String CANCEL_BY_ADMIN = "[Admin] ";

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

    @Column(name = "auto_confirmed", nullable = false)
    private boolean autoConfirmed = false;

    @Column(name = "chat_violations", nullable = false)
    private short chatViolations = 0;

    @Column(name = "reserved_at")
    private Instant reservedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    // 500 ký tự người dùng nhập + tiền tố người huỷ
    @Column(name = "cancel_reason", length = 600)
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
     * Chuyển trạng thái yêu cầu theo sơ đồ vòng đời (SRS mục 3.9).
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
     * Huỷ yêu cầu khi còn PENDING (YC-05).
     */
    public void cancel(String reason) {
        transitionTo(RequestStatus.CANCELLED);
        this.cancelReason = reason;
    }

    /**
     * Chủ bài chọn người nhận (YC-07). Chuyển sang RESERVED và ghi nhận reserved_at.
     */
    public void select(Instant now) {
        transitionTo(RequestStatus.RESERVED);
        this.reservedAt = now;
    }

    /**
     * Tự động từ chối yêu cầu (YC-07). Chuyển sang REJECTED.
     */
    public void reject() {
        transitionTo(RequestStatus.REJECTED);
    }

    /**
     * Chuyển đổi loại yêu cầu Cho ↔ Trao đổi khi chủ bài chọn người nhận (CD-02, CD-04, CD-06).
     *
     * @param newType loại mới ("GIVE" hoặc "SWAP")
     */
    public void convertType(String newType) {
        if (newType == null || (!newType.equals("GIVE") && !newType.equals("SWAP"))) {
            throw new IllegalArgumentException("Invalid request type: " + newType);
        }
        this.type = newType;
    }

    /**
     * Gán món người nhận đem đổi khi chủ bài đồng ý đề nghị trong chat (Cho → Trao đổi).
     *
     * @param newOfferedItemId id món đem đổi, không được null
     * @throws BusinessException REQUEST_OFFER_CHANGE_NOT_ALLOWED nếu giao dịch đã có món đem đổi
     */
    public void attachOfferedItem(Long newOfferedItemId) {
        if (newOfferedItemId == null) {
            throw new IllegalArgumentException("Offered item id must not be null");
        }
        // Mỗi giao dịch chỉ giữ một món đem đổi; thay món cũ sẽ bỏ sót việc nhả giữ chỗ món đó.
        if (this.offeredItemId != null) {
            throw new BusinessException(RequestErrorCode.REQUEST_OFFER_CHANGE_NOT_ALLOWED);
        }
        this.offeredItemId = newOfferedItemId;
    }

    /** Thay món đề nghị trước khi chốt lịch; hai bên cần xác nhận lại lịch đã đề xuất. */
    public void changeOfferedItem(Long newOfferedItemId) {
        if (newOfferedItemId == null) {
            throw new IllegalArgumentException("Offered item id must not be null");
        }
        if (this.offeredItemId == null
                || (this.status != RequestStatus.PENDING
                        && this.status != RequestStatus.RESERVED
                        && this.status != RequestStatus.AWAITING_LOGISTICS)) {
            throw new BusinessException(RequestErrorCode.REQUEST_OFFERED_ITEM_CHANGE_LOCKED);
        }
        this.offeredItemId = newOfferedItemId;
        this.donorScheduleOkAt = null;
        this.receiverScheduleOkAt = null;
    }

    /**
     * Đề xuất hoặc cập nhật lịch hẹn giao nhận (GN-01, GN-02).
     *
     * <p>Chuyển sang AWAITING_LOGISTICS nếu đang là RESERVED.
     * Ghi nhận đồng ý lịch và cam kết của bên đề xuất, xoá cả hai xác nhận của bên còn lại.
     */
    public void proposeSchedule(
            String deliveryMethod,
            String meetingPlace,
            Instant meetingTime,
            boolean isDonor,
            boolean agreeToWaiver,
            Instant now) {
        if (!agreeToWaiver) {
            throw new BusinessException(RequestErrorCode.REQUEST_WAIVER_REQUIRED);
        }
        if (this.status == RequestStatus.RESERVED) {
            transitionTo(RequestStatus.AWAITING_LOGISTICS);
        } else if (this.status != RequestStatus.AWAITING_LOGISTICS) {
            throw new BusinessException(RequestErrorCode.REQUEST_SCHEDULE_LOCKED);
        }

        this.deliveryMethod = deliveryMethod;
        this.meetingPlace = meetingPlace;
        this.meetingTime = meetingTime;

        if (isDonor) {
            this.donorScheduleOkAt = now;
            this.donorWaiverAt = now;
            this.receiverScheduleOkAt = null;
            this.receiverWaiverAt = null;
        } else {
            this.receiverScheduleOkAt = now;
            this.receiverWaiverAt = now;
            this.donorScheduleOkAt = null;
            this.donorWaiverAt = null;
        }
    }

    /**
     * Xác nhận lịch hẹn giao nhận và cam kết trách nhiệm (GN-02, GN-03).
     *
     * <p>Khi cả hai bên đều đã đồng ý lịch và cam kết trách nhiệm,
     * Request chuyển sang LOGISTICS_CONFIRMED và ghi nhận contactRevealedAt.
     */
    public void confirmLogistics(boolean isDonor, boolean agreeToWaiver, Instant now) {
        if (this.status != RequestStatus.AWAITING_LOGISTICS) {
            throw new BusinessException(RequestErrorCode.REQUEST_INVALID_STATE_TRANSITION);
        }
        if (this.meetingTime == null || this.deliveryMethod == null) {
            throw new BusinessException(RequestErrorCode.REQUEST_SCHEDULE_NOT_SET);
        }

        Instant waiverAt = isDonor ? this.donorWaiverAt : this.receiverWaiverAt;
        if (waiverAt == null && !agreeToWaiver) {
            throw new BusinessException(RequestErrorCode.REQUEST_WAIVER_REQUIRED);
        }

        Instant confirmedWaiverAt = waiverAt != null ? waiverAt : now;
        if (isDonor) {
            this.donorScheduleOkAt = now;
            this.donorWaiverAt = confirmedWaiverAt;
        } else {
            this.receiverScheduleOkAt = now;
            this.receiverWaiverAt = confirmedWaiverAt;
        }

        boolean donorOk = this.donorScheduleOkAt != null && this.donorWaiverAt != null;
        boolean receiverOk = this.receiverScheduleOkAt != null && this.receiverWaiverAt != null;

        if (donorOk && receiverOk) {
            transitionTo(RequestStatus.LOGISTICS_CONFIRMED);
            this.contactRevealedAt = now;
        }
    }

    /**
     * Xác nhận giao nhận từ một bên (GN-06, GN-08, UC-27).
     *
     * <p>Chỉ được gọi khi Request đang ở LOGISTICS_CONFIRMED hoặc AWAITING_HANDOVER.
     * Khi 1 bên bấm, Request chuyển sang AWAITING_HANDOVER và tiếp tục chờ bên kia (GN-08).
     * Khi cả 2 bên đều đã xác nhận, Request chuyển sang COMPLETED và ghi nhận completedAt.
     * Hàm này không bao giờ chuyển sang DISPUTED: từ chối hoặc báo thất bại đi qua hàm riêng.
     *
     * @param isDonor true nếu người xác nhận là chủ bài đăng (người giao), false nếu là người nhận
     * @param now thời điểm xác nhận
     */
    public void confirmHandover(boolean isDonor, Instant now) {
        if (!isHandoverOpen()) {
            throw new BusinessException(RequestErrorCode.REQUEST_INVALID_STATE_TRANSITION);
        }
        if (hasConfirmedHandover(isDonor)) {
            throw new BusinessException(RequestErrorCode.REQUEST_HANDOVER_ALREADY_CONFIRMED);
        }

        if (isDonor) {
            this.donorHandoverAt = now;
        } else {
            this.receiverHandoverAt = now;
        }

        if (this.status == RequestStatus.LOGISTICS_CONFIRMED) {
            transitionTo(RequestStatus.AWAITING_HANDOVER);
        }
        if (this.donorHandoverAt != null && this.receiverHandoverAt != null) {
            transitionTo(RequestStatus.COMPLETED);
            this.completedAt = now;
        }
    }

    /**
     * Một bên từ chối nhận món đồ: chuyển thẳng sang DISPUTED (GN-07).
     *
     * <p>Chỉ hợp lệ khi giao dịch ở LOGISTICS_CONFIRMED hoặc AWAITING_HANDOVER và bên từ chối chưa bấm hoàn tất. Từ
     * LOGISTICS_CONFIRMED phải đi qua AWAITING_HANDOVER vì sơ đồ trạng thái không có cạnh đi thẳng sang DISPUTED.
     *
     * @param isDonor true nếu người từ chối là chủ bài, false nếu là người nhận
     * @throws BusinessException REQUEST_INVALID_STATE_TRANSITION khi sai trạng thái hoặc bên từ chối đã bấm hoàn tất
     */
    public void refuseHandover(boolean isDonor) {
        if (!isHandoverOpen() || hasConfirmedHandover(isDonor)) {
            throw new BusinessException(RequestErrorCode.REQUEST_INVALID_STATE_TRANSITION);
        }
        if (this.status == RequestStatus.LOGISTICS_CONFIRMED) {
            transitionTo(RequestStatus.AWAITING_HANDOVER);
        }
        transitionTo(RequestStatus.DISPUTED);
    }

    /** Giao dịch còn đang chờ giao nhận (đã chốt lịch, chưa hoàn tất): nơi ba nút kết quả được phép dùng. */
    public boolean isHandoverOpen() {
        return this.status == RequestStatus.LOGISTICS_CONFIRMED || this.status == RequestStatus.AWAITING_HANDOVER;
    }

    /** Bên này đã bấm hoàn tất giao nhận chưa. */
    public boolean hasConfirmedHandover(boolean isDonor) {
        return isDonor ? this.donorHandoverAt != null : this.receiverHandoverAt != null;
    }

    /** Bên kia đã bấm hoàn tất giao nhận chưa (quyết định "giao nhận không thành công" huỷ hay mở tranh chấp). */
    public boolean hasOtherPartyConfirmedHandover(boolean isDonor) {
        return hasConfirmedHandover(!isDonor);
    }

    /**
     * Một bên báo chưa nhận được hoặc chưa giao được đồ: chuyển AWAITING_HANDOVER sang DISPUTED.
     *
     * <p>Chỉ hợp lệ khi bên kia đã xác nhận bàn giao còn bên báo thì chưa; trạng thái hoặc điều kiện sai thì ném
     * REQUEST_INVALID_STATE_TRANSITION.
     *
     * @param isDonor true nếu người báo là chủ bài, false nếu là người nhận
     */
    public void openHandoverIssue(boolean isDonor) {
        boolean selfConfirmed = isDonor ? this.donorHandoverAt != null : this.receiverHandoverAt != null;
        boolean otherConfirmed = isDonor ? this.receiverHandoverAt != null : this.donorHandoverAt != null;
        if (this.status != RequestStatus.AWAITING_HANDOVER || selfConfirmed || !otherConfirmed) {
            throw new BusinessException(RequestErrorCode.REQUEST_INVALID_STATE_TRANSITION);
        }
        transitionTo(RequestStatus.DISPUTED);
    }

    /**
     * Admin bác bỏ khiếu nại: giao dịch DISPUTED được công nhận hoàn tất như bình thường.
     *
     * @param now thời điểm hoàn tất
     */
    public void completeAfterDispute(Instant now) {
        transitionTo(RequestStatus.COMPLETED);
        this.completedAt = now;
    }

    /**
     * Tự động xác nhận giao nhận khi một bên im lặng quá hạn (GN-09).
     * Chuyển trạng thái từ AWAITING_HANDOVER sang COMPLETED.
     */
    public void autoConfirm(Instant now) {
        transitionTo(RequestStatus.COMPLETED);
        this.autoConfirmed = true;
        this.completedAt = now;
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
