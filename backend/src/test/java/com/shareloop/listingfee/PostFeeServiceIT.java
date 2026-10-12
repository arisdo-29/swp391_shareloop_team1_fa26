package com.shareloop.listingfee;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import com.shareloop.common.exception.BusinessException;
import com.shareloop.common.exception.ErrorCode;
import com.shareloop.listingfee.service.PostFeeService;
import com.shareloop.support.IntegrationTest;
import com.shareloop.wallet.WalletErrorCode;
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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Test PostFeeService cùng CreditService thật trên PostgreSQL (Testcontainers). Không @Transactional ở lớp test: mỗi
 * lời gọi service tự mở transaction và commit thật, nên dữ liệu tự tạo được dọn trong @AfterEach.
 */
class PostFeeServiceIT extends IntegrationTest {

    // Bằng giá trị post_fee seed trong V202610051200__setting_seed_config.sql.
    private static final int POST_FEE = 5;
    private static final int START_BALANCE = 20;
    private static final AtomicInteger PHONE_SEQUENCE = new AtomicInteger();

    @Autowired
    PostFeeService postFeeService;

    @Autowired
    JdbcTemplate jdbc;

    private final List<Long> createdUserIds = new ArrayList<>();

    // Thứ tự xoá: credit_ledger và items tham chiếu users nên phải xoá trước; chỉ xoá dữ liệu do test tạo ra.
    @AfterEach
    void cleanUp() {
        for (Long userId : createdUserIds) {
            jdbc.update("DELETE FROM credit_ledger WHERE user_id = ?", userId);
            jdbc.update("DELETE FROM items WHERE donor_id = ?", userId);
            jdbc.update("DELETE FROM users WHERE id = ?", userId);
        }
        createdUserIds.clear();
    }

    // ---------- holdPostFee ----------

    @Test
    void holdPostFee_whenBalanceEnough_holdsFeeAndMarksItemHeld() {
        // given
        long ownerId = insertUser(START_BALANCE, 0);
        long itemId = insertItem(ownerId);

        // when
        postFeeService.holdPostFee(itemId, ownerId);

        // then
        assertThat(heldOf(ownerId)).isEqualTo(POST_FEE);
        assertThat(balanceOf(ownerId)).isEqualTo(START_BALANCE);
        Map<String, Object> fee = feeColumnsOf(itemId);
        assertThat(fee.get("fee_state")).isEqualTo("HELD");
        assertThat(fee.get("pending_fee")).isEqualTo(POST_FEE);
        assertThat(fee.get("pending_fee_type")).isEqualTo("POST");
    }

    @Test
    void holdPostFee_whenCalledTwice_throwsAlreadyHeldAndKeepsHeldUnchanged() {
        // given
        long ownerId = insertUser(START_BALANCE, 0);
        long itemId = insertItem(ownerId);
        postFeeService.holdPostFee(itemId, ownerId);

        // when
        Throwable thrown = catchThrowable(() -> postFeeService.holdPostFee(itemId, ownerId));

        // then
        assertBusinessError(thrown, ListingFeeErrorCode.LISTINGFEE_ALREADY_HELD);
        assertThat(heldOf(ownerId)).isEqualTo(POST_FEE);
    }

    @Test
    void holdPostFee_whenBalanceNotEnough_throwsInsufficientBalanceAndKeepsFeeStateNone() {
        // given: chỉ có 3 Credit, phí đăng là 5
        long ownerId = insertUser(3, 0);
        long itemId = insertItem(ownerId);

        // when
        Throwable thrown = catchThrowable(() -> postFeeService.holdPostFee(itemId, ownerId));

        // then: transaction đã hoàn tác, bài vẫn chưa giữ phí
        assertBusinessError(thrown, WalletErrorCode.WALLET_INSUFFICIENT_BALANCE);
        assertThat(feeColumnsOf(itemId).get("fee_state")).isEqualTo("NONE");
        assertThat(heldOf(ownerId)).isZero();
    }

    @Test
    void holdPostFee_whenCallerIsNotOwner_throwsNotOwner() {
        // given
        long ownerId = insertUser(START_BALANCE, 0);
        long otherUserId = insertUser(START_BALANCE, 0);
        long itemId = insertItem(ownerId);

        // when
        Throwable thrown = catchThrowable(() -> postFeeService.holdPostFee(itemId, otherUserId));

        // then
        assertBusinessError(thrown, ListingFeeErrorCode.LISTINGFEE_NOT_OWNER);
        assertThat(heldOf(otherUserId)).isZero();
        assertThat(feeColumnsOf(itemId).get("fee_state")).isEqualTo("NONE");
    }

    // ---------- chargeHeldFee / releaseHeldFee ----------

