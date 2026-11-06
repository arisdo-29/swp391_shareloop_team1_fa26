package com.shareloop.listingfee;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.assertj.core.api.Assertions.within;

import com.shareloop.common.exception.BusinessException;
import com.shareloop.common.exception.ErrorCode;
import com.shareloop.listingfee.dto.BoostQuote;
import com.shareloop.listingfee.dto.BoostResponse;
import com.shareloop.listingfee.repository.ItemFeeRepository;
import com.shareloop.listingfee.service.BoostService;
import com.shareloop.setting.ConfigKey;
import com.shareloop.setting.service.ConfigService;
import com.shareloop.support.IntegrationTest;
import com.shareloop.wallet.WalletErrorCode;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Test BoostService cùng CreditService và CategoryService thật trên PostgreSQL (Testcontainers). Không @Transactional ở
 * lớp test: mỗi lời gọi service tự commit, dữ liệu tự tạo được dọn trong @AfterEach. Mức phí và số ngày đọc từ
 * ConfigService để test vẫn đúng khi seed đổi giá trị.
 */
class BoostServiceIT extends IntegrationTest {

    private static final int START_BALANCE = 20;
    private static final AtomicInteger PHONE_SEQUENCE = new AtomicInteger();

    @Autowired
    BoostService boostService;

    @Autowired
    ConfigService configService;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    ItemFeeRepository itemFeeRepository;

    private final List<Long> createdUserIds = new ArrayList<>();
    private final List<Long> createdCategoryIds = new ArrayList<>();

    // Chỉ có giá trị khi test đã đổi boost_max_active; cleanUp trả lại giá trị cũ.
    private Long configAdminId;
    private String originalMaxActive;

    // Thứ tự xoá: trả cấu hình trước (ghi activity_logs của Admin test), rồi credit_ledger và items tham chiếu users,
    // items tham chiếu item_categories nên xoá trước.
    @AfterEach
    void cleanUp() {
        if (originalMaxActive != null) {
            configService.update(ConfigKey.BOOST_MAX_ACTIVE, originalMaxActive, "Trả lại sau test", configAdminId);
            jdbc.update("DELETE FROM activity_logs WHERE actor_id = ?", configAdminId);
            originalMaxActive = null;
            configAdminId = null;
        }
        for (Long userId : createdUserIds) {
            jdbc.update("DELETE FROM credit_ledger WHERE user_id = ?", userId);
            jdbc.update("DELETE FROM items WHERE donor_id = ?", userId);
            jdbc.update("DELETE FROM users WHERE id = ?", userId);
        }
        for (Long categoryId : createdCategoryIds) {
            jdbc.update("DELETE FROM item_categories WHERE id = ?", categoryId);
        }
        createdUserIds.clear();
        createdCategoryIds.clear();
    }

    // ---------- thành công ----------

    @Test
    void boost_whenApprovedOwnerNotBoostedYet_chargesFeeAndBoostsFromNow() {
        // given: bài chưa từng được đẩy, còn hạn 30 ngày
        long ownerId = insertUser(START_BALANCE);
        long itemId = insertItem(ownerId, "APPROVED", true, boostableCategory(), daysFromNow(30), null);
        Instant before = Instant.now();

        // when
        BoostResponse response = boostService.boost(itemId, ownerId);

        // then: đẩy boost_days ngày tính từ lúc gọi, trừ phí và ghi sổ cái BOOST_FEE gắn với bài
        Instant after = Instant.now();
        assertThat(response.itemId()).isEqualTo(itemId);
        assertThat(response.boostedUntil())
                .isBetween(before.plus(boostDays(), ChronoUnit.DAYS), after.plus(boostDays(), ChronoUnit.DAYS));
        assertThat(response.creditBalance()).isEqualTo(START_BALANCE - boostFee());
        assertThat(balanceOf(ownerId)).isEqualTo(START_BALANCE - boostFee());
        Map<String, Object> ledgerRow =
                jdbc.queryForMap("SELECT amount, type FROM credit_ledger WHERE item_id = ?", itemId);
        assertThat(ledgerRow.get("amount")).isEqualTo(-boostFee());
        assertThat(ledgerRow.get("type")).isEqualTo("BOOST_FEE");
        Instant lastBoostedAt = jdbc.queryForObject(
                        "SELECT last_boosted_at FROM items WHERE id = ?", Timestamp.class, itemId)
                .toInstant();
        assertThat(lastBoostedAt).isBetween(before, after);
    }

