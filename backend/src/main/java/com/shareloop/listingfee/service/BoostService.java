package com.shareloop.listingfee.service;

import com.shareloop.catalog.service.CategoryService;
import com.shareloop.common.exception.BusinessException;
import com.shareloop.item.entity.ItemStatus;
import com.shareloop.listingfee.ListingFeeErrorCode;
import com.shareloop.listingfee.dto.BoostQuote;
import com.shareloop.listingfee.dto.BoostResponse;
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
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Đẩy bài (C15, FR-36, BR-P08; SRS 4.6, UC-16): trừ phí đẩy bài ngay và kéo dài boosted_until. Chỉ ghi cột
 * boosted_until qua entity lát cắt ItemFee; đổi tiền chỉ qua {@code wallet.CreditService}. Việc xếp bài đang đẩy lên
 * đầu kết quả tìm kiếm thuộc module item/search, không làm ở đây.
 */
@Service
// Mọi hàm public chạy trong một transaction: trừ phí lỗi hoặc ghi boosted_until lỗi thì hoàn tác cả hai.
@Transactional
public class BoostService {

    private final ItemFeeRepository itemFeeRepository;
    private final CreditService creditService;
    private final CategoryService categoryService;
    private final ConfigService configService;
    private final Clock clock;

    public BoostService(
            ItemFeeRepository itemFeeRepository,
            CreditService creditService,
            CategoryService categoryService,
            ConfigService configService,
            Clock clock) {
        this.itemFeeRepository = itemFeeRepository;
        this.creditService = creditService;
        this.categoryService = categoryService;
        this.configService = configService;
        this.clock = clock;
    }

    /**
     * Đẩy bài của chính mình thêm một gói (POST /items/{id}/boost).
     *
     * <p>Quy tắc: chỉ chủ bài; bài APPROVED và không bị ẩn; danh mục có is_boostable. boosted_until mới = max(now,
     * boosted_until cũ) + boost_days ngày, không được vượt now + boost_max_days và không vượt expire_at (SRS 4.6). Phí
     * boost_fee trừ ngay qua {@code CreditService.spend} (BOOST_FEE, gắn itemId); phí 0 thì không đụng ví.
     *
     * <p>Transaction: REQUIRED. Khoá dòng users của chủ bài rồi dòng items (cùng thứ tự với RenewService), nên hai lần
     * đẩy cùng lúc chạy lần lượt và lần sau cộng tiếp từ mốc lần trước vừa ghi.
     *
     * @param itemId bài cần đẩy
     * @param userId người gọi, lấy từ token
     * @return id bài, thời điểm hết đẩy mới và số dư Credit sau khi trừ phí
     * @throws BusinessException LISTINGFEE_ITEM_NOT_FOUND, LISTINGFEE_NOT_OWNER, LISTINGFEE_BOOST_NOT_ALLOWED,
     *     LISTINGFEE_CATEGORY_NOT_BOOSTABLE, LISTINGFEE_BOOST_LIMIT_EXCEEDED, LISTINGFEE_BOOST_EXCEEDS_EXPIRY,
     *     WALLET_INSUFFICIENT_BALANCE
     */
    public BoostResponse boost(long itemId, long userId) {
        // Kiểm sớm trên ảnh chụp chưa khoá: sai điều kiện thì báo lỗi ngay, không khoá ví.
        ItemFeeSnapshot snapshot = readSnapshot(itemId);
        requireOwner(snapshot, userId);
        requireVisibleApproved(snapshot);
        requireCategoryBoostable(snapshot.getCategoryId());

        creditService.lockWalletRow(userId);
        ItemFee itemFee = lockItemFee(itemId);
        // Đọc lại sau khi khoá: trong lúc chờ khoá, bài có thể đã bị đổi trạng thái hoặc bị ẩn.
        requireVisibleApproved(readSnapshot(itemId));

        // Kiểm giới hạn ngày TRƯỚC khi trừ tiền, để bị từ chối thì không mất Credit.
        Instant now = clock.instant();
        Instant newBoostedUntil = calculateNewBoostedUntil(itemFee.getBoostedUntil(), now);
        requireWithinMaxDays(newBoostedUntil, now);
        requireWithinExpireAt(newBoostedUntil, itemFee.getExpireAt());

        chargeBoostFee(userId, itemId);
        itemFee.extendBoostedUntil(newBoostedUntil, now);
        long creditBalance = creditService.getWallet(userId).creditBalance();
        return new BoostResponse(itemId, newBoostedUntil, creditBalance);
    }

