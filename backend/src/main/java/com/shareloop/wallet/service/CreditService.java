package com.shareloop.wallet.service;

import com.shareloop.wallet.dto.WalletResponse;
import com.shareloop.wallet.entity.CreditTxType;
import org.springframework.stereotype.Service;

/**
 * Nơi duy nhất được đổi tiền trong hệ thống (Lộ trình 4.7, 4.8). Mọi hàm đổi số dư khoá dòng {@code users} của
 * người dùng bằng {@code WalletAccountRepository.lockById} (SELECT ... FOR UPDATE) và chạy trong transaction của
 * người gọi (REQUIRED); khi không có transaction ngoài thì tự mở transaction mới.
 *
 * <p>Mọi số tiền do người gọi truyền vào; người gọi lấy mức phí từ ConfigService (post_fee, edit_fee, renew_fee,
 * boost_fee, ai_search_fee), CreditService không viết cứng con số nào.
 *
 * <p>Người gọi: listingfee (hold, release, chargeHeld), item/search (spend), payment IPN (topUp).
 */
@Service
public class CreditService {

    private static final String TODO = "TODO BE1 - tuan 5";

    /**
     * Giữ chỗ Credit khi gửi bài chờ duyệt (C02: giữ khi gửi, trừ khi duyệt lần đầu; F05 bước 3, F07 bước 4).
     *
     * <p>Quy tắc: nếu {@code credit_balance - held_credit >= amount} thì {@code held_credit += amount}; không đổi
     * {@code credit_balance} và không ghi credit_ledger.
     *
     * <p>Transaction: REQUIRED, tham gia transaction của người gọi (ví dụ tạo bài). Khoá: dòng users của userId.
     *
     * @param userId người bị giữ Credit
     * @param amount số Credit giữ, phải lớn hơn 0
     * @param type POST_FEE hoặc EDIT_FEE (dùng cho kiểm tra hợp lệ và log)
     * @param itemId bài liên quan
     * @throws com.shareloop.common.exception.BusinessException WALLET_INSUFFICIENT_BALANCE khi khả dụng không đủ;
     *     WALLET_INVALID_AMOUNT khi amount không dương; WALLET_NOT_FOUND khi không có user
     */
    public void hold(long userId, long amount, CreditTxType type, Long itemId) {
        throw new UnsupportedOperationException(TODO);
    }

    /**
     * Nhả Credit đang giữ khi Admin từ chối hoặc chủ bài rút/xoá bài (F06 bước 4, F05 DELETE).
     *
     * <p>Quy tắc: {@code held_credit -= amount}; {@code credit_balance} giữ nguyên, không ghi credit_ledger.
     *
     * <p>Transaction: REQUIRED, tham gia transaction của người gọi. Khoá: dòng users của userId.
     *
     * @param userId người được nhả
     * @param amount số Credit nhả, phải lớn hơn 0
     * @param type loại phí đang giữ (POST_FEE hoặc EDIT_FEE)
     * @param itemId bài liên quan
     * @throws com.shareloop.common.exception.BusinessException WALLET_HELD_INSUFFICIENT khi held_credit nhỏ hơn
     *     amount; WALLET_INVALID_AMOUNT; WALLET_NOT_FOUND
     */
    public void release(long userId, long amount, CreditTxType type, Long itemId) {
        throw new UnsupportedOperationException(TODO);
    }

    /**
     * Trừ khoản đang giữ khi Admin duyệt (C02, F06 bước 3).
     *
     * <p>Quy tắc: {@code held_credit -= amount} và {@code credit_balance -= amount} trong một lần, ghi
     * credit_ledger với amount âm, balance_after, item_id và type (POST_FEE hoặc EDIT_FEE).
     *
     * <p>Transaction: REQUIRED, tham gia transaction của người gọi (ReviewService.approve). Khoá: dòng users
     * của userId.
     *
     * @param userId người bị trừ (chủ bài)
     * @param amount số Credit trừ, phải lớn hơn 0
     * @param type POST_FEE hoặc EDIT_FEE
     * @param itemId bài liên quan
     * @throws com.shareloop.common.exception.BusinessException WALLET_HELD_INSUFFICIENT khi held_credit nhỏ hơn
     *     amount; WALLET_INVALID_AMOUNT; WALLET_NOT_FOUND
     */
    public void chargeHeld(long userId, long amount, CreditTxType type, Long itemId) {
        throw new UnsupportedOperationException(TODO);
    }