    @Test
    void boost_whenAlreadyBoosted_extendsFromCurrentBoostedUntil() {
        // given: bài đang được đẩy tới 2 ngày nữa
        Instant currentBoostedUntil = daysFromNow(2);
        long ownerId = insertUser(START_BALANCE);
        long itemId = insertItem(ownerId, "APPROVED", true, boostableCategory(), daysFromNow(30), currentBoostedUntil);
        Instant previousBoostedAt = Instant.now().minus(1, ChronoUnit.DAYS);
        jdbc.update("UPDATE items SET last_boosted_at = ? WHERE id = ?", Timestamp.from(previousBoostedAt), itemId);

        // when
        BoostResponse response = boostService.boost(itemId, ownerId);

        // then: cộng nối tiếp từ mốc cũ, không tính lại từ now
        Instant expectedBoostedUntil = currentBoostedUntil.plus(boostDays(), ChronoUnit.DAYS);
        assertThat(response.boostedUntil()).isEqualTo(expectedBoostedUntil);
        assertThat(boostedUntilOf(itemId)).isEqualTo(expectedBoostedUntil);
        Instant lastBoostedAt = jdbc.queryForObject(
                        "SELECT last_boosted_at FROM items WHERE id = ?", Timestamp.class, itemId)
                .toInstant();
        assertThat(lastBoostedAt).isAfter(previousBoostedAt);
    }

    // ---------- vượt giới hạn ----------

    @Test
    void boost_whenNewBoostedUntilExceedsChainLimit_throwsChainLimitAndChargesNothing() {
        // given: đợt đẩy bắt đầu từ now, cộng thêm một gói sẽ vượt boost_started_at + boost_max_days đúng 1 ngày
        Instant currentBoostedUntil = daysFromNow(boostMaxDays() - boostDays() + 1);
        long ownerId = insertUser(START_BALANCE);
        long itemId = insertItem(ownerId, "APPROVED", true, boostableCategory(), daysFromNow(60), currentBoostedUntil);
        setBoostStartedAt(itemId, daysFromNow(0));

        // when
        Throwable thrown = catchThrowable(() -> boostService.boost(itemId, ownerId));

        // then
        assertBusinessError(thrown, ListingFeeErrorCode.LISTINGFEE_BOOST_CHAIN_LIMIT);
        assertThat(balanceOf(ownerId)).isEqualTo(START_BALANCE);
        assertThat(boostedUntilOf(itemId)).isEqualTo(currentBoostedUntil);
        assertThat(ledgerCountOfItem(itemId)).isZero();
    }

    @Test
    void boost_whenNewBoostedUntilAfterExpireAt_throwsExceedsExpiryAndChargesNothing() {
        // given: bài chỉ còn hạn ngắn hơn một gói đẩy
        long ownerId = insertUser(START_BALANCE);
        Instant expireAt = Instant.now().plus(boostDays(), ChronoUnit.DAYS).minus(1, ChronoUnit.HOURS);
        long itemId = insertItem(ownerId, "APPROVED", true, boostableCategory(), expireAt, null);

        // when
        Throwable thrown = catchThrowable(() -> boostService.boost(itemId, ownerId));

        // then
        assertBusinessError(thrown, ListingFeeErrorCode.LISTINGFEE_BOOST_EXCEEDS_EXPIRY);
        assertThat(balanceOf(ownerId)).isEqualTo(START_BALANCE);
        assertThat(boostedUntilOf(itemId)).isNull();
    }

    // ---------- báo giá ----------

    @Test
    void quoteBoost_whenAllowed_matchesRealBoostAndChangesNothing() {
        // given: bài đang được đẩy, còn hạn dài
        Instant currentBoostedUntil = daysFromNow(2).truncatedTo(ChronoUnit.SECONDS);
        long ownerId = insertUser(START_BALANCE);
        long itemId = insertItem(ownerId, "APPROVED", true, boostableCategory(), daysFromNow(60), currentBoostedUntil);

        // when
        BoostQuote quote = boostService.quoteBoost(itemId, ownerId);

        // then: báo giá cộng dồn từ mốc cũ, không trừ tiền, không đổi boosted_until
        assertThat(quote.allowed()).isTrue();
        assertThat(quote.reason()).isNull();
        assertThat(quote.fee()).isEqualTo(boostFee());
        assertThat(quote.newBoostedUntil()).isEqualTo(currentBoostedUntil.plus(boostDays(), ChronoUnit.DAYS));
        assertThat(balanceOf(ownerId)).isEqualTo(START_BALANCE);
        assertThat(boostedUntilOf(itemId)).isEqualTo(currentBoostedUntil);
        assertThat(ledgerCountOfItem(itemId)).isZero();
    }

