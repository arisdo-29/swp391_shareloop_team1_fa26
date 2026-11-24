package com.shareloop.admin.service;

import com.shareloop.admin.dto.AdminItemHistoryEntry;
import com.shareloop.admin.dto.AdminItemHistoryEntry.ActorRole;
import com.shareloop.admin.dto.AdminItemRequestRow;
import com.shareloop.audit.ActivityType;
import com.shareloop.audit.dto.ActivityLogDto;
import com.shareloop.item.dto.AdminItemBase;
import com.shareloop.item.entity.ItemStatus;
import com.shareloop.report.dto.ItemReportBrief;
import com.shareloop.review.service.ReviewQueryService;
import com.shareloop.review.service.ReviewQueryService.Rejection;
import com.shareloop.user.dto.UserBrief;
import com.shareloop.user.service.UserService;
import com.shareloop.wallet.dto.WalletTransactionResponse;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;

@Service
public class AdminItemHistoryService {
    private final ReviewQueryService reviewQueryService;
    private final UserService userService;

    public AdminItemHistoryService(ReviewQueryService reviewQueryService, UserService userService) {
        this.reviewQueryService = reviewQueryService;
        this.userService = userService;
    }

    public List<AdminItemHistoryEntry> build(
            AdminItemBase item,
            String ownerName,
            List<ActivityLogDto> logs,
            List<AdminItemRequestRow> requests,
            List<ItemReportBrief> reports,
            List<WalletTransactionResponse> fees) {
        List<AdminItemHistoryEntry> history = new ArrayList<>();
        for (ActivityLogDto log : logs) {
            ActorRole role =
                    switch (ActivityType.valueOf(log.type())) {
                        case ITEM_EDIT_SUBMITTED -> ActorRole.OWNER;
                        case ITEM_DISPLAY_COMPENSATED -> ActorRole.SYSTEM;
                        default -> ActorRole.ADMIN;
                    };
            history.add(new AdminItemHistoryEntry(
                    "log-" + log.id(), log.typeLabel(), role, log.actorName(), log.createdAt(), log.reason()));
        }

        history.add(new AdminItemHistoryEntry(
                "submitted", "Đăng bài và gửi duyệt", ActorRole.OWNER, ownerName, item.createdAt(), null));
        if (item.reviewSubmittedAt() != null && !hasLog(logs, ActivityType.ITEM_EDIT_SUBMITTED)) {
            history.add(new AdminItemHistoryEntry(
                    "last-edit",
                    ActivityType.ITEM_EDIT_SUBMITTED.label(),
                    ActorRole.OWNER,
                    ownerName,
                    item.reviewSubmittedAt(),
                    null));
        }
        if (item.approvedAt() != null && !hasLog(logs, ActivityType.REVIEW_APPROVE)) {
            history.add(new AdminItemHistoryEntry(
                    "last-approval",
                    ActivityType.REVIEW_APPROVE.label(),
                    ActorRole.ADMIN,
                    null,
                    item.approvedAt(),
                    null));
        }

        // Dữ liệu cũ có thể còn lý do trên bài nhưng chưa có dòng nhật ký tương ứng.
        boolean needsRejection = item.status() == ItemStatus.REJECTED && !hasLog(logs, ActivityType.REVIEW_REJECT);
        Rejection rejection = needsRejection
                ? reviewQueryService.findRejection(item.id()).orElse(new Rejection(null, null, null))
                : null;
        Set<Long> userIds = new HashSet<>();
        reports.forEach(report -> userIds.add(report.reporterId()));
        if (rejection != null && rejection.actorId() != null) {
            userIds.add(rejection.actorId());
        }
        Map<Long, UserBrief> users = userService.findBriefs(userIds);
        if (rejection != null) {
            history.add(new AdminItemHistoryEntry(
                    "last-rejection",
                    ActivityType.REVIEW_REJECT.label(),
                    ActorRole.ADMIN,
                    findName(users, rejection.actorId()),
                    rejection.createdAt(),
                    rejection.reason()));
        }

        for (AdminItemRequestRow request : requests) {
            String action = "SWAP".equals(request.type()) ? "Gửi yêu cầu trao đổi" : "Gửi yêu cầu xin nhận";
            history.add(new AdminItemHistoryEntry(
                    "request-" + request.id(),
                    action,
                    ActorRole.USER,
                    request.receiverName(),
                    request.createdAt(),
                    null));
        }
        for (ItemReportBrief report : reports) {
            history.add(new AdminItemHistoryEntry(
                    "report-" + report.id(),
                    "Gửi báo cáo liên quan đến bài",
                    ActorRole.USER,
                    findName(users, report.reporterId()),
                    report.createdAt(),
                    null));
        }
        for (WalletTransactionResponse fee : fees) {
            String action =
                    switch (fee.type()) {
                        case RENEW_FEE -> "Gia hạn bài";
                        case BOOST_FEE -> "Đẩy bài";
                        default -> null;
                    };
            if (action != null && fee.amount() < 0) {
                history.add(new AdminItemHistoryEntry(
                        "fee-" + fee.id(), action, ActorRole.OWNER, ownerName, fee.createdAt(), null));
            }
        }

        history.sort(Comparator.comparing(
                AdminItemHistoryEntry::createdAt, Comparator.nullsLast(Comparator.reverseOrder())));
        return history;
    }

    private static boolean hasLog(List<ActivityLogDto> logs, ActivityType type) {
        return logs.stream().anyMatch(log -> type.code().equals(log.type()));
    }

    private static String findName(Map<Long, UserBrief> users, Long id) {
        UserBrief user = id == null ? null : users.get(id);
        return user == null ? null : user.fullName();
    }
}
