package com.shareloop.admin.service;

import com.shareloop.admin.dto.AdminItemFullDetail;
import com.shareloop.admin.dto.AdminItemReportRow;
import com.shareloop.admin.dto.AdminItemRequestRow;
import com.shareloop.admin.dto.AdminItemRow;
import com.shareloop.audit.ActivityType;
import com.shareloop.audit.dto.ActivityLogDto;
import com.shareloop.audit.service.ActivityLogQueryService;
import com.shareloop.audit.service.ActivityLogService;
import com.shareloop.catalog.service.CategoryService;
import com.shareloop.common.api.PageResponse;
import com.shareloop.common.exception.BusinessException;
import com.shareloop.item.ItemErrorCode;
import com.shareloop.item.dto.AdminItemBase;
import com.shareloop.item.dto.AdminItemQuery;
import com.shareloop.item.entity.ItemStatus;
import com.shareloop.item.entity.OfferType;
import com.shareloop.item.service.ItemQueryService;
import com.shareloop.item.service.ItemService;
import com.shareloop.listingfee.dto.DisplayWindow;
import com.shareloop.listingfee.service.ListingFeeQueryService;
import com.shareloop.notification.service.NotificationService;
import com.shareloop.report.dto.ItemReportBrief;
import com.shareloop.report.service.ReportService;
import com.shareloop.request.dto.ItemRequestBrief;
import com.shareloop.request.service.RequestQueryService;
import com.shareloop.request.service.RequestService;
import com.shareloop.user.dto.UserBrief;
import com.shareloop.user.service.UserService;
import com.shareloop.wallet.dto.WalletTransactionResponse;
import com.shareloop.wallet.service.CreditService;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Quản lý bài đăng của Admin: xem mọi bài ở mọi trạng thái, xem chi tiết kèm phí, yêu cầu, báo cáo, và ẩn hoặc hiện lại
 * bài. Đặt ở module admin để module item không phải phụ thuộc vào wallet, request và report. Service này không ai
 * inject lại nên không tạo vòng phụ thuộc.
 */
@Service
public class AdminItemService {

    private static final ZoneId VIETNAM_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final Instant EARLIEST = Instant.EPOCH;
    private static final Instant LATEST = Instant.parse("9999-12-31T00:00:00Z");

    // Giao dịch đã chọn người và chưa kết thúc: ẩn bài lúc này sẽ làm kẹt giao dịch
    private static final Set<String> RUNNING_REQUEST_STATUSES =
            Set.of("RESERVED", "AWAITING_LOGISTICS", "LOGISTICS_CONFIRMED", "AWAITING_HANDOVER", "DISPUTED");

    private static final String SOURCE_ADMIN = "ADMIN";
    private static final String SOURCE_OWNER_LOCKED = "OWNER_LOCKED";
    private static final String SOURCE_OTHER = "OTHER";

    private final ItemService itemService;
    private final ItemQueryService itemQueryService;
    private final ListingFeeQueryService listingFeeQueryService;
    private final CreditService creditService;
    private final RequestQueryService requestQueryService;
    private final RequestService requestService;
    private final ReportService reportService;
    private final UserService userService;
    private final CategoryService categoryService;
    private final ActivityLogService activityLogService;
    private final ActivityLogQueryService activityLogQueryService;
    private final AdminItemHistoryService itemHistoryService;
    private final NotificationService notificationService;
    private final Clock clock;

    public AdminItemService(
            ItemService itemService,
            ItemQueryService itemQueryService,
            ListingFeeQueryService listingFeeQueryService,
            CreditService creditService,
            RequestQueryService requestQueryService,
            RequestService requestService,
            ReportService reportService,
            UserService userService,
            CategoryService categoryService,
            ActivityLogService activityLogService,
            ActivityLogQueryService activityLogQueryService,
            AdminItemHistoryService itemHistoryService,
            NotificationService notificationService,
            Clock clock) {
        this.itemService = itemService;
        this.itemQueryService = itemQueryService;
        this.listingFeeQueryService = listingFeeQueryService;
        this.creditService = creditService;
        this.requestQueryService = requestQueryService;
        this.requestService = requestService;
        this.reportService = reportService;
        this.userService = userService;
        this.categoryService = categoryService;
        this.activityLogService = activityLogService;
        this.activityLogQueryService = activityLogQueryService;
        this.itemHistoryService = itemHistoryService;
        this.notificationService = notificationService;
        this.clock = clock;
    }

