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
 * <p>Mọi bài đang chờ duyệt đều ở fee_state = HELD, kể cả khi phí bằng 0 (lượt sửa miễn phí, hoặc Admin cấu hình
 * phí 0): khi đó pending_fee = 0 và không đụng tới ví. Nhờ vậy ReviewService luôn gọi được chargeHeldFee /
 * releaseHeldFee, và lúc duyệt biết được bài vừa duyệt là bản sửa (pending_fee_type = EDIT).
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
     * <p>Quy tắc (C14, C14b, BR-D07, R36), xét theo thứ tự: chưa từng duyệt (approved_at null) và đang giữ phí đăng
     * thì miễn phí (NOT_YET_APPROVED); chưa từng duyệt và phí đăng đã nhả thì giữ lại phí đăng post_fee
     * (REPOST_FEE); đã duyệt và đang có bản sửa chờ duyệt thì sửa đè miễn phí (PENDING_REVIEW_OVERWRITE); đã duyệt và
     * free_edit_used = false thì miễn phí một lần (FIRST_FREE_EDIT); còn lại có phí edit_fee (PAID_EDIT).
     *
     * <p>Transaction: read-only, không khoá dòng nào; không đổi dữ liệu.
     *
     * @param itemId bài định sửa
     * @param userId người sửa, phải là chủ bài
     * @return miễn phí hay có phí, số Credit, lý do
     * @throws com.shareloop.common.exception.BusinessException LISTINGFEE_ITEM_NOT_FOUND, LISTINGFEE_NOT_OWNER
     */
    @Transactional(readOnly = true)
    public EditQuote quoteEdit(long itemId, long userId) {
        ItemFeeSnapshot snapshot = readSnapshot(itemId);
        requireOwner(snapshot, userId);
        return toEditQuote(snapshot);
    }

    /**
     * Giữ phí đăng khi tạo bài (C01, C02, F05 bước 3).
     *
     * <p>Quy tắc: số phí = ConfigKey post_fee. Phí lớn hơn 0 thì gọi
     * {@code CreditService.hold(userId, fee, POST_FEE, itemId)}; phí bằng 0 (Admin cấu hình miễn phí) thì không đụng
     * ví. Sau đó ghi fee_state = HELD, pending_fee = fee, pending_fee_type = POST.
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
        // Kiểm sớm trên ảnh chụp chưa khoá: sai thì báo lỗi ngay, không đụng tới ví.
        ItemFeeSnapshot snapshot = readSnapshot(itemId);
        requireOwner(snapshot, userId);
        if (snapshot.getFeeState() == ItemFeeState.HELD) {
            throw new BusinessException(ListingFeeErrorCode.LISTINGFEE_ALREADY_HELD);
        }

        int fee = configService.getInt(ConfigKey.POST_FEE);
        holdOnWallet(userId, fee, ItemFeeType.POST, itemId);

        // Khoá items sau users. markHeld tự kiểm lại "đang HELD" trên dữ liệu vừa khoá; ném lỗi thì hoàn tác cả hold.
        ItemFee itemFee = lockItemFee(itemId);
        itemFee.markHeld(fee, ItemFeeType.POST);
    }

    /**
     * Giữ phí khi gửi bản sửa (C14, C14b, F07 bước 4). ItemService gọi hàm này cho MỌI lần sửa; hàm tự quyết giữ bao
     * nhiêu, đúng bằng số {@link #quoteEdit} báo.
     *
     * <p>Quy tắc: đang HELD (bài đang chờ duyệt) thì sửa đè, không làm gì (UC-14 2a). Còn lại giữ {@code amount} của
     * báo giá: REPOST_FEE giữ loại POST; FIRST_FREE_EDIT và PAID_EDIT giữ loại EDIT (lượt miễn phí giữ 0 Credit, chỉ
     * để lúc duyệt biết mà bật free_edit_used). Phí 0 thì không đụng ví. Không bật free_edit_used ở đây: lượt chỉ tính
     * khi bản sửa được duyệt (BR-D07), xem {@link #chargeHeldFee}.
     *
     * <p>Transaction: REQUIRED, tham gia transaction sửa bài của ItemService. Khoá: dòng users của chủ bài TRƯỚC khi
     * đọc dữ liệu để tính báo giá, rồi dòng items. Hai lần gọi cùng lúc cho một bài xếp hàng ở dòng users; lần sau
     * thấy bài đã HELD nên không giữ thêm.
     *
     * @param itemId bài đang sửa
     * @param userId chủ bài
     * @throws com.shareloop.common.exception.BusinessException LISTINGFEE_ITEM_NOT_FOUND, LISTINGFEE_NOT_OWNER,
     *     WALLET_INSUFFICIENT_BALANCE
     */
    public void holdEditFee(long itemId, long userId) {
        // Kiểm sớm chủ bài trên ảnh chụp chưa khoá: người khác gọi thì báo lỗi ngay, không khoá ví của ai.
        requireOwner(readSnapshot(itemId), userId);

        // Khoá ví chủ bài rồi mới đọc lại cột phí: luồng thứ hai phải chờ ở đây tới khi luồng đầu commit, nên ảnh
        // chụp đọc sau đó đã thấy HELD của luồng đầu. Đọc bằng projection (không phải entity) nên luôn lấy dữ liệu mới.
        creditService.lockWalletRow(userId);
        ItemFeeSnapshot snapshot = readSnapshot(itemId);
        if (snapshot.getFeeState() == ItemFeeState.HELD) {
            return;
        }

        EditQuote quote = toEditQuote(snapshot);
        ItemFeeType feeType = quote.reason() == EditQuote.Reason.REPOST_FEE ? ItemFeeType.POST : ItemFeeType.EDIT;
        holdOnWallet(userId, quote.amount(), feeType, itemId);

        ItemFee itemFee = lockItemFee(itemId);
        itemFee.markHeld(quote.amount(), feeType);
    }

    /**
     * Trừ khoản phí đang giữ khi Admin duyệt (F06 bước 3).
     *
     * <p>Quy tắc: đọc pending_fee và pending_fee_type của bài; pending_fee lớn hơn 0 thì gọi
     * {@code CreditService.chargeHeld} (bằng 0 thì không đụng ví, không ghi credit_ledger); fee_state = CHARGED. Loại
     * POST thì post_fee_paid = true (expire_at do ItemService.markApproved đặt). Loại EDIT thì free_edit_used = true
     * (lượt sửa chỉ tính khi được duyệt). pending_fee và pending_fee_type giữ nguyên sau khi trừ (lý do ở Javadoc lớp).
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

        if (snapshot.getPendingFee() > 0) {
            creditService.chargeHeld(
                    snapshot.getDonorId(),
                    snapshot.getPendingFee(),
                    toCreditTxType(snapshot.getPendingFeeType()),
                    itemId);
        }

        ItemFee itemFee = lockItemFee(itemId);
        requireSameHeldFee(itemFee, snapshot);
        itemFee.markCharged();
    }

    /**
     * Nhả khoản phí đang giữ khi từ chối hoặc rút/xoá bài (F06 bước 4, F05 DELETE).
     *
     * <p>Quy tắc: pending_fee lớn hơn 0 thì gọi {@code CreditService.release}; fee_state = RELEASED. Không ghi
     * credit_ledger (chưa mất tiền); reason chỉ để ghi log hoạt động của người gọi. Không đụng free_edit_used: bản sửa
     * bị từ chối không tính lượt (R36). pending_fee và pending_fee_type giữ nguyên (lý do ở Javadoc lớp).
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

        if (snapshot.getPendingFee() > 0) {
            creditService.release(
                    snapshot.getDonorId(),
                    snapshot.getPendingFee(),
                    toCreditTxType(snapshot.getPendingFeeType()),
                    itemId);
        }

        ItemFee itemFee = lockItemFee(itemId);
        requireSameHeldFee(itemFee, snapshot);
        itemFee.markReleased();
    }

    // Bảng luật trong Javadoc quoteEdit; mỗi điều kiện một dòng, xét từ trên xuống, gặp dòng đúng là trả về.
    private EditQuote toEditQuote(ItemFeeSnapshot snapshot) {
        boolean feeHeld = snapshot.getFeeState() == ItemFeeState.HELD;
        if (!snapshot.getEverApproved() && feeHeld) {
            return freeQuote(EditQuote.Reason.NOT_YET_APPROVED);
        }
        if (!snapshot.getEverApproved()) {
            return paidQuote(configService.getInt(ConfigKey.POST_FEE), EditQuote.Reason.REPOST_FEE);
        }
        if (feeHeld) {
            return freeQuote(EditQuote.Reason.PENDING_REVIEW_OVERWRITE);
        }
        if (!snapshot.getFreeEditUsed()) {
            return freeQuote(EditQuote.Reason.FIRST_FREE_EDIT);
        }
        return paidQuote(configService.getInt(ConfigKey.EDIT_FEE), EditQuote.Reason.PAID_EDIT);
    }

    private EditQuote freeQuote(EditQuote.Reason reason) {
        return new EditQuote(true, 0, reason);
    }

    // Admin có thể cấu hình mức phí bằng 0; khi đó báo miễn phí nhưng vẫn giữ lý do gốc.
    private EditQuote paidQuote(int amount, EditQuote.Reason reason) {
        return new EditQuote(amount == 0, amount, reason);
    }

    // CreditService.hold không nhận số 0 (WALLET_INVALID_AMOUNT); phí 0 nghĩa là không có gì để giữ ở ví.
    private void holdOnWallet(long userId, int fee, ItemFeeType feeType, long itemId) {
        if (fee > 0) {
            creditService.hold(userId, fee, toCreditTxType(feeType), itemId);
        }
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
