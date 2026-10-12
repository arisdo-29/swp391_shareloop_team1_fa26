package com.shareloop.wallet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import com.shareloop.common.exception.BusinessException;
import com.shareloop.support.IntegrationTest;
import com.shareloop.wallet.dto.WalletResponse;
import com.shareloop.wallet.entity.CreditTxType;
import com.shareloop.wallet.service.CreditService;
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
 * Test CreditService với PostgreSQL thật (Testcontainers) để kiểm cả khoá dòng lẫn CHECK của DB. Không @Transactional
 * ở lớp test: mỗi lời gọi service tự mở transaction và commit thật, nên dữ liệu tự tạo được dọn trong @AfterEach.
 */
class CreditServiceIT extends IntegrationTest {

    private static final AtomicInteger PHONE_SEQUENCE = new AtomicInteger();
    private static final long MISSING_USER_ID = 999_999_999L;

    @Autowired
    CreditService creditService;

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

    // ---------- hold ----------

    @Test
    void hold_whenAvailableEnough_increasesHeldAndKeepsBalance() {
        // given
        long userId = insertUser(20, 0);

        // when
        creditService.hold(userId, 5, CreditTxType.POST_FEE, null);

        // then
        assertThat(balanceOf(userId)).isEqualTo(20);
        assertThat(heldOf(userId)).isEqualTo(5);
        assertThat(ledgerCountOf(userId)).isZero();
    }

    @Test
    void hold_whenAvailableNotEnough_throwsInsufficientBalance() {
        // given: tổng 10 nhưng đã giữ 8, khả dụng chỉ còn 2
        long userId = insertUser(10, 8);

        // when
        Throwable thrown = catchThrowable(() -> creditService.hold(userId, 5, CreditTxType.POST_FEE, null));

        // then
        assertBusinessError(thrown, WalletErrorCode.WALLET_INSUFFICIENT_BALANCE);
        assertThat(heldOf(userId)).isEqualTo(8);
    }

    @Test
    void hold_whenAmountNotPositive_throwsInvalidAmount() {
        // given
        long userId = insertUser(20, 0);

        // when
        Throwable thrown = catchThrowable(() -> creditService.hold(userId, 0, CreditTxType.POST_FEE, null));

        // then
        assertBusinessError(thrown, WalletErrorCode.WALLET_INVALID_AMOUNT);
        assertThat(heldOf(userId)).isZero();
    }

    @Test
    void hold_whenUserMissing_throwsWalletNotFound() {
        // given: không tạo user nào

        // when
        Throwable thrown = catchThrowable(() -> creditService.hold(MISSING_USER_ID, 5, CreditTxType.POST_FEE, null));

        // then
        assertBusinessError(thrown, WalletErrorCode.WALLET_NOT_FOUND);
    }

    @Test
    void hold_whenTwoThreadsRaceForLastCredit_onlyOneSucceeds() throws Exception {
        // given: ví chỉ đủ cho đúng một lần giữ 5
        long userId = insertUser(5, 0);
        CountDownLatch bothReady = new CountDownLatch(2);
        CountDownLatch startSignal = new CountDownLatch(1);
        Callable<Throwable> holdTask = () -> {
            bothReady.countDown();
            startSignal.await();
            try {
                // Gọi qua bean thật: mỗi luồng có transaction riêng, tranh nhau dòng users bằng lockById.
                creditService.hold(userId, 5, CreditTxType.POST_FEE, null);
                return null;
            } catch (BusinessException ex) {
                return ex;
            }
        };

        // when: hai luồng cùng bắt đầu một lúc
        ExecutorService executor = Executors.newFixedThreadPool(2);
        List<Throwable> results = new ArrayList<>();
        try {
            Future<Throwable> first = executor.submit(holdTask);
            Future<Throwable> second = executor.submit(holdTask);
            bothReady.await();
            startSignal.countDown();
            results.add(first.get(30, TimeUnit.SECONDS));
            results.add(second.get(30, TimeUnit.SECONDS));
        } finally {
            executor.shutdownNow();
        }

        // then: đúng một thành công, một bị từ chối, held cuối cùng là 5 chứ không phải 10
        List<Throwable> failures =
                results.stream().filter(result -> result != null).toList();
        assertThat(failures).hasSize(1);
        assertBusinessError(failures.get(0), WalletErrorCode.WALLET_INSUFFICIENT_BALANCE);
        assertThat(heldOf(userId)).isEqualTo(5);
    }

