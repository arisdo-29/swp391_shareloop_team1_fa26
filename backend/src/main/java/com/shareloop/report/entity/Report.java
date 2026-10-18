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

    /**
     * Tạo khiếu nại giao dịch đã hoàn thành (FR-89, FR-90, BR-T08, Issue #112).
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
     * Bên bị khiếu nại gửi giải trình kèm bằng chứng (FR-90, BR-T08, Issue #112).
     */
    public void respond(String note, String responseEvidence, Instant now) {
        this.response = note != null ? note.trim() : null;
        this.responseEvidence = responseEvidence;
        this.respondedAt = now;
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
}
