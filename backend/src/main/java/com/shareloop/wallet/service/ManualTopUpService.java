package com.shareloop.wallet.service;

import com.shareloop.audit.service.ActivityLogService;
import com.shareloop.common.api.PageResponse;
import com.shareloop.common.exception.BusinessException;
import com.shareloop.setting.ConfigKey;
import com.shareloop.setting.service.ConfigService;
import com.shareloop.user.dto.UserBrief;
import com.shareloop.user.service.UserService;
import com.shareloop.wallet.WalletErrorCode;
import com.shareloop.wallet.dto.TopUpOrderDetailResponse;
import com.shareloop.wallet.entity.PaymentOrder;
import com.shareloop.wallet.entity.PaymentOrderStatus;
import com.shareloop.wallet.entity.PaymentProvider;
import com.shareloop.wallet.repository.PaymentOrderRepository;
import com.shareloop.wallet.repository.PaymentOrderSnapshot;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class ManualTopUpService {
    private final PaymentOrderRepository orders;
    private final CreditService creditService;
    private final ConfigService config;
    private final ActivityLogService audit;
    private final UserService userService;
    private final Clock clock;

    public ManualTopUpService(
            PaymentOrderRepository orders,
            CreditService creditService,
            ConfigService config,
            ActivityLogService audit,
            UserService userService,
            Clock clock) {
        this.orders = orders;
        this.creditService = creditService;
        this.config = config;
        this.audit = audit;
        this.userService = userService;
        this.clock = clock;
    }

    public TopUpOrderDetailResponse getOrder(long userId, String code) {
        PaymentOrder order = lockOwned(userId, code);
        expireIfNecessary(order);
        return detail(order);
    }

    // Giữ EXPIRED khi trả lỗi 409 cho thao tác trên đơn vừa hết hạn.
    @Transactional(noRollbackFor = BusinessException.class)
    public TopUpOrderDetailResponse markPaid(long userId, String code) {
        PaymentOrder order = lockOwned(userId, code);
        expireIfNecessary(order);
        requireManualPending(order);
        order.submit(clock.instant());
        return detail(order);
    }

    /**
     * Danh sách đơn nạp thủ công cho Admin, đơn mới tạo nằm trên cùng. status null nghĩa là mọi trạng thái;
     * submittedOnly = true chỉ lấy đơn người dùng đã bấm xác nhận thanh toán. Kèm tên chủ đơn và tên Admin duyệt.
     */
    @Transactional(readOnly = true)
    public PageResponse<TopUpOrderDetailResponse> list(
            PaymentOrderStatus status, boolean submittedOnly, int page, int size) {
        Sort newestFirst = Sort.by(Sort.Direction.DESC, "createdAt", "id");
        Page<PaymentOrder> orderPage =
                orders.findManualOrders(status, submittedOnly, PageRequest.of(page, size, newestFirst));
        // Hỏi tên của cả trang bằng một truy vấn, không hỏi từng đơn.
        Map<Long, UserBrief> briefs = findBriefs(orderPage.getContent());
        return PageResponse.from(orderPage.map(order -> detail(order, briefs)));
    }

    public TopUpOrderDetailResponse approve(long adminId, String code, String note) {
        PaymentOrder order = lock(code);
        requireSubmitted(order);
        if (order.getStatus() == PaymentOrderStatus.SUCCESS) return adminDetail(order);
        requireManualPending(order);
        creditService.topUpManual(order.getUserId(), order.getId());
        order.review(adminId, clock.instant(), note);
        log("TOP_UP_APPROVED", adminId, order, note);
        return adminDetail(order);
    }

    public TopUpOrderDetailResponse reject(long adminId, String code, String reason) {
        if (reason == null || reason.isBlank() || reason.length() > 500) {
            throw new BusinessException(WalletErrorCode.WALLET_PAYMENT_ORDER_NOT_PAYABLE);
        }
        PaymentOrder order = lock(code);
        requireSubmitted(order);
        requireManualPending(order);
        order.markFailed();
        order.review(adminId, clock.instant(), reason.trim());
        log("TOP_UP_REJECTED", adminId, order, reason.trim());
        return adminDetail(order);
    }

    private PaymentOrder lockOwned(long userId, String code) {
        PaymentOrderSnapshot snapshot = snapshot(code);
        if (snapshot.getUserId() != userId) throw notFound();
        return lockSnapshot(snapshot);
    }

    private PaymentOrder lock(String code) {
        return lockSnapshot(snapshot(code));
    }

    private PaymentOrderSnapshot snapshot(String code) {
        return orders.findSnapshotByOrderCode(code).orElseThrow(this::notFound);
    }

    // Projection không đưa entity vào persistence context trước khi lấy khóa.
    private PaymentOrder lockSnapshot(PaymentOrderSnapshot snapshot) {
        creditService.lockWalletRow(snapshot.getUserId());
        return orders.lockById(snapshot.getId()).orElseThrow(this::notFound);
    }

    private BusinessException notFound() {
        return new BusinessException(WalletErrorCode.WALLET_PAYMENT_ORDER_NOT_FOUND);
    }

    private void requireManualPending(PaymentOrder order) {
        if (order.getProvider() != PaymentProvider.MANUAL || order.getStatus() != PaymentOrderStatus.PENDING) {
            throw new BusinessException(WalletErrorCode.WALLET_PAYMENT_ORDER_NOT_PAYABLE);
        }
    }

    private void requireSubmitted(PaymentOrder order) {
        if (order.getProvider() != PaymentProvider.MANUAL || order.getSubmittedAt() == null) {
            throw new BusinessException(WalletErrorCode.WALLET_PAYMENT_ORDER_NOT_PAYABLE);
        }
    }

    private Instant expiresAt(PaymentOrder order) {
        return order.getCreatedAt().plus(config.getInt(ConfigKey.PAYMENT_ORDER_EXPIRE_MINUTES), ChronoUnit.MINUTES);
    }

    private void expireIfNecessary(PaymentOrder order) {
        if (order.getProvider() == PaymentProvider.MANUAL
                && order.getStatus() == PaymentOrderStatus.PENDING
                && order.getSubmittedAt() == null
                && !clock.instant().isBefore(expiresAt(order))) order.markExpired();
    }

    // API của người dùng không trả tên: họ chỉ xem đơn của chính mình.
    private TopUpOrderDetailResponse detail(PaymentOrder order) {
        return detail(order, Map.of());
    }

    private TopUpOrderDetailResponse adminDetail(PaymentOrder order) {
        return detail(order, findBriefs(List.of(order)));
    }

    // Gom id chủ đơn và id Admin duyệt rồi hỏi module user một lần. User đã xoá mềm không có trong kết quả.
    private Map<Long, UserBrief> findBriefs(List<PaymentOrder> orderList) {
        Set<Long> userIds = new HashSet<>();
        for (PaymentOrder order : orderList) {
            userIds.add(order.getUserId());
            if (order.getReviewedBy() != null) {
                userIds.add(order.getReviewedBy());
            }
        }
        return userService.findBriefs(userIds);
    }

    private TopUpOrderDetailResponse detail(PaymentOrder order, Map<Long, UserBrief> briefs) {
        UserBrief owner = briefs.get(order.getUserId());
        UserBrief reviewer = order.getReviewedBy() == null ? null : briefs.get(order.getReviewedBy());
        return new TopUpOrderDetailResponse(
                order.getOrderCode(),
                order.getUserId(),
                order.getAmountVnd(),
                order.getCredits(),
                order.getVndPerCredit(),
                order.getStatus(),
                order.getProvider(),
                order.getCreatedAt(),
                expiresAt(order),
                order.getSubmittedAt(),
                order.getReviewedBy(),
                order.getReviewedAt(),
                order.getReviewNote(),
                owner == null ? null : owner.fullName(),
                owner == null ? null : owner.email(),
                reviewer == null ? null : reviewer.fullName());
    }

    private void log(String type, long adminId, PaymentOrder order, String note) {
        audit.log(
                type,
                adminId,
                "PAYMENT_ORDER",
                order.getId(),
                Map.<String, Object>of(
                        "orderCode", order.getOrderCode(), "userId", order.getUserId(), "credits", order.getCredits()),
                note);
    }
}
