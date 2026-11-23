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
    // Câu mở đầu lý do huỷ khi một bên từ chối nhận, đặt sau tiền tố người huỷ (GN-07)
    public static final String REFUSAL_REASON = "Từ chối nhận vì món không đúng mô tả. ";

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

    // Độ dài cột cancel_reason (migration V202610272320). Lý do dài hơn được rút gọn khi ghi, xem fitCancelReason.
    public static final int CANCEL_REASON_MAX_LENGTH = 600;

    // 500 ký tự người dùng nhập + tiền tố người huỷ
    @Column(name = "cancel_reason", length = CANCEL_REASON_MAX_LENGTH)
    private String cancelReason;

    // Đề nghị huỷ giao dịch đã chốt lịch đang chờ bên kia phản hồi (GN-06). Null khi không có đề nghị nào.
    @Column(name = "cancel_requested_by")
    private Long cancelRequestedBy;

    @Column(name = "cancel_requested_at")
    private Instant cancelRequestedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "cancel_request_code", length = 30)
    private CancelRequestCode cancelRequestCode;

    @Column(name = "cancel_request_reason", length = 500)
    private String cancelRequestReason;

    // Đề nghị dời lịch đang chờ bên kia phản hồi (GN-11). Null khi không có đề nghị nào.
    @Column(name = "reschedule_proposed_by")
    private Long rescheduleProposedBy;

    @Column(name = "reschedule_proposed_at")
    private Instant rescheduleProposedAt;

    @Column(name = "reschedule_meeting_time")
    private Instant rescheduleMeetingTime;

    @Column(name = "reschedule_meeting_place", length = 255)
    private String rescheduleMeetingPlace;

    // Số lần dời lịch đã được đồng ý
    @Column(name = "reschedule_count", nullable = false)
    private int rescheduleCount = 0;

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
        // Huỷ vì lý do khác thì đề nghị huỷ còn dang dở không còn ý nghĩa (chỉ giữ lại khi huỷ vì chính đề nghị đó)
        clearCancelRequest();
        cancelKeepingCancelRequest(reason);
    }

    // Đường ghi cancel_reason duy nhất: dùng riêng khi huỷ vì đề nghị huỷ để giữ lại dấu vết cho màn Giám sát
    private void cancelKeepingCancelRequest(String reason) {
        transitionTo(RequestStatus.CANCELLED);
        this.cancelReason = fitCancelReason(reason);
    }

    /**
     * Rút gọn lý do để vừa cột cancel_reason (E2): cắt ở ranh giới từ và thêm "…". Mọi đường ghi cancel_reason đều đi
     * qua {@link #cancel}, nên đây là chỗ duy nhất. Lý do "Từ chối giao nhận" tới 1000 ký tự vẫn được lưu đủ ở báo cáo.
     */
    static String fitCancelReason(String reason) {
        if (reason == null || reason.length() <= CANCEL_REASON_MAX_LENGTH) {
            return reason;
        }
        String shortened = reason.substring(0, CANCEL_REASON_MAX_LENGTH - 1);
        int lastSpace = shortened.lastIndexOf(' ');
        if (lastSpace > 0) {
            shortened = shortened.substring(0, lastSpace);
        }
        return shortened.stripTrailing() + "…";
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
        // GN-11: đã có bên bấm hoàn tất thì không dời lịch nữa, đề nghị dời còn chờ không còn ý nghĩa
        clearReschedule();

        if (this.status == RequestStatus.LOGISTICS_CONFIRMED) {
            transitionTo(RequestStatus.AWAITING_HANDOVER);
        }
        if (this.donorHandoverAt != null && this.receiverHandoverAt != null) {
            transitionTo(RequestStatus.COMPLETED);
            this.completedAt = now;
        }
    }

    /**
     * Một bên từ chối nhận món đồ: việc trao đồ không xảy ra nên giao dịch huỷ ngay (GN-07), không qua DISPUTED.
     *
     * <p>Chỉ hợp lệ khi giao dịch ở LOGISTICS_CONFIRMED hoặc AWAITING_HANDOVER và bên từ chối chưa bấm hoàn tất.
     *
     * @param isDonor true nếu người từ chối là chủ bài, false nếu là người nhận
     * @param reason lý do bên từ chối đã nhập
     * @throws BusinessException REQUEST_INVALID_STATE_TRANSITION khi sai trạng thái hoặc bên từ chối đã bấm hoàn tất
     */
    public void refuseHandover(boolean isDonor, String reason) {
        if (!isHandoverOpen() || hasConfirmedHandover(isDonor)) {
            throw new BusinessException(RequestErrorCode.REQUEST_INVALID_STATE_TRANSITION);
        }
        String prefix = isDonor ? CANCEL_BY_DONOR : CANCEL_BY_RECEIVER;
        cancel(prefix + REFUSAL_REASON + reason);
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

    // ===== Dời lịch sau khi chốt (GN-11) =====

    /** GN-11: có đề nghị dời lịch đang chờ bên kia phản hồi (chỉ tính khi giao dịch còn đang chờ giao nhận). */
    public boolean hasPendingReschedule() {
        return this.rescheduleProposedAt != null && isHandoverOpen();
    }

    /**
     * GN-11: một bên đề xuất giờ mới (và địa điểm mới nếu Gặp trực tiếp muốn đổi). Lịch cũ giữ nguyên cho tới khi bên
     * kia đồng ý.
     *
     * @param maxCount số lần dời tối đa mỗi giao dịch, đọc từ cấu hình
     * @throws BusinessException REQUEST_RESCHEDULE_NOT_ALLOWED khi sai trạng thái, đã qua giờ hẹn hoặc đã có bên bấm
     *     hoàn tất, REQUEST_RESCHEDULE_LIMIT_REACHED khi hết số lần dời, REQUEST_RESCHEDULE_PENDING hoặc
     *     REQUEST_CANCEL_REQUEST_PENDING khi đang có đề nghị khác chờ
     */
    public void proposeReschedule(Long proposerId, Instant newTime, String newPlace, Instant now, int maxCount) {
        boolean someoneCompleted = this.donorHandoverAt != null || this.receiverHandoverAt != null;
        if (!isHandoverOpen() || someoneCompleted || !isBeforeMeetingTime(now)) {
            throw new BusinessException(RequestErrorCode.REQUEST_RESCHEDULE_NOT_ALLOWED);
        }
        if (hasPendingCancelRequest()) {
            throw new BusinessException(RequestErrorCode.REQUEST_CANCEL_REQUEST_PENDING);
        }
        if (hasPendingReschedule()) {
            throw new BusinessException(RequestErrorCode.REQUEST_RESCHEDULE_PENDING);
        }
        if (this.rescheduleCount >= maxCount) {
            throw new BusinessException(RequestErrorCode.REQUEST_RESCHEDULE_LIMIT_REACHED);
        }
        this.rescheduleProposedBy = proposerId;
        this.rescheduleProposedAt = now;
        this.rescheduleMeetingTime = newTime;
        this.rescheduleMeetingPlace = newPlace;
    }

    /** GN-11: bên kia đồng ý thì đổi lịch, liên hệ vẫn mở, hai bên không phải cam kết lại. Tăng bộ đếm. */
    public void acceptReschedule(Long accepterId) {
        requirePendingReschedule(accepterId);
        this.meetingTime = this.rescheduleMeetingTime;
        if (this.rescheduleMeetingPlace != null) {
            this.meetingPlace = this.rescheduleMeetingPlace;
        }
        this.rescheduleCount++;
        clearReschedule();
    }

    /** GN-11: bên kia từ chối thì giữ lịch cũ, không tăng bộ đếm. */
    public void rejectReschedule(Long rejecterId) {
        requirePendingReschedule(rejecterId);
        clearReschedule();
    }

    /** GN-11: đề nghị dời hết hạn thì giữ lịch cũ. Hạn là sớm hơn giữa giờ hẹn cũ và lúc đề xuất cộng số giờ phản hồi. */
    public void expireReschedule() {
        clearReschedule();
    }

    private void requirePendingReschedule(Long callerId) {
        if (!hasPendingReschedule()) {
            throw new BusinessException(RequestErrorCode.REQUEST_RESCHEDULE_NOT_FOUND);
        }
        // Người đề xuất không tự đồng ý hay từ chối đề nghị của mình
        if (callerId.equals(this.rescheduleProposedBy)) {
            throw new BusinessException(RequestErrorCode.REQUEST_FORBIDDEN);
        }
    }

    private void clearReschedule() {
        this.rescheduleProposedBy = null;
        this.rescheduleProposedAt = null;
        this.rescheduleMeetingTime = null;
        this.rescheduleMeetingPlace = null;
    }

    // ===== Đề nghị huỷ có xác nhận của bên kia (GN-06) =====

    /** GN-06: có đề nghị huỷ đang chờ bên kia phản hồi (chỉ tính khi giao dịch còn đang chờ giao nhận). */
    public boolean hasPendingCancelRequest() {
        return this.cancelRequestedAt != null && isHandoverOpen();
    }

    /** GN-06: chưa tới giờ hẹn thì chỉ được "Đề nghị huỷ", từ giờ hẹn trở đi mới có "Giao nhận không thành công". */
    public boolean isBeforeMeetingTime(Instant now) {
        return this.meetingTime != null && now.isBefore(this.meetingTime);
    }

    /**
     * GN-06: một bên đề nghị huỷ. Giao dịch chưa huỷ, chỉ lưu đề nghị để bên kia phản hồi.
     *
     * @throws BusinessException REQUEST_INVALID_STATE_TRANSITION khi sai trạng thái hoặc một bên đã bấm hoàn tất
     *     (khi đó phải đi đường khiếu nại), REQUEST_CANCEL_REQUEST_PENDING khi đã có đề nghị đang chờ
     */
    public void requestCancel(Long requesterId, boolean isDonor, CancelRequestCode code, String reason, Instant now) {
        if (!isHandoverOpen() || hasConfirmedHandover(isDonor) || hasOtherPartyConfirmedHandover(isDonor)) {
            throw new BusinessException(RequestErrorCode.REQUEST_INVALID_STATE_TRANSITION);
        }
        if (hasPendingCancelRequest()) {
            throw new BusinessException(RequestErrorCode.REQUEST_CANCEL_REQUEST_PENDING);
        }
        // Không cho có đồng thời đề nghị huỷ và đề nghị dời lịch
        if (hasPendingReschedule()) {
            throw new BusinessException(RequestErrorCode.REQUEST_RESCHEDULE_PENDING);
        }
        this.cancelRequestedBy = requesterId;
        this.cancelRequestedAt = now;
        this.cancelRequestCode = code;
        this.cancelRequestReason = reason;
    }

    /** GN-06: người đề nghị rút lại trước khi bên kia phản hồi. */
    public void withdrawCancelRequest(Long userId) {
        requirePendingCancelRequest();
        if (!userId.equals(this.cancelRequestedBy)) {
            throw new BusinessException(RequestErrorCode.REQUEST_FORBIDDEN);
        }
        clearCancelRequest();
    }

    /**
     * GN-06: bên kia đồng ý, giao dịch huỷ như luật cũ (không phạt). Giữ lại dấu vết đề nghị để Admin đếm số lần huỷ.
     *
     * @param accepterIsDonor true nếu người đồng ý là chủ bài
     */
    public void acceptCancelRequest(Long accepterId, boolean accepterIsDonor) {
        requirePendingCancelRequest();
        if (accepterId.equals(this.cancelRequestedBy)) {
            throw new BusinessException(RequestErrorCode.REQUEST_FORBIDDEN);
        }
        // Người đề nghị là bên còn lại nên tiền tố ghi theo người đề nghị
        String prefix = accepterIsDonor ? CANCEL_BY_RECEIVER : CANCEL_BY_DONOR;
        cancelKeepingCancelRequest(prefix + "Bên kia đồng ý huỷ. " + cancelRequestSummary());
    }

    /** GN-12: giao dịch đã chốt lịch mà không ai làm gì quá hạn thì hệ thống huỷ, không phạt ai. */
    public void cancelAsStale(String reason) {
        if (this.status != RequestStatus.LOGISTICS_CONFIRMED || hasPendingCancelRequest()) {
            throw new BusinessException(RequestErrorCode.REQUEST_INVALID_STATE_TRANSITION);
        }
        cancel(CANCEL_BY_SYSTEM + reason);
    }

    /** GN-06: bên kia im lặng quá hạn phản hồi thì huỷ như "Đồng ý". Hệ thống là bên huỷ. */
    public void expireCancelRequest(String silenceSentence) {
        requirePendingCancelRequest();
        cancelKeepingCancelRequest(CANCEL_BY_SYSTEM + silenceSentence + " " + cancelRequestSummary());
    }

    /**
     * GN-06: bên kia không đồng ý nên giao dịch sang DISPUTED để Admin xử lý. Đây là đường duy nhất được đi cạnh
     * LOGISTICS_CONFIRMED sang DISPUTED (xem RequestStatus.canTransition).
     */
    public void rejectCancelRequest(Long rejecterId) {
        requirePendingCancelRequest();
        if (rejecterId.equals(this.cancelRequestedBy)) {
            throw new BusinessException(RequestErrorCode.REQUEST_FORBIDDEN);
        }
        transitionTo(RequestStatus.DISPUTED);
    }

    /** Câu mô tả lý do đề nghị để ghép vào lý do huỷ và thông báo, ví dụ "bạn không đến điểm hẹn". */
    public String cancelRequestSummary() {
        if (this.cancelRequestCode == null) {
            return "";
        }
        return "Lý do: " + this.cancelRequestCode.label()
                + (this.cancelRequestReason == null || this.cancelRequestReason.isBlank()
                        ? ""
                        : ". " + this.cancelRequestReason);
    }

    private void requirePendingCancelRequest() {
        if (!hasPendingCancelRequest()) {
            throw new BusinessException(RequestErrorCode.REQUEST_CANCEL_REQUEST_NOT_FOUND);
        }
    }

    private void clearCancelRequest() {
        this.cancelRequestedBy = null;
        this.cancelRequestedAt = null;
        this.cancelRequestCode = null;
        this.cancelRequestReason = null;
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

    public Long getRescheduleProposedBy() {
        return rescheduleProposedBy;
    }

    public Instant getRescheduleProposedAt() {
        return rescheduleProposedAt;
    }

    public Instant getRescheduleMeetingTime() {
        return rescheduleMeetingTime;
    }

    public String getRescheduleMeetingPlace() {
        return rescheduleMeetingPlace;
    }

    public int getRescheduleCount() {
        return rescheduleCount;
    }

    public Long getCancelRequestedBy() {
        return cancelRequestedBy;
    }

    public Instant getCancelRequestedAt() {
        return cancelRequestedAt;
    }

    public CancelRequestCode getCancelRequestCode() {
        return cancelRequestCode;
    }

    public String getCancelRequestReason() {
        return cancelRequestReason;
    }
}