    @Test
    void quoteBoost_whenNewBoostedUntilExceedsChainLimit_returnsNotAllowedWithReason() {
        // given: đợt đẩy bắt đầu từ now, cộng thêm một gói sẽ vượt boost_started_at + boost_max_days
        long ownerId = insertUser(START_BALANCE);
        // Lưu mốc vào biến để so lại đúng giá trị đã ghi; gọi daysFromNow lần nữa có thể lệch sang giây sau.
        Instant expireAt = daysFromNow(60);
        long itemId = insertItem(
                ownerId,
                "APPROVED",
                true,
                boostableCategory(),
                expireAt,
                daysFromNow(boostMaxDays() - boostDays() + 1));
        Instant chainStartedAt = daysFromNow(0);
        setBoostStartedAt(itemId, chainStartedAt);

        // when
        BoostQuote quote = boostService.quoteBoost(itemId, ownerId);

        // then: lý do kèm mã lỗi; các mốc lấy theo đợt đang chạy, nghỉ tính từ boosted_until hiện tại
        assertThat(quote.allowed()).isFalse();
        assertThat(quote.reason()).isEqualTo(ListingFeeErrorCode.LISTINGFEE_BOOST_CHAIN_LIMIT.defaultMessage());
        assertThat(quote.reasonCode()).isEqualTo(ListingFeeErrorCode.LISTINGFEE_BOOST_CHAIN_LIMIT.code());
        assertThat(quote.extending()).isTrue();
        assertThat(quote.boostStartedAt()).isEqualTo(chainStartedAt);
        assertThat(quote.chainEndsAt()).isEqualTo(chainStartedAt.plus(boostMaxDays(), ChronoUnit.DAYS));
        assertThat(quote.cooldownUntil()).isEqualTo(boostedUntilOf(itemId).plus(boostCooldownDays(), ChronoUnit.DAYS));
        assertThat(quote.expireAt()).isEqualTo(expireAt);
    }

    @Test
    void quoteBoost_whenOwnerAtUserLimit_returnsUserLimitWithEarliestOwnBoostEnd() {
        // given: chủ bài đã có đủ boost_max_per_user bài đang đẩy, bài hết đẩy sớm nhất là sau 1 ngày
        long ownerId = insertUser(START_BALANCE);
        Instant earliestBoostEnd = daysFromNow(1);
        for (int day = 1; day <= boostMaxPerUser(); day++) {
            Instant boostedUntil = earliestBoostEnd.plus(day - 1, ChronoUnit.DAYS);
            insertItem(ownerId, "APPROVED", true, boostableCategory(), daysFromNow(30), boostedUntil);
        }
        long itemId = insertItem(ownerId, "APPROVED", true, boostableCategory(), daysFromNow(30), null);

        // when
        BoostQuote quote = boostService.quoteBoost(itemId, ownerId);

        // then
        assertThat(quote.allowed()).isFalse();
        assertThat(quote.reasonCode()).isEqualTo(ListingFeeErrorCode.LISTINGFEE_BOOST_USER_LIMIT.code());
        assertThat(quote.extending()).isFalse();
        assertThat(quote.userActiveBoosts()).isEqualTo(boostMaxPerUser());
        assertThat(quote.userEarliestBoostEndAt()).isEqualTo(earliestBoostEnd);
        assertThat(quote.cooldownUntil()).isNull();
    }

    @Test
    void quoteBoost_whenCategoryNotBoostable_returnsNotAllowedWithReason() {
        // given
        long ownerId = insertUser(START_BALANCE);
        long itemId = insertItem(ownerId, "APPROVED", true, insertCategory(false), daysFromNow(30), null);

        // when
        BoostQuote quote = boostService.quoteBoost(itemId, ownerId);

        // then
        assertThat(quote.allowed()).isFalse();
        assertThat(quote.reason()).isEqualTo(ListingFeeErrorCode.LISTINGFEE_CATEGORY_NOT_BOOSTABLE.defaultMessage());
    }

    @Test
    void quoteBoost_whenCallerIsNotOwner_throwsNotOwner() {
        // given
        long ownerId = insertUser(START_BALANCE);
        long strangerId = insertUser(START_BALANCE);
        long itemId = insertItem(ownerId, "APPROVED", true, boostableCategory(), daysFromNow(30), null);

        // when
        Throwable thrown = catchThrowable(() -> boostService.quoteBoost(itemId, strangerId));

        // then
        assertBusinessError(thrown, ListingFeeErrorCode.LISTINGFEE_NOT_OWNER);
    }

    // ---------- sai điều kiện ----------

    @Test
    void boost_whenCallerIsNotOwner_throwsNotOwnerAndChargesNothing() {
        // given
        long ownerId = insertUser(START_BALANCE);
        long otherUserId = insertUser(START_BALANCE);
        long itemId = insertItem(ownerId, "APPROVED", true, boostableCategory(), daysFromNow(30), null);

        // when
        Throwable thrown = catchThrowable(() -> boostService.boost(itemId, otherUserId));

        // then
        assertBusinessError(thrown, ListingFeeErrorCode.LISTINGFEE_NOT_OWNER);
        assertThat(balanceOf(otherUserId)).isEqualTo(START_BALANCE);
        assertThat(boostedUntilOf(itemId)).isNull();
    }

    @Test
    void boost_whenItemPendingReview_throwsBoostNotAllowed() {
        // given: bài chưa được duyệt
        long ownerId = insertUser(START_BALANCE);
        long itemId = insertItem(ownerId, "PENDING_REVIEW", true, boostableCategory(), null, null);

        // when
        Throwable thrown = catchThrowable(() -> boostService.boost(itemId, ownerId));

        // then
        assertBusinessError(thrown, ListingFeeErrorCode.LISTINGFEE_BOOST_NOT_ALLOWED);
        assertThat(balanceOf(ownerId)).isEqualTo(START_BALANCE);
    }

