package com.shareloop.report.entity;

import com.shareloop.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import org.hibernate.annotations.SQLRestriction;

@Entity
@Table(name = "reports")
@SQLRestriction("is_deleted = false")
public class Report extends BaseEntity {

    @Column(name = "reporter_id", nullable = false)
    private Long reporterId;

    @Enumerated(EnumType.STRING)
    @Column(name = "target_type", nullable = false, length = 10)
    private TargetType targetType;

    @Column(name = "item_id")
    private Long itemId;

    @Column(name = "target_user_id")
    private Long targetUserId;

    @Column(name = "request_id")
    private Long requestId;

    @Enumerated(EnumType.STRING)
    @Column(name = "reason", nullable = false, length = 30)
    private ReportReason reason;

    @Column(name = "description", nullable = false, columnDefinition = "TEXT")
    private String description;

    @Column(name = "system_title", length = 150)
    private String systemTitle;

    @Enumerated(EnumType.STRING)
    @Column(name = "stage", nullable = false, length = 8)
    private ReportStage stage = ReportStage.BEFORE;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private ReportStatus status = ReportStatus.OPEN;

    @Column(name = "response", columnDefinition = "TEXT")
    private String response;

    @Column(name = "responded_at")
    private Instant respondedAt;

    @Column(name = "response_due_at")
    private Instant responseDueAt;

    @Column(name = "evidence", columnDefinition = "TEXT")
    private String evidence;

    @Column(name = "response_evidence", columnDefinition = "TEXT")
    private String responseEvidence;

    @Column(name = "resolved_by")
    private Long resolvedBy;

    @Column(name = "resolution", columnDefinition = "TEXT")
    private String resolution;

    @Column(name = "stars_deducted", nullable = false)
    private short starsDeducted = 0;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    protected Report() {}

    public Report(
            Long reporterId,
            TargetType targetType,
            Long itemId,
            Long targetUserId,
            Long requestId,
            ReportReason reason,
            String description,
            ReportStage stage) {
        this.reporterId = reporterId;
        this.targetType = targetType;
        this.itemId = itemId;
        this.targetUserId = targetUserId;
        this.requestId = requestId;
        this.reason = reason;
        this.description = description;
        this.stage = stage != null ? stage : ReportStage.BEFORE;
        this.status = ReportStatus.OPEN;
        this.starsDeducted = 0;
    }

    public static Report createSystemReport(Long reporterId, String title, String description) {
        Report report = new Report(
                reporterId, TargetType.SYSTEM, null, null, null, ReportReason.OTHER, description, ReportStage.BEFORE);
        report.systemTitle = title;
        return report;
    }

    public String getSystemTitle() {
        return systemTitle;
    }

    public void resolveSystemReport(Long adminId, String resolution, Instant now) {
        this.status = ReportStatus.RESOLVED;
        this.resolvedBy = adminId;
        this.resolution = resolution;
        this.resolvedAt = now;
    }

    /**
     * Tạo khiếu nại giao dịch đã hoàn thành (BC-07, BC-08, Issue #112).
     */
    public static Report createDispute(
            Long reporterId,
            Long requestId,
            Long itemId,
            Long respondentId,
            ReportReason reason,
            String note,
            String evidence,
            Instant responseDueAt) {
        Report report = new Report(
                reporterId,
                TargetType.REQUEST,
                itemId,
                respondentId,
                requestId,
                reason,
                note != null && !note.isBlank() ? note.trim() : reason.name(),
                ReportStage.AFTER);
        report.status = ReportStatus.AWAITING_RESPONSE;
        report.evidence = evidence;
        report.responseDueAt = responseDueAt;
        return report;
    }

    /**
     * Tạo báo cáo vi phạm khi một bên từ chối nhận món đồ (GN-07, BC-09). Đây là báo cáo bài, không phải khiếu nại:
     * nhắm vào món bị từ chối, gắn giao dịch, không có hạn giải trình nên Admin kết luận được ngay.
     */
    public static Report createRefusalReport(
            Long reporterId, Long requestId, Long refusedItemId, Long reportedUserId, String note, String evidence) {
        Report report = new Report(
                reporterId,
                TargetType.ITEM,
                refusedItemId,
                reportedUserId,
                requestId,
                ReportReason.NOT_AS_DESCRIBED,
                note,
                ReportStage.AFTER);
        report.evidence = evidence;
        return report;
    }

    /** Báo cáo này có phải báo cáo từ chối nhận không: báo cáo bài, lý do Không đúng mô tả, có gắn giao dịch. */
    public boolean isRefusalReport() {
        return targetType == TargetType.ITEM && requestId != null && reason == ReportReason.NOT_AS_DESCRIBED;
    }

