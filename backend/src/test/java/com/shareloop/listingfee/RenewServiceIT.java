package com.shareloop.listingfee;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import com.shareloop.common.exception.BusinessException;
import com.shareloop.common.exception.ErrorCode;
import com.shareloop.listingfee.dto.RenewResponse;
import com.shareloop.listingfee.service.RenewService;
import com.shareloop.support.IntegrationTest;
import com.shareloop.wallet.WalletErrorCode;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Test RenewService cùng CreditService thật trên PostgreSQL (Testcontainers). Không @Transactional ở lớp test: mỗi
 * lời gọi service tự mở transaction và commit thật, nên dữ liệu tự tạo được dọn trong @AfterEach.
 */
class RenewServiceIT extends IntegrationTest {

    // Bằng giá trị renew_fee, post_display_days seed trong V202610051200__setting_seed_config.sql.
    private static final int RENEW_FEE = 5;
    private static final int RENEW_DAYS = 30;
    private static final int START_BALANCE = 20;
    private static final AtomicInteger PHONE_SEQUENCE = new AtomicInteger();

    @Autowired
    RenewService renewService;

    @Autowired
    JdbcTemplate jdbc;

    private final List<Long> createdUserIds = new ArrayList<>();
    // Giá trị cấu hình gốc của các khoá bị test đổi, để trả lại sau mỗi test.
    private final Map<String, String> originalConfigValues = new HashMap<>();

    // Thứ tự xoá: credit_ledger và items tham chiếu users nên phải xoá trước; chỉ xoá dữ liệu do test tạo ra.
    @AfterEach
    void cleanUp() {
        for (Long userId : createdUserIds) {
            jdbc.update("DELETE FROM credit_ledger WHERE user_id = ?", userId);
            jdbc.update("DELETE FROM items WHERE donor_id = ?", userId);
            jdbc.update("DELETE FROM users WHERE id = ?", userId);
        }
        createdUserIds.clear();
        originalConfigValues.forEach(this::updateConfig);
        originalConfigValues.clear();
    }

    // ---------- thành công ----------

    @Test
    void renew_whenApprovedOwner_chargesRenewFeeAndExtendsFromOldExpireAt() {
        // given: bài còn hạn 5 ngày
        Instant oldExpireAt = Instant.now().plus(5, ChronoUnit.DAYS).truncatedTo(ChronoUnit.SECONDS);
        long ownerId = insertUser(START_BALANCE);
        long itemId = insertItem(ownerId, "APPROVED", oldExpireAt);

        // when
        RenewResponse response = renewService.renew(itemId, ownerId);

        // then: cộng nối tiếp từ hạn cũ, trừ phí ngay và ghi sổ cái RENEW_FEE; status giữ nguyên
        Instant expectedExpireAt = oldExpireAt.plus(RENEW_DAYS, ChronoUnit.DAYS);
        assertThat(response).isEqualTo(new RenewResponse(itemId, RENEW_FEE, expectedExpireAt));
        assertThat(expireAtOf(itemId)).isEqualTo(expectedExpireAt);
        assertThat(statusOf(itemId)).isEqualTo("APPROVED");
        assertThat(balanceOf(ownerId)).isEqualTo(START_BALANCE - RENEW_FEE);
        Map<String, Object> ledgerRow =
                jdbc.queryForMap("SELECT amount, type FROM credit_ledger WHERE item_id = ?", itemId);
        assertThat(ledgerRow.get("amount")).isEqualTo(-RENEW_FEE);
        assertThat(ledgerRow.get("type")).isEqualTo("RENEW_FEE");
    }

    @Test
    void renew_whenExpireAtAlreadyPassedButStillApproved_extendsFromNow() {
        // given: đã quá hạn 2 ngày nhưng job hết hạn chưa chạy nên bài vẫn APPROVED
        long ownerId = insertUser(START_BALANCE);
        long itemId = insertItem(ownerId, "APPROVED", Instant.now().minus(2, ChronoUnit.DAYS));
        Instant before = Instant.now().truncatedTo(ChronoUnit.SECONDS);

        // when
        renewService.renew(itemId, ownerId);

        // then: tính từ lúc gia hạn, không cộng vào hạn cũ đã qua
        Instant after = Instant.now().plusSeconds(1);
        assertThat(expireAtOf(itemId))
                .isBetween(before.plus(RENEW_DAYS, ChronoUnit.DAYS), after.plus(RENEW_DAYS, ChronoUnit.DAYS));
    }

    @Test
    void renew_whenRenewFeeConfiguredZero_extendsWithoutTouchingWallet() {
        // given: Admin cấu hình phí gia hạn = 0; ví không có Credit nào
        setConfig("renew_fee", "0");
        Instant oldExpireAt = Instant.now().plus(5, ChronoUnit.DAYS).truncatedTo(ChronoUnit.SECONDS);
        long ownerId = insertUser(0);
        long itemId = insertItem(ownerId, "APPROVED", oldExpireAt);

        // when
        RenewResponse response = renewService.renew(itemId, ownerId);

        // then
        assertThat(response.fee()).isZero();
        assertThat(expireAtOf(itemId)).isEqualTo(oldExpireAt.plus(RENEW_DAYS, ChronoUnit.DAYS));
        assertThat(ledgerCountOfItem(itemId)).isZero();
    }