    @Test
    void boost_whenItemHidden_throwsBoostNotAllowed() {
        // given: bài APPROVED nhưng đang bị ẩn (is_active = false)
        long ownerId = insertUser(START_BALANCE);
        long itemId = insertItem(ownerId, "APPROVED", false, boostableCategory(), daysFromNow(30), null);

        // when
        Throwable thrown = catchThrowable(() -> boostService.boost(itemId, ownerId));

        // then
        assertBusinessError(thrown, ListingFeeErrorCode.LISTINGFEE_BOOST_NOT_ALLOWED);
        assertThat(balanceOf(ownerId)).isEqualTo(START_BALANCE);
    }

    @Test
    void boost_whenCategoryNotBoostable_throwsCategoryNotBoostable() {
        // given: danh mục tắt is_boostable (như danh mục "Khác")
        long ownerId = insertUser(START_BALANCE);
        long itemId = insertItem(ownerId, "APPROVED", true, insertCategory(false), daysFromNow(30), null);

        // when
        Throwable thrown = catchThrowable(() -> boostService.boost(itemId, ownerId));

        // then
        assertBusinessError(thrown, ListingFeeErrorCode.LISTINGFEE_CATEGORY_NOT_BOOSTABLE);
        assertThat(balanceOf(ownerId)).isEqualTo(START_BALANCE);
        assertThat(boostedUntilOf(itemId)).isNull();
    }

    @Test
    void boost_whenBalanceNotEnough_throwsInsufficientBalanceAndKeepsBoostedUntil() {
        // given: thiếu đúng 1 Credit so với phí đẩy bài
        long ownerId = insertUser(boostFee() - 1);
        long itemId = insertItem(ownerId, "APPROVED", true, boostableCategory(), daysFromNow(30), null);

        // when
        Throwable thrown = catchThrowable(() -> boostService.boost(itemId, ownerId));

        // then: transaction hoàn tác, boosted_until vẫn trống
        assertBusinessError(thrown, WalletErrorCode.WALLET_INSUFFICIENT_BALANCE);
        assertThat(balanceOf(ownerId)).isEqualTo(boostFee() - 1);
        assertThat(boostedUntilOf(itemId)).isNull();
        assertThat(ledgerCountOfItem(itemId)).isZero();
    }

    // ---------- đợt đẩy liên tục ----------

    @Test
    void boost_whenStartingNewChain_setsBoostStartedAtToNow() {
        // given: bài chưa từng được đẩy
        long ownerId = insertUser(START_BALANCE);
        long itemId = insertItem(ownerId, "APPROVED", true, boostableCategory(), daysFromNow(30), null);
        Instant before = Instant.now();

        // when
        boostService.boost(itemId, ownerId);

        // then: đợt mới bắt đầu đúng lúc mua
        assertThat(boostStartedAtOf(itemId)).isBetween(before, Instant.now());
    }

    @Test
    void boost_whenBuyingMoreInSameChain_keepsBoostStartedAtAndAddsBoostDays() {
        // given: bài vừa mở đợt bằng một gói
        long ownerId = insertUser(START_BALANCE);
        long itemId = insertItem(ownerId, "APPROVED", true, boostableCategory(), daysFromNow(30), null);
        boostService.boost(itemId, ownerId);
        Instant chainStartedAt = boostStartedAtOf(itemId);
        Instant boostedUntilAfterFirst = boostedUntilOf(itemId);

        // when: mua thêm một gói khi đang đẩy
        BoostResponse response = boostService.boost(itemId, ownerId);

        // then: giữ nguyên mốc bắt đầu đợt, cộng nối boost_days từ mốc hết đẩy cũ
        assertThat(boostStartedAtOf(itemId)).isEqualTo(chainStartedAt);
        assertThat(response.boostedUntil()).isEqualTo(boostedUntilAfterFirst.plus(boostDays(), ChronoUnit.DAYS));
    }

    @Test
    void boost_whenChainAlreadyFull_throwsChainLimitAndChargesNothingMore() {
        // given: đã mua liên tiếp đủ số gói lấp kín một đợt (boost_max_days / boost_days, mặc định 6 / 3 = 2 gói)
        int packagesPerChain = boostMaxDays() / boostDays();
        long ownerId = insertUser((long) boostFee() * (packagesPerChain + 1));
        long itemId = insertItem(ownerId, "APPROVED", true, boostableCategory(), daysFromNow(60), null);
        for (int purchase = 0; purchase < packagesPerChain; purchase++) {
            boostService.boost(itemId, ownerId);
        }
        Instant boostedUntilWhenFull = boostedUntilOf(itemId);

        // when: mua thêm gói nữa trong cùng đợt
        Throwable thrown = catchThrowable(() -> boostService.boost(itemId, ownerId));

        // then: bị từ chối trước khi trừ tiền, chỉ còn đúng phí của các gói đã mua
        assertBusinessError(thrown, ListingFeeErrorCode.LISTINGFEE_BOOST_CHAIN_LIMIT);
        assertThat(balanceOf(ownerId)).isEqualTo(boostFee());
        assertThat(ledgerCountOfItem(itemId)).isEqualTo(packagesPerChain);
        assertThat(boostedUntilOf(itemId)).isEqualTo(boostedUntilWhenFull);
    }