    /**
     * Danh sách bài ở mọi trạng thái, mới nhất trước. Mọi bộ lọc đều tuỳ chọn. active true chỉ bài đang hiện, false chỉ bài
     * đang ẩn. q khớp tiêu đề hoặc tên hay email chủ bài. from và to là ngày tạo theo giờ Việt Nam, to tính trọn ngày.
     * Bài đã xoá mềm không có trong danh sách.
     */
    @Transactional(readOnly = true)
    public PageResponse<AdminItemRow> list(
            ItemStatus status,
            Boolean active,
            OfferType offerType,
            Long categoryId,
            Long ownerId,
            String keyword,
            LocalDate from,
            LocalDate to,
            boolean boostedOnly,
            int page,
            int size) {
        List<Long> keywordOwnerIds = userService.findIdsByKeyword(keyword);
        List<Long> boostedIds = boostedOnly ? listingFeeQueryService.findActiveBoostedIds(Instant.now(clock)) : null;
        AdminItemQuery query = new AdminItemQuery(
                status,
                active,
                offerType,
                categoryId,
                ownerId,
                keyword,
                keywordOwnerIds,
                from == null ? EARLIEST : from.atStartOfDay(VIETNAM_ZONE).toInstant(),
                to == null ? LATEST : to.plusDays(1).atStartOfDay(VIETNAM_ZONE).toInstant(),
                boostedIds);
        Sort newestFirst = Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"));
        Page<AdminItemBase> bases = itemQueryService.searchForAdmin(query, PageRequest.of(page, size, newestFirst));
        return new PageResponse<>(
                toRows(bases.getContent()), bases.getNumber(), bases.getSize(), bases.getTotalElements());
    }

    /**
     * Chi tiết một bài ở mọi trạng thái kèm phí, yêu cầu, báo cáo và lịch sử bài đăng.
     *
     * @throws BusinessException ITEM_NOT_FOUND khi không có bài hoặc đã xoá
     */
    @Transactional(readOnly = true)
    public AdminItemFullDetail getDetail(long itemId) {
        AdminItemBase base = itemQueryService.getAdminBase(itemId);
        AdminItemRow summary = toRows(List.of(base)).get(0);
        List<ItemRequestBrief> requestBriefs = requestQueryService.findBriefsByItem(itemId);
        List<AdminItemRequestRow> requests = toRequestRows(requestBriefs);
        Long runningRequestId = null;
        for (AdminItemRequestRow row : requests) {
            if (row.running()) {
                runningRequestId = row.id();
                break;
            }
        }
        List<ActivityLogDto> history = activityLogQueryService.findHistory(
                "ITEM",
                itemId,
                List.of(
                        ActivityType.ADMIN_ITEM_HIDDEN.code(),
                        ActivityType.ADMIN_ITEM_UNHIDDEN.code(),
                        ActivityType.REVIEW_APPROVE.code(),
                        ActivityType.REVIEW_REJECT.code(),
                        ActivityType.ITEM_EDIT_SUBMITTED.code(),
                        ActivityType.ITEM_DISPLAY_COMPENSATED.code()));
        List<ActivityLogDto> visibilityHistory = history.stream()
                .filter(log -> ActivityType.ADMIN_ITEM_HIDDEN.code().equals(log.type())
                        || ActivityType.ADMIN_ITEM_UNHIDDEN.code().equals(log.type()))
                .toList();
        List<WalletTransactionResponse> fees = creditService.getLedgerOfItem(itemId);
        List<ItemReportBrief> reports = reportService.findReportsOfItem(itemId);
        return new AdminItemFullDetail(
                itemService.getItemForAdmin(itemId),
                summary,
                fees,
                requests,
                runningRequestId != null,
                runningRequestId,
                toReportRows(reports),
                base.hiddenByAdmin() ? findHiddenReason(visibilityHistory) : null,
                visibilityHistory,
                itemHistoryService.build(base, summary.ownerName(), history, requests, reports, fees));
    }

