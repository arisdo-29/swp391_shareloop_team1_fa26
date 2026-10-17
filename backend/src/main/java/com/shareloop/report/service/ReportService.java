package com.shareloop.report.service;

import com.shareloop.common.exception.BusinessException;
import com.shareloop.item.dto.ItemSummary;
import com.shareloop.item.service.ItemQueryService;
import com.shareloop.item.service.ItemService;
import com.shareloop.report.ReportErrorCode;
import com.shareloop.report.dto.CreateReportRequest;
import com.shareloop.report.dto.ReportResponse;
import com.shareloop.report.entity.Report;
import com.shareloop.report.entity.ReportStage;
import com.shareloop.report.entity.TargetType;
import com.shareloop.report.repository.ReportRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReportService {

    private final ReportRepository reportRepository;
    private final ItemQueryService itemQueryService;
    private final ItemService itemService;

    public ReportService(
            ReportRepository reportRepository, ItemQueryService itemQueryService, ItemService itemService) {
        this.reportRepository = reportRepository;
        this.itemQueryService = itemQueryService;
        this.itemService = itemService;
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
            if (count >= 3) {
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
}
