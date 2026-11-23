package com.shareloop.request.service;

import com.shareloop.item.dto.ItemSummary;
import com.shareloop.item.service.ItemQueryService;
import com.shareloop.request.dto.AdminRequestPageResponse;
import com.shareloop.request.dto.AdminRequestRow;
import com.shareloop.request.dto.CancelTransactionRequest;
import com.shareloop.request.dto.RequestResponse;
import com.shareloop.request.entity.Request;
import com.shareloop.request.entity.RequestStatus;
import com.shareloop.request.repository.RequestRepository;
import com.shareloop.setting.ConfigKey;
import com.shareloop.setting.service.ConfigService;
import com.shareloop.user.dto.UserBrief;
import com.shareloop.user.service.UserService;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Danh sách giao dịch cho Admin và huỷ giao dịch bị kẹt. Giao dịch bị kẹt là giao dịch đang mở mà quá giờ hẹn hơn
 * STUCK_REQUEST_HOURS giờ nhưng chưa bên nào xác nhận bàn giao, hoặc có một bên đang bị khoá tài khoản.
 */
@Service
public class AdminRequestService {

    // Các trạng thái giao dịch đang mở, đã được chọn người và chưa kết thúc
    private static final java.time.ZoneId VIETNAM_ZONE = java.time.ZoneId.of("Asia/Ho_Chi_Minh");

    private static final Set<RequestStatus> OPEN_STATUSES = Set.of(
            RequestStatus.RESERVED,
            RequestStatus.AWAITING_LOGISTICS,
            RequestStatus.LOGISTICS_CONFIRMED,
            RequestStatus.AWAITING_HANDOVER,
            RequestStatus.DISPUTED);

    private final RequestRepository requestRepository;
    private final RequestService requestService;
    private final ItemQueryService itemQueryService;
    private final UserService userService;
    private final ConfigService configService;
    private final Clock clock;

    public AdminRequestService(
            RequestRepository requestRepository,
            RequestService requestService,
            ItemQueryService itemQueryService,
            UserService userService,
            ConfigService configService,
            Clock clock) {
        this.requestRepository = requestRepository;
        this.requestService = requestService;
        this.itemQueryService = itemQueryService;
        this.userService = userService;
        this.configService = configService;
        this.clock = clock;
    }

    /**
     * Danh sách giao dịch, mới nhất trước. Mọi bộ lọc đều tuỳ chọn: status, stuck (true chỉ lấy giao dịch bị kẹt,
     * false chỉ lấy giao dịch không kẹt), q (khớp một phần mã giao dịch, tên bài hoặc tên hai bên). Lọc và phân trang
     * làm trong bộ nhớ chỉ khi có q hoặc stuck, vì tên bài, tên người và khoá tài khoản nằm ở module khác; các bộ lọc còn lại chạy trong SQL.
     */
    @Transactional(readOnly = true)
    public AdminRequestPageResponse list(RequestStatus status, Boolean stuck, String q, int page, int size) {
        return list(status, stuck, q, null, null, null, null, page, size);
    }

    /**
     * Như hàm trên, thêm lọc theo loại giao dịch (GIVE hoặc SWAP), hình thức giao nhận và khoảng ngày tạo. from và to
     * tính theo giờ Việt Nam, to tính trọn ngày. Lọc trong bộ nhớ như các bộ lọc khác, đợt sau sẽ chuyển sang SQL.
     */
    @Transactional(readOnly = true)
    public AdminRequestPageResponse list(
            RequestStatus status,
            Boolean stuck,
            String q,
            String type,
            String deliveryMethod,
            java.time.LocalDate from,
            java.time.LocalDate to,
            int page,
            int size) {
        Instant fromInstant =
                from == null ? null : from.atStartOfDay(VIETNAM_ZONE).toInstant();
        Instant toInstant =
                to == null ? null : to.plusDays(1).atStartOfDay(VIETNAM_ZONE).toInstant();
        // Không có bộ lọc cần dữ liệu module khác (tên, khoá tài khoản): lọc và chia trang ngay trong SQL, chỉ dựng
        // dòng cho trang (G2)
        boolean needsMemoryFilter = stuck != null || (q != null && !q.isBlank());
        if (!needsMemoryFilter) {
            var result = requestRepository.findForAdmin(
                    status == null ? java.util.EnumSet.allOf(RequestStatus.class) : java.util.EnumSet.of(status),
                    type == null ? "" : type,
                    deliveryMethod == null ? "" : deliveryMethod,
                    fromInstant == null ? Instant.EPOCH : fromInstant,
                    toInstant == null ? Instant.parse("9999-01-01T00:00:00Z") : toInstant,
                    org.springframework.data.domain.PageRequest.of(
                            page,
                            size,
                            org.springframework.data.domain.Sort.by(
                                    org.springframework.data.domain.Sort.Order.desc("createdAt"),
                                    org.springframework.data.domain.Sort.Order.desc("id"))));
            return new AdminRequestPageResponse(
                    toRows(result.getContent()), page, size, result.getTotalElements(), result.getTotalPages());
        }
        List<Request> requests = status == null
                ? requestRepository.findAllByOrderByCreatedAtDesc()
                : requestRepository.findByStatusOrderByCreatedAtDesc(status);

        List<AdminRequestRow> filtered = new ArrayList<>();
        for (AdminRequestRow row : toRows(requests)) {
            boolean stuckMatches = stuck == null || stuck == (row.flagReason() != null);
            boolean typeMatches = type == null || type.equals(row.type());
            boolean deliveryMatches = deliveryMethod == null || deliveryMethod.equals(row.deliveryMethod());
            boolean dateMatches = (fromInstant == null || !row.createdAt().isBefore(fromInstant))
                    && (toInstant == null || row.createdAt().isBefore(toInstant));
            if (stuckMatches && matchesQuery(row, q) && typeMatches && deliveryMatches && dateMatches) {
                filtered.add(row);
            }
        }
        return paginate(filtered, page, size);
    }

