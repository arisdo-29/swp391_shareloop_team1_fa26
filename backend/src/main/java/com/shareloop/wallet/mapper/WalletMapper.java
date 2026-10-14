package com.shareloop.wallet.mapper;

import com.shareloop.wallet.dto.TopUpOrderResponse;
import com.shareloop.wallet.dto.WalletResponse;
import com.shareloop.wallet.dto.WalletTransactionResponse;
import com.shareloop.wallet.entity.CreditLedgerEntry;
import com.shareloop.wallet.entity.PaymentOrder;
import com.shareloop.wallet.entity.WalletAccount;
import org.springframework.stereotype.Component;

/** Entity sang DTO, viết tay (không dùng MapStruct). */
@Component
public class WalletMapper {

    public WalletResponse toResponse(WalletAccount wallet) {
        return new WalletResponse(
                wallet.getCreditBalance(), wallet.getHeldCredit(), wallet.available(), wallet.hasToppedUp());
    }

    public WalletTransactionResponse toTransactionResponse(CreditLedgerEntry entry) {
        return new WalletTransactionResponse(
                entry.getId(),
                entry.getType(),
                entry.getAmount(),
                entry.getBalanceAfter(),
                entry.getItemId(),
                entry.getPaymentOrderId(),
                entry.getNote(),
                entry.getCreatedAt());
    }

    // redirectUrl không lưu trong DB nên truyền vào từ PaymentGatewayClient.
    public TopUpOrderResponse toTopUpOrderResponse(PaymentOrder order, String redirectUrl) {
        return new TopUpOrderResponse(
                order.getOrderCode(),
                order.getAmountVnd(),
                order.getCredits(),
                order.getStatus(),
                order.getProvider(),
                redirectUrl);
    }
}
