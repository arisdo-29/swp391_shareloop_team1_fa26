package com.shareloop.wallet.service;

import com.shareloop.common.exception.BusinessException;
import com.shareloop.integration.payment.VnPaySigner;
import com.shareloop.wallet.WalletErrorCode;
import com.shareloop.wallet.dto.VnPayIpnResponse;
import com.shareloop.wallet.entity.PaymentOrderStatus;
import com.shareloop.wallet.entity.PaymentProvider;
import com.shareloop.wallet.repository.PaymentOrderRepository;
import com.shareloop.wallet.repository.WalletAccountRepository;
import java.math.BigInteger;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class VnPayCallbackService {
    private final PaymentOrderRepository orders;
    private final WalletAccountRepository wallets;
    private final CreditService creditService;
    private final String secret;

    public VnPayCallbackService(
            PaymentOrderRepository orders,
            WalletAccountRepository wallets,
            CreditService creditService,
            @Value("${app.payment.vnpay.hash-secret:}") String secret) {
        this.orders = orders;
        this.wallets = wallets;
        this.creditService = creditService;
        this.secret = secret;
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
        String amount = params.get("vnp_Amount");
        if (amount == null
                || !amount.matches("[0-9]+")
                || !new BigInteger(amount)
                        .equals(BigInteger.valueOf(order.getAmountVnd()).multiply(BigInteger.valueOf(100)))) {
            return new VnPayIpnResponse("04", "Invalid amount");
        }
        if (isSuccessful(params)) {
            creditService.topUpFromGateway(order.getId());
        } else {
            order.markFailed();
        }
        return new VnPayIpnResponse("00", "Confirm Success");
    }
}