    @Test
    void chargeHeldFee_whenHeld_chargesWalletWritesLedgerAndMarksPostFeePaid() {
        // given
        long ownerId = insertUser(START_BALANCE, 0);
        long itemId = insertItem(ownerId);
        postFeeService.holdPostFee(itemId, ownerId);

        // when
        postFeeService.chargeHeldFee(itemId);

        // then
        assertThat(balanceOf(ownerId)).isEqualTo(START_BALANCE - POST_FEE);
        assertThat(heldOf(ownerId)).isZero();
        assertPostFeeChargedOnce(itemId);
        Map<String, Object> fee = feeColumnsOf(itemId);
        assertThat(fee.get("fee_state")).isEqualTo("CHARGED");
        assertThat(fee.get("post_fee_paid")).isEqualTo(true);
    }

    @Test
    void releaseHeldFee_whenHeld_releasesWalletWithoutLedger() {
        // given
        long ownerId = insertUser(START_BALANCE, 0);
        long itemId = insertItem(ownerId);
        postFeeService.holdPostFee(itemId, ownerId);

        // when
        postFeeService.releaseHeldFee(itemId, "REJECTED");

        // then
        assertThat(heldOf(ownerId)).isZero();
        assertThat(balanceOf(ownerId)).isEqualTo(START_BALANCE);
        assertThat(ledgerCountOfItem(itemId)).isZero();
        assertThat(feeColumnsOf(itemId).get("fee_state")).isEqualTo("RELEASED");
    }

    @Test
    void chargeAndRelease_whenNothingHeld_throwNothingHeld() {
        // given: bài mới, fee_state = NONE
        long ownerId = insertUser(START_BALANCE, 0);
        long itemId = insertItem(ownerId);

        // when
        Throwable chargeError = catchThrowable(() -> postFeeService.chargeHeldFee(itemId));
        Throwable releaseError = catchThrowable(() -> postFeeService.releaseHeldFee(itemId, "DELETED"));

        // then
        assertBusinessError(chargeError, ListingFeeErrorCode.LISTINGFEE_NOTHING_HELD);
        assertBusinessError(releaseError, ListingFeeErrorCode.LISTINGFEE_NOTHING_HELD);
        assertThat(balanceOf(ownerId)).isEqualTo(START_BALANCE);
    }

    // ---------- hai luồng cùng lúc ----------

    @Test
    void chargeAndRelease_whenOwnerHasAnotherHeldItem_doNotTouchOtherItemsHold() throws Exception {
        // given: chủ bài đang giữ 10 = 5 cho bài này + 5 cho bài khác, nên luồng thua luôn qua được ví
        // và bị chặn ở bước kiểm lại sau khi khoá items
        long ownerId = insertUser(START_BALANCE, 2 * POST_FEE);
        long itemId = insertHeldItem(ownerId, POST_FEE);
        long otherItemId = insertHeldItem(ownerId, POST_FEE);

        // when
        RaceResult race = raceChargeAgainstRelease(itemId);

        // then
        assertThat(race.loserError()).isNotNull();
        assertBusinessError(race.loserError(), ListingFeeErrorCode.LISTINGFEE_NOTHING_HELD);
        assertThat(heldOf(ownerId)).isEqualTo(POST_FEE);
        Map<String, Object> otherFee = feeColumnsOf(otherItemId);
        assertThat(otherFee.get("fee_state")).isEqualTo("HELD");
        assertThat(otherFee.get("pending_fee")).isEqualTo(POST_FEE);
        assertWinnerState(race.chargeWon(), ownerId, itemId);
    }

