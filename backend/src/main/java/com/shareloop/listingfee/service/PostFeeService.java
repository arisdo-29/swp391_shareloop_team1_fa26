package com.shareloop.listingfee.service;

import com.shareloop.common.exception.BusinessException;
import com.shareloop.listingfee.ListingFeeErrorCode;
import com.shareloop.listingfee.dto.EditQuote;
import com.shareloop.listingfee.entity.ItemFee;
import com.shareloop.listingfee.entity.ItemFeeState;
import com.shareloop.listingfee.entity.ItemFeeType;
import com.shareloop.listingfee.repository.ItemFeeRepository;
import com.shareloop.listingfee.repository.ItemFeeSnapshot;
import com.shareloop.setting.ConfigKey;
import com.shareloop.setting.service.ConfigService;
import com.shareloop.wallet.entity.CreditTxType;
import com.shareloop.wallet.service.CreditService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Phí đăng và phí sửa bài (C01, C02, C14, C14b). Chỉ đổi cột phí của items (fee_state, pending_fee,
 * pending_fee_type, post_fee_paid, free_edit_used) qua entity lát cắt ItemFee; đổi tiền chỉ qua
 * {@code wallet.CreditService}; đổi status bài chỉ qua ItemService.
 *
 * <p>Mọi mức phí đọc từ ConfigService (ConfigKey post_fee, edit_fee), không viết cứng.
 *
 * <p>Sau CHARGED hoặc RELEASED, pending_fee và pending_fee_type được giữ nguyên, không reset về 0 / NULL: CHECK của
 * bảng items không ràng buộc hai cột này theo fee_state, và giữ lại cho biết khoản vừa trừ hoặc nhả là bao nhiêu,
 * loại gì (truy vết khi đối soát với credit_ledger). Lần giữ phí kế tiếp (markHeld) ghi đè cả hai cột.
 *
 * <p>Người gọi: item (tạo, sửa, xoá bài), review (duyệt, từ chối).
 */
@Service
// REQUIRED: tham gia transaction của ItemService / ReviewService; lỗi ở bất kỳ bước nào hoàn tác cả tiền lẫn cột phí.
@Transactional
public class PostFeeService {

    private static final String TODO = "TODO BE1 - tuan 5";

    private final ItemFeeRepository itemFeeRepository;
    private final CreditService creditService;
    private final ConfigService configService;