    // ---------- release ----------

    @Test
    void release_whenHeldEnough_decreasesHeldAndKeepsBalance() {
        // given
        long userId = insertUser(20, 5);

        // when
        creditService.release(userId, 5, CreditTxType.POST_FEE, null);

        // then
        assertThat(heldOf(userId)).isZero();
        assertThat(balanceOf(userId)).isEqualTo(20);
        assertThat(ledgerCountOf(userId)).isZero();
    }

    @Test
    void release_whenAmountExceedsHeld_throwsHeldInsufficient() {
        // given
        long userId = insertUser(20, 3);

        // when
        Throwable thrown = catchThrowable(() -> creditService.release(userId, 5, CreditTxType.POST_FEE, null));

        // then
        assertBusinessError(thrown, WalletErrorCode.WALLET_HELD_INSUFFICIENT);
        assertThat(heldOf(userId)).isEqualTo(3);
    }

    // ---------- chargeHeld ----------

    @Test
    void chargeHeld_whenHeldEnough_decreasesBalanceAndHeldAndWritesLedger() {
        // given: có bài thật vì credit_ledger.item_id là khoá ngoại sang items
        long userId = insertUser(20, 5);
        long itemId = insertItem(userId);

        // when
        creditService.chargeHeld(userId, 5, CreditTxType.POST_FEE, itemId);

        // then
        assertThat(balanceOf(userId)).isEqualTo(15);
        assertThat(heldOf(userId)).isZero();
        Map<String, Object> ledgerRow = jdbc.queryForMap(
                "SELECT amount, type, balance_after, item_id FROM credit_ledger WHERE user_id = ?", userId);
        assertThat(ledgerRow.get("amount")).isEqualTo(-5);
        assertThat(ledgerRow.get("type")).isEqualTo("POST_FEE");
        assertThat(ledgerRow.get("balance_after")).isEqualTo(15L);
        assertThat(ledgerRow.get("item_id")).isEqualTo(itemId);
    }

    @Test
    void chargeHeld_whenAmountExceedsHeld_throwsHeldInsufficientAndWritesNoLedger() {
        // given
        long userId = insertUser(20, 3);

        // when
        Throwable thrown = catchThrowable(() -> creditService.chargeHeld(userId, 5, CreditTxType.POST_FEE, null));

        // then
        assertBusinessError(thrown, WalletErrorCode.WALLET_HELD_INSUFFICIENT);
        assertThat(balanceOf(userId)).isEqualTo(20);
        assertThat(ledgerCountOf(userId)).isZero();
    }

    // ---------- spend ----------

    @Test
    void spend_whenAvailableEnough_decreasesBalanceAndWritesLedgerKeepingHeld() {
        // given
        long userId = insertUser(20, 5);

        // when
        creditService.spend(userId, 10, CreditTxType.AI_SEARCH_FEE, null);

        // then
        assertThat(balanceOf(userId)).isEqualTo(10);
        assertThat(heldOf(userId)).isEqualTo(5);
        Map<String, Object> ledgerRow =
                jdbc.queryForMap("SELECT amount, type, balance_after FROM credit_ledger WHERE user_id = ?", userId);
        assertThat(ledgerRow.get("amount")).isEqualTo(-10);
        assertThat(ledgerRow.get("type")).isEqualTo("AI_SEARCH_FEE");
        assertThat(ledgerRow.get("balance_after")).isEqualTo(10L);
    }

    @Test
    void spend_whenOnlyHeldCreditLeft_throwsInsufficientBalance() {
        // given: 10 Credit nhưng 8 đang giữ cho bài khác, chỉ khả dụng 2
        long userId = insertUser(10, 8);

        // when
        Throwable thrown = catchThrowable(() -> creditService.spend(userId, 5, CreditTxType.BOOST_FEE, null));

        // then
        assertBusinessError(thrown, WalletErrorCode.WALLET_INSUFFICIENT_BALANCE);
        assertThat(balanceOf(userId)).isEqualTo(10);
        assertThat(ledgerCountOf(userId)).isZero();
    }

