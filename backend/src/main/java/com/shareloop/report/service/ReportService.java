package com.shareloop.report.service;

import com.shareloop.audit.ActivityType;
import com.shareloop.audit.service.ActivityLogService;
import com.shareloop.common.exception.BusinessException;
import com.shareloop.item.dto.ItemSummary;
import com.shareloop.item.service.ItemQueryService;
import com.shareloop.item.service.ItemService;
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
import com.shareloop.report.dto.RespondReportRequest;
import com.shareloop.report.entity.Report;
import com.shareloop.report.entity.ReportReason;
import com.shareloop.report.entity.ReportStage;
import com.shareloop.report.entity.ReportStatus;
import com.shareloop.report.entity.TargetType;
import com.shareloop.report.repository.ReportRepository;
import com.shareloop.reputation.service.ReputationService;
import com.shareloop.request.dto.RequestChatContext;
import com.shareloop.request.service.RequestService;
import com.shareloop.setting.ConfigKey;
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

    /** Report chưa có kết luận; DISMISSED và RESOLVED không tính vào ngưỡng tự ẩn bài. */
    private static final List<ReportStatus> PENDING_STATUSES =
            List.of(ReportStatus.OPEN, ReportStatus.AWAITING_RESPONSE);

    private final ReportRepository reportRepository;
    private final ItemQueryService itemQueryService;
    private final ItemService itemService;
    private final NotificationService notificationService;
    private final ReputationService reputationService;
    private final ActivityLogService activityLogService;
    private final ConfigService configService;
    private final RequestService requestService;
    private final ReportEvidenceService evidenceService;
    private final JdbcClient jdbcClient;
    private final Clock clock;

    @Autowired
    public ReportService(
            ReportRepository reportRepository,
            ItemQueryService itemQueryService,
            @Autowired(required = false) ItemService itemService,
            ReportEvidenceService evidenceService,
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
        this.evidenceService = evidenceService;
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
                resolvedTargetUserId = summary.donorId();
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
                return toReportResponse(openDisputeReport(
                        reporterId,
                        resolvedRequestId,
                        new CreateDisputeRequest(request.reason(), request.description(), request.evidenceMediaIds())));
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

        report.setEvidence(evidenceService.attach(request.evidenceMediaIds(), reporterId));
        Report saved = reportRepository.save(report);

        if (targetType == TargetType.ITEM && resolvedItemId != null) {
            hideItemWhenReportThresholdReached(resolvedItemId);
        }

        return toReportResponse(saved);
    }

    /**
     * BR-A03, FR-49: bài có đủ ngưỡng report đang chờ xử lý thì tự ẩn (is_active = false, giữ nguyên status). Hàng
     * chờ là trang khiếu nại của Admin, nơi các report OPEN đã nằm sẵn, nên chỉ báo Admin một lần lúc bài vừa chạm
     * ngưỡng; report sau đó vào bài đã ẩn không báo lại.
     */
    private void hideItemWhenReportThresholdReached(long itemId) {
        int threshold = configService.getInt(ConfigKey.REPORT_HIDE_THRESHOLD);
        long pendingReports =
                reportRepository.countByTargetTypeAndItemIdAndStatusIn(TargetType.ITEM, itemId, PENDING_STATUSES);
        if (pendingReports < threshold || itemService == null) {
            return;
        }

        itemService.hide(itemId);
        if (pendingReports == threshold && notificationService != null) {
            notificationService.notifyAdmins(
                    "ITEM_AUTO_HIDDEN",
                    "Bài đăng bị ẩn do nhiều báo cáo",
                    "Bài" + quotedTitle(itemTitle(itemId)) + " đã tự ẩn sau " + pendingReports
                            + " báo cáo đang chờ xử lý. Vui lòng xem xét trong hàng chờ khiếu nại.",
                    "/admin/complaints");
        }
    }

    private ReportResponse toReportResponse(Report saved) {
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

    @Transactional
    public void respond(Long userId, long reportId, RespondReportRequest input) {
        Report report = reportRepository
                .findByIdForUpdate(reportId)
                .orElseThrow(() -> new BusinessException(ReportErrorCode.REPORT_NOT_FOUND));
        Long targetId = resolveViolator(report);
        if (!userId.equals(targetId)) throw new BusinessException(ReportErrorCode.REPORT_NOT_PARTICIPANT);
        Instant now = Instant.now(clock);
        if (report.getStatus() != ReportStatus.AWAITING_RESPONSE
                || report.getRespondedAt() != null
                || report.getResponseDueAt() == null
                || now.isAfter(report.getResponseDueAt())) {
            throw new BusinessException(ReportErrorCode.REPORT_RESPONSE_NOT_ALLOWED);
        }
        report.respond(input.response().trim(), evidenceService.attach(input.evidenceMediaIds(), userId), now);
    }

    /**
     * Mở khiếu nại giao dịch đã hoàn tất trong vòng 7 ngày (FR-89, BR-T08, Issue #112).
     */
    @Transactional
    public DisputeResponse openDispute(Long userId, Long requestId, CreateDisputeRequest request) {
        Report saved = openDisputeReport(userId, requestId, request);
        return new DisputeResponse(saved.getId(), saved.getResponseDueAt());
    }

    private Report openDisputeReport(Long userId, Long requestId, CreateDisputeRequest request) {
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
        int windowDays = disputeDays(ConfigKey.COMPLAINT_WINDOW_DAYS);
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

        // 7. Mở cửa sổ giải trình 3 ngày cho bên bị khiếu nại
        int responseDays = disputeDays(ConfigKey.RESPONSE_WINDOW_DAYS);
        Instant now = Instant.now(clock);
        Instant responseDueAt = now.plus(Duration.ofDays(responseDays));
        Long respondentId = userId.equals(donorId) ? receiverId : donorId;

        String evidenceStr = evidenceService.attach(evidenceMediaIds, userId);

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
            // Gọi giao dịch bằng tên món thay cho "Giao dịch #id"
            String transaction = "Giao dịch" + quotedTitle(itemTitle(reqSnapshot.itemId()));
            notificationService.notify(
                    respondentId,
                    "DISPUTE_OPENED",
                    transaction + " bị khiếu nại",
                    transaction + " đã bị đối tác khiếu nại. Bạn có " + (responseDays * 24) + " giờ để giải trình.",
                    "/me/disputes/" + saved.getId());
        }

        return saved;
    }

    /**
     * Tự mở report NOT_AS_DESCRIBED khi bên nhận chọn "Không đúng mô tả" lúc xác nhận giao nhận (FR-85, BR-R05).
     * Khác openDisputeReport ở chỗ không đòi giao dịch đã COMPLETED, vì bên nhận có thể trả lời trước khi bên cho xác
     * nhận. Mỗi bên nhận chỉ có một report cho mỗi giao dịch: gọi lại thì bỏ qua, không lỗi.
     *
     * @throws BusinessException REPORT_TARGET_NOT_FOUND khi không thấy giao dịch hoặc bài đăng; DISPUTE_NOT_A_PARTY khi
     *     receiverId không phải bên nhận của giao dịch
     */
    @Transactional
    public void openNotAsDescribedReport(Long receiverId, Long requestId, String reason) {
        // Event có thể tới hai lần nên kiểm trùng trước, không ném lỗi
        if (reportRepository.existsByReporterIdAndTargetTypeAndRequestId(receiverId, TargetType.REQUEST, requestId)) {
            return;
        }

        RequestSnapshot requestSnapshot = findRequest(requestId);
        if (requestSnapshot == null) {
            throw new BusinessException(ReportErrorCode.REPORT_TARGET_NOT_FOUND, "Không tìm thấy yêu cầu giao dịch.");
        }
        if (!receiverId.equals(requestSnapshot.receiverId())) {
            throw new BusinessException(ReportErrorCode.DISPUTE_NOT_A_PARTY);
        }
        ItemSummary itemSummary = itemQueryService.getSummary(requestSnapshot.itemId());
        if (itemSummary == null) {
            throw new BusinessException(
                    ReportErrorCode.REPORT_TARGET_NOT_FOUND, "Không tìm thấy bài đăng của giao dịch.");
        }

        // Bên cho là bên bị báo cáo và có thời hạn giải trình như khiếu nại thường (FR-90)
        int responseDays = disputeDays(ConfigKey.RESPONSE_WINDOW_DAYS);
        Instant responseDueAt = Instant.now(clock).plus(Duration.ofDays(responseDays));
        Report report = Report.createDispute(
                receiverId,
                requestId,
                requestSnapshot.itemId(),
                itemSummary.donorId(),
                ReportReason.NOT_AS_DESCRIBED,
                reason,
                null,
                responseDueAt);
        Report saved = reportRepository.save(report);

        if (notificationService != null) {
            notificationService.notify(
                    itemSummary.donorId(),
                    "DISPUTE_OPENED",
                    "Món đồ bị báo không đúng mô tả",
                    "Người nhận báo món đồ không đúng mô tả. Bạn có " + (responseDays * 24) + " giờ để giải trình.",
                    "/me/disputes/" + saved.getId());
        }
    }

    /**
     * Mở khiếu nại NO_SHOW khi một bên báo chưa nhận được hoặc chưa giao được đồ lúc giao dịch đã sang DISPUTED.
     * Bên còn lại là bên bị báo cáo và có thời hạn giải trình như khiếu nại thường (FR-90). Mỗi người chỉ có một
     * report cho mỗi giao dịch: gọi lại thì bỏ qua, không lỗi.
     *
     * @param reporterId bên báo sự cố, phải là chủ bài hoặc người nhận của giao dịch
     * @param evidenceMediaIds id media minh chứng, có thể null hoặc rỗng
     * @throws BusinessException REPORT_TARGET_NOT_FOUND khi không thấy giao dịch hoặc bài đăng; DISPUTE_NOT_A_PARTY
     *     khi reporterId không thuộc giao dịch
     */
    @Transactional
    public void openHandoverIssueReport(Long reporterId, Long requestId, String note, List<Long> evidenceMediaIds) {
        if (reportRepository.existsByReporterIdAndTargetTypeAndRequestId(reporterId, TargetType.REQUEST, requestId)) {
            return;
        }

        RequestSnapshot requestSnapshot = findRequest(requestId);
        if (requestSnapshot == null) {
            throw new BusinessException(ReportErrorCode.REPORT_TARGET_NOT_FOUND, "Không tìm thấy yêu cầu giao dịch.");
        }
        ItemSummary itemSummary = itemQueryService.getSummary(requestSnapshot.itemId());
        if (itemSummary == null) {
            throw new BusinessException(
                    ReportErrorCode.REPORT_TARGET_NOT_FOUND, "Không tìm thấy bài đăng của giao dịch.");
        }
        Long donorId = itemSummary.donorId();
        Long receiverId = requestSnapshot.receiverId();
        boolean reporterIsReceiver = reporterId.equals(receiverId);
        if (!reporterIsReceiver && !reporterId.equals(donorId)) {
            throw new BusinessException(ReportErrorCode.DISPUTE_NOT_A_PARTY);
        }

        Long respondentId = reporterIsReceiver ? donorId : receiverId;
        int responseDays = disputeDays(ConfigKey.RESPONSE_WINDOW_DAYS);
        Instant responseDueAt = Instant.now(clock).plus(Duration.ofDays(responseDays));
        Report report = Report.createDispute(
                reporterId,
                requestId,
                requestSnapshot.itemId(),
                respondentId,
                ReportReason.NO_SHOW,
                note,
                evidenceService.attach(evidenceMediaIds, reporterId),
                responseDueAt);
        Report saved = reportRepository.save(report);

        if (notificationService != null) {
            String issue = reporterIsReceiver ? "chưa nhận được đồ" : "chưa giao được đồ";
            notificationService.notify(
                    respondentId,
                    "DISPUTE_OPENED",
                    "Đối phương báo " + issue,
                    "Đối phương báo " + issue + " dù bạn đã xác nhận bàn giao. Bạn có " + (responseDays * 24)
                            + " giờ để giải trình.",
                    "/me/disputes/" + saved.getId());
        }
    }

    /** Giao dịch đã có report nào chưa (FR-87: job tự hoàn tất chỉ xử lý giao dịch không có report). */
    @Transactional(readOnly = true)
    public boolean hasReportForRequest(Long requestId) {
        return reportRepository.existsByRequestId(requestId);
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

        String evidenceStr = evidenceService.attach(evidenceMediaIds, userId);

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

        // Ghi chú mặc định gọi báo cáo bằng tên bài, không dùng "báo cáo #id". Báo cáo người dùng không có bài thì để
        // trống.
        String title = itemTitle(report.getItemId());
        String about = title == null || title.isBlank() ? "" : " về “" + title + "”";

        if (decision == ReportDecision.DISMISS) {
            if (activityLogService != null) {
                Map<String, Object> logPayload = new HashMap<>();
                logPayload.put("decision", "DISMISS");
                logPayload.put("reportId", reportId);
                logPayload.put("targetType", report.getTargetType().name());
                // Nhật ký Admin dùng itemId để hiện tên bài thay cho "Báo cáo #id"
                if (report.getItemId() != null) {
                    logPayload.put("itemId", report.getItemId());
                }
                activityLogService.log(
                        ActivityType.REPORT_DISMISSED.name(),
                        adminId,
                        "REPORT",
                        reportId,
                        logPayload,
                        note != null ? note : "Bác bỏ báo cáo" + about);
            }
        } else if (decision == ReportDecision.UPHOLD) {
            Long violatorId = resolveViolator(report);

            // BR-U04: Trừ 1 sao uy tín
            if (violatorId != null && reputationService != null) {
                reputationService.deductStar(
                        violatorId, 1, note != null ? note : "Bị kết luận vi phạm theo báo cáo" + about);
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
                if (report.getItemId() != null) {
                    logPayload.put("itemId", report.getItemId());
                }

                activityLogService.log(
                        ActivityType.REPORT_UPHELD.name(),
                        adminId,
                        "REPORT",
                        reportId,
                        logPayload,
                        note != null ? note : "Chấp thuận báo cáo" + about + ", xử phạt trừ 1 sao uy tín");
            }
        }

        // Khiếu nại mở từ handover-issue đưa giao dịch vào DISPUTED; kết luận xong phải đưa nó ra. Giao dịch không ở
        // DISPUTED (khiếu nại mở sau COMPLETED, báo cáo bài/người) thì RequestService bỏ qua.
        if (requestService != null && report.getTargetType() == TargetType.REQUEST && report.getRequestId() != null) {
            requestService.resolveDisputedRequest(report.getRequestId(), decision == ReportDecision.UPHOLD);
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

    // Đọc đúng key mà trang Admin sửa (ConfigKey, viết thường), không đọc key viết hoa cũ của migration.
    // configService null chỉ gặp ở unit test dựng service rút gọn; khi đó dùng giá trị mặc định.
    private int disputeDays(ConfigKey key) {
        return configService != null ? configService.getInt(key) : key.getDefaultValue();
    }

    // Tên bài để đưa vào câu chữ thay cho "Bài #id", "Giao dịch #id". Không tìm được thì trả null.
    private String itemTitle(Long itemId) {
        if (itemId == null || itemQueryService == null) {
            return null;
        }
        return itemQueryService.findTitles(List.of(itemId)).get(itemId);
    }

    // " “Tên bài”" khi có tên, chuỗi rỗng khi không có, để ghép vào cuối câu chữ
    private static String quotedTitle(String title) {
        return title == null || title.isBlank() ? "" : " “" + title + "”";
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
