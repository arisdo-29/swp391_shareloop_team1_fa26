package com.shareloop.report.service;

import com.shareloop.common.exception.BusinessException;
import com.shareloop.item.service.ItemQueryService;
import com.shareloop.report.ReportErrorCode;
import com.shareloop.report.dto.AdminReportDetail;
import com.shareloop.report.dto.AdminReportDetail.Participant;
import com.shareloop.report.dto.AdminReportPage;
import com.shareloop.report.dto.ReportResponse;
import com.shareloop.report.entity.Report;
import com.shareloop.report.entity.ReportStatus;
import com.shareloop.report.entity.TargetType;
import com.shareloop.report.repository.ReportRepository;
import com.shareloop.request.service.RequestService;
import com.shareloop.user.service.UserService;
import java.util.HashSet;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

@Service
public class AdminReportQueryService {
    private final ReportRepository reports;
    private final UserService users;
    private final ItemQueryService items;
    private final RequestService requests;
    private final ReportEvidenceService evidence;

    public AdminReportQueryService(
            ReportRepository reports,
            UserService users,
            ItemQueryService items,
            RequestService requests,
            ReportEvidenceService evidence) {
        this.reports = reports;
        this.users = users;
        this.items = items;
        this.requests = requests;
        this.evidence = evidence;
    }

    public AdminReportPage list(ReportStatus status, TargetType targetType, int page, int size) {
        var result = reports.findForAdmin(
                status, targetType, PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt", "id")));
        return new AdminReportPage(
                result.getContent().stream().map(this::summary).toList(),
                page,
                size,
                result.getTotalElements(),
                result.getTotalPages());
    }

    public AdminReportDetail detail(long reportId) {
        Report report =
                reports.findById(reportId).orElseThrow(() -> new BusinessException(ReportErrorCode.REPORT_NOT_FOUND));
        Long targetId = report.getTargetUserId();
        Long itemId = report.getItemId();
        try {
            if (report.getTargetType() == TargetType.ITEM && itemId != null) {
                var item = items.getSummary(itemId);
                if (item != null) targetId = item.donorId();
            } else if (report.getTargetType() == TargetType.REQUEST && report.getRequestId() != null) {
                var context = requests.getChatContext(report.getRequestId());
                itemId = context.itemId();
                if (targetId == null) {
                    if (report.getReporterId().equals(context.donorId())) targetId = context.receiverId();
                    else if (report.getReporterId().equals(context.receiverId())) targetId = context.donorId();
                }
            }
        } catch (BusinessException exception) {
            if (exception.getErrorCode().status().value() != 404) throw exception;
            // Preserve the report when its related target has been removed.
        }
        var ids = new HashSet<Long>();
        ids.add(report.getReporterId());
        if (targetId != null) ids.add(targetId);
        var briefs = users.findBriefs(ids);
        var reporter = briefs.get(report.getReporterId());
        var reported = targetId == null ? null : briefs.get(targetId);
        return new AdminReportDetail(
                summary(report),
                new Participant(report.getReporterId(), reporter == null ? null : reporter.fullName()),
                targetId == null ? null : new Participant(targetId, reported == null ? null : reported.fullName()),
                itemId,
                report.getResponse(),
                report.getRespondedAt(),
                report.getResponseDueAt(),
                evidence.describe(reportId, report.getEvidence()),
                evidence.describe(reportId, report.getResponseEvidence()),
                report.getResolvedBy(),
                report.getResolution(),
                report.getStarsDeducted(),
                report.getResolvedAt());
    }

    private ReportResponse summary(Report report) {
        return new ReportResponse(
                report.getId(),
                report.getReporterId(),
                report.getTargetType(),
                report.getItemId(),
                report.getTargetUserId(),
                report.getRequestId(),
                report.getReason(),
                report.getDescription(),
                report.getStage(),
                report.getStatus(),
                report.getCreatedAt());
    }
}
