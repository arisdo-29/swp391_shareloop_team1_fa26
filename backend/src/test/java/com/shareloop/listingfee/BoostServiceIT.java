package com.shareloop.listingfee;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import com.shareloop.common.exception.BusinessException;
import com.shareloop.common.exception.ErrorCode;
import com.shareloop.listingfee.dto.BoostQuote;
import com.shareloop.listingfee.dto.BoostResponse;
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
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
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

    private final List<Long> createdUserIds = new ArrayList<>();
    private final List<Long> createdCategoryIds = new ArrayList<>();

    // Thứ tự xoá: credit_ledger và items tham chiếu users, items tham chiếu item_categories nên xoá trước.
    @AfterEach
    void cleanUp() {
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
    void boost_whenTotalExceedsMaxDays_throwsLimitExceededAndChargesNothing() {
        // given: đang đẩy tới mức mà cộng thêm một gói sẽ vượt boost_max_days đúng 1 ngày
        Instant currentBoostedUntil = daysFromNow(boostMaxDays() - boostDays() + 1);
        long ownerId = insertUser(START_BALANCE);
        long itemId = insertItem(ownerId, "APPROVED", true, boostableCategory(), daysFromNow(60), currentBoostedUntil);

        // when
        Throwable thrown = catchThrowable(() -> boostService.boost(itemId, ownerId));

        // then
        assertBusinessError(thrown, ListingFeeErrorCode.LISTINGFEE_BOOST_LIMIT_EXCEEDED);
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
    void quoteBoost_whenTotalExceedsMaxDays_returnsNotAllowedWithReason() {
        // given: cộng thêm một gói sẽ vượt boost_max_days
        long ownerId = insertUser(START_BALANCE);
        long itemId = insertItem(
                ownerId,
                "APPROVED",
                true,
                boostableCategory(),
                daysFromNow(60),
                daysFromNow(boostMaxDays() - boostDays() + 1));

        // when
        BoostQuote quote = boostService.quoteBoost(itemId, ownerId);

        // then
        assertThat(quote.allowed()).isFalse();
        assertThat(quote.reason()).isEqualTo(ListingFeeErrorCode.LISTINGFEE_BOOST_LIMIT_EXCEEDED.defaultMessage());
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