    /**
     * Admin ẩn bài vi phạm. Không hoàn phí đăng. Bài đang có giao dịch đã chọn (RESERVED trở lên) không ẩn được, Admin
     * phải huỷ giao dịch ở trang Giao dịch trước. Ẩn bài thì huỷ các yêu cầu đang chờ tới bài và các yêu cầu dùng bài
     * làm món đổi, báo chủ bài kèm lý do và ghi nhật ký. Lượt đẩy đang chạy tự dừng vì bài ẩn không còn nằm trong
     * danh sách đẩy.
     *
     * @throws BusinessException ITEM_NOT_FOUND; ITEM_ALREADY_HIDDEN; ITEM_HIDE_NOT_ALLOWED khi có giao dịch đang chạy
     */
    @Transactional
    public AdminItemFullDetail hide(long adminId, long itemId, String reason) {
        AdminItemBase base = itemQueryService.getAdminBase(itemId);
        if (!base.active()) {
            throw new BusinessException(ItemErrorCode.ITEM_ALREADY_HIDDEN);
        }
        boolean hasRunningTransaction = base.status() == ItemStatus.RESERVED
                || base.status() == ItemStatus.TRADED
                || requestQueryService.hasReservedOrHigherRequest(itemId);
        if (hasRunningTransaction) {
            throw new BusinessException(ItemErrorCode.ITEM_HIDE_NOT_ALLOWED);
        }

        String note = reason.trim();
        // ItemService phát ItemUnavailableEvent, listener của module request huỷ các yêu cầu dùng bài này làm món đổi.
        itemService.hideByAdmin(itemId);
        int cancelledRequests = requestService.cancelPendingRequestsToItem(itemId, "Bài đăng đã bị quản trị viên ẩn");

        notificationService.notify(
                base.donorId(),
                "ITEM_HIDDEN_BY_ADMIN",
                "Bài đăng của bạn đã bị ẩn",
                "Quản trị viên đã ẩn bài «" + base.title() + "». Lý do: " + note,
                "/items/" + itemId);
        activityLogService.log(
                ActivityType.ADMIN_ITEM_HIDDEN.code(),
                adminId,
                "ITEM",
                itemId,
                Map.<String, Object>of("ownerId", base.donorId(), "cancelledRequests", cancelledRequests),
                note);
        return getDetail(itemId);
    }

    /**
     * Admin hiện lại bài đang ẩn (do Admin, báo cáo hoặc khoá tài khoản đã mở). Bài hiện như cũ nếu còn trong hạn hiển
     * thị. Xoá cờ hidden_by_admin. Bài của tài khoản đang bị khoá chưa hiện lại được.
     *
     * @throws BusinessException ITEM_NOT_FOUND; ITEM_NOT_HIDDEN; ITEM_UNHIDE_NOT_ALLOWED khi chủ bài đang bị khoá
     */
    @Transactional
    public AdminItemFullDetail unhide(long adminId, long itemId, String reason) {
        AdminItemBase base = itemQueryService.getAdminBase(itemId);
        if (base.active()) {
            throw new BusinessException(ItemErrorCode.ITEM_NOT_HIDDEN);
        }
        if (userService.isLocked(base.donorId())) {
            throw new BusinessException(ItemErrorCode.ITEM_UNHIDE_NOT_ALLOWED);
        }

        String note = reason.trim();
        itemService.unhideByAdmin(itemId);
        notificationService.notify(
                base.donorId(),
                "ITEM_UNHIDDEN_BY_ADMIN",
                "Bài đăng của bạn đã hiện lại",
                "Quản trị viên đã hiện lại bài «" + base.title() + "». Ghi chú: " + note,
                "/items/" + itemId);
        activityLogService.log(
                ActivityType.ADMIN_ITEM_UNHIDDEN.code(),
                adminId,
                "ITEM",
                itemId,
                Map.<String, Object>of("ownerId", base.donorId()),
                note);
        return getDetail(itemId);
    }

