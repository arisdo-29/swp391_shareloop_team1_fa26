package com.shareloop.wallet.service;

import com.shareloop.audit.service.ActivityLogService;
import com.shareloop.common.api.PageResponse;
import com.shareloop.common.exception.BusinessException;
import com.shareloop.setting.ConfigKey;
import com.shareloop.setting.service.ConfigService;
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
import java.util.Map;
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
    private final Clock clock;

    public ManualTopUpService(
            PaymentOrderRepository orders,
            CreditService creditService,
            ConfigService config,
            ActivityLogService audit,
            Clock clock) {
        this.orders = orders;
        this.creditService = creditService;
        this.config = config;
        this.audit = audit;
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

    @Transactional(readOnly = true)
    public PageResponse<TopUpOrderDetailResponse> list(
            PaymentOrderStatus status, boolean submittedOnly, int page, int size) {
        return PageResponse.from(orders.findManualOrders(
                        status,
                        submittedOnly,
                        PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "submittedAt", "id")))
                .map(this::detail));
    }

    public TopUpOrderDetailResponse approve(long adminId, String code, String note) {
        PaymentOrder order = lock(code);
        requireSubmitted(order);
        if (order.getStatus() == PaymentOrderStatus.SUCCESS) return detail(order);
        requireManualPending(order);
        creditService.topUpManual(order.getUserId(), order.getId());
        order.review(adminId, clock.instant(), note);
        log("TOP_UP_APPROVED", adminId, order, note);
        return detail(order);
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
        return detail(order);
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

    private TopUpOrderDetailResponse detail(PaymentOrder order) {
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
                order.getReviewNote());
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
