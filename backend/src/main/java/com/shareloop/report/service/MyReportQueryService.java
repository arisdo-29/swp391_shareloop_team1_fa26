package com.shareloop.report.service;

import com.shareloop.common.exception.BusinessException;
import com.shareloop.item.service.ItemQueryService;
import com.shareloop.report.ReportErrorCode;
import com.shareloop.report.dto.MyReportSummary;
import com.shareloop.report.dto.RespondentReportDetail;
import com.shareloop.report.entity.Report;
import com.shareloop.report.entity.TargetType;
import com.shareloop.report.repository.ReportRepository;
import com.shareloop.request.service.RequestService;
import com.shareloop.user.dto.UserBrief;
import com.shareloop.user.service.UserService;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Đọc hồ sơ báo cáo và khiếu nại cho chính Member: hồ sơ đã gửi và hồ sơ cần giải trình. */
@Service
public class MyReportQueryService {
    private final ReportRepository reportRepository;
    private final UserService userService;
    private final ItemQueryService itemQueryService;
    private final RequestService requestService;
    private final ReportEvidenceService reportEvidenceService;
    private final Clock clock;

    public MyReportQueryService(
            ReportRepository reportRepository,
            UserService userService,
            ItemQueryService itemQueryService,
            RequestService requestService,
            ReportEvidenceService reportEvidenceService,
            Clock clock) {
        this.reportRepository = reportRepository;
        this.userService = userService;
        this.itemQueryService = itemQueryService;
        this.requestService = requestService;
        this.reportEvidenceService = reportEvidenceService;
        this.clock = clock;
    }

    /**
     * Các báo cáo bài đăng, người dùng và khiếu nại giao dịch tôi đã gửi, mới nhất lên trước, kèm trạng thái xử lý
     * và ghi chú kết luận của Admin (FR-88, FR-89).
     */
    // readOnly: chỉ đọc nên Hibernate không phải theo dõi thay đổi của các entity đã tải.
    @Transactional(readOnly = true)
    public List<MyReportSummary> listSent(Long userId) {
        return toSummaries(reportRepository.findByReporterIdOrderByCreatedAtDescIdDesc(userId));
    }

    /**
     * Các hồ sơ nhắm vào tôi mà Admin đã yêu cầu giải trình hoặc là khiếu nại giao dịch (đã có hạn giải trình), mới
     * nhất lên trước (D-07, BC-03).
     */
    @Transactional(readOnly = true)
    public List<MyReportSummary> listAgainstMe(Long userId) {
        return toSummaries(
                reportRepository.findByTargetUserIdAndResponseDueAtIsNotNullOrderByCreatedAtDescIdDesc(userId));
    }

    /**
     * Chi tiết một hồ sơ cho bên bị phản ánh trên trang giải trình: lý do, mô tả, ảnh của bên báo cáo và hạn giải trình
     * (D-07, BC-03). Không trả người gửi.
     *
     * @throws BusinessException REPORT_NOT_FOUND khi hồ sơ không tồn tại, không nhắm vào tôi hoặc chưa có hạn giải
     *     trình (không phân biệt để người ngoài không dò được hồ sơ)
     */
    @Transactional(readOnly = true)
    public RespondentReportDetail detailForRespondent(Long userId, long reportId) {
        Report report = reportRepository
                .findById(reportId)
                .filter(found -> found.isVisibleToRespondent(userId))
                .orElseThrow(() -> new BusinessException(ReportErrorCode.REPORT_NOT_FOUND));
        // Dùng lại cách tính tên đối tượng và trạng thái hạn của danh sách để hai màn hình luôn khớp nhau.
        MyReportSummary summary = toSummaries(List.of(report)).getFirst();
        return new RespondentReportDetail(
                report.getId(),
                report.getTargetType(),
                report.getItemId(),
                report.getRequestId(),
                report.getReason(),
                report.getDescription(),
                summary.targetName(),
                report.getStatus(),
                report.getCreatedAt(),
                report.getResponseDueAt(),
                summary.responseWindowOpen(),
                reportEvidenceService.describeForRespondent(reportId, report.getEvidence()),
                report.getResponse(),
                report.getRespondedAt(),
                reportEvidenceService.describeForRespondent(reportId, report.getResponseEvidence()),
                report.getResolution(),
                report.getResolvedAt());
    }

    // Tra tên cho cả danh sách một lần thay vì từng dòng, tránh gọi module khác lặp lại nhiều lần.
    private List<MyReportSummary> toSummaries(List<Report> reports) {
        Map<Long, Long> itemIdByReportId = findRelatedItemIds(reports);
        Map<Long, String> itemTitles = itemQueryService.findTitles(new HashSet<>(itemIdByReportId.values()));
        Map<Long, UserBrief> briefs = userService.findBriefs(collectTargetUserIds(reports));
        Instant now = Instant.now(clock);

        List<MyReportSummary> summaries = new ArrayList<>();
        for (Report report : reports) {
            Long itemId = itemIdByReportId.get(report.getId());
            String itemTitle = itemId == null ? null : itemTitles.get(itemId);
            String targetName =
                    report.getTargetType() == TargetType.USER ? nameOf(briefs, report.getTargetUserId()) : itemTitle;
            summaries.add(toSummary(report, targetName, now));
        }
        return summaries;
    }

    // Hồ sơ giao dịch không lưu item_id nên lấy bài qua giao dịch; giao dịch đã xoá thì để trống tên.
    private Map<Long, Long> findRelatedItemIds(List<Report> reports) {
        Map<Long, Long> itemIdByReportId = new HashMap<>();
        for (Report report : reports) {
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
            return requestService.getChatContext(requestId).itemId();
        } catch (BusinessException exception) {
            if (exception.getErrorCode().status().value() != 404) throw exception;
            return null;
        }
    }

    private Set<Long> collectTargetUserIds(List<Report> reports) {
        Set<Long> userIds = new HashSet<>();
        for (Report report : reports) {
            if (report.getTargetType() == TargetType.USER && report.getTargetUserId() != null) {
                userIds.add(report.getTargetUserId());
            }
        }
        return userIds;
    }

    private String nameOf(Map<Long, UserBrief> briefs, Long userId) {
        UserBrief brief = userId == null ? null : briefs.get(userId);
        return brief == null ? null : brief.fullName();
    }

    private MyReportSummary toSummary(Report report, String targetName, Instant now) {
        return new MyReportSummary(
                report.getId(),
                report.getTargetType(),
                report.getItemId(),
                report.getRequestId(),
                report.getReason(),
                report.getStatus(),
                targetName,
                report.getCreatedAt(),
                report.getResponseDueAt(),
                report.getRespondedAt(),
                // Cùng luật với ReportService.respond: chỉ còn mở khi chưa giải trình và chưa quá hạn
                report.isResponseWindowOpen(now) && report.getRespondedAt() == null,
                report.getResolution(),
                report.getResolvedAt());
    }
}