    // ---------- nhánh lỗi ----------

    @Test
    void renew_whenCallerIsNotOwner_throwsNotOwnerAndChangesNothing() {
        // given
        Instant oldExpireAt = Instant.now().plus(5, ChronoUnit.DAYS).truncatedTo(ChronoUnit.SECONDS);
        long ownerId = insertUser(START_BALANCE);
        long otherUserId = insertUser(START_BALANCE);
        long itemId = insertItem(ownerId, "APPROVED", oldExpireAt);

        // when
        Throwable thrown = catchThrowable(() -> renewService.renew(itemId, otherUserId));

        // then
        assertBusinessError(thrown, ListingFeeErrorCode.LISTINGFEE_NOT_OWNER);
        assertThat(balanceOf(otherUserId)).isEqualTo(START_BALANCE);
        assertThat(expireAtOf(itemId)).isEqualTo(oldExpireAt);
    }

    @Test
    void renew_whenItemPendingReview_throwsRenewNotAllowed() {
        // given: bài chưa được duyệt nên chưa hiển thị
        long ownerId = insertUser(START_BALANCE);
        long itemId = insertItem(ownerId, "PENDING_REVIEW", null);

        // when
        Throwable thrown = catchThrowable(() -> renewService.renew(itemId, ownerId));

        // then
        assertBusinessError(thrown, ListingFeeErrorCode.LISTINGFEE_RENEW_NOT_ALLOWED);
        assertThat(balanceOf(ownerId)).isEqualTo(START_BALANCE);
    }

    @Test
    void renew_whenItemExpired_throwsRenewNotAllowedAndChargesNothing() {
        // given: bài đã EXPIRED (gia hạn bài EXPIRED chưa hỗ trợ ở #88)
        Instant oldExpireAt = Instant.now().minus(1, ChronoUnit.DAYS).truncatedTo(ChronoUnit.SECONDS);
        long ownerId = insertUser(START_BALANCE);
        long itemId = insertItem(ownerId, "EXPIRED", oldExpireAt);

        // when
        Throwable thrown = catchThrowable(() -> renewService.renew(itemId, ownerId));

        // then
        assertBusinessError(thrown, ListingFeeErrorCode.LISTINGFEE_RENEW_NOT_ALLOWED);
        assertThat(balanceOf(ownerId)).isEqualTo(START_BALANCE);
        assertThat(expireAtOf(itemId)).isEqualTo(oldExpireAt);
        assertThat(statusOf(itemId)).isEqualTo("EXPIRED");
    }

    @Test
    void renew_whenBalanceNotEnough_throwsInsufficientBalanceAndKeepsExpireAt() {
        // given: chỉ có 3 Credit, phí gia hạn là 5
        Instant oldExpireAt = Instant.now().plus(5, ChronoUnit.DAYS).truncatedTo(ChronoUnit.SECONDS);
        long ownerId = insertUser(3);
        long itemId = insertItem(ownerId, "APPROVED", oldExpireAt);

        // when
        Throwable thrown = catchThrowable(() -> renewService.renew(itemId, ownerId));

        // then: transaction đã hoàn tác, hạn giữ nguyên
        assertBusinessError(thrown, WalletErrorCode.WALLET_INSUFFICIENT_BALANCE);
        assertThat(balanceOf(ownerId)).isEqualTo(3);
        assertThat(expireAtOf(itemId)).isEqualTo(oldExpireAt);
        assertThat(ledgerCountOfItem(itemId)).isZero();
    }

    // ---------- hai luồng cùng lúc ----------

    @Test
    void renew_whenCalledTwiceConcurrently_chargesTwiceAndExtendsTwice() throws Exception {
        // given: đủ tiền cho cả hai lần
        Instant oldExpireAt = Instant.now().plus(5, ChronoUnit.DAYS).truncatedTo(ChronoUnit.SECONDS);
        long ownerId = insertUser(START_BALANCE);
        long itemId = insertItem(ownerId, "APPROVED", oldExpireAt);

        // when
        List<Throwable> errors = raceTwoRenews(itemId, ownerId);

        // then: luồng sau chờ khoá ví rồi cộng tiếp từ hạn luồng trước vừa ghi; không mất lần nào
        assertThat(errors).containsOnlyNulls();
        assertThat(expireAtOf(itemId)).isEqualTo(oldExpireAt.plus(2L * RENEW_DAYS, ChronoUnit.DAYS));
        assertThat(balanceOf(ownerId)).isEqualTo(START_BALANCE - 2L * RENEW_FEE);
        assertThat(ledgerCountOfItem(itemId)).isEqualTo(2);
    }

    // ---------- hàm hỗ trợ ----------