    /**
     * Báo trước một lần đẩy bài (GET /items/{id}/boost-quote): phí, mốc hết đẩy mới và có được đẩy không.
     *
     * <p>Dùng chung với {@link #boost} cùng hàm tính mốc mới, cùng các hàm kiểm điều kiện và cùng cách đọc phí, nên
     * số báo giá khớp số thật. Chỉ đọc: không khoá, không trừ tiền. Không được đẩy thì trả allowed = false kèm lý
     * do (thông điệp của mã lỗi mà {@link #boost} sẽ ném), không ném lỗi nghiệp vụ.
     *
     * @param itemId bài định đẩy
     * @param userId người gọi, phải là chủ bài
     * @return phí, mốc hết đẩy mới, allowed và reason
     * @throws BusinessException LISTINGFEE_ITEM_NOT_FOUND, LISTINGFEE_NOT_OWNER
     */
    // NOT_SUPPORTED: không chạy trong transaction. Khi CategoryService ném lỗi (danh mục ẩn) mà ta bắt lại để báo
    // allowed = false, nếu đang chung một transaction thì nó bị đánh dấu rollback-only và commit sẽ báo lỗi.
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public BoostQuote quoteBoost(long itemId, long userId) {
        ItemFeeSnapshot snapshot = readSnapshot(itemId);
        requireOwner(snapshot, userId);
        ItemFee itemFee = itemFeeRepository
                .findById(itemId)
                .orElseThrow(() -> new BusinessException(ListingFeeErrorCode.LISTINGFEE_ITEM_NOT_FOUND));

        Instant now = clock.instant();
        Instant newBoostedUntil = calculateNewBoostedUntil(itemFee.getBoostedUntil(), now);
        int fee = configService.getInt(ConfigKey.BOOST_FEE);
        try {
            requireVisibleApproved(snapshot);
            requireCategoryBoostable(snapshot.getCategoryId());
            requireWithinMaxDays(newBoostedUntil, now);
            requireWithinExpireAt(newBoostedUntil, itemFee.getExpireAt());
        } catch (BusinessException notAllowed) {
            return new BoostQuote(fee, newBoostedUntil, false, notAllowed.getMessage());
        }
        return new BoostQuote(fee, newBoostedUntil, true, null);
    }

    // Đang được đẩy thì cộng nối tiếp từ mốc cũ (mua thêm gói, SRS 4.6); chưa đẩy hoặc đã hết đẩy thì tính từ now.
    private Instant calculateNewBoostedUntil(Instant currentBoostedUntil, Instant now) {
        Instant start = now;
        if (currentBoostedUntil != null && currentBoostedUntil.isAfter(now)) {
            start = currentBoostedUntil;
        }
        int boostDays = configService.getInt(ConfigKey.BOOST_DAYS);
        return start.plus(boostDays, ChronoUnit.DAYS);
    }

    private void requireWithinMaxDays(Instant newBoostedUntil, Instant now) {
        int maxDays = configService.getInt(ConfigKey.BOOST_MAX_DAYS);
        if (newBoostedUntil.isAfter(now.plus(maxDays, ChronoUnit.DAYS))) {
            throw new BusinessException(ListingFeeErrorCode.LISTINGFEE_BOOST_LIMIT_EXCEEDED);
        }
    }

    // Bài chưa có expire_at (dữ liệu cũ) thì không biết hạn hiển thị, coi như vượt hạn để không đẩy quá hạn bài.
    private void requireWithinExpireAt(Instant newBoostedUntil, Instant expireAt) {
        if (expireAt == null || newBoostedUntil.isAfter(expireAt)) {
            throw new BusinessException(ListingFeeErrorCode.LISTINGFEE_BOOST_EXCEEDS_EXPIRY);
        }
    }

    // CreditService.spend không nhận số 0 (WALLET_INVALID_AMOUNT); phí 0 nghĩa là không có gì để trừ.
    private void chargeBoostFee(long userId, long itemId) {
        int fee = configService.getInt(ConfigKey.BOOST_FEE);
        if (fee > 0) {
            creditService.spend(userId, fee, CreditTxType.BOOST_FEE, itemId);
        }
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

    private void requireVisibleApproved(ItemFeeSnapshot snapshot) {
        if (snapshot.getStatus() != ItemStatus.APPROVED || !snapshot.getActive()) {
            throw new BusinessException(ListingFeeErrorCode.LISTINGFEE_BOOST_NOT_ALLOWED);
        }
    }

    // Hỏi catalog qua CategoryService (không inject repository của module khác). Danh mục bị ẩn thì CategoryService
    // ném CATALOG_CATEGORY_NOT_FOUND, để lỗi đó đi thẳng ra ngoài.
    private void requireCategoryBoostable(long categoryId) {
        if (!categoryService.getById(categoryId).boostable()) {
            throw new BusinessException(ListingFeeErrorCode.LISTINGFEE_CATEGORY_NOT_BOOSTABLE);
        }
    }
}
