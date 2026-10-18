package com.shareloop.report.service;

import com.shareloop.audit.service.ActivityLogService;
import com.shareloop.common.exception.BusinessException;
import com.shareloop.item.dto.ItemSummary;
import com.shareloop.item.service.ItemQueryService;
import com.shareloop.item.service.ItemService;
import com.shareloop.report.ReportErrorCode;
import com.shareloop.report.dto.ConcludeReportRequest;
import com.shareloop.report.dto.CreateReportRequest;
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
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReportService {

    private final ReportRepository reportRepository;
    private final ItemQueryService itemQueryService;
    private final ItemService itemService;
    private final ReputationService reputationService;
    private final ActivityLogService activityLogService;
    private final ConfigService configService;
    private final RequestService requestService;

    public ReportService(
            ReportRepository reportRepository,
            ItemQueryService itemQueryService,
            ItemService itemService,
            @Autowired(required = false) ReputationService reputationService,
            @Autowired(required = false) ActivityLogService activityLogService,
            @Autowired(required = false) ConfigService configService,
            @Autowired(required = false) RequestService requestService) {
        this.reportRepository = reportRepository;
        this.itemQueryService = itemQueryService;
        this.itemService = itemService;
        this.reputationService = reputationService;
        this.activityLogService = activityLogService;
        this.configService = configService;
        this.requestService = requestService;
    }

    public ReportService(
            ReportRepository reportRepository, ItemQueryService itemQueryService, ItemService itemService) {
        this(reportRepository, itemQueryService, itemService, null, null, null, null);
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
     * Admin kết luận khiếu nại (FR-91, FR-93, BR-U04, BR-A07, Issue #113).
     *
     * @param adminId ID của Admin thực hiện
     * @param reportId ID của báo cáo vi phạm
     * @param request quyết định kết luận và ghi chú
     * @return kết quả kết luận báo cáo
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
                        "REPORT_DISMISSED",
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
                        "REPORT_UPHELD",
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
}