    /**
     * Trừ Credit ngay, không qua giữ chỗ: gia hạn (C16, RENEW_FEE), đẩy bài (C15, BOOST_FEE), AI trả lời
     * (C17, AI_SEARCH_FEE).
     *
     * <p>Quy tắc: nếu {@code credit_balance - held_credit >= amount} thì {@code credit_balance -= amount} và ghi
     * credit_ledger (amount âm, balance_after, item_id nếu có, type). Credit đang giữ cho bài khác không bị dùng.
     *
     * <p>Transaction: REQUIRED, cùng transaction với thay đổi nghiệp vụ của người gọi để lỗi thì hoàn tác cả hai.
     * Khoá: dòng users của userId.
     *
     * @param userId người bị trừ
     * @param amount số Credit trừ, phải lớn hơn 0
     * @param type RENEW_FEE, BOOST_FEE hoặc AI_SEARCH_FEE
     * @param itemId bài liên quan; null khi không gắn bài (AI_SEARCH_FEE)
     * @throws com.shareloop.common.exception.BusinessException WALLET_INSUFFICIENT_BALANCE khi khả dụng không đủ;
     *     WALLET_INVALID_AMOUNT; WALLET_NOT_FOUND
     */
    public void spend(long userId, long amount, CreditTxType type, Long itemId) {
        throw new UnsupportedOperationException(TODO);
    }

    /**
     * Cộng Credit cho đơn nạp đã thanh toán thành công (F04 bước 3).
     *
     * <p>Quy tắc: đơn payment_orders phải SUCCESS và đúng chủ; {@code credit_balance += credits} của đơn,
     * {@code has_topped_up = true}, ghi credit_ledger TOP_UP với payment_order_id và balance_after. Index unique
     * ux_ledger_order chặn cộng hai lần một đơn; gọi lại cho đơn đã cộng bị từ chối.
     *
     * <p>Transaction: một transaction duy nhất cùng với việc chốt đơn SUCCESS và lưu callback_data do người gọi
     * (IPN) thực hiện, chỉ sau khi đã kiểm chữ ký và đối chiếu số tiền. Khoá: dòng users của userId. Số Credit
     * lấy từ {@code payment_orders.credits} (đã tính khi tạo đơn theo ConfigService), hàm này không tự tính.
     *
     * @param userId chủ ví
     * @param paymentOrderId đơn nạp cần cộng
     * @throws com.shareloop.common.exception.BusinessException WALLET_PAYMENT_ORDER_NOT_FOUND khi không có đơn;
     *     WALLET_PAYMENT_ORDER_NOT_PAYABLE khi đơn không SUCCESS, sai chủ hoặc đã cộng; WALLET_NOT_FOUND
     */
    public void topUp(long userId, long paymentOrderId) {
        throw new UnsupportedOperationException(TODO);
    }

    /**
     * Đọc ví cho GET /wallet và để module khác kiểm số dư (F04).
     *
     * <p>Transaction: read-only, không khoá dòng (chỉ đọc, không dùng để quyết định đổi tiền).
     *
     * @param userId chủ ví
     * @return số dư, số đang giữ, số khả dụng, đã từng nạp hay chưa
     * @throws com.shareloop.common.exception.BusinessException WALLET_NOT_FOUND khi không có user
     */
    public WalletResponse getWallet(long userId) {
        throw new UnsupportedOperationException(TODO);
    }
}