    // ---------- thời gian nghỉ ----------

    @Test
    void boost_whenChainEndedWithinCooldown_throwsCooldownAndChargesNothing() {
        // given: đợt đẩy vừa hết 1 giờ trước
        Instant endedAt = Instant.now().minus(1, ChronoUnit.HOURS).truncatedTo(ChronoUnit.SECONDS);
        long ownerId = insertUser(START_BALANCE);
        long itemId = insertItem(ownerId, "APPROVED", true, boostableCategory(), daysFromNow(30), endedAt);

        // when
        Throwable thrown = catchThrowable(() -> boostService.boost(itemId, ownerId));

        // then
        assertBusinessError(thrown, ListingFeeErrorCode.LISTINGFEE_BOOST_COOLDOWN);
        assertThat(balanceOf(ownerId)).isEqualTo(START_BALANCE);
        assertThat(boostedUntilOf(itemId)).isEqualTo(endedAt);
        assertThat(ledgerCountOfItem(itemId)).isZero();
    }

    @Test
    void boost_whenCooldownPassed_startsNewChainFromNow() {
        // given: đợt đẩy đã hết quá boost_cooldown_days ngày (thêm 1 giờ để không sát mốc)
        Instant endedAt = Instant.now()
                .minus(boostCooldownDays(), ChronoUnit.DAYS)
                .minus(1, ChronoUnit.HOURS)
                .truncatedTo(ChronoUnit.SECONDS);
        long ownerId = insertUser(START_BALANCE);
        long itemId = insertItem(ownerId, "APPROVED", true, boostableCategory(), daysFromNow(30), endedAt);
        Instant before = Instant.now();

        // when
        BoostResponse response = boostService.boost(itemId, ownerId);

        // then: mở đợt mới tính từ now, không nối tiếp mốc cũ
        Instant after = Instant.now();
        assertThat(boostStartedAtOf(itemId)).isBetween(before, after);
        assertThat(response.boostedUntil())
                .isBetween(before.plus(boostDays(), ChronoUnit.DAYS), after.plus(boostDays(), ChronoUnit.DAYS));
    }

    // ---------- giới hạn số bài mỗi người ----------

    @Test
    void boost_whenOwnerAlreadyHasMaxActiveBoosts_throwsUserLimitAndChargesNothing() {
        // given: chủ bài đã có đủ boost_max_per_user bài đang đẩy
        long ownerId = insertUser(START_BALANCE);
        insertBoostingItems(ownerId, boostMaxPerUser(), daysFromNow(1));
        long newItemId = insertItem(ownerId, "APPROVED", true, boostableCategory(), daysFromNow(30), null);

        // when: đẩy thêm một bài mới
        Throwable thrown = catchThrowable(() -> boostService.boost(newItemId, ownerId));

        // then
        assertBusinessError(thrown, ListingFeeErrorCode.LISTINGFEE_BOOST_USER_LIMIT);
        assertThat(balanceOf(ownerId)).isEqualTo(START_BALANCE);
        assertThat(boostedUntilOf(newItemId)).isNull();
    }

    @Test
    void boost_whenOwnerAtUserLimitButItemAlreadyBoosting_extendsWithoutTakingNewSlot() {
        // given: chủ bài đã có đủ boost_max_per_user bài đang đẩy
        long ownerId = insertUser(START_BALANCE);
        Instant currentBoostedUntil = daysFromNow(1);
        List<Long> boostingItemIds = insertBoostingItems(ownerId, boostMaxPerUser(), currentBoostedUntil);

        // when: mua thêm cho một bài đang đẩy
        BoostResponse response = boostService.boost(boostingItemIds.get(0), ownerId);

        // then: vẫn được, cộng nối từ mốc cũ
        assertThat(response.boostedUntil()).isEqualTo(currentBoostedUntil.plus(boostDays(), ChronoUnit.DAYS));
        assertThat(balanceOf(ownerId)).isEqualTo(START_BALANCE - boostFee());
    }

    // ---------- suất toàn hệ thống ----------

    @Test
    void boost_whenAllSlotsTaken_throwsSoldOutAndChargesNothing() {
        // given: một bài của người khác đang đẩy; boost_max_active vừa bằng số bài đang đẩy (hết suất)
        long otherOwnerId = insertUser(START_BALANCE);
        insertBoostingItems(otherOwnerId, 1, daysFromNow(1));
        long ownerId = insertUser(START_BALANCE);
        long itemId = insertItem(ownerId, "APPROVED", true, boostableCategory(), daysFromNow(30), null);
        setBoostMaxActive(activeBoostCount());

        // when
        Throwable thrown = catchThrowable(() -> boostService.boost(itemId, ownerId));

        // then
        assertBusinessError(thrown, ListingFeeErrorCode.LISTINGFEE_BOOST_SOLD_OUT);
        assertThat(balanceOf(ownerId)).isEqualTo(START_BALANCE);
        assertThat(boostedUntilOf(itemId)).isNull();
    }

