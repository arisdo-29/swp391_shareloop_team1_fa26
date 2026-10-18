package com.shareloop.report.service;

import com.shareloop.common.exception.BusinessException;
import com.shareloop.item.dto.ItemSummary;
import com.shareloop.item.service.ItemQueryService;
import com.shareloop.item.service.ItemService;
import com.shareloop.media.service.MediaService;
import com.shareloop.notification.service.NotificationService;
import com.shareloop.report.ReportErrorCode;
import com.shareloop.report.dto.CreateDisputeRequest;
import com.shareloop.report.dto.CreateReportRequest;
import com.shareloop.report.dto.DisputeResponse;
import com.shareloop.report.dto.DisputeResponseRequest;
import com.shareloop.report.dto.ReportResponse;
import com.shareloop.report.entity.Report;
import com.shareloop.report.entity.ReportStage;
import com.shareloop.report.entity.ReportStatus;
import com.shareloop.report.entity.TargetType;
import com.shareloop.report.repository.ReportRepository;
import com.shareloop.setting.service.ConfigService;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
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
    private final ConfigService configService;
    private final NotificationService notificationService;
    private final Clock clock;
    private final JdbcClient jdbcClient;

    @Autowired
    public ReportService(
            ReportRepository reportRepository,
            ItemQueryService itemQueryService,
            ItemService itemService,
            @Autowired(required = false) MediaService mediaService,
            @Autowired(required = false) ConfigService configService,
            @Autowired(required = false) NotificationService notificationService,
            @Autowired(required = false) Clock clock,
            @Autowired(required = false) JdbcClient jdbcClient) {
        this.reportRepository = reportRepository;
        this.itemQueryService = itemQueryService;
        this.itemService = itemService;
        this.mediaService = mediaService;
        this.configService = configService;
        this.notificationService = notificationService;
        this.clock = clock != null ? clock : Clock.systemDefaultZone();
        this.jdbcClient = jdbcClient;
    }

    @Transactional
    public ReportResponse createReport(Long reporterId, CreateReportRequest request) {
        if (reporterId == null) {
            throw new IllegalArgumentException("reporterId cannot be null");
        }

        TargetType targetType = request.targetType();
        Long resolvedItemId = null;
        Long resolvedTargetUserId = null;
        Long resolvedRequestId = null;

        if (targetType == TargetType.ITEM) {
            resolvedItemId = request.resolvedItemId();
            if (resolvedItemId == null) {
                throw new BusinessException(
                        ReportErrorCode.REPORT_TARGET_REQUIRED, "Thiếu itemId để báo cáo bài đăng.");
            }

            // Kiểm tra bài đăng tồn tại
            ItemSummary itemSummary = itemQueryService.getSummary(resolvedItemId);
            if (itemSummary == null) {
                throw new BusinessException(
                        ReportErrorCode.REPORT_TARGET_NOT_FOUND, "Không tìm thấy bài đăng cần báo cáo.");
            }

            // Không tự báo cáo bài của chính mình
            if (reporterId.equals(itemSummary.donorId())) {
                throw new BusinessException(
                        ReportErrorCode.REPORT_CANNOT_REPORT_SELF, "Không thể tự báo cáo bài đăng của chính mình.");
            }

            // Mỗi người chỉ báo cáo một đối tượng một lần
            if (reportRepository.existsByReporterIdAndTargetTypeAndItemId(
                    reporterId, TargetType.ITEM, resolvedItemId)) {
                throw new BusinessException(
                        ReportErrorCode.REPORT_ALREADY_REPORTED, "Bạn đã báo cáo bài đăng này rồi.");
            }

        } else if (targetType == TargetType.USER) {
            resolvedTargetUserId = request.resolvedTargetUserId();
            if (resolvedTargetUserId == null) {
                throw new BusinessException(
                        ReportErrorCode.REPORT_TARGET_REQUIRED, "Thiếu targetUserId để báo cáo người dùng.");
            }

            // Không tự báo cáo chính mình
            if (reporterId.equals(resolvedTargetUserId)) {
                throw new BusinessException(
                        ReportErrorCode.REPORT_CANNOT_REPORT_SELF, "Không thể tự báo cáo chính mình.");
            }

            // Mỗi người chỉ báo cáo một đối tượng một lần
            if (reportRepository.existsByReporterIdAndTargetTypeAndTargetUserId(
                    reporterId, TargetType.USER, resolvedTargetUserId)) {
                throw new BusinessException(
                        ReportErrorCode.REPORT_ALREADY_REPORTED, "Bạn đã báo cáo người dùng này rồi.");
            }

        } else if (targetType == TargetType.REQUEST) {
            resolvedRequestId = request.resolvedRequestId();
            if (resolvedRequestId == null) {
                throw new BusinessException(
                        ReportErrorCode.REPORT_TARGET_REQUIRED, "Thiếu requestId để báo cáo giao dịch.");
            }

            if (reportRepository.existsByReporterIdAndTargetTypeAndRequestId(
                    reporterId, TargetType.REQUEST, resolvedRequestId)) {
                throw new BusinessException(
                        ReportErrorCode.REPORT_ALREADY_REPORTED, "Bạn đã báo cáo yêu cầu giao dịch này rồi.");
            }
        }

        Report report = new Report(
                reporterId,
                targetType,
                resolvedItemId,
                resolvedTargetUserId,
                resolvedRequestId,
                request.reason(),
                request.description().trim(),
                ReportStage.BEFORE);

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
     * Mở khiếu nại giao dịch đã hoàn thành trong vòng 7 ngày (FR-89, BR-T08, Issue #112).
     *
     * @param userId ID người gửi khiếu nại
     * @param requestId ID yêu cầu giao dịch
     * @param request thông tin khiếu nại kèm danh sách bằng chứng
     * @return thông tin khiếu nại được tạo và hạn chót giải trình
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
     *
     * @param userId ID người giải trình
     * @param disputeId ID bản ghi khiếu nại
     * @param request nội dung giải trình và danh sách ID media bằng chứng
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