    // ---------- type sai ----------

    @Test
    void holdAndSpend_whenTypeNotAllowed_throwIllegalArgumentAndKeepWallet() {
        // given
        long userId = insertUser(20, 0);

        // when
        Throwable spendWithPostFee = catchThrowable(() -> creditService.spend(userId, 5, CreditTxType.POST_FEE, null));
        Throwable holdWithRenewFee = catchThrowable(() -> creditService.hold(userId, 5, CreditTxType.RENEW_FEE, null));

        // then
        assertThat(spendWithPostFee).isInstanceOf(IllegalArgumentException.class);
        assertThat(holdWithRenewFee).isInstanceOf(IllegalArgumentException.class);
        assertThat(balanceOf(userId)).isEqualTo(20);
        assertThat(heldOf(userId)).isZero();
    }

    // ---------- getWallet ----------

    @Test
    void getWallet_whenUserExists_returnsBalanceHeldAndAvailable() {
        // given
        long userId = insertUser(20, 5);

        // when
        WalletResponse wallet = creditService.getWallet(userId);

        // then
        assertThat(wallet.creditBalance()).isEqualTo(20);
        assertThat(wallet.heldCredit()).isEqualTo(5);
        assertThat(wallet.available()).isEqualTo(15);
        assertThat(wallet.hasToppedUp()).isFalse();
    }

    @Test
    void getWallet_whenUserMissing_throwsWalletNotFound() {
        // given: không tạo user nào

        // when
        Throwable thrown = catchThrowable(() -> creditService.getWallet(MISSING_USER_ID));

        // then
        assertBusinessError(thrown, WalletErrorCode.WALLET_NOT_FOUND);
    }

    // ---------- hàm hỗ trợ ----------

    private void assertBusinessError(Throwable thrown, WalletErrorCode expected) {
        assertThat(thrown)
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(expected));
    }

    private long insertUser(long creditBalance, long heldCredit) {
        // Email và phone có unique index nên mỗi user một giá trị riêng.
        int sequence = PHONE_SEQUENCE.incrementAndGet();
        String email = "wallet-it-" + sequence + "-" + System.nanoTime() + "@test.local";
        String phone = String.format("0999%06d", sequence);
        Long userId = jdbc.queryForObject(
                "INSERT INTO users (email, password_hash, full_name, phone, status, credit_balance, held_credit)"
                        + " VALUES (?, 'not-a-real-hash', 'Wallet IT', ?, 'ACTIVE', ?, ?) RETURNING id",
                Long.class,
                email,
                phone,
                creditBalance,
                heldCredit);
        createdUserIds.add(userId);
        return userId;
    }

    // Mượn một danh mục và một khu vực có sẵn từ seed (không tạo thêm, không xoá) để thoả khoá ngoại của items.
    private long insertItem(long donorId) {
        Long categoryId = jdbc.queryForObject("SELECT id FROM item_categories ORDER BY id LIMIT 1", Long.class);
        Long areaId = jdbc.queryForObject("SELECT id FROM areas WHERE level = 2 ORDER BY id LIMIT 1", Long.class);
        return jdbc.queryForObject(
                "INSERT INTO items (donor_id, category_id, area_id, offer_type, title, description, condition)"
                        + " VALUES (?, ?, ?, 'GIVE', 'Wallet IT item', 'Mô tả', 'NEW') RETURNING id",
                Long.class,
                donorId,
                categoryId,
                areaId);
    }

    private long balanceOf(long userId) {
        return jdbc.queryForObject("SELECT credit_balance FROM users WHERE id = ?", Long.class, userId);
    }

    private long heldOf(long userId) {
        return jdbc.queryForObject("SELECT held_credit FROM users WHERE id = ?", Long.class, userId);
    }

    private long ledgerCountOf(long userId) {
        return jdbc.queryForObject("SELECT count(*) FROM credit_ledger WHERE user_id = ?", Long.class, userId);
    }
}