    @Test
    void boost_whenAllSlotsTakenButItemAlreadyBoosting_extendsWithoutTakingNewSlot() {
        // given: bài đang đẩy; boost_max_active vừa bằng số bài đang đẩy (hết suất)
        long ownerId = insertUser(START_BALANCE);
        Instant currentBoostedUntil = daysFromNow(1);
        long itemId = insertItem(ownerId, "APPROVED", true, boostableCategory(), daysFromNow(30), currentBoostedUntil);
        setBoostMaxActive(activeBoostCount());

        // when
        BoostResponse response = boostService.boost(itemId, ownerId);

        // then: mua thêm không chiếm suất mới nên vẫn được
        assertThat(response.boostedUntil()).isEqualTo(currentBoostedUntil.plus(boostDays(), ChronoUnit.DAYS));
        assertThat(balanceOf(ownerId)).isEqualTo(START_BALANCE - boostFee());
    }

    @Test
    @Timeout(30)
    void boost_whenTwoOwnersRaceForLastSlot_onlyOneSucceedsAndLoserKeepsCredit() throws Exception {
        // given: hai chủ bài khác nhau, mỗi người một bài chưa đẩy; hệ thống còn đúng một suất
        long firstOwnerId = insertUser(START_BALANCE);
        long secondOwnerId = insertUser(START_BALANCE);
        long firstItemId = insertItem(firstOwnerId, "APPROVED", true, boostableCategory(), daysFromNow(30), null);
        long secondItemId = insertItem(secondOwnerId, "APPROVED", true, boostableCategory(), daysFromNow(30), null);
        setBoostMaxActive(activeBoostCount() + 1);

        // when: hai người cùng bấm đẩy một lúc
        List<Throwable> failures = runConcurrently(
                () -> boostService.boost(firstItemId, firstOwnerId),
                () -> boostService.boost(secondItemId, secondOwnerId));

        // then: đúng một người bị hết suất; người đó không mất Credit và bài không được đẩy
        assertThat(failures).hasSize(1);
        assertBusinessError(failures.get(0), ListingFeeErrorCode.LISTINGFEE_BOOST_SOLD_OUT);
        boolean firstLost = boostedUntilOf(firstItemId) == null;
        long loserOwnerId = firstLost ? firstOwnerId : secondOwnerId;
        long loserItemId = firstLost ? firstItemId : secondItemId;
        assertThat(balanceOf(loserOwnerId)).isEqualTo(START_BALANCE);
        assertThat(ledgerCountOfItem(loserItemId)).isZero();
        assertThat(boostedUntilOf(loserItemId)).isNull();
    }

    // ---------- báo giá khớp với đẩy thật ----------

    @Test
    void quoteBoost_whenStartingNewChain_returnsTimelineMatchingRealBoost() {
        // given: bài chưa từng được đẩy
        long ownerId = insertUser(START_BALANCE);
        long itemId = insertItem(ownerId, "APPROVED", true, boostableCategory(), daysFromNow(30), null);

        // when: xem báo giá rồi đẩy thật ngay sau đó
        BoostQuote quote = boostService.quoteBoost(itemId, ownerId);
        BoostResponse response = boostService.boost(itemId, ownerId);

        // then: các mốc của báo giá nhất quán với nhau và khớp lần đẩy thật (lệch vài mili giây giữa hai lời gọi)
        assertThat(quote.allowed()).isTrue();
        assertThat(quote.reasonCode()).isNull();
        assertThat(quote.extending()).isFalse();
        assertThat(quote.chainEndsAt()).isEqualTo(quote.boostStartedAt().plus(boostMaxDays(), ChronoUnit.DAYS));
        assertThat(quote.cooldownUntil()).isEqualTo(quote.newBoostedUntil().plus(boostCooldownDays(), ChronoUnit.DAYS));
        assertThat(response.boostedUntil()).isCloseTo(quote.newBoostedUntil(), within(5, ChronoUnit.SECONDS));
        assertThat(boostStartedAtOf(itemId)).isCloseTo(quote.boostStartedAt(), within(5, ChronoUnit.SECONDS));
    }

