package com.shareloop.wallet.mapper;

import com.shareloop.wallet.dto.WalletResponse;
import com.shareloop.wallet.entity.WalletAccount;
import org.springframework.stereotype.Component;

/** Entity sang DTO, viết tay (không dùng MapStruct). */
@Component
public class WalletMapper {

    public WalletResponse toResponse(WalletAccount wallet) {
        return new WalletResponse(
                wallet.getCreditBalance(), wallet.getHeldCredit(), wallet.available(), wallet.hasToppedUp());
    }
}