    /**
     * Bên bị khiếu nại gửi giải trình kèm bằng chứng (BC-08, BC-07, Issue #112).
     */
    public void respond(String note, String responseEvidence, Instant now) {
        this.response = note != null ? note.trim() : null;
        this.responseEvidence = responseEvidence;
        this.respondedAt = now;
        this.status = ReportStatus.OPEN;
    }

    public Long getReporterId() {
        return reporterId;
    }

    public TargetType getTargetType() {
        return targetType;
    }

    public Long getItemId() {
        return itemId;
    }

    public Long getTargetUserId() {
        return targetUserId;
    }

    public Long getRequestId() {
        return requestId;
    }

    public ReportReason getReason() {
        return reason;
    }

    public String getDescription() {
        return description;
    }

    public ReportStage getStage() {
        return stage;
    }

    public ReportStatus getStatus() {
        return status;
    }

    public String getResponse() {
        return response;
    }

    public Instant getRespondedAt() {
        return respondedAt;
    }

    public Instant getResponseDueAt() {
        return responseDueAt;
    }

    public String getEvidence() {
        return evidence;
    }

    public String getResponseEvidence() {
        return responseEvidence;
    }

    public void setEvidence(String evidence) {
        this.evidence = evidence;
    }

    /**
     * Còn đang chờ giải trình và chưa tới hạn (D-15, BC-10): Admin chưa được kết luận. Giải trình xong thì status về
     * OPEN; hết hạn mà chưa giải trình thì Admin kết luận được luôn. Mốc đúng bằng hạn vẫn tính là còn hạn, giống
     * {@code ReportService.respond}.
     */
    public boolean isResponseWindowOpen(Instant now) {
        return status == ReportStatus.AWAITING_RESPONSE && responseDueAt != null && !now.isAfter(responseDueAt);
    }

    /**
     * Người dùng có được xem hồ sơ với tư cách bên bị phản ánh không: phải là target_user_id và hồ sơ đã có hạn giải
     * trình (Admin đã yêu cầu, hoặc là khiếu nại giao dịch). Hồ sơ chưa có hạn thì bên bị phản ánh chưa được biết.
     */
    public boolean isVisibleToRespondent(Long userId) {
        // Báo cáo từ chối nhận không có hạn nhưng bên bị báo cáo đã được báo ngay và được giải trình (GN-07)
        return userId != null && userId.equals(targetUserId) && (responseDueAt != null || isRefusalReport());
    }

    /** Bên bị báo cáo còn gửi giải trình được không: chưa kết luận, chưa giải trình và còn hạn (nếu có hạn). */
    public boolean canStillRespond(Instant now) {
        if (status == ReportStatus.RESOLVED || status == ReportStatus.DISMISSED || respondedAt != null) {
            return false;
        }
        if (isRefusalReport()) {
            return true;
        }
        return status == ReportStatus.AWAITING_RESPONSE && responseDueAt != null && !now.isAfter(responseDueAt);
    }

    public void awaitResponse(Instant dueAt) {
        this.responseDueAt = dueAt;
        this.status = ReportStatus.AWAITING_RESPONSE;
    }

    public Long getResolvedBy() {
        return resolvedBy;
    }

    public String getResolution() {
        return resolution;
    }

    public short getStarsDeducted() {
        return starsDeducted;
    }

    public Instant getResolvedAt() {
        return resolvedAt;
    }

    /**
     * Ghi nhận kết luận của Admin đối với báo cáo vi phạm (BC-12, UT-02, BC-06, Issue #113).
     *
     * @param decision quyết định UPHOLD hoặc DISMISS
     * @param adminId ID của Admin thực hiện
     * @param note ghi chú kết luận
     */
    public void conclude(com.shareloop.report.dto.ReportDecision decision, Long adminId, String note) {
        this.status = (decision == com.shareloop.report.dto.ReportDecision.UPHOLD)
                ? ReportStatus.RESOLVED
                : ReportStatus.DISMISSED;
        this.resolvedBy = adminId;
        this.resolution = note;
        this.resolvedAt = Instant.now();
        if (decision == com.shareloop.report.dto.ReportDecision.UPHOLD) {
            this.starsDeducted = 1;
        }
    }

    /** Khiếu nại được chấp thuận nhưng Admin quyết không trừ sao ai (đề nghị huỷ không tố ai). */
    public void markNoStarsDeducted() {
        this.starsDeducted = 0;
    }

    /**
     * Đóng một báo cáo đang chờ của cùng bài khi Admin đã chấp thuận báo cáo khác về bài đó (D-07, BC-05): tính là có
     * cơ sở nhưng không trừ sao, vì mỗi bài chỉ bị trừ 1 sao cho lần chấp thuận.
     */
    public void closeWithUpheldReport(Long adminId, String note) {
        this.status = ReportStatus.RESOLVED;
        this.resolvedBy = adminId;
        this.resolution = note;
        this.resolvedAt = Instant.now();
        this.starsDeducted = 0;
    }
}