    @Test
    void quoteBoost_whenInCooldown_returnsCooldownCodeAndUntilMatchingBoostError() {
        // given: đợt đẩy vừa hết 1 giờ trước
        Instant endedAt = Instant.now().minus(1, ChronoUnit.HOURS).truncatedTo(ChronoUnit.SECONDS);
        long ownerId = insertUser(START_BALANCE);
        long itemId = insertItem(ownerId, "APPROVED", true, boostableCategory(), daysFromNow(30), endedAt);

        // when
        BoostQuote quote = boostService.quoteBoost(itemId, ownerId);
        Throwable thrown = catchThrowable(() -> boostService.boost(itemId, ownerId));

        // then: báo giá và lần đẩy thật cùng một mã lỗi; mốc được đẩy lại = boosted_until cũ + boost_cooldown_days
        assertThat(quote.allowed()).isFalse();
        assertThat(quote.reasonCode()).isEqualTo(ListingFeeErrorCode.LISTINGFEE_BOOST_COOLDOWN.code());
        assertThat(quote.cooldownUntil()).isEqualTo(endedAt.plus(boostCooldownDays(), ChronoUnit.DAYS));
        assertBusinessError(thrown, ListingFeeErrorCode.LISTINGFEE_BOOST_COOLDOWN);
    }

    @Test
    void quoteBoost_whenAllSlotsTaken_returnsSoldOutWithEarliestSlotMatchingBoostError() {
        // given: một bài của người khác đang đẩy tới ngày mai; hết suất
        long otherOwnerId = insertUser(START_BALANCE);
        Instant otherBoostedUntil = daysFromNow(1);
        insertBoostingItems(otherOwnerId, 1, otherBoostedUntil);
        long ownerId = insertUser(START_BALANCE);
        long itemId = insertItem(ownerId, "APPROVED", true, boostableCategory(), daysFromNow(30), null);
        setBoostMaxActive(activeBoostCount());

        // when
        BoostQuote quote = boostService.quoteBoost(itemId, ownerId);
        Throwable thrown = catchThrowable(() -> boostService.boost(itemId, ownerId));

        // then: không còn suất; mốc có suất sớm nhất không muộn hơn lúc bài kia hết đẩy
        assertThat(quote.allowed()).isFalse();
        assertThat(quote.reasonCode()).isEqualTo(ListingFeeErrorCode.LISTINGFEE_BOOST_SOLD_OUT.code());
        assertThat(quote.slotsLeft()).isZero();
        assertThat(quote.earliestSlotAt()).isNotNull().isBeforeOrEqualTo(otherBoostedUntil);
        assertBusinessError(thrown, ListingFeeErrorCode.LISTINGFEE_BOOST_SOLD_OUT);
    }

    // ---------- hàm hỗ trợ ----------

    private int boostFee() {
        return configService.getInt(ConfigKey.BOOST_FEE);
    }

    private int boostDays() {
        return configService.getInt(ConfigKey.BOOST_DAYS);
    }

    private int boostMaxDays() {
        return configService.getInt(ConfigKey.BOOST_MAX_DAYS);
    }

    private int boostCooldownDays() {
        return configService.getInt(ConfigKey.BOOST_COOLDOWN_DAYS);
    }

    private int boostMaxPerUser() {
        return configService.getInt(ConfigKey.BOOST_MAX_PER_USER);
    }

    // Cắt tới giây để so bằng được với giá trị đọc lại từ DB (TIMESTAMPTZ lưu tới micro giây).
    private Instant daysFromNow(long days) {
        return Instant.now().plus(days, ChronoUnit.DAYS).truncatedTo(ChronoUnit.SECONDS);
    }

