package com.shareloop.wallet;

import com.shareloop.common.exception.ErrorCode;
import org.springframework.http.HttpStatus;

/** Mã lỗi của module wallet; tiền tố WALLET_ và là một phần của contract (docs/api/wallet.md). */
public enum WalletErrorCode implements ErrorCode {
    WALLET_INSUFFICIENT_BALANCE(HttpStatus.PAYMENT_REQUIRED, "Số dư Credit khả dụng không đủ."),
    WALLET_NOT_FOUND(HttpStatus.NOT_FOUND, "Không tìm thấy ví của người dùng."),
    WALLET_INVALID_AMOUNT(HttpStatus.UNPROCESSABLE_ENTITY, "Số Credit phải lớn hơn 0."),
    WALLET_HELD_INSUFFICIENT(HttpStatus.CONFLICT, "Số Credit đang giữ không đủ để nhả hoặc trừ."),
    WALLET_PAYMENT_ORDER_NOT_FOUND(HttpStatus.NOT_FOUND, "Không tìm thấy đơn nạp."),
    WALLET_PAYMENT_ORDER_NOT_PAYABLE(HttpStatus.CONFLICT, "Đơn nạp đã thất bại hoặc hết hạn, không thể cộng Credit."),
    WALLET_TOPUP_AMOUNT_INVALID(
            HttpStatus.UNPROCESSABLE_ENTITY, "Số tiền nạp phải đạt mức tối thiểu và là bội số của giá một Credit."),
    WALLET_INVALID_DATE_RANGE(HttpStatus.UNPROCESSABLE_ENTITY, "Thời điểm bắt đầu phải trước thời điểm kết thúc.");

    private final HttpStatus status;
    private final String defaultMessage;

    WalletErrorCode(HttpStatus status, String defaultMessage) {
        this.status = status;
        this.defaultMessage = defaultMessage;
    }

    @Override
    public String code() {
        return name();
    }

    @Override
    public HttpStatus status() {
        return status;
    }

    @Override
    public String defaultMessage() {
        return defaultMessage;
    }
}
