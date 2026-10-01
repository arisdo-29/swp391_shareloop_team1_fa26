package com.shareloop.listingfee.service;

import com.shareloop.listingfee.dto.EditQuote;
import org.springframework.stereotype.Service;

/**
 * Phí đăng và phí sửa bài (C01, C02, C14, C14b). Chỉ đổi cột phí của items (fee_state, pending_fee,
 * pending_fee_type, post_fee_paid, free_edit_used) qua entity lát cắt ItemFee; đổi tiền chỉ qua
 * {@code wallet.CreditService}; đổi status bài chỉ qua ItemService.
 *
 * <p>Mọi mức phí đọc từ ConfigService (ConfigKey post_fee, edit_fee), không viết cứng.
 *
 * <p>Người gọi: item (tạo, sửa, xoá bài), review (duyệt, từ chối).
 */
@Service
public class PostFeeService {

    private static final String TODO = "TODO BE1 - tuan 5";

    /**
     * Báo trước lượt sửa tới là miễn phí hay có phí (F07 bước 2; GET /items/{id}/edit-quote).
     *
     * <p>Quy tắc (C14, C14b): approved_at null thì miễn phí (NOT_YET_APPROVED); đã duyệt và free_edit_used = false
     * thì miễn phí một lần (FIRST_FREE_EDIT); còn lại có phí bằng ConfigKey edit_fee (PAID_EDIT).
     *
     * <p>Transaction: read-only, không khoá dòng nào; không đổi dữ liệu.
     *
     * @param itemId bài định sửa
     * @param userId người sửa, phải là chủ bài
     * @return miễn phí hay có phí, số Credit, lý do
     * @throws com.shareloop.common.exception.BusinessException LISTINGFEE_ITEM_NOT_FOUND, LISTINGFEE_NOT_OWNER
     */
    public EditQuote quoteEdit(long itemId, long userId) {
        throw new UnsupportedOperationException(TODO);
    }

    /**
     * Giữ phí đăng khi tạo bài (C01, C02, F05 bước 3).
     *
     * <p>Quy tắc: số phí = ConfigKey post_fee. Gọi {@code CreditService.hold(userId, fee, POST_FEE, itemId)}, rồi
     * ghi fee_state = HELD, pending_fee = fee, pending_fee_type = POST.
     *
     * <p>Transaction: REQUIRED, tham gia transaction tạo bài của ItemService. Khoá: dòng users (trong CreditService),
     * rồi dòng items của itemId; thứ tự này cố định để tránh deadlock.
     *
     * @param itemId bài vừa tạo
     * @param userId chủ bài
     * @throws com.shareloop.common.exception.BusinessException LISTINGFEE_ITEM_NOT_FOUND, LISTINGFEE_NOT_OWNER,
     *     LISTINGFEE_ALREADY_HELD khi fee_state đã là HELD, WALLET_INSUFFICIENT_BALANCE
     */
    public void holdPostFee(long itemId, long userId) {
        throw new UnsupportedOperationException(TODO);
    }

    /**
     * Giữ phí khi sửa bài (C14, C14b, F07 bước 4).
     *
     * <p>Quy tắc: tính lượt bằng {@link #quoteEdit}. Miễn phí thì không giữ gì. Có phí thì giữ ConfigKey edit_fee
     * với pending_fee_type = EDIT. Bài chưa từng trả phí đăng và chưa đang giữ thì giữ phí đăng (loại POST) như
     * {@link #holdPostFee}.
     *
     * <p>Transaction: REQUIRED, tham gia transaction sửa bài của ItemService. Khoá: dòng users (trong
     * CreditService), rồi dòng items.
     *
     * @param itemId bài đang sửa
     * @param userId chủ bài
     * @throws com.shareloop.common.exception.BusinessException LISTINGFEE_ITEM_NOT_FOUND, LISTINGFEE_NOT_OWNER,
     *     LISTINGFEE_EDIT_NOT_ALLOWED, LISTINGFEE_ALREADY_HELD, WALLET_INSUFFICIENT_BALANCE
     */
    public void holdEditFee(long itemId, long userId) {
        throw new UnsupportedOperationException(TODO);
    }

    /**
     * Trừ khoản phí đang giữ khi Admin duyệt (F06 bước 3).
     *
     * <p>Quy tắc: đọc pending_fee và pending_fee_type của bài; gọi {@code CreditService.chargeHeld}; fee_state =
     * CHARGED. Loại POST thì post_fee_paid = true (expire_at do ItemService.markApproved đặt). Lượt sửa miễn phí
     * thì free_edit_used = true.
     *
     * <p>Transaction: REQUIRED, cùng transaction với ReviewService.approve. Khoá: dòng users của chủ bài (trong
     * CreditService), rồi dòng items.
     *
     * @param itemId bài được duyệt
     * @throws com.shareloop.common.exception.BusinessException LISTINGFEE_ITEM_NOT_FOUND, LISTINGFEE_NOTHING_HELD
     *     khi fee_state không phải HELD
     */
    public void chargeHeldFee(long itemId) {
        throw new UnsupportedOperationException(TODO);
    }

    /**
     * Nhả khoản phí đang giữ khi từ chối hoặc rút/xoá bài (F06 bước 4, F05 DELETE).
     *
     * <p>Quy tắc: gọi {@code CreditService.release}; fee_state = RELEASED. Không ghi credit_ledger (chưa mất tiền);
     * reason chỉ để ghi log hoạt động của người gọi.
     *
     * <p>Transaction: REQUIRED, tham gia transaction của người gọi (từ chối, xoá bài). Khoá: dòng users của chủ
     * bài (trong CreditService), rồi dòng items.
     *
     * @param itemId bài liên quan
     * @param reason lý do nhả (REJECTED, DELETED, ...) để người gọi ghi log
     * @throws com.shareloop.common.exception.BusinessException LISTINGFEE_ITEM_NOT_FOUND, LISTINGFEE_NOTHING_HELD
     *     khi fee_state không phải HELD
     */
    public void releaseHeldFee(long itemId, String reason) {
        throw new UnsupportedOperationException(TODO);
    }
}