    private void assertBusinessError(Throwable thrown, ErrorCode expected) {
        assertThat(thrown)
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(expected));
    }

    private long insertUser(long creditBalance) {
        // Email và phone có unique index nên mỗi user một giá trị riêng; đầu số 0986 khác các test khác.
        int sequence = PHONE_SEQUENCE.incrementAndGet();
        String email = "boost-it-" + sequence + "-" + System.nanoTime() + "@test.local";
        String phone = String.format("0986%06d", sequence);
        Long userId = jdbc.queryForObject(
                "INSERT INTO users (email, password_hash, full_name, phone, status, credit_balance, held_credit)"
                        + " VALUES (?, 'not-a-real-hash', 'Boost IT', ?, 'ACTIVE', ?, 0) RETURNING id",
                Long.class,
                email,
                phone,
                creditBalance);
        createdUserIds.add(userId);
        return userId;
    }

    // Tự tạo danh mục riêng thay vì mượn seed, để cờ is_boostable đúng như test cần.
    private long insertCategory(boolean boostable) {
        Long categoryId = jdbc.queryForObject(
                "INSERT INTO item_categories (name, is_boostable) VALUES (?, ?) RETURNING id",
                Long.class,
                "Boost IT " + System.nanoTime(),
                boostable);
        createdCategoryIds.add(categoryId);
        return categoryId;
    }

    private long boostableCategory() {
        return insertCategory(true);
    }

    // expireAt và boostedUntil có thể null (bài chưa duyệt, bài chưa từng được đẩy).
    private long insertItem(
            long donorId, String status, boolean active, long categoryId, Instant expireAt, Instant boostedUntil) {
        return jdbc.queryForObject(
                "INSERT INTO items (donor_id, category_id, area_id, offer_type, title, description, condition,"
                        + " status, is_active, post_fee_paid, fee_state, pending_fee, pending_fee_type, expire_at,"
                        + " boosted_until) VALUES (?, ?, ?, 'GIVE', 'Boost IT item', 'Mô tả', 'NEW', ?, ?, true,"
                        + " 'CHARGED', 5, 'POST', ?, ?) RETURNING id",
                Long.class,
                donorId,
                categoryId,
                seedAreaId(),
                status,
                active,
                toTimestamp(expireAt),
                toTimestamp(boostedUntil));
    }

    // Các bài APPROVED đang đẩy tới cùng một mốc, mỗi bài một danh mục riêng.
    private List<Long> insertBoostingItems(long donorId, int count, Instant boostedUntil) {
        List<Long> itemIds = new ArrayList<>();
        for (int index = 0; index < count; index++) {
            itemIds.add(insertItem(donorId, "APPROVED", true, boostableCategory(), daysFromNow(30), boostedUntil));
        }
        return itemIds;
    }

    // Đếm theo đúng query BoostService dùng, để đặt boost_max_active bám theo dữ liệu đang có trong DB dùng chung.
    private long activeBoostCount() {
        return itemFeeRepository.countActiveBoosts(Instant.now());
    }

    // Đổi qua ConfigService như Admin làm (ghi activity_logs nên cần một user thật); cleanUp trả lại giá trị cũ.
    private void setBoostMaxActive(long maxActive) {
        if (originalMaxActive == null) {
            configAdminId = insertUser(0);
            originalMaxActive = configService.get(ConfigKey.BOOST_MAX_ACTIVE);
        }
        configService.update(ConfigKey.BOOST_MAX_ACTIVE, String.valueOf(maxActive), "BoostServiceIT", configAdminId);
    }

    /**
     * Chạy hai việc cùng lúc trên hai luồng (mỗi lời gọi service tự mở transaction riêng); trả các BusinessException
     * bắt được. Hai CountDownLatch giữ hai luồng ở vạch xuất phát để các lời gọi thật sự chen nhau.
     */
    private List<Throwable> runConcurrently(Runnable firstTask, Runnable secondTask) throws Exception {
        CountDownLatch bothReady = new CountDownLatch(2);
        CountDownLatch startSignal = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<Throwable> first = executor.submit(waitThenRun(firstTask, bothReady, startSignal));
            Future<Throwable> second = executor.submit(waitThenRun(secondTask, bothReady, startSignal));
            bothReady.await();
            startSignal.countDown();
            List<Throwable> failures = new ArrayList<>();
            addIfNotNull(failures, first.get(20, TimeUnit.SECONDS));
            addIfNotNull(failures, second.get(20, TimeUnit.SECONDS));
            return failures;
        } finally {
            executor.shutdownNow();
        }
    }

    // Lỗi khác BusinessException được ném tiếp qua Future.get để test fail rõ ràng.
    private Callable<Throwable> waitThenRun(Runnable task, CountDownLatch bothReady, CountDownLatch startSignal) {
        return () -> {
            bothReady.countDown();
            startSignal.await();
            try {
                task.run();
                return null;
            } catch (BusinessException exception) {
                return exception;
            }
        };
    }

    private void addIfNotNull(List<Throwable> failures, Throwable failure) {
        if (failure != null) {
            failures.add(failure);
        }
    }

    private Instant boostStartedAtOf(long itemId) {
        Timestamp boostStartedAt =
                jdbc.queryForObject("SELECT boost_started_at FROM items WHERE id = ?", Timestamp.class, itemId);
        return boostStartedAt == null ? null : boostStartedAt.toInstant();
    }

    private void setBoostStartedAt(long itemId, Instant boostStartedAt) {
        jdbc.update("UPDATE items SET boost_started_at = ? WHERE id = ?", toTimestamp(boostStartedAt), itemId);
    }

    private Timestamp toTimestamp(Instant instant) {
        return instant == null ? null : Timestamp.from(instant);
    }

    // Mượn khu vực có sẵn từ seed (không tạo thêm, không xoá) để thoả khoá ngoại của items.
    private long seedAreaId() {
        return jdbc.queryForObject("SELECT id FROM areas WHERE level = 2 ORDER BY id LIMIT 1", Long.class);
    }

    // Đọc qua Timestamp vì driver PostgreSQL không đổi thẳng TIMESTAMPTZ sang Instant.
    private Instant boostedUntilOf(long itemId) {
        Timestamp boostedUntil =
                jdbc.queryForObject("SELECT boosted_until FROM items WHERE id = ?", Timestamp.class, itemId);
        return boostedUntil == null ? null : boostedUntil.toInstant();
    }

    private long balanceOf(long userId) {
        return jdbc.queryForObject("SELECT credit_balance FROM users WHERE id = ?", Long.class, userId);
    }

    private long ledgerCountOfItem(long itemId) {
        return jdbc.queryForObject("SELECT count(*) FROM credit_ledger WHERE item_id = ?", Long.class, itemId);
    }
}