    // Hai luồng gọi qua bean thật (mỗi luồng một transaction riêng), cùng xuất phát nhờ CountDownLatch.
    // Dùng Arrays.asList vì List.of không nhận phần tử null.
    private List<Throwable> raceTwoRenews(long itemId, long ownerId) throws Exception {
        CountDownLatch bothReady = new CountDownLatch(2);
        CountDownLatch startSignal = new CountDownLatch(1);
        Callable<Throwable> renew =
                () -> runAfterSignal(bothReady, startSignal, () -> renewService.renew(itemId, ownerId));

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<Throwable> firstFuture = executor.submit(renew);
            Future<Throwable> secondFuture = executor.submit(renew);
            bothReady.await();
            startSignal.countDown();
            return Arrays.asList(firstFuture.get(30, TimeUnit.SECONDS), secondFuture.get(30, TimeUnit.SECONDS));
        } finally {
            executor.shutdownNow();
        }
    }

    private Throwable runAfterSignal(CountDownLatch bothReady, CountDownLatch startSignal, Runnable action)
            throws InterruptedException {
        bothReady.countDown();
        startSignal.await();
        try {
            action.run();
            return null;
        } catch (BusinessException ex) {
            return ex;
        }
    }

    private void assertBusinessError(Throwable thrown, ErrorCode expected) {
        assertThat(thrown)
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(expected));
    }

    private long insertUser(long creditBalance) {
        // Email và phone có unique index nên mỗi user một giá trị riêng; đầu số 0997 khác PostFeeServiceIT (0998).
        int sequence = PHONE_SEQUENCE.incrementAndGet();
        String email = "renew-it-" + sequence + "-" + System.nanoTime() + "@test.local";
        String phone = String.format("0997%06d", sequence);
        Long userId = jdbc.queryForObject(
                "INSERT INTO users (email, password_hash, full_name, phone, status, credit_balance, held_credit)"
                        + " VALUES (?, 'not-a-real-hash', 'Renew IT', ?, 'ACTIVE', ?, 0) RETURNING id",
                Long.class,
                email,
                phone,
                creditBalance);
        createdUserIds.add(userId);
        return userId;
    }

    // Bài ở status cho trước, phí đăng đã trừ; expireAt có thể null (bài chưa từng được duyệt).
    private long insertItem(long donorId, String status, Instant expireAt) {
        Timestamp expireAtValue = expireAt == null ? null : Timestamp.from(expireAt);
        return jdbc.queryForObject(
                "INSERT INTO items (donor_id, category_id, area_id, offer_type, title, description, condition,"
                        + " status, post_fee_paid, fee_state, pending_fee, pending_fee_type, expire_at)"
                        + " VALUES (?, ?, ?, 'GIVE', 'Renew IT item', 'Mô tả', 'NEW', ?, true, 'CHARGED', 5, 'POST', ?)"
                        + " RETURNING id",
                Long.class,
                donorId,
                seedCategoryId(),
                seedAreaId(),
                status,
                expireAtValue);
    }

    // Mượn danh mục và khu vực có sẵn từ seed (không tạo thêm, không xoá) để thoả khoá ngoại của items.
    private long seedCategoryId() {
        return jdbc.queryForObject("SELECT id FROM item_categories ORDER BY id LIMIT 1", Long.class);
    }

    private long seedAreaId() {
        return jdbc.queryForObject("SELECT id FROM areas WHERE level = 2 ORDER BY id LIMIT 1", Long.class);
    }

    // Đổi một khoá cấu hình cho riêng test này; cleanUp trả lại giá trị gốc.
    private void setConfig(String attrKey, String value) {
        String original = jdbc.queryForObject(
                "SELECT attr_value FROM website_attributes WHERE attr_group = 'CONFIG' AND attr_key = ?",
                String.class,
                attrKey);
        originalConfigValues.putIfAbsent(attrKey, original);
        updateConfig(attrKey, value);
    }

    private void updateConfig(String attrKey, String value) {
        jdbc.update(
                "UPDATE website_attributes SET attr_value = ? WHERE attr_group = 'CONFIG' AND attr_key = ?",
                value,
                attrKey);
    }

    // Đọc qua Timestamp vì driver PostgreSQL không đổi thẳng TIMESTAMPTZ sang Instant.
    private Instant expireAtOf(long itemId) {
        Timestamp expireAt = jdbc.queryForObject("SELECT expire_at FROM items WHERE id = ?", Timestamp.class, itemId);
        return expireAt == null ? null : expireAt.toInstant();
    }

    private String statusOf(long itemId) {
        return jdbc.queryForObject("SELECT status FROM items WHERE id = ?", String.class, itemId);
    }

    private long balanceOf(long userId) {
        return jdbc.queryForObject("SELECT credit_balance FROM users WHERE id = ?", Long.class, userId);
    }

    private long ledgerCountOfItem(long itemId) {
        return jdbc.queryForObject("SELECT count(*) FROM credit_ledger WHERE item_id = ?", Long.class, itemId);
    }
}
