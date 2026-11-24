package com.shareloop.wallet.service;

import com.shareloop.audit.ActivityType;
import com.shareloop.audit.service.ActivityLogService;
import com.shareloop.common.api.PageResponse;
import com.shareloop.common.exception.BusinessException;
import com.shareloop.notification.service.NotificationService;
import com.shareloop.user.dto.UserBrief;
import com.shareloop.user.service.UserService;
import com.shareloop.wallet.WalletErrorCode;
import com.shareloop.wallet.dto.AdminTopUpOrderDetail;
import com.shareloop.wallet.dto.AdminTopUpOrderResponse;
import com.shareloop.wallet.dto.AdminTopUpSummary;
import com.shareloop.wallet.dto.ReconcileTopUpRequest;
import com.shareloop.wallet.dto.TopUpLedgerLine;
import com.shareloop.wallet.entity.CreditLedgerEntry;
import com.shareloop.wallet.entity.PaymentOrder;
import com.shareloop.wallet.entity.PaymentOrderStatus;
import com.shareloop.wallet.entity.PaymentProvider;
import com.shareloop.wallet.mapper.TopUpOrderCsvWriter;
import com.shareloop.wallet.mapper.TopUpOrderExcelWriter;
import com.shareloop.wallet.repository.CreditLedgerRepository;
import com.shareloop.wallet.repository.PaymentOrderRepository;
import com.shareloop.wallet.repository.PaymentOrderSnapshot;
import com.shareloop.wallet.repository.StatusTotalView;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Trang Giao dịch nạp tiền của Admin: tìm đơn, xem chi tiết, tổng hợp, xuất CSV và đối soát đơn VNPay đã đóng mà tiền
 * đã về. Việc cộng Credit vẫn do {@link CreditService} làm, nơi duy nhất đổi số dư.
 */
@Service
@Transactional(readOnly = true)
public class AdminTopUpService {

    private static final ZoneId VIETNAM_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final Instant EARLIEST = Instant.EPOCH;
    private static final Instant LATEST = Instant.parse("9999-12-31T00:00:00Z");
    private static final int EXPORT_MAX_ROWS = 10_000;

    // Chỉ các khoá VNPay cần để đối soát. Không trả vnp_SecureHash và các khoá khác.
    private static final List<String> SAFE_CALLBACK_KEYS = List.of(
            "vnp_ResponseCode",
            "vnp_TransactionStatus",
            "vnp_TransactionNo",
            "vnp_BankCode",
            "vnp_PayDate",
            "vnp_Amount");

    private final PaymentOrderRepository paymentOrderRepository;
    private final CreditLedgerRepository creditLedgerRepository;
    private final CreditService creditService;
    private final UserService userService;
    private final NotificationService notificationService;
    private final ActivityLogService activityLogService;
    private final TopUpOrderCsvWriter csvWriter;
    private final TopUpOrderExcelWriter excelWriter;
    private final Clock clock;

    public AdminTopUpService(
            PaymentOrderRepository paymentOrderRepository,
            CreditLedgerRepository creditLedgerRepository,
            CreditService creditService,
            UserService userService,
            NotificationService notificationService,
            ActivityLogService activityLogService,
            TopUpOrderCsvWriter csvWriter,
            TopUpOrderExcelWriter excelWriter,
            Clock clock) {
        this.paymentOrderRepository = paymentOrderRepository;
        this.creditLedgerRepository = creditLedgerRepository;
        this.creditService = creditService;
        this.userService = userService;
        this.notificationService = notificationService;
        this.activityLogService = activityLogService;
        this.csvWriter = csvWriter;
        this.excelWriter = excelWriter;
        this.clock = clock;
    }

    /** Danh sách đơn nạp, mới nhất trước. Mọi bộ lọc đều tuỳ chọn; from và to tính theo ngày giờ Việt Nam. */
    public PageResponse<AdminTopUpOrderResponse> list(
            PaymentProvider provider,
            PaymentOrderStatus status,
            String keyword,
            LocalDate from,
            LocalDate to,
            boolean paidDate,
            boolean needsReconcile,
            int page,
            int size) {
        Page<PaymentOrder> orders = findOrders(
                provider,
                status,
                keyword,
                from,
                to,
                paidDate,
                needsReconcile,
                PageRequest.of(page, size, newestFirst(paidDate)));
        Map<Long, UserBrief> briefs = findBriefs(orders.getContent());
        return PageResponse.from(orders.map(order -> toResponse(order, briefs)));
    }

