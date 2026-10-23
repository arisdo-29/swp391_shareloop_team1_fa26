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
import com.shareloop.user.dto.UserBrief;
import com.shareloop.user.service.UserService;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
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

    /**
     * Trang danh sách hồ sơ cho Admin. Mỗi dòng kèm tên người gửi và tên đối tượng bị báo cáo để trang quản trị không
     * phải hiện id. Tên được tra một lần cho cả trang.
     */
    public AdminReportPage list(ReportStatus status, TargetType targetType, int page, int size) {
        var result = reports.findForAdmin(
                status, targetType, PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt", "id")));
        List<Report> content = result.getContent();

        Map<Long, Long> itemIdByReportId = findRelatedItemIds(content);
        Map<Long, String> itemTitles = items.findTitles(new HashSet<>(itemIdByReportId.values()));
        Map<Long, UserBrief> briefs = users.findBriefs(collectUserIds(content));

        List<ReportResponse> rows = new ArrayList<>();
        for (Report report : content) {
            Long itemId = itemIdByReportId.get(report.getId());
            String itemTitle = itemId == null ? null : itemTitles.get(itemId);
            String reporterName = nameOf(briefs, report.getReporterId());
            rows.add(summary(report, reporterName, targetNameOf(report, briefs, itemTitle)));
        }
        return new AdminReportPage(rows, page, size, result.getTotalElements(), result.getTotalPages());
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
        if (report.getResolvedBy() != null) ids.add(report.getResolvedBy());
        var briefs = users.findBriefs(ids);
        var reporter = briefs.get(report.getReporterId());
        var reported = targetId == null ? null : briefs.get(targetId);
        String itemTitle =
                itemId == null ? null : items.findTitles(List.of(itemId)).get(itemId);
        String reporterName = reporter == null ? null : reporter.fullName();
        return new AdminReportDetail(
                summary(report, reporterName, targetNameOf(report, briefs, itemTitle)),
                new Participant(report.getReporterId(), reporterName),
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
                report.getResolvedAt(),
                itemTitle,
                nameOf(briefs, report.getResolvedBy()));
    }

    // Id bài đăng liên quan của từng hồ sơ. Hồ sơ REQUEST lấy bài qua giao dịch, giao dịch đã xoá thì bỏ qua.
    private Map<Long, Long> findRelatedItemIds(List<Report> content) {
        Map<Long, Long> itemIdByReportId = new HashMap<>();
        for (Report report : content) {
            Long itemId = report.getItemId();
            if (itemId == null && report.getTargetType() == TargetType.REQUEST && report.getRequestId() != null) {
                itemId = findItemIdOfRequest(report.getRequestId());
            }
            if (itemId != null) {
                itemIdByReportId.put(report.getId(), itemId);
            }
        }
        return itemIdByReportId;
    }

    private Long findItemIdOfRequest(Long requestId) {
        try {
            return requests.getChatContext(requestId).itemId();
        } catch (BusinessException exception) {
            if (exception.getErrorCode().status().value() != 404) throw exception;
            return null;
        }
    }

    private Set<Long> collectUserIds(List<Report> content) {
        Set<Long> userIds = new HashSet<>();
        for (Report report : content) {
            userIds.add(report.getReporterId());
            if (report.getTargetUserId() != null) {
                userIds.add(report.getTargetUserId());
            }
        }
        return userIds;
    }

    // Hồ sơ về người dùng thì đối tượng là tên người đó, hồ sơ về bài đăng hoặc giao dịch thì là tiêu đề bài.
    private String targetNameOf(Report report, Map<Long, UserBrief> briefs, String itemTitle) {
        if (report.getTargetType() == TargetType.USER) {
            return nameOf(briefs, report.getTargetUserId());
        }
        return itemTitle;
    }

    private String nameOf(Map<Long, UserBrief> briefs, Long userId) {
        if (userId == null) {
            return null;
        }
        UserBrief brief = briefs.get(userId);
        return brief == null ? null : brief.fullName();
    }

    private ReportResponse summary(Report report, String reporterName, String targetName) {
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
                report.getCreatedAt(),
                reporterName,
                targetName);
    }
}