    // Tra chủ bài, danh mục, phí đăng, hạn hiển thị và số yêu cầu chờ cho cả trang bằng mỗi loại một truy vấn.
    private List<AdminItemRow> toRows(List<AdminItemBase> bases) {
        Set<Long> itemIds = new HashSet<>();
        Set<Long> ownerIds = new HashSet<>();
        for (AdminItemBase base : bases) {
            itemIds.add(base.id());
            ownerIds.add(base.donorId());
        }
        Map<Long, UserBrief> owners = userService.findBriefs(ownerIds);
        Set<Long> lockedOwnerIds = userService.findLockedIds(ownerIds);
        Map<Long, DisplayWindow> windows = listingFeeQueryService.findDisplayWindows(itemIds);
        Map<Long, Long> postFees = creditService.getPostFeePaidByItem(itemIds);
        Map<Long, Long> pendingCounts = requestQueryService.countPendingByItemIds(itemIds);
        Map<Long, String> categoryNames = new HashMap<>();
        Instant now = Instant.now(clock);

        List<AdminItemRow> rows = new ArrayList<>();
        for (AdminItemBase base : bases) {
            UserBrief owner = owners.get(base.donorId());
            DisplayWindow window = windows.get(base.id());
            Instant boostedUntil = window == null ? null : window.boostedUntil();
            rows.add(new AdminItemRow(
                    base.id(),
                    base.title(),
                    base.imageUrl(),
                    base.donorId(),
                    owner == null ? null : owner.fullName(),
                    owner == null ? null : owner.email(),
                    categoryNameOf(base.categoryId(), categoryNames),
                    base.offerType(),
                    base.status(),
                    base.active(),
                    hiddenSourceOf(base, lockedOwnerIds),
                    postFees.getOrDefault(base.id(), 0L),
                    pendingCounts.getOrDefault(base.id(), 0L),
                    base.createdAt(),
                    base.approvedAt(),
                    window == null ? null : window.expireAt(),
                    boostedUntil,
                    base.active() && boostedUntil != null && boostedUntil.isAfter(now)));
        }
        return rows;
    }

    private String categoryNameOf(Long categoryId, Map<Long, String> cache) {
        if (categoryId == null) {
            return null;
        }
        return cache.computeIfAbsent(
                categoryId, id -> categoryService.findName(id).orElse(null));
    }

    private static String hiddenSourceOf(AdminItemBase base, Set<Long> lockedOwnerIds) {
        if (base.active()) {
            return null;
        }
        if (base.hiddenByAdmin()) {
            return SOURCE_ADMIN;
        }
        return lockedOwnerIds.contains(base.donorId()) ? SOURCE_OWNER_LOCKED : SOURCE_OTHER;
    }

    private List<AdminItemRequestRow> toRequestRows(List<ItemRequestBrief> briefs) {
        Set<Long> userIds = new HashSet<>();
        Set<Long> offeredItemIds = new HashSet<>();
        for (ItemRequestBrief brief : briefs) {
            userIds.add(brief.receiverId());
            if (brief.offeredItemId() != null) {
                offeredItemIds.add(brief.offeredItemId());
            }
        }
        Map<Long, UserBrief> receivers = userService.findBriefs(userIds);
        Map<Long, String> offeredTitles = itemQueryService.findTitles(offeredItemIds);

        List<AdminItemRequestRow> rows = new ArrayList<>();
        for (ItemRequestBrief brief : briefs) {
            UserBrief receiver = receivers.get(brief.receiverId());
            rows.add(new AdminItemRequestRow(
                    brief.id(),
                    receiver == null ? null : receiver.fullName(),
                    brief.type(),
                    brief.status(),
                    brief.offeredItemId() == null ? null : offeredTitles.get(brief.offeredItemId()),
                    RUNNING_REQUEST_STATUSES.contains(brief.status()),
                    brief.createdAt()));
        }
        return rows;
    }

    private static List<AdminItemReportRow> toReportRows(List<ItemReportBrief> briefs) {
        List<AdminItemReportRow> rows = new ArrayList<>();
        for (ItemReportBrief brief : briefs) {
            rows.add(new AdminItemReportRow(brief.id(), brief.reason(), brief.status(), brief.createdAt()));
        }
        return rows;
    }

    // Lịch sử mới nhất trước: lần ẩn gần nhất là dòng ADMIN_ITEM_HIDDEN đầu tiên.
    private static String findHiddenReason(List<ActivityLogDto> history) {
        for (ActivityLogDto log : history) {
            if (ActivityType.ADMIN_ITEM_HIDDEN.code().equals(log.type())) {
                return log.reason();
            }
        }
        return null;
    }
}
