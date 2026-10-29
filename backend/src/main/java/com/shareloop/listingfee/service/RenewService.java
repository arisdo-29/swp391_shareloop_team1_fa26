package com.shareloop.listingfee.service;

import com.shareloop.common.exception.BusinessException;
import com.shareloop.item.entity.ItemStatus;
import com.shareloop.item.entity.OfferType;
import com.shareloop.listingfee.ListingFeeErrorCode;
import com.shareloop.listingfee.dto.RenewQuote;
import com.shareloop.listingfee.dto.RenewResponse;
import com.shareloop.listingfee.entity.ItemFee;
import com.shareloop.listingfee.repository.ItemFeeRepository;
import com.shareloop.listingfee.repository.ItemFeeSnapshot;
import com.shareloop.setting.ConfigKey;
import com.shareloop.setting.service.ConfigService;
import com.shareloop.wallet.entity.CreditTxType;
import com.shareloop.wallet.service.CreditService;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Gia hạn bài (C16, FR-28, BR-D03, BR-P12): trừ phí gia hạn ngay và kéo dài expire_at. Nội dung bài không đổi nên
 * không qua kiểm duyệt lại và không đổi status. Chỉ ghi cột expire_at qua entity lát cắt ItemFee; đổi tiền chỉ qua
 * {@code wallet.CreditService}.
 *
 * <p>Gia hạn bài EXPIRED (renew_expired_window_days, kèm EXPIRED → APPROVED) chưa làm ở đây: cần ItemService hỗ trợ
 * chuyển trạng thái đó (#91).
 */
@Service
// Mọi hàm public chạy trong một transaction: thu phí lỗi hoặc ghi expire_at lỗi thì hoàn tác cả hai.
@Transactional
public class RenewService {

    private final ItemFeeRepository itemFeeRepository;
    private final CreditService creditService;
    private final ConfigService configService;
    private final Clock clock;

    public RenewService(
            ItemFeeRepository itemFeeRepository,
            CreditService creditService,
            ConfigService configService,
            Clock clock) {
        this.itemFeeRepository = itemFeeRepository;
        this.creditService = creditService;
        this.configService = configService;
        this.clock = clock;
    }

    /**
     * Gia hạn bài của chính mình (F08 bước 1, POST /items/{id}/renew).
     *
     * <p>Quy tắc: chỉ chủ bài, bài phải đang APPROVED; gia hạn lúc nào cũng được. Phí = ConfigKey renew_fee, trừ
     * ngay qua {@code CreditService.spend} (RENEW_FEE, ghi credit_ledger); phí 0 thì không đụng ví. expire_at mới =
     * max(now, expire_at cũ) + post_display_days ngày, không phụ thuộc hạng (BR-P12: áp dụng cho mọi bài).
     *
     * <p>Transaction: REQUIRED. Khoá dòng users của chủ bài rồi dòng items (cùng thứ tự với PostFeeService). Hai lần
     * gia hạn cùng lúc chạy lần lượt; lần sau cộng tiếp từ expire_at lần trước vừa ghi, không mất lần nào.
     *
     * @param itemId bài cần gia hạn
     * @param userId người gọi, lấy từ token
     * @return số phí vừa trừ và hạn mới
     * @throws BusinessException LISTINGFEE_ITEM_NOT_FOUND, LISTINGFEE_NOT_OWNER, LISTINGFEE_RENEW_NOT_ALLOWED khi bài
     *     không ở APPROVED, WALLET_INSUFFICIENT_BALANCE khi không đủ Credit khả dụng
     */
    public RenewResponse renew(long itemId, long userId) {
        // Kiểm sớm trên ảnh chụp chưa khoá: người khác gọi hoặc bài không hiển thị thì báo lỗi ngay, không khoá ví.
        ItemFeeSnapshot snapshot = readSnapshot(itemId);
        requireOwner(snapshot, userId);
        requireApproved(snapshot);

        // Khoá ví trước rồi mới khoá items. Lần gia hạn thứ hai chờ ở đây tới khi lần đầu commit; ItemFee chưa từng
        // được nạp trong transaction này nên lockById đọc mới từ DB và thấy expire_at lần đầu vừa ghi.
        creditService.lockWalletRow(userId);
        ItemFee itemFee = lockItemFee(itemId);
        // Đọc lại status sau khi khoá: trong lúc chờ khoá, bài có thể đã bị đổi trạng thái.
        requireApproved(readSnapshot(itemId));

        int fee = calculateRenewFee(snapshot.getOfferType());
        // CreditService.spend không nhận số 0 (WALLET_INVALID_AMOUNT); phí 0 nghĩa là không có gì để trừ.
        if (fee > 0) {
            creditService.spend(userId, fee, CreditTxType.RENEW_FEE, itemId);
        }

        Instant newExpireAt = calculateNewExpireAt(itemFee.getExpireAt());
        itemFee.extendExpireAt(newExpireAt);
        return new RenewResponse(itemId, fee, newExpireAt);
    }

    /**
     * Báo trước một lần gia hạn (GET /items/{id}/renew-quote): phí, hạn mới và có được gia hạn không.
     *
     * <p>Dùng chung với {@link #renew} hàm tính phí, hàm tính hạn mới và hàm kiểm điều kiện, nên số báo giá khớp số
     * thật. Chỉ đọc: không khoá, không trừ tiền. Không được gia hạn thì trả allowed = false kèm lý do, không ném lỗi
     * nghiệp vụ.
     *
     * @param itemId bài định gia hạn
     * @param userId người gọi, phải là chủ bài
     * @return phí, hạn mới, allowed và reason
     * @throws BusinessException LISTINGFEE_ITEM_NOT_FOUND, LISTINGFEE_NOT_OWNER
     */
    @Transactional(readOnly = true)
    public RenewQuote quoteRenew(long itemId, long userId) {
        ItemFeeSnapshot snapshot = readSnapshot(itemId);
        requireOwner(snapshot, userId);
        ItemFee itemFee = itemFeeRepository
                .findById(itemId)
                .orElseThrow(() -> new BusinessException(ListingFeeErrorCode.LISTINGFEE_ITEM_NOT_FOUND));

        int fee = calculateRenewFee(snapshot.getOfferType());
        Instant newExpireAt = calculateNewExpireAt(itemFee.getExpireAt());
        if (snapshot.getStatus() != ItemStatus.APPROVED) {
            String reason = ListingFeeErrorCode.LISTINGFEE_RENEW_NOT_ALLOWED.defaultMessage();
            return new RenewQuote(fee, newExpireAt, false, reason);
        }
        return new RenewQuote(fee, newExpireAt, true, null);
    }

    // Một chỗ duy nhất quyết định phí gia hạn, cả báo giá lẫn gia hạn thật đều gọi. Hiện mọi bài đều thu renew_fee,
    // kể cả bài GIVE; luật "gia hạn bài Cho miễn phí" thuộc N02, khi làm chỉ cần sửa ở hàm này.
    private int calculateRenewFee(OfferType offerType) {
        return configService.getInt(ConfigKey.RENEW_FEE);
    }

    // SRS F08: expire_at = max(now, expire_at) + số ngày. Còn hạn thì cộng nối tiếp để không mất ngày đã trả; đã quá
    // hạn mà job chưa kịp chuyển EXPIRED, hoặc chưa có expire_at (bài duyệt trước khi cột này được ghi), thì tính từ
    // now.
    private Instant calculateNewExpireAt(Instant currentExpireAt) {
        Instant now = clock.instant();
        Instant start = now;
        if (currentExpireAt != null && currentExpireAt.isAfter(now)) {
            start = currentExpireAt;
        }
        int renewDays = configService.getInt(ConfigKey.POST_DISPLAY_DAYS);
        return start.plus(renewDays, ChronoUnit.DAYS);
    }

    // Đọc bằng projection (không phải entity) để không đưa ItemFee vào persistence context trước khi khoá.
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

    private void requireApproved(ItemFeeSnapshot snapshot) {
        if (snapshot.getStatus() != ItemStatus.APPROVED) {
            throw new BusinessException(ListingFeeErrorCode.LISTINGFEE_RENEW_NOT_ALLOWED);
        }
    }
}
