package com.shareloop.wallet.service;

import com.shareloop.common.api.PageResponse;
import com.shareloop.common.exception.BusinessException;
import com.shareloop.user.dto.AdminAccountRole;
import com.shareloop.user.dto.AdminUserSummary;
import com.shareloop.user.dto.ContactInfo;
import com.shareloop.user.dto.UserBrief;
import com.shareloop.user.service.UserService;
import com.shareloop.wallet.FinanceErrorCode;
import com.shareloop.wallet.dto.AdminFinanceUserSummary;
import com.shareloop.wallet.dto.FinanceSummaryResponse;
import com.shareloop.wallet.dto.TopUpReportItem;
import com.shareloop.wallet.dto.WalletResponse;
import com.shareloop.wallet.dto.WalletTransactionResponse;
import com.shareloop.wallet.entity.PaymentOrder;
import com.shareloop.wallet.entity.PaymentOrderStatus;
import com.shareloop.wallet.repository.PaymentOrderRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service phục vụ báo cáo tài chính và tra cứu lịch sử giao dịch người dùng cho Admin (VI-09, AD-03).
 * Mọi số liệu và biến động Credit được ủy thác qua CreditService.
 */
@Service
public class AdminFinanceService {

    private static final ZoneId VIETNAM_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final Instant EARLIEST = Instant.EPOCH;
    private static final Instant LATEST = Instant.parse("9999-12-31T00:00:00Z");

    private final CreditService creditService;
    private final UserService userService;
    private final PaymentOrderRepository paymentOrderRepository;

    public AdminFinanceService(
            CreditService creditService, UserService userService, PaymentOrderRepository paymentOrderRepository) {
        this.creditService = creditService;
        this.userService = userService;
        this.paymentOrderRepository = paymentOrderRepository;
    }

    /**
     * Danh sách người dùng thường (không gồm Admin) kèm số liệu Credit cơ bản để Admin tra cứu (AD-03). Tìm theo họ
     * tên, email hoặc số điện thoại. Danh sách người dùng do module user cung cấp, số dư và tổng đã nạp do wallet
     * ghép vào.
     */
    @Transactional(readOnly = true)
    public PageResponse<AdminFinanceUserSummary> listUsers(String keyword, int page, int size) {
        PageResponse<AdminUserSummary> users =
                userService.searchAccounts(keyword, null, AdminAccountRole.USER, page, size);

        List<Long> userIds = new ArrayList<>();
        for (AdminUserSummary user : users.items()) {
            userIds.add(user.id());
        }
        Map<Long, WalletResponse> wallets = creditService.getWallets(userIds);
        Map<Long, Long> toppedUp = creditService.getToppedUpCredits(userIds);

        List<AdminFinanceUserSummary> items = new ArrayList<>();
        for (AdminUserSummary user : users.items()) {
            items.add(toFinanceSummary(
                    user.id(),
                    user.fullName(),
                    user.email(),
                    user.phone(),
                    wallets.get(user.id()),
                    toppedUp.getOrDefault(user.id(), 0L)));
        }
        return new PageResponse<>(items, users.page(), users.size(), users.totalElements());
    }

    /**
     * Thông tin và số liệu Credit của một người dùng, cho màn hình chi tiết của Admin (AD-03).
     *
     * @throws com.shareloop.common.exception.BusinessException USER_NOT_FOUND khi không có người dùng,
     *     WALLET_NOT_FOUND khi người dùng không có ví
     */
    @Transactional(readOnly = true)
    public AdminFinanceUserSummary getUserFinance(long userId) {
        ContactInfo user = userService.findContact(userId);
        WalletResponse wallet = creditService.getWallet(userId);
        long toppedUp = creditService.getToppedUpCredits(List.of(userId)).getOrDefault(userId, 0L);
        return toFinanceSummary(user.userId(), user.fullName(), user.email(), user.phone(), wallet, toppedUp);
    }

