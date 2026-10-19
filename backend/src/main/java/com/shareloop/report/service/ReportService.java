package com.shareloop.report.service;

import com.shareloop.audit.ActivityType;
import com.shareloop.audit.service.ActivityLogService;
import com.shareloop.common.exception.BusinessException;
import com.shareloop.item.dto.ItemSummary;
import com.shareloop.item.service.ItemQueryService;
import com.shareloop.item.service.ItemService;
import com.shareloop.media.service.MediaService;
import com.shareloop.notification.service.NotificationService;
import com.shareloop.report.ReportErrorCode;
import com.shareloop.report.dto.ConcludeReportRequest;
import com.shareloop.report.dto.CreateDisputeRequest;
import com.shareloop.report.dto.CreateReportRequest;
import com.shareloop.report.dto.DisputeResponse;
import com.shareloop.report.dto.DisputeResponseRequest;
import com.shareloop.report.dto.ReportConclusionResponse;
import com.shareloop.report.dto.ReportDecision;
import com.shareloop.report.dto.ReportResponse;
import com.shareloop.report.entity.Report;
import com.shareloop.report.entity.ReportReason;
import com.shareloop.report.entity.ReportStage;
import com.shareloop.report.entity.ReportStatus;
import com.shareloop.report.entity.TargetType;
import com.shareloop.report.repository.ReportRepository;
import com.shareloop.reputation.service.ReputationService;
import com.shareloop.request.dto.RequestChatContext;
import com.shareloop.request.service.RequestService;
import com.shareloop.setting.service.ConfigService;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReportService {

    private final ReportRepository reportRepository;
    private final ItemQueryService itemQueryService;
    private final ItemService itemService;
    private final MediaService mediaService;
    private final NotificationService notificationService;
    private final ReputationService reputationService;
    private final ActivityLogService activityLogService;
    private final ConfigService configService;
    private final RequestService requestService;
    private final JdbcClient jdbcClient;
    private final Clock clock;

    @Autowired
    public ReportService(
            ReportRepository reportRepository,
            ItemQueryService itemQueryService,
            @Autowired(required = false) ItemService itemService,
            @Autowired(required = false) MediaService mediaService,
            @Autowired(required = false) NotificationService notificationService,
            @Autowired(required = false) ReputationService reputationService,
            @Autowired(required = false) ActivityLogService activityLogService,
            @Autowired(required = false) ConfigService configService,
            @Autowired(required = false) RequestService requestService,
            @Autowired(required = false) JdbcClient jdbcClient,
            @Autowired(required = false) Clock clock) {
        this.reportRepository = reportRepository;
        this.itemQueryService = itemQueryService;
        this.itemService = itemService;
        this.mediaService = mediaService;
        this.notificationService = notificationService;
        this.reputationService = reputationService;
        this.activityLogService = activityLogService;
        this.configService = configService;
        this.requestService = requestService;
        this.jdbcClient = jdbcClient;
        this.clock = clock != null ? clock : Clock.systemUTC();
    }

    @Transactional
    public ReportResponse createReport(Long reporterId, CreateReportRequest request) {
        return create(reporterId, request);
    }

    @Transactional
    public ReportResponse create(Long reporterId, CreateReportRequest request) {
        if (reporterId == null) {
            throw new IllegalArgumentException("reporterId cannot be null");
        }
        if (request == null) {
            throw new IllegalArgumentException("request cannot be null");
        }

        TargetType targetType = request.targetType();
        if (targetType == null) {
            throw new BusinessException(ReportErrorCode.REPORT_TARGET_REQUIRED);
        }

        Long resolvedItemId = request.resolvedItemId();
        Long resolvedTargetUserId = request.resolvedTargetUserId();
        Long resolvedRequestId = request.resolvedRequestId();

        switch (targetType) {
            case ITEM -> {
                if (resolvedItemId == null) {
                    throw new BusinessException(ReportErrorCode.REPORT_TARGET_REQUIRED);
                }
                ItemSummary summary = itemQueryService.getSummary(resolvedItemId);
                if (summary == null) {
                    throw new BusinessException(ReportErrorCode.REPORT_TARGET_NOT_FOUND);
                }
                if (reporterId.equals(summary.donorId())) {
                    throw new BusinessException(ReportErrorCode.REPORT_CANNOT_REPORT_SELF);
                }
                if (reportRepository.existsByReporterIdAndTargetTypeAndItemId(reporterId, targetType, resolvedItemId)) {
                    throw new BusinessException(ReportErrorCode.REPORT_ALREADY_REPORTED);
                }
            }
            case USER -> {
                if (resolvedTargetUserId == null) {
                    throw new BusinessException(ReportErrorCode.REPORT_TARGET_REQUIRED);
                }
                if (reporterId.equals(resolvedTargetUserId)) {
                    throw new BusinessException(ReportErrorCode.REPORT_CANNOT_REPORT_SELF);
                }
                if (reportRepository.existsByReporterIdAndTargetTypeAndTargetUserId(
                        reporterId, targetType, resolvedTargetUserId)) {
                    throw new BusinessException(ReportErrorCode.REPORT_ALREADY_REPORTED);
                }
            }
            case REQUEST -> {
                if (resolvedRequestId == null) {
                    throw new BusinessException(ReportErrorCode.REPORT_TARGET_REQUIRED);
                }
                if (reportRepository.existsByReporterIdAndTargetTypeAndRequestId(
                        reporterId, targetType, resolvedRequestId)) {
                    throw new BusinessException(ReportErrorCode.REPORT_ALREADY_REPORTED);
                }
            }
        }

        ReportStage stage = resolvedRequestId != null ? ReportStage.AFTER : ReportStage.BEFORE;

        Report report = new Report(
                reporterId,
                targetType,
                resolvedItemId,
                resolvedTargetUserId,
                resolvedRequestId,
                request.reason(),
                request.description(),
                stage);

        Report saved = reportRepository.save(report);

        // BR-A03: Đạt 3 report thì tự ẩn bài
        if (targetType == TargetType.ITEM && resolvedItemId != null) {
            long count = reportRepository.countByItemId(resolvedItemId);
            if (count >= 3 && itemService != null) {
                itemService.hide(resolvedItemId);
            }
        }

        return new ReportResponse(
                saved.getId(),
                saved.getReporterId(),
                saved.getTargetType(),
                saved.getItemId(),
                saved.getTargetUserId(),
                saved.getRequestId(),
                saved.getReason(),
                saved.getDescription(),
                saved.getStage(),
                saved.getStatus(),
                saved.getCreatedAt());
    }

    /**
     * Mở khiếu nại giao dịch đã hoàn tất trong vòng 7 ngày (FR-89, BR-T08, Issue #112).
     */
    @Transactional
    public DisputeResponse openDispute(Long userId, Long requestId, CreateDisputeRequest request) {
        if (userId == null) {
            throw new IllegalArgumentException("userId cannot be null");
        }
        if (requestId == null) {
            throw new IllegalArgumentException("requestId cannot be null");
        }
        if (request == null || request.reason() == null) {
            throw new IllegalArgumentException("request and reason cannot be null");
        }

        // 1. Tìm thông tin giao dịch
        RequestSnapshot reqSnapshot = findRequest(requestId);
        if (reqSnapshot == null) {
            throw new BusinessException(ReportErrorCode.REPORT_TARGET_NOT_FOUND, "Không tìm thấy yêu cầu giao dịch.");
        }

        // 2. Xác định các bên tham gia giao dịch
        ItemSummary itemSummary = itemQueryService.getSummary(reqSnapshot.itemId());
        if (itemSummary == null) {
            throw new BusinessException(
                    ReportErrorCode.REPORT_TARGET_NOT_FOUND, "Không tìm thấy bài đăng của giao dịch.");
        }
        Long donorId = itemSummary.donorId();
        Long receiverId = reqSnapshot.receiverId();

        if (!userId.equals(donorId) && !userId.equals(receiverId)) {
            throw new BusinessException(ReportErrorCode.DISPUTE_NOT_A_PARTY);
        }

        // 3. Kiểm tra trạng thái giao dịch phải là COMPLETED
        if (!"COMPLETED".equalsIgnoreCase(reqSnapshot.status())) {
            throw new BusinessException(ReportErrorCode.DISPUTE_REQUEST_NOT_COMPLETED);
        }

        // 4. Kiểm tra cửa sổ khiếu nại (7 ngày sau completedAt)
        int windowDays = 7;
        if (configService != null) {
            try {
                windowDays = configService.getDecimal("DISPUTE_WINDOW_DAYS").intValue();
            } catch (Exception e1) {
                try {
                    windowDays =
                            configService.getDecimal("COMPLAINT_WINDOW_DAYS").intValue();
                } catch (Exception e2) {
                    windowDays = 7;
                }
            }
        }
        Instant completedAt = reqSnapshot.completedAt() != null ? reqSnapshot.completedAt() : Instant.now(clock);
        Instant deadline = completedAt.plus(Duration.ofDays(windowDays));
        if (Instant.now(clock).isAfter(deadline)) {
            throw new BusinessException(ReportErrorCode.DISPUTE_WINDOW_EXPIRED);
        }

        // 5. Mỗi người chỉ khiếu nại giao dịch này một lần
        if (reportRepository.existsByReporterIdAndTargetTypeAndRequestId(userId, TargetType.REQUEST, requestId)) {
            throw new BusinessException(ReportErrorCode.REPORT_ALREADY_REPORTED);
        }

        // 6. Kiểm tra và đính kèm bằng chứng nếu có
        List<Long> evidenceMediaIds = request.evidenceMediaIds();
        if (evidenceMediaIds != null && !evidenceMediaIds.isEmpty() && mediaService != null) {
            mediaService.attach(evidenceMediaIds, userId);
        }

        // 7. Mở cửa sổ giải trình 3 ngày cho bên bị khiếu nại
        int responseDays = 3;
        if (configService != null) {
            try {
                responseDays = configService.getDecimal("DISPUTE_RESPONSE_DAYS").intValue();
            } catch (Exception e1) {
                try {
                    responseDays =
                            configService.getDecimal("RESPONSE_WINDOW_DAYS").intValue();
                } catch (Exception e2) {
                    responseDays = 3;
                }
            }
        }
        Instant now = Instant.now(clock);
        Instant responseDueAt = now.plus(Duration.ofDays(responseDays));
        Long respondentId = userId.equals(donorId) ? receiverId : donorId;

        String evidenceStr =
                (evidenceMediaIds != null && !evidenceMediaIds.isEmpty()) ? evidenceMediaIds.toString() : null;

        Report dispute = Report.createDispute(
                userId,
                requestId,
                reqSnapshot.itemId(),
                respondentId,
                request.reason(),
                request.note(),
                evidenceStr,
                responseDueAt);

        Report saved = reportRepository.save(dispute);

        // 8. Gửi thông báo cho bên bị khiếu nại
        if (notificationService != null) {
            notificationService.notify(
                    respondentId,
                    "DISPUTE_OPENED",
                    "Giao dịch #" + requestId + " bị khiếu nại",
                    "Giao dịch #" + requestId + " đã bị đối tác khiếu nại. Bạn có " + responseDays
                            + " ngày để giải trình.",
                    "/me/disputes/" + saved.getId());
        }

        return new DisputeResponse(saved.getId(), saved.getResponseDueAt());
    }

    /**
     * Bên bị khiếu nại gửi giải trình kèm bằng chứng trong vòng 3 ngày (FR-90, BR-T08, Issue #112).
     */
    @Transactional
    public void respondDispute(Long userId, Long disputeId, DisputeResponseRequest request) {
        if (userId == null) {
            throw new IllegalArgumentException("userId cannot be null");
        }
        if (disputeId == null) {
            throw new IllegalArgumentException("disputeId cannot be null");
        }
        if (request == null || request.note() == null || request.note().isBlank()) {
            throw new IllegalArgumentException("request and note cannot be null or blank");
        }

        Report dispute = reportRepository
                .findByIdForUpdate(disputeId)
                .orElseThrow(() -> new BusinessException(ReportErrorCode.DISPUTE_NOT_FOUND));

        // Chỉ bên bị khiếu nại mới được giải trình
        if (dispute.getTargetUserId() == null || !userId.equals(dispute.getTargetUserId())) {
            throw new BusinessException(ReportErrorCode.DISPUTE_NOT_RESPONDENT);
        }

        // Đã kết luận thì không thể giải trình thêm
        if (dispute.getStatus() == ReportStatus.RESOLVED || dispute.getStatus() == ReportStatus.DISMISSED) {
            throw new BusinessException(ReportErrorCode.DISPUTE_ALREADY_CONCLUDED);
        }

        // Đã giải trình trước đó
        if (dispute.getRespondedAt() != null) {
            throw new BusinessException(ReportErrorCode.DISPUTE_ALREADY_RESPONDED);
        }

        // Quá hạn giải trình 3 ngày
        if (dispute.getResponseDueAt() != null && Instant.now(clock).isAfter(dispute.getResponseDueAt())) {
            throw new BusinessException(ReportErrorCode.DISPUTE_RESPONSE_WINDOW_EXPIRED);
        }

        // Đính kèm bằng chứng nếu có
        List<Long> evidenceMediaIds = request.evidenceMediaIds();
        if (evidenceMediaIds != null && !evidenceMediaIds.isEmpty() && mediaService != null) {
            mediaService.attach(evidenceMediaIds, userId);
        }

        String evidenceStr =
                (evidenceMediaIds != null && !evidenceMediaIds.isEmpty()) ? evidenceMediaIds.toString() : null;

        dispute.respond(request.note(), evidenceStr, Instant.now(clock));
        reportRepository.save(dispute);
    }

    /**
     * Admin kết luận khiếu nại (FR-91, FR-93, BR-U04, BR-A07, Issue #113).
     */
    @Transactional
    public ReportConclusionResponse conclude(Long adminId, Long reportId, ConcludeReportRequest request) {
        if (adminId == null) {
            throw new IllegalArgumentException("adminId cannot be null");
        }
        if (reportId == null) {
            throw new IllegalArgumentException("reportId cannot be null");
        }
        if (request == null || request.decision() == null) {
            throw new IllegalArgumentException("request and decision cannot be null");
        }

        Report report = reportRepository
                .findByIdForUpdate(reportId)
                .orElseThrow(() -> new BusinessException(ReportErrorCode.REPORT_NOT_FOUND));

        if (report.getStatus() == ReportStatus.RESOLVED || report.getStatus() == ReportStatus.DISMISSED) {
            throw new BusinessException(ReportErrorCode.REPORT_ALREADY_CONCLUDED);
        }

        ReportDecision decision = request.decision();
        String note = request.note() != null ? request.note().trim() : null;

        report.conclude(decision, adminId, note);

        if (decision == ReportDecision.DISMISS) {
            if (activityLogService != null) {
                activityLogService.log(
                        ActivityType.REPORT_DISMISSED.name(),
                        adminId,
                        "REPORT",
                        reportId,
                        Map.of(
                                "decision",
                                "DISMISS",
                                "reportId",
                                reportId,
                                "targetType",
                                report.getTargetType().name()),
                        note != null ? note : "Bác bỏ báo cáo #" + reportId);
            }
        } else if (decision == ReportDecision.UPHOLD) {
            Long violatorId = resolveViolator(report);

            // BR-U04: Trừ 1 sao uy tín
            if (violatorId != null && reputationService != null) {
                reputationService.deductStar(
                        violatorId, 1, note != null ? note : "Bị kết luận vi phạm khiếu nại #" + reportId);
            }

            // BR-A07: Nếu lý do thuộc nhóm nghiêm trọng, rà soát ẩn toàn bộ bài đăng khác của người vi phạm
            boolean serious = isSeriousReason(report.getReason());
            if (serious && violatorId != null && itemService != null) {
                itemService.hideAllOfUser(violatorId);
            }

            // Ẩn bài đăng cụ thể nếu đối tượng báo cáo là bài đăng
            if (report.getTargetType() == TargetType.ITEM && report.getItemId() != null && itemService != null) {
                itemService.hide(report.getItemId());
            }

            if (activityLogService != null) {
                Map<String, Object> logPayload = new HashMap<>();
                logPayload.put("decision", "UPHOLD");
                logPayload.put("reportId", reportId);
                logPayload.put("targetType", report.getTargetType().name());
                if (violatorId != null) {
                    logPayload.put("violatorId", violatorId);
                }
                logPayload.put("starsDeducted", 1);
                logPayload.put("seriousViolation", serious);

                activityLogService.log(
                        ActivityType.REPORT_UPHELD.name(),
                        adminId,
                        "REPORT",
                        reportId,
                        logPayload,
                        note != null ? note : "Chấp thuận báo cáo #" + reportId + ", xử phạt trừ 1 sao uy tín");
            }
        }

        Report saved = reportRepository.save(report);

        return new ReportConclusionResponse(
                saved.getId(),
                saved.getTargetType(),
                saved.getItemId(),
                saved.getTargetUserId(),
                saved.getRequestId(),
                saved.getReason(),
                saved.getStatus(),
                saved.getResolvedBy(),
                saved.getResolution(),
                saved.getStarsDeducted(),
                saved.getResolvedAt());
    }

    private Long resolveViolator(Report report) {
        if (report.getTargetType() == TargetType.USER) {
            return report.getTargetUserId();
        }
        if (report.getTargetType() == TargetType.ITEM && report.getItemId() != null) {
            if (itemQueryService != null) {
                ItemSummary summary = itemQueryService.getSummary(report.getItemId());
                if (summary != null) {
                    return summary.donorId();
                }
            }
            return null;
        }
        if (report.getTargetType() == TargetType.REQUEST && report.getRequestId() != null) {
            if (report.getTargetUserId() != null) {
                return report.getTargetUserId();
            }
            if (requestService != null) {
                try {
                    RequestChatContext context = requestService.getChatContext(report.getRequestId());
                    if (context != null) {
                        Long reporterId = report.getReporterId();
                        if (reporterId != null && reporterId.equals(context.receiverId())) {
                            return context.donorId();
                        } else if (reporterId != null && reporterId.equals(context.donorId())) {
                            return context.receiverId();
                        }
                        return context.receiverId();
                    }
                } catch (Exception ignored) {
                }
            }
        }
        return report.getTargetUserId();
    }

    private boolean isSeriousReason(ReportReason reason) {
        if (reason == null) {
            return false;
        }
        String configured = null;
        if (configService != null) {
            try {
                configured = configService.getRaw("REPORT_SERIOUS_REASONS");
            } catch (Exception e1) {
                try {
                    configured = configService.getRaw("report_serious_reasons");
                } catch (Exception ignored) {
                }
            }
        }
        if (configured == null || configured.isBlank()) {
            configured = "PROHIBITED_ITEM,COUNTERFEIT";
        }
        Set<String> seriousSet = Arrays.stream(configured.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toSet());
        return seriousSet.contains(reason.name());
    }

    RequestSnapshot findRequest(Long requestId) {
        if (jdbcClient == null) {
            return null;
        }
        return jdbcClient
                .sql(
                        "SELECT id, item_id, receiver_id, status, completed_at FROM requests WHERE id = ? AND is_deleted = false")
                .param(requestId)
                .query((rs, rowNum) -> new RequestSnapshot(
                        rs.getLong("id"),
                        rs.getLong("item_id"),
                        rs.getLong("receiver_id"),
                        rs.getString("status"),
                        rs.getTimestamp("completed_at") != null
                                ? rs.getTimestamp("completed_at").toInstant()
                                : null))
                .optional()
                .orElse(null);
    }

    public record RequestSnapshot(Long id, Long itemId, Long receiverId, String status, Instant completedAt) {}
}