    public PostFeeService(
            ItemFeeRepository itemFeeRepository, CreditService creditService, ConfigService configService) {
        this.itemFeeRepository = itemFeeRepository;
        this.creditService = creditService;
        this.configService = configService;
    }

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
     * <p>Chưa xử lý trường hợp post_fee = 0 (Admin cấu hình miễn phí): hiện CreditService.hold sẽ ném
     * WALLET_INVALID_AMOUNT. Xử lý ở R2 cùng luồng sửa bài.
     *
     * @param itemId bài vừa tạo
     * @param userId chủ bài
     * @throws com.shareloop.common.exception.BusinessException LISTINGFEE_ITEM_NOT_FOUND, LISTINGFEE_NOT_OWNER,
     *     LISTINGFEE_ALREADY_HELD khi fee_state đã là HELD, WALLET_INSUFFICIENT_BALANCE
     */
    public void holdPostFee(long itemId, long userId) {
        // Kiểm sớm trên ảnh chụp chưa khoá: sai thì báo lỗi ngay, không đụng tới ví.
        ItemFeeSnapshot snapshot = readSnapshot(itemId);
        requireOwner(snapshot, userId);
        if (snapshot.getFeeState() == ItemFeeState.HELD) {
            throw new BusinessException(ListingFeeErrorCode.LISTINGFEE_ALREADY_HELD);
        }

        int fee = configService.getInt(ConfigKey.POST_FEE);
        creditService.hold(userId, fee, CreditTxType.POST_FEE, itemId);

        // Khoá items sau users. markHeld tự kiểm lại "đang HELD" trên dữ liệu vừa khoá; ném lỗi thì hoàn tác cả hold.
        ItemFee itemFee = lockItemFee(itemId);
        itemFee.markHeld(fee, ItemFeeType.POST);
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
     * thì free_edit_used = true. pending_fee và pending_fee_type giữ nguyên sau khi trừ (lý do ở Javadoc lớp).
     *
     * <p>Transaction: REQUIRED, cùng transaction với ReviewService.approve. Khoá: dòng users của chủ bài (trong
     * CreditService), rồi dòng items. Sau khi khoá items, nếu bài không còn HELD hoặc khoản giữ đã khác số vừa
     * trừ (luồng khác đã nhả / giữ lại) thì ném lỗi để hoàn tác cả lần trừ tiền.
     *
     * @param itemId bài được duyệt
     * @throws com.shareloop.common.exception.BusinessException LISTINGFEE_ITEM_NOT_FOUND, LISTINGFEE_NOTHING_HELD
     *     khi fee_state không phải HELD
     */
    public void chargeHeldFee(long itemId) {
        ItemFeeSnapshot snapshot = readSnapshot(itemId);
        requireHeld(snapshot);

        creditService.chargeHeld(
                snapshot.getDonorId(), snapshot.getPendingFee(), toCreditTxType(snapshot.getPendingFeeType()), itemId);

        ItemFee itemFee = lockItemFee(itemId);
        requireSameHeldFee(itemFee, snapshot);
        itemFee.markCharged();
    }

    /**
     * Nhả khoản phí đang giữ khi từ chối hoặc rút/xoá bài (F06 bước 4, F05 DELETE).
     *
     * <p>Quy tắc: gọi {@code CreditService.release}; fee_state = RELEASED. Không ghi credit_ledger (chưa mất tiền);
     * reason chỉ để ghi log hoạt động của người gọi. pending_fee và pending_fee_type giữ nguyên (lý do ở Javadoc
     * lớp).
     *
     * <p>Transaction: REQUIRED, tham gia transaction của người gọi (từ chối, xoá bài). Khoá: dòng users của chủ
     * bài (trong CreditService), rồi dòng items. Kiểm lại sau khi khoá như {@link #chargeHeldFee}.
     *
     * @param itemId bài liên quan
     * @param reason lý do nhả (REJECTED, DELETED, ...) để người gọi ghi log
     * @throws com.shareloop.common.exception.BusinessException LISTINGFEE_ITEM_NOT_FOUND, LISTINGFEE_NOTHING_HELD
     *     khi fee_state không phải HELD
     */
    public void releaseHeldFee(long itemId, String reason) {
        ItemFeeSnapshot snapshot = readSnapshot(itemId);
        requireHeld(snapshot);

        creditService.release(
                snapshot.getDonorId(), snapshot.getPendingFee(), toCreditTxType(snapshot.getPendingFeeType()), itemId);

        ItemFee itemFee = lockItemFee(itemId);
        requireSameHeldFee(itemFee, snapshot);
        itemFee.markReleased();
    }

    // Không dùng findById(ItemFee) để đọc trước: entity đó sẽ nằm trong persistence context, và lockById sau đó
    // trả lại đúng instance cũ mà không nạp lại field, nên bước kiểm lại sau khi khoá sẽ đọc giá trị cũ.
    private ItemFeeSnapshot readSnapshot(long itemId) {
        return itemFeeRepository
                .findFeeSnapshotById(itemId)
                .orElseThrow(() -> new BusinessException(ListingFeeErrorCode.LISTINGFEE_ITEM_NOT_FOUND));
    }

    private ItemFee lockItemFee(long itemId) {
        return itemFeeRepository
                .lockById(itemId)
                .orElseThrow(() -> new BusinessException(ListingFeeErrorCode.LISTINGFEE_ITEM_NOT_FOUND));
    }

    private void requireOwner(ItemFeeSnapshot snapshot, long userId) {
        if (snapshot.getDonorId() != userId) {
            throw new BusinessException(ListingFeeErrorCode.LISTINGFEE_NOT_OWNER);
        }
    }

    private void requireHeld(ItemFeeSnapshot snapshot) {
        if (snapshot.getFeeState() != ItemFeeState.HELD) {
            throw new BusinessException(ListingFeeErrorCode.LISTINGFEE_NOTHING_HELD);
        }
    }

    // Số vừa trừ / nhả ở ví lấy từ ảnh chụp chưa khoá; phải khớp với dòng items đã khoá thì mới được ghi trạng thái.
    private void requireSameHeldFee(ItemFee lockedItemFee, ItemFeeSnapshot snapshot) {
        if (lockedItemFee.getFeeState() != ItemFeeState.HELD) {
            throw new BusinessException(ListingFeeErrorCode.LISTINGFEE_NOTHING_HELD);
        }
        boolean sameFee = lockedItemFee.getPendingFee() == snapshot.getPendingFee()
                && lockedItemFee.getPendingFeeType() == snapshot.getPendingFeeType();
        if (!sameFee) {
            throw new BusinessException(
                    ListingFeeErrorCode.LISTINGFEE_NOTHING_HELD, "Khoản phí đang giữ đã thay đổi, vui lòng thử lại.");
        }
    }

    private CreditTxType toCreditTxType(ItemFeeType feeType) {
        return switch (feeType) {
            case POST -> CreditTxType.POST_FEE;
            case EDIT -> CreditTxType.EDIT_FEE;
        };
    }
}