    // Người dùng chưa có ví (hiếm, dữ liệu lỗi) thì hiện 0 thay vì làm hỏng cả danh sách.
    private AdminFinanceUserSummary toFinanceSummary(
            Long userId, String fullName, String email, String phone, WalletResponse wallet, long toppedUp) {
        long available = wallet == null ? 0 : wallet.available();
        long held = wallet == null ? 0 : wallet.heldCredit();
        long total = wallet == null ? 0 : wallet.creditBalance();
        return new AdminFinanceUserSummary(userId, fullName, email, phone, available, held, total, toppedUp);
    }

    /**
     * Báo cáo tài chính ba sổ và đối chiếu TopUp (VI-09).
     */
    @Transactional(readOnly = true)
    public FinanceSummaryResponse getReport(LocalDate from, LocalDate to) {
        if (from != null && to != null && from.isAfter(to)) {
            throw new BusinessException(FinanceErrorCode.INVALID_DATE_RANGE);
        }

        Instant fromInstant = from != null ? from.atStartOfDay(VIETNAM_ZONE).toInstant() : null;
        Instant toInstant =
                to != null ? to.plusDays(1).atStartOfDay(VIETNAM_ZONE).toInstant() : null;

        return creditService.getFinanceSummary(fromInstant, toInstant);
    }

    /**
     * Chi tiết các đơn nạp tiền thành công kèm thông tin họ tên, email thành viên (L02).
     */
    @Transactional(readOnly = true)
    public List<TopUpReportItem> getTopUpReportDetails(LocalDate from, LocalDate to) {
        if (from != null && to != null && from.isAfter(to)) {
            throw new BusinessException(FinanceErrorCode.INVALID_DATE_RANGE);
        }

        Instant fromInstant = from != null ? from.atStartOfDay(VIETNAM_ZONE).toInstant() : null;
        Instant toInstant =
                to != null ? to.plusDays(1).atStartOfDay(VIETNAM_ZONE).toInstant() : null;

        Instant rangeStart = fromInstant != null ? fromInstant : EARLIEST;
        Instant rangeEnd = toInstant != null ? toInstant : LATEST;

        List<PaymentOrder> orders =
                paymentOrderRepository.findSuccessfulOrders(PaymentOrderStatus.SUCCESS, rangeStart, rangeEnd);
        if (orders.isEmpty()) {
            return List.of();
        }

        Set<Long> userIds = orders.stream().map(PaymentOrder::getUserId).collect(Collectors.toSet());
        Map<Long, UserBrief> briefs = userService.findBriefs(userIds);

        return orders.stream()
                .map(o -> {
                    UserBrief brief = briefs.get(o.getUserId());
                    String name = brief != null && brief.fullName() != null ? brief.fullName() : "N/A";
                    String email = brief != null && brief.email() != null ? brief.email() : "N/A";
                    return new TopUpReportItem(
                            o.getPaidAt(),
                            o.getOrderCode(),
                            o.getUserId(),
                            name,
                            email,
                            o.getAmountVnd(),
                            o.getCredits(),
                            o.getProvider());
                })
                .toList();
    }

    /**
     * Tra cứu lịch sử ví Credit của một người dùng (AD-03).
     */
    @Transactional(readOnly = true)
    public PageResponse<WalletTransactionResponse> getUserHistory(
            Long userId, LocalDate from, LocalDate to, int page, int size) {
        if (userId == null) {
            throw new IllegalArgumentException("userId must not be null");
        }

        // Kiểm tra người dùng / ví có tồn tại không. Nếu không, ném WALLET_NOT_FOUND (404)
        creditService.getWallet(userId);

        if (from != null && to != null && from.isAfter(to)) {
            throw new BusinessException(FinanceErrorCode.INVALID_DATE_RANGE);
        }

        Instant fromInstant = from != null ? from.atStartOfDay(VIETNAM_ZONE).toInstant() : null;
        Instant toInstant =
                to != null ? to.plusDays(1).atStartOfDay(VIETNAM_ZONE).toInstant() : null;

        int pageNumber = Math.max(page, 0);
        int pageSize = Math.min(Math.max(size, 1), 100);

        return creditService.getTransactions(userId, fromInstant, toInstant, pageNumber, pageSize);
    }
}
