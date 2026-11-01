package com.shareloop.wallet.service;

import com.shareloop.common.exception.BusinessException;
import com.shareloop.integration.payment.VnPaySigner;
import com.shareloop.setting.ConfigKey;
import com.shareloop.setting.service.ConfigService;
import com.shareloop.wallet.WalletErrorCode;
import com.shareloop.wallet.dto.VnPayIpnResponse;
import com.shareloop.wallet.entity.PaymentOrder;
import com.shareloop.wallet.entity.PaymentOrderStatus;
import com.shareloop.wallet.entity.PaymentProvider;
import com.shareloop.wallet.repository.PaymentOrderRepository;
import com.shareloop.wallet.repository.WalletAccountRepository;
import java.math.BigInteger;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class VnPayCallbackService {
    private static final Logger log = LoggerFactory.getLogger(VnPayCallbackService.class);
    private final PaymentOrderRepository orders;
    private final WalletAccountRepository wallets;
    private final CreditService creditService;
    private final ConfigService configService;
    private final Clock clock;
    private final String secret;
    private final long expiryGraceMinutes;

    public VnPayCallbackService(
            PaymentOrderRepository orders,
            WalletAccountRepository wallets,
            CreditService creditService,
            ConfigService configService,
            Clock clock,
            @Value("${app.payment.vnpay.hash-secret:}") String secret,
            @Value("${app.payment.vnpay.expiry-grace-minutes:2}") long expiryGraceMinutes) {
        this.orders = orders;
        this.wallets = wallets;
        this.creditService = creditService;
        this.configService = configService;
        this.clock = clock;
        this.secret = secret;
        this.expiryGraceMinutes = expiryGraceMinutes;
    }

    public boolean verify(Map<String, String> params) {
        return VnPaySigner.verify(params, secret);
    }

    public boolean isSuccessful(Map<String, String> params) {
        return "00".equals(params.get("vnp_ResponseCode")) && "00".equals(params.get("vnp_TransactionStatus"));
    }

    @Transactional
    public VnPayIpnResponse processIpn(Map<String, String> params) {
        if (!verify(params)) {
            return new VnPayIpnResponse("97", "Invalid signature");
        }
        var snapshot = orders.findSnapshotByOrderCode(params.get("vnp_TxnRef"));
        if (snapshot.isEmpty() || snapshot.get().getProvider() != PaymentProvider.VNPAY) {
            return new VnPayIpnResponse("01", "Order not found");
        }
        // Lock users before payment_orders, including callbacks that mark an order FAILED.
        wallets.lockById(snapshot.get().getUserId())
                .orElseThrow(() -> new BusinessException(WalletErrorCode.WALLET_NOT_FOUND));
        var order = orders.lockById(snapshot.get().getId())
                .orElseThrow(() -> new BusinessException(WalletErrorCode.WALLET_PAYMENT_ORDER_NOT_FOUND));
        order.recordGatewayResult(params.get("vnp_TransactionNo"), true, params);
        if (order.getStatus() != PaymentOrderStatus.PENDING) {
            return new VnPayIpnResponse("02", "Order already confirmed");
        }
        if (!isAmountMatching(order, params.get("vnp_Amount"))) {
            return new VnPayIpnResponse("04", "Invalid amount");
        }
        return settleOrder(order, params);
    }

    private boolean isAmountMatching(PaymentOrder order, String amount) {
        if (amount == null || !amount.matches("[0-9]+")) {
            return false;
        }
        return new BigInteger(amount)
                .equals(BigInteger.valueOf(order.getAmountVnd()).multiply(BigInteger.valueOf(100)));
    }

    private VnPayIpnResponse settleOrder(PaymentOrder order, Map<String, String> params) {
        if (!isSuccessful(params)) {
            order.markFailed();
            return new VnPayIpnResponse("00", "Confirm Success");
        }
        if (isPastDeadline(order)) {
            return rejectExpiredOrder(order, params);
        }
        creditService.topUpFromGateway(order.getId());
        return new VnPayIpnResponse("00", "Confirm Success");
    }

    // PaymentExpiryJob chỉ chạy mỗi 5 phút nên đơn quá hạn vẫn có thể còn PENDING; kiểm lại ở đây để không cộng nhầm
    // (FR-18).
    // Grace bù độ lệch đồng hồ giữa server và VNPAY.
    private boolean isPastDeadline(PaymentOrder order) {
        long expireMinutes = configService.getInt(ConfigKey.PAYMENT_ORDER_EXPIRE_MINUTES);
        Instant deadline = order.getCreatedAt().plus(expireMinutes + expiryGraceMinutes, ChronoUnit.MINUTES);
        return clock.instant().isAfter(deadline);
    }

    // VNPAY báo thành công nhưng đơn đã quá hạn: không cộng Credit, ghi WARN để đối soát tay.
    // Trả 02 (đã xử lý xong) để VNPAY không gọi lại IPN.
    private VnPayIpnResponse rejectExpiredOrder(PaymentOrder order, Map<String, String> params) {
        order.markExpired();
        log.warn(
                "VNPAY IPN paid after deadline, order set EXPIRED, credits NOT granted: orderCode={}, amountVnd={}, vnp_TransactionNo={}",
                order.getOrderCode(),
                order.getAmountVnd(),
                params.get("vnp_TransactionNo"));
        return new VnPayIpnResponse("02", "Order expired");
    }
}
