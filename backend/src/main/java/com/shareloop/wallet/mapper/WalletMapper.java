package com.shareloop.wallet.mapper;

import com.shareloop.wallet.dto.CreditTypeTotal;
import com.shareloop.wallet.dto.TopUpOrderResponse;
import com.shareloop.wallet.dto.WalletResponse;
import com.shareloop.wallet.dto.WalletTransactionResponse;
import com.shareloop.wallet.entity.CreditLedgerEntry;
import com.shareloop.wallet.entity.PaymentOrder;
import com.shareloop.wallet.entity.WalletAccount;
import com.shareloop.wallet.repository.CreditTypeTotalView;
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

    // Báo cáo hiển thị cả cộng vào lẫn trừ ra bằng số dương; dấu của sổ cái đã thể hiện qua nhóm chứa dòng này.
    public CreditTypeTotal toCreditTypeTotal(CreditTypeTotalView view) {
        return new CreditTypeTotal(view.getType(), Math.abs(view.getTotal()), view.getEntryCount());
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