    /**
     * Admin huỷ giao dịch (thường là giao dịch kẹt). Dùng chung logic huỷ với người dùng: nhả hai món, thông báo cả
     * hai bên, ghi activity log.
     */
    @Transactional
    public RequestResponse cancel(long adminId, long requestId, CancelTransactionRequest request) {
        return requestService.cancelByAdmin(requestId, adminId, request);
    }

    private List<AdminRequestRow> toRows(List<Request> requests) {
        Map<Long, ItemSummary> summaries = new HashMap<>();
        Set<Long> userIds = new HashSet<>();
        for (Request request : requests) {
            ItemSummary summary = summaries.computeIfAbsent(request.getItemId(), this::findSummary);
            userIds.add(request.getReceiverId());
            if (summary != null) {
                userIds.add(summary.donorId());
            }
        }
        Map<Long, String> titles = itemQueryService.findTitles(summaries.keySet());
        Map<Long, UserBrief> briefs = userService.findBriefs(userIds);
        Set<Long> lockedIds = userService.findLockedIds(userIds);
        Duration overdueAfter = Duration.ofHours(configService.getInt(ConfigKey.STUCK_REQUEST_HOURS));
        Instant now = Instant.now(clock);

        List<AdminRequestRow> rows = new ArrayList<>();
        for (Request request : requests) {
            ItemSummary summary = summaries.get(request.getItemId());
            Long donorId = summary != null ? summary.donorId() : null;
            rows.add(new AdminRequestRow(
                    request.getId(),
                    request.getItemId(),
                    titles.get(request.getItemId()),
                    request.getType(),
                    request.getStatus().name(),
                    donorId,
                    nameOf(briefs, donorId),
                    request.getReceiverId(),
                    nameOf(briefs, request.getReceiverId()),
                    request.getMeetingTime(),
                    request.getCreatedAt(),
                    flagReasonOf(request, donorId, lockedIds, overdueAfter, now),
                    request.getDeliveryMethod()));
        }
        return rows;
    }

    private ItemSummary findSummary(Long itemId) {
        try {
            return itemQueryService.getSummary(itemId);
        } catch (RuntimeException exception) {
            // Bài đã xoá thì vẫn hiện dòng giao dịch, chỉ thiếu tên bài và chủ bài
            return null;
        }
    }

    private static String nameOf(Map<Long, UserBrief> briefs, Long userId) {
        UserBrief brief = userId == null ? null : briefs.get(userId);
        return brief == null ? null : brief.fullName();
    }

    // Trả lý do gắn cờ, hoặc null khi giao dịch không bị kẹt. Khoá tài khoản được xét trước vì nặng hơn.
    private String flagReasonOf(
            Request request, Long donorId, Set<Long> lockedIds, Duration overdueAfter, Instant now) {
        if (!OPEN_STATUSES.contains(request.getStatus())) {
            return null;
        }
        if (donorId != null && lockedIds.contains(donorId)) {
            return "Tài khoản chủ bài đang bị khoá";
        }
        if (lockedIds.contains(request.getReceiverId())) {
            return "Tài khoản người nhận đang bị khoá";
        }
        boolean nobodyConfirmed = request.getDonorHandoverAt() == null && request.getReceiverHandoverAt() == null;
        boolean overdue = request.getMeetingTime() != null
                && request.getMeetingTime().plus(overdueAfter).isBefore(now);
        if (nobodyConfirmed && overdue) {
            return "Quá giờ hẹn hơn " + overdueAfter.toHours() + " giờ mà chưa bên nào xác nhận bàn giao";
        }
        return null;
    }

    private static boolean matchesQuery(AdminRequestRow row, String q) {
        if (q == null || q.isBlank()) {
            return true;
        }
        String needle = q.trim().toLowerCase(Locale.ROOT);
        return String.valueOf(row.id()).equals(needle)
                || contains(row.itemTitle(), needle)
                || contains(row.donorName(), needle)
                || contains(row.receiverName(), needle);
    }

    private static boolean contains(String text, String needle) {
        return text != null && text.toLowerCase(Locale.ROOT).contains(needle);
    }

    private static AdminRequestPageResponse paginate(List<AdminRequestRow> rows, int page, int size) {
        int from = Math.min(page * size, rows.size());
        int to = Math.min(from + size, rows.size());
        int totalPages = (int) Math.ceil(rows.size() / (double) size);
        return new AdminRequestPageResponse(rows.subList(from, to), page, size, rows.size(), totalPages);
    }
}