    @Test
    void chargeAndRelease_whenOnlyThisItemHeld_exactlyOneSucceeds() throws Exception {
        // given: chỉ giữ 5 cho đúng bài này; luồng thua có thể bị ví chặn trước khi tới bước khoá items
        long ownerId = insertUser(START_BALANCE, POST_FEE);
        long itemId = insertHeldItem(ownerId, POST_FEE);

        // when
        RaceResult race = raceChargeAgainstRelease(itemId);

        // then
        assertThat(race.loserError())
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        ex -> assertThat(ex.getErrorCode())
                                .isIn(
                                        WalletErrorCode.WALLET_HELD_INSUFFICIENT,
                                        ListingFeeErrorCode.LISTINGFEE_NOTHING_HELD));
        assertThat(heldOf(ownerId)).isZero();
        assertWinnerState(race.chargeWon(), ownerId, itemId);
    }

    // ---------- hàm hỗ trợ ----------

    /** Kết quả cuộc đua: luồng nào thắng và lỗi của luồng thua (null nếu cả hai cùng thành công). */
    private record RaceResult(boolean chargeWon, Throwable loserError) {}

    // Hai luồng gọi qua bean thật (mỗi luồng một transaction riêng), cùng xuất phát nhờ CountDownLatch.
    private RaceResult raceChargeAgainstRelease(long itemId) throws Exception {
        CountDownLatch bothReady = new CountDownLatch(2);
        CountDownLatch startSignal = new CountDownLatch(1);
        Callable<Throwable> charge =
                () -> runAfterSignal(bothReady, startSignal, () -> postFeeService.chargeHeldFee(itemId));
        Callable<Throwable> release =
                () -> runAfterSignal(bothReady, startSignal, () -> postFeeService.releaseHeldFee(itemId, "DELETED"));

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<Throwable> chargeFuture = executor.submit(charge);
            Future<Throwable> releaseFuture = executor.submit(release);
            bothReady.await();
            startSignal.countDown();
            Throwable chargeError = chargeFuture.get(30, TimeUnit.SECONDS);
            Throwable releaseError = releaseFuture.get(30, TimeUnit.SECONDS);
            // Đúng một luồng thành công: một null, một có lỗi.
            assertThat(chargeError == null).isNotEqualTo(releaseError == null);
            return chargeError == null ? new RaceResult(true, releaseError) : new RaceResult(false, chargeError);
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

    private void assertWinnerState(boolean chargeWon, long ownerId, long itemId) {
        Map<String, Object> fee = feeColumnsOf(itemId);
        if (chargeWon) {
            assertThat(fee.get("fee_state")).isEqualTo("CHARGED");
            assertThat(fee.get("post_fee_paid")).isEqualTo(true);
            assertThat(balanceOf(ownerId)).isEqualTo(START_BALANCE - POST_FEE);
            assertPostFeeChargedOnce(itemId);
            return;
        }
        assertThat(fee.get("fee_state")).isEqualTo("RELEASED");
        assertThat(balanceOf(ownerId)).isEqualTo(START_BALANCE);
        assertThat(ledgerCountOfItem(itemId)).isZero();
    }

    private void assertPostFeeChargedOnce(long itemId) {
        Map<String, Object> ledgerRow =
                jdbc.queryForMap("SELECT amount, type FROM credit_ledger WHERE item_id = ?", itemId);
        assertThat(ledgerRow.get("amount")).isEqualTo(-POST_FEE);
        assertThat(ledgerRow.get("type")).isEqualTo("POST_FEE");
    }

    private void assertBusinessError(Throwable thrown, ErrorCode expected) {
        assertThat(thrown)
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(expected));
    }

    private long insertUser(long creditBalance, long heldCredit) {
        // Email và phone có unique index nên mỗi user một giá trị riêng; đầu số 0998 khác CreditServiceIT (0999).
        int sequence = PHONE_SEQUENCE.incrementAndGet();
        String email = "listingfee-it-" + sequence + "-" + System.nanoTime() + "@test.local";
        String phone = String.format("0998%06d", sequence);
        Long userId = jdbc.queryForObject(
                "INSERT INTO users (email, password_hash, full_name, phone, status, credit_balance, held_credit)"
                        + " VALUES (?, 'not-a-real-hash', 'ListingFee IT', ?, 'ACTIVE', ?, ?) RETURNING id",
                Long.class,
                email,
                phone,
                creditBalance,
                heldCredit);
        createdUserIds.add(userId);
        return userId;
    }

    // Bài mới tạo: các cột phí lấy mặc định của DB (fee_state = NONE, pending_fee = 0).
    private long insertItem(long donorId) {
        return jdbc.queryForObject(
                "INSERT INTO items (donor_id, category_id, area_id, offer_type, title, description, condition)"
                        + " VALUES (?, ?, ?, 'GIVE', 'ListingFee IT item', 'Mô tả', 'NEW') RETURNING id",
                Long.class,
                donorId,
                seedCategoryId(),
                seedAreaId());
    }

    // Bài đang giữ phí đăng sẵn; ví của chủ bài phải được tạo với held_credit tương ứng.
    private long insertHeldItem(long donorId, int pendingFee) {
        return jdbc.queryForObject(
                "INSERT INTO items (donor_id, category_id, area_id, offer_type, title, description, condition,"
                        + " fee_state, pending_fee, pending_fee_type)"
                        + " VALUES (?, ?, ?, 'GIVE', 'ListingFee IT item', 'Mô tả', 'NEW', 'HELD', ?, 'POST')"
                        + " RETURNING id",
                Long.class,
                donorId,
                seedCategoryId(),
                seedAreaId(),
                pendingFee);
    }

    // Mượn danh mục và khu vực có sẵn từ seed (không tạo thêm, không xoá) để thoả khoá ngoại của items.
    private long seedCategoryId() {
        return jdbc.queryForObject("SELECT id FROM item_categories ORDER BY id LIMIT 1", Long.class);
    }

    private long seedAreaId() {
        return jdbc.queryForObject("SELECT id FROM areas WHERE level = 2 ORDER BY id LIMIT 1", Long.class);
    }

    private Map<String, Object> feeColumnsOf(long itemId) {
        return jdbc.queryForMap(
                "SELECT fee_state, pending_fee, pending_fee_type, post_fee_paid FROM items WHERE id = ?", itemId);
    }

    private long balanceOf(long userId) {
        return jdbc.queryForObject("SELECT credit_balance FROM users WHERE id = ?", Long.class, userId);
    }

    private long heldOf(long userId) {
        return jdbc.queryForObject("SELECT held_credit FROM users WHERE id = ?", Long.class, userId);
    }

    private long ledgerCountOfItem(long itemId) {
        return jdbc.queryForObject("SELECT count(*) FROM credit_ledger WHERE item_id = ?", Long.class, itemId);
    }
}