    /** File CSV theo đúng bộ lọc đang chọn, tối đa 10000 dòng mới nhất. */
    public byte[] exportCsv(
            PaymentProvider provider,
            PaymentOrderStatus status,
            String keyword,
            LocalDate from,
            LocalDate to,
            boolean paidDate,
            boolean needsReconcile) {
        return csvWriter.write(findExportRows(provider, status, keyword, from, to, paidDate, needsReconcile));
    }

    public byte[] exportExcel(
            PaymentProvider provider,
            PaymentOrderStatus status,
            String keyword,
            LocalDate from,
            LocalDate to,
            boolean paidDate,
            boolean needsReconcile) {
        return excelWriter.write(
                findExportRows(provider, status, keyword, from, to, paidDate, needsReconcile), from, to, paidDate);
    }

    private List<AdminTopUpOrderResponse> findExportRows(
            PaymentProvider provider,
            PaymentOrderStatus status,
            String keyword,
            LocalDate from,
            LocalDate to,
            boolean paidDate,
            boolean needsReconcile) {
        Page<PaymentOrder> orders = findOrders(
                provider,
                status,
                keyword,
                from,
                to,
                paidDate,
                needsReconcile,
                PageRequest.of(0, EXPORT_MAX_ROWS, newestFirst(paidDate)));
        Map<Long, UserBrief> briefs = findBriefs(orders.getContent());
        return orders.stream().map(order -> toResponse(order, briefs)).toList();
    }

    /**
     * Chi tiết một đơn kèm dữ liệu callback an toàn và các dòng sổ cái đã cộng.
     *
     * @throws BusinessException WALLET_PAYMENT_ORDER_NOT_FOUND khi không có đơn
     */
    public AdminTopUpOrderDetail getDetail(String orderCode) {
        PaymentOrder order = paymentOrderRepository
                .findByOrderCode(orderCode)
                .orElseThrow(() -> new BusinessException(WalletErrorCode.WALLET_PAYMENT_ORDER_NOT_FOUND));
        Map<Long, UserBrief> briefs = findBriefs(List.of(order));
        List<TopUpLedgerLine> ledgerLines = new ArrayList<>();
        for (CreditLedgerEntry entry : creditLedgerRepository.findByPaymentOrderIdOrderByIdAsc(order.getId())) {
            ledgerLines.add(new TopUpLedgerLine(
                    entry.getId(), entry.getAmount(), entry.getBalanceAfter(), entry.getCreatedAt()));
        }
        return new AdminTopUpOrderDetail(toResponse(order, briefs), safeCallback(order), ledgerLines);
    }

    /** Số đơn và tổng tiền theo từng trạng thái cùng số đơn cần đối soát trong khoảng lọc. */
    public AdminTopUpSummary summarize(LocalDate from, LocalDate to, boolean paidDate) {
        Instant fromInstant = startOf(from);
        Instant toInstant = endOf(to);
        Map<PaymentOrderStatus, StatusTotalView> found = new LinkedHashMap<>();
        for (StatusTotalView view : paymentOrderRepository.summarizeByStatus(fromInstant, toInstant, paidDate)) {
            found.put(view.getStatus(), view);
        }
        List<AdminTopUpSummary.StatusTotal> totals = new ArrayList<>();
        for (PaymentOrderStatus status : PaymentOrderStatus.values()) {
            StatusTotalView view = found.get(status);
            totals.add(new AdminTopUpSummary.StatusTotal(
                    status, view == null ? 0 : view.getOrderCount(), view == null ? 0 : view.getAmountVnd()));
        }
        return new AdminTopUpSummary(
                totals, paymentOrderRepository.countNeedingReconcile(fromInstant, toInstant, paidDate));
    }

    /**
     * Admin xử lý một đơn cần đối soát, mỗi đơn một lần. GRANT_CREDIT cộng Credit qua CreditService và báo người nạp;
     * REFUNDED_OUTSIDE chỉ ghi nhận đã hoàn tiền ngoài hệ thống. Cả hai đều ghi nhật ký.
     *
     * @throws BusinessException WALLET_PAYMENT_ORDER_NOT_FOUND; WALLET_TOPUP_NOT_RECONCILABLE khi đơn không cần đối soát
     *     hoặc đã xử lý
     */
    @Transactional
    public AdminTopUpOrderDetail reconcile(long adminId, String orderCode, ReconcileTopUpRequest request) {
        PaymentOrderSnapshot snapshot = paymentOrderRepository
                .findSnapshotByOrderCode(orderCode)
                .orElseThrow(() -> new BusinessException(WalletErrorCode.WALLET_PAYMENT_ORDER_NOT_FOUND));

        String note = request.note().trim();
        if (request.action() == ReconcileTopUpRequest.Action.GRANT_CREDIT) {
            creditService.grantReconciledTopUp(snapshot.getId(), adminId, note);
            notifyPayer(snapshot);
        } else {
            markRefundedOutside(snapshot, adminId, note);
        }
        activityLogService.log(
                ActivityType.ADMIN_TOPUP_RECONCILED.code(),
                adminId,
                "PAYMENT_ORDER",
                snapshot.getId(),
                Map.<String, Object>of(
                        "orderCode", orderCode, "action", request.action().name()),
                note);
        return getDetail(orderCode);
    }

