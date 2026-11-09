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
 * Đẩy bài (C15, HT-04; SRS 4.6, UC-16): trừ phí đẩy bài ngay và kéo dài boosted_until. Chỉ ghi các cột đẩy
 * bài (boosted_until, boost_started_at, last_boosted_at) qua entity lát cắt ItemFee; đổi tiền chỉ qua
 * {@code wallet.CreditService}. Việc xếp bài đang đẩy lên
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
     * boosted_until cũ) + boost_days ngày. Một đợt đẩy liên tục không vượt boost_started_at + boost_max_days; hết đợt
     * phải nghỉ boost_cooldown_days ngày; không vượt expire_at (SRS 4.6). Bài mở đợt mới còn phải còn suất: mỗi người
     * tối đa boost_max_per_user bài và toàn hệ thống tối đa boost_max_active bài đang đẩy. Mọi kiểm tra chạy trước khi
     * trừ boost_fee qua {@code CreditService.spend} (BOOST_FEE, gắn itemId); phí 0 thì không đụng ví.
     *
     * <p>Transaction: REQUIRED. Khoá dòng users của chủ bài, rồi dòng items (cùng thứ tự với RenewService), rồi khoá
     * suất đẩy toàn hệ thống; nhờ vậy hai lần đẩy cùng lúc chạy lần lượt và không vượt số suất.
     *
     * @param itemId bài cần đẩy
     * @param userId người gọi, lấy từ token
     * @return id bài, thời điểm hết đẩy mới và số dư Credit sau khi trừ phí
     * @throws BusinessException LISTINGFEE_ITEM_NOT_FOUND, LISTINGFEE_NOT_OWNER, LISTINGFEE_BOOST_NOT_ALLOWED,
     *     LISTINGFEE_CATEGORY_NOT_BOOSTABLE, LISTINGFEE_BOOST_COOLDOWN, LISTINGFEE_BOOST_CHAIN_LIMIT,
     *     LISTINGFEE_BOOST_EXCEEDS_EXPIRY, LISTINGFEE_BOOST_USER_LIMIT, LISTINGFEE_BOOST_SOLD_OUT,
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
        // Khoá suất SAU ví và dòng items (mọi luồng cùng thứ tự nên không chờ vòng): ai vào sau đếm lại số suất.
        itemFeeRepository.lockBoostSlots();
        // Đọc lại sau khi khoá: trong lúc chờ khoá, bài có thể đã bị đổi trạng thái hoặc bị ẩn.
        requireVisibleApproved(readSnapshot(itemId));

        // Mọi kiểm tra chạy TRƯỚC khi trừ tiền, để bị từ chối thì không mất Credit.
        Instant now = clock.instant();
        Instant newBoostedUntil = calculateNewBoostedUntil(itemFee.getBoostedUntil(), now);
        Instant chainStartedAt = calculateChainStartedAt(itemFee, now);
        requireBoostRules(itemFee, userId, newBoostedUntil, chainStartedAt, now);

        chargeBoostFee(userId, itemId);
        itemFee.extendBoostedUntil(newBoostedUntil, chainStartedAt, now);
        long creditBalance = creditService.getWallet(userId).creditBalance();
        return new BoostResponse(itemId, newBoostedUntil, creditBalance);
    }

    /**
     * Báo trước một lần đẩy bài (GET /items/{id}/boost-quote): phí, mốc hết đẩy mới và có được đẩy không.
     *
     * <p>Dùng chung với {@link #boost} cùng hàm tính mốc mới, cùng các hàm kiểm điều kiện và cùng cách đọc phí, nên
     * số báo giá khớp số thật. Chỉ đọc: không khoá, không trừ tiền. Không được đẩy thì trả allowed = false kèm lý
     * do và mã lỗi mà {@link #boost} sẽ ném, không ném lỗi nghiệp vụ. Luôn trả kèm các mốc của đợt đẩy, số suất còn
     * trống và hạn hiển thị để FE dựng popup mà không tự tính.
     *
     * @param itemId bài định đẩy
     * @param userId người gọi, phải là chủ bài
     * @return phí, mốc hết đẩy mới, allowed, reason, reasonCode và các mốc, số suất liên quan
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
        Instant chainStartedAt = calculateChainStartedAt(itemFee, now);
        BusinessException rejection = null;
        try {
            requireVisibleApproved(snapshot);
            requireCategoryBoostable(snapshot.getCategoryId());
            requireBoostRules(itemFee, userId, newBoostedUntil, chainStartedAt, now);
        } catch (BusinessException notAllowed) {
            rejection = notAllowed;
        }
        return buildQuote(itemFee, userId, rejection, newBoostedUntil, chainStartedAt, now);
    }

    // rejection null nghĩa là được đẩy. Đếm suất bằng đúng query mà boost dùng để kiểm, nên số FE thấy khớp lúc mua.
    private BoostQuote buildQuote(
            ItemFee itemFee,
            long userId,
            BusinessException rejection,
            Instant newBoostedUntil,
            Instant chainStartedAt,
            Instant now) {
        boolean allowed = rejection == null;
        int maxDays = configService.getInt(ConfigKey.BOOST_MAX_DAYS);
        int maxActive = configService.getInt(ConfigKey.BOOST_MAX_ACTIVE);
        long slotsLeft = Math.max(0, maxActive - itemFeeRepository.countActiveBoosts(now));
        long userActiveBoosts = itemFeeRepository.countActiveBoostsByDonor(userId, now);
        boolean userAtLimit = userActiveBoosts >= configService.getInt(ConfigKey.BOOST_MAX_PER_USER);
        // Hai mốc "sớm nhất" chỉ cần khi đã hết suất, nên chỉ query khi đó.
        Instant earliestSlotAt = slotsLeft == 0
                ? itemFeeRepository.findEarliestActiveBoostEnd(now).orElse(null)
                : null;
        Instant userEarliestBoostEndAt = userAtLimit
                ? itemFeeRepository
                        .findEarliestActiveBoostEndByDonor(userId, now)
                        .orElse(null)
                : null;
        return new BoostQuote(
                configService.getInt(ConfigKey.BOOST_FEE),
                newBoostedUntil,
                allowed,
                allowed ? null : rejection.getMessage(),
                allowed ? null : rejection.getErrorCode().code(),
                chainStartedAt,
                chainStartedAt.plus(maxDays, ChronoUnit.DAYS),
                calculateCooldownUntil(itemFee.getBoostedUntil(), newBoostedUntil, allowed, now),
                isBoosting(itemFee.getBoostedUntil(), now),
                slotsLeft,
                earliestSlotAt,
                userActiveBoosts,
                userEarliestBoostEndAt,
                itemFee.getExpireAt());
    }

    /**
     * Mốc được mở đợt đẩy mới. Được đẩy: tính từ mốc hết đẩy mới (nếu mua gói này rồi không mua thêm). Không được đẩy
     * mà bài đang đẩy hoặc đang nghỉ: tính từ boosted_until hiện tại. Còn lại (chưa từng đẩy, đã nghỉ đủ) thì null.
     */
    private Instant calculateCooldownUntil(
            Instant currentBoostedUntil, Instant newBoostedUntil, boolean allowed, Instant now) {
        int cooldownDays = configService.getInt(ConfigKey.BOOST_COOLDOWN_DAYS);
        if (allowed) {
            return newBoostedUntil.plus(cooldownDays, ChronoUnit.DAYS);
        }
        if (currentBoostedUntil == null) {
            return null;
        }
        Instant cooldownUntil = currentBoostedUntil.plus(cooldownDays, ChronoUnit.DAYS);
        return cooldownUntil.isAfter(now) ? cooldownUntil : null;
    }

    // Đang được đẩy thì cộng nối tiếp từ mốc cũ (mua thêm gói, SRS 4.6); chưa đẩy hoặc đã hết đẩy thì tính từ now.
    private Instant calculateNewBoostedUntil(Instant currentBoostedUntil, Instant now) {
        Instant start = now;
        if (isBoosting(currentBoostedUntil, now)) {
            start = currentBoostedUntil;
        }
        int boostDays = configService.getInt(ConfigKey.BOOST_DAYS);
        return start.plus(boostDays, ChronoUnit.DAYS);
    }

    /**
     * Mốc bắt đầu đợt đẩy mà lần mua này thuộc về. Đang đẩy thì giữ mốc của đợt hiện tại (mua thêm không mở đợt mới);
     * chưa đẩy hoặc đã hết đẩy thì đợt mới bắt đầu từ now. Dữ liệu cũ đang đẩy mà chưa có boost_started_at thì lấy
     * lần mua gần nhất, giống cách migration V202611061200 điền.
     */
    private Instant calculateChainStartedAt(ItemFee itemFee, Instant now) {
        if (!isBoosting(itemFee.getBoostedUntil(), now)) {
            return now;
        }
        if (itemFee.getBoostStartedAt() != null) {
            return itemFee.getBoostStartedAt();
        }
        if (itemFee.getLastBoostedAt() != null) {
            return itemFee.getLastBoostedAt();
        }
        return now;
    }

    private boolean isBoosting(Instant boostedUntil, Instant now) {
        return boostedUntil != null && boostedUntil.isAfter(now);
    }

    // Thứ tự kiểm giống hệt ở boost và quoteBoost, nên lý do báo giá luôn trùng lỗi khi đẩy thật.
    private void requireBoostRules(
            ItemFee itemFee, long donorId, Instant newBoostedUntil, Instant chainStartedAt, Instant now) {
        requireNotInCooldown(itemFee.getBoostedUntil(), now);
        requireWithinChainLimit(newBoostedUntil, chainStartedAt);
        requireWithinExpireAt(newBoostedUntil, itemFee.getExpireAt());
        // Mua thêm cho bài đang đẩy không chiếm suất mới: bài đó đã nằm trong số đếm.
        if (!isBoosting(itemFee.getBoostedUntil(), now)) {
            requireFreeBoostSlot(donorId, now);
        }
    }

    // Hết đợt đẩy phải nghỉ boost_cooldown_days ngày, tính từ boosted_until cũ, mới mở được đợt mới.
    private void requireNotInCooldown(Instant boostedUntil, Instant now) {
        if (boostedUntil == null || isBoosting(boostedUntil, now)) {
            return;
        }
        int cooldownDays = configService.getInt(ConfigKey.BOOST_COOLDOWN_DAYS);
        if (now.isBefore(boostedUntil.plus(cooldownDays, ChronoUnit.DAYS))) {
            throw new BusinessException(ListingFeeErrorCode.LISTINGFEE_BOOST_COOLDOWN);
        }
    }

    // Tính từ lúc BẮT ĐẦU đợt chứ không từ now: nếu tính từ now thì cứ mua nối vài ngày một lần là đẩy mãi được.
    private void requireWithinChainLimit(Instant newBoostedUntil, Instant chainStartedAt) {
        int maxDays = configService.getInt(ConfigKey.BOOST_MAX_DAYS);
        if (newBoostedUntil.isAfter(chainStartedAt.plus(maxDays, ChronoUnit.DAYS))) {
            throw new BusinessException(ListingFeeErrorCode.LISTINGFEE_BOOST_CHAIN_LIMIT);
        }
    }

    private void requireFreeBoostSlot(long donorId, Instant now) {
        int maxPerUser = configService.getInt(ConfigKey.BOOST_MAX_PER_USER);
        if (itemFeeRepository.countActiveBoostsByDonor(donorId, now) >= maxPerUser) {
            throw new BusinessException(ListingFeeErrorCode.LISTINGFEE_BOOST_USER_LIMIT);
        }
        int maxActive = configService.getInt(ConfigKey.BOOST_MAX_ACTIVE);
        if (itemFeeRepository.countActiveBoosts(now) >= maxActive) {
            throw new BusinessException(ListingFeeErrorCode.LISTINGFEE_BOOST_SOLD_OUT);
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
