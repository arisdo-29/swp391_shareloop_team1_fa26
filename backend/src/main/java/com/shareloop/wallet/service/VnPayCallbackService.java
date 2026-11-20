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
        if (order.getStatus() != PaymentOrderStatus.PENDING) {
            flagIfPaidAfterClosed(order, params);
            return new VnPayIpnResponse("02", "Order already confirmed");
        }
        order.recordGatewayResult(params.get("vnp_TransactionNo"), true, params);
        if (!isAmountMatching(order, params.get("vnp_Amount"))) {
            return new VnPayIpnResponse("04", "Invalid amount");
        }
        return settleOrder(order, params);
    }

    /**
     * Người dùng quay về từ VNPay với kết quả thất bại (vd vnp_ResponseCode 24 = khách hủy): chuyển đơn FAILED ngay, không
     * chờ IPN hay job hết hạn. Tham số Return được VNPay ký giống IPN nên tin được sau khi kiểm chữ ký; chỉ đổi đơn
     * VNPAY còn PENDING và đúng số tiền, ghi lại callback để biết mã lỗi. Kết quả thành công ở Return không cộng
     * Credit: chỉ IPN mới cộng (giữ nguyên luồng cũ). Không ném lỗi: Return luôn chuyển hướng về trang Ví.
     *
     * @return true nếu vừa chuyển đơn sang FAILED
     */
    @Transactional
    public boolean failFromReturn(Map<String, String> params) {
        if (!verify(params) || isSuccessful(params)) {
            return false;
        }
        var snapshot = orders.findSnapshotByOrderCode(params.get("vnp_TxnRef"));
        if (snapshot.isEmpty()
                || snapshot.get().getProvider() != PaymentProvider.VNPAY
                || snapshot.get().getStatus() != PaymentOrderStatus.PENDING) {
            return false;
        }
        // Cùng thứ tự khoá với IPN (users → payment_orders) để Return và IPN đến cùng lúc không chờ vòng.
        wallets.lockById(snapshot.get().getUserId())
                .orElseThrow(() -> new BusinessException(WalletErrorCode.WALLET_NOT_FOUND));
        var order = orders.lockById(snapshot.get().getId())
                .orElseThrow(() -> new BusinessException(WalletErrorCode.WALLET_PAYMENT_ORDER_NOT_FOUND));
        if (order.getStatus() != PaymentOrderStatus.PENDING || !isAmountMatching(order, params.get("vnp_Amount"))) {
            return false;
        }
        order.recordGatewayResult(params.get("vnp_TransactionNo"), true, params);
        order.markFailed();
        log.info(
                "VNPAY return failed, order set FAILED: orderCode={}, vnp_ResponseCode={}",
                order.getOrderCode(),
                params.get("vnp_ResponseCode"));
        return true;
    }

    // IPN báo đã thu tiền cho đơn đã hủy hoặc hết hạn: theo cách project xử lý đơn trả sau hạn (rejectExpiredOrder),
    // không tự cộng Credit mà ghi WARN và đánh dấu đơn cần đối soát, hoàn tiền tay. Không đè callback_data cũ.
    private void flagIfPaidAfterClosed(PaymentOrder order, Map<String, String> params) {
        boolean closedUnpaid =
                order.getStatus() == PaymentOrderStatus.FAILED || order.getStatus() == PaymentOrderStatus.EXPIRED;
        if (!closedUnpaid || !isSuccessful(params) || !isAmountMatching(order, params.get("vnp_Amount"))) {
            return;
        }
        String transactionNo = params.get("vnp_TransactionNo");
        order.flagPaidAfterClosed("VNPay báo đã thu tiền sau khi đơn đã đóng; đơn chờ đối soát để hoàn tiền"
                + " (mã giao dịch VNPay: " + transactionNo + ").");
        log.warn(
                "VNPAY IPN paid after order closed, credits NOT granted, needs refund: orderCode={}, status={},"
                        + " amountVnd={}, vnp_TransactionNo={}",
                order.getOrderCode(),
                order.getStatus(),
                order.getAmountVnd(),
                transactionNo);
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
    // (VI-06, TD-05).
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