    // Khoá users rồi payment_orders như IPN, kiểm lại sau khi khoá để hai Admin bấm cùng lúc chỉ một người xử lý được.
    private void markRefundedOutside(PaymentOrderSnapshot snapshot, long adminId, String note) {
        creditService.lockWalletRow(snapshot.getUserId());
        PaymentOrder order = paymentOrderRepository
                .lockById(snapshot.getId())
                .orElseThrow(() -> new BusinessException(WalletErrorCode.WALLET_PAYMENT_ORDER_NOT_FOUND));
        if (!order.needsReconcile()) {
            throw new BusinessException(WalletErrorCode.WALLET_TOPUP_NOT_RECONCILABLE);
        }
        order.review(adminId, clock.instant(), note);
    }

    private void notifyPayer(PaymentOrderSnapshot snapshot) {
        notificationService.notify(
                snapshot.getUserId(),
                "TOPUP_RECONCILED",
                "Đơn nạp đã được cộng Credit",
                "Quản trị viên đã đối soát và cộng " + snapshot.getCredits() + " Credit cho đơn nạp của bạn.",
                "/credit");
    }

    private Page<PaymentOrder> findOrders(
            PaymentProvider provider,
            PaymentOrderStatus status,
            String keyword,
            LocalDate from,
            LocalDate to,
            boolean paidDate,
            boolean needsReconcile,
            PageRequest pageRequest) {
        boolean hasKeyword = keyword != null && !keyword.isBlank();
        String pattern = hasKeyword ? "%" + keyword.trim().toLowerCase() + "%" : null;
        // IN không được rỗng nên luôn có ít nhất một phần tử; id -1 không khớp người nào.
        List<Long> userIds = new ArrayList<>(hasKeyword ? userService.findIdsByKeyword(keyword) : List.of());
        if (userIds.isEmpty()) {
            userIds.add(-1L);
        }
        return paymentOrderRepository.findAdminOrders(
                provider, status, pattern, userIds, startOf(from), endOf(to), paidDate, needsReconcile, pageRequest);
    }

    private static Sort newestFirst(boolean paidDate) {
        return Sort.by(Sort.Direction.DESC, paidDate ? "paidAt" : "createdAt", "id");
    }

    private static Instant startOf(LocalDate from) {
        return from == null ? EARLIEST : from.atStartOfDay(VIETNAM_ZONE).toInstant();
    }

    // Ngày kết thúc tính trọn ngày nên cận trên là đầu ngày hôm sau.
    private static Instant endOf(LocalDate to) {
        return to == null ? LATEST : to.plusDays(1).atStartOfDay(VIETNAM_ZONE).toInstant();
    }

    private Map<Long, UserBrief> findBriefs(List<PaymentOrder> orders) {
        Set<Long> userIds = new HashSet<>();
        for (PaymentOrder order : orders) {
            userIds.add(order.getUserId());
            if (order.getReviewedBy() != null) {
                userIds.add(order.getReviewedBy());
            }
        }
        return userService.findBriefs(userIds);
    }

    private AdminTopUpOrderResponse toResponse(PaymentOrder order, Map<Long, UserBrief> briefs) {
        UserBrief owner = briefs.get(order.getUserId());
        UserBrief reviewer = order.getReviewedBy() == null ? null : briefs.get(order.getReviewedBy());
        return new AdminTopUpOrderResponse(
                order.getOrderCode(),
                order.getUserId(),
                owner == null ? null : owner.fullName(),
                owner == null ? null : owner.email(),
                order.getAmountVnd(),
                order.getCredits(),
                order.getProvider(),
                order.getStatus(),
                order.getProviderTxnId(),
                order.getCreatedAt(),
                order.getPaidAt(),
                order.needsReconcile(),
                order.getReviewedAt(),
                reviewer == null ? null : reviewer.fullName(),
                order.getReviewNote());
    }

    private static Map<String, String> safeCallback(PaymentOrder order) {
        Map<String, String> callback = order.getCallbackData();
        Map<String, String> safe = new LinkedHashMap<>();
        if (callback == null) {
            return safe;
        }
        for (String key : SAFE_CALLBACK_KEYS) {
            if (callback.containsKey(key)) {
                safe.put(key, callback.get(key));
            }
        }
        return safe;
    }
}
