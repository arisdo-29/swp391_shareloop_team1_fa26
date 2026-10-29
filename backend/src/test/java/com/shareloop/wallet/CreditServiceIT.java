package com.shareloop.wallet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import com.shareloop.common.api.PageResponse;
import com.shareloop.common.exception.BusinessException;
import com.shareloop.setting.ConfigKey;
import com.shareloop.setting.service.ConfigService;
import com.shareloop.support.IntegrationTest;
import com.shareloop.wallet.dto.WalletHistoryCounts;
import com.shareloop.wallet.dto.WalletResponse;
import com.shareloop.wallet.dto.WalletTransactionPage;
import com.shareloop.wallet.dto.WalletTransactionResponse;
import com.shareloop.wallet.entity.CreditTxType;
import com.shareloop.wallet.service.CreditService;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
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
    ConfigService configService;

    @Autowired
    JdbcTemplate jdbc;

    private final List<Long> createdUserIds = new ArrayList<>();

    // Thứ tự xoá: credit_ledger tham chiếu payment_orders, items; các bảng đó tham chiếu users nên phải xoá trước.
    // Chỉ xoá dữ liệu do test tạo ra.
    @AfterEach
    void cleanUp() {
        for (Long userId : createdUserIds) {
            jdbc.update("DELETE FROM credit_ledger WHERE user_id = ?", userId);
            jdbc.update("DELETE FROM payment_orders WHERE user_id = ?", userId);
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

    // ---------- chargeAiSearch ----------

    @Test
    void chargeAiSearch_whenAvailableEnough_decreasesBalanceByConfiguredFeeAndReturnsLedgerId() {
        // given
        long aiFee = aiSearchFee();
        long userId = insertUser(20, 0);

        // when
        long ledgerId = creditService.chargeAiSearch(userId);

        // then
        assertThat(balanceOf(userId)).isEqualTo(20 - aiFee);
        Map<String, Object> ledgerRow = jdbc.queryForMap(
                "SELECT user_id, amount, type, balance_after FROM credit_ledger WHERE id = ?", ledgerId);
        assertThat(ledgerRow.get("user_id")).isEqualTo(userId);
        assertThat(ledgerRow.get("amount")).isEqualTo((int) -aiFee);
        assertThat(ledgerRow.get("type")).isEqualTo("AI_SEARCH_FEE");
        assertThat(ledgerRow.get("balance_after")).isEqualTo(20 - aiFee);
    }

    @Test
    void chargeAiSearch_whenAvailableNotEnough_throwsInsufficientBalance() {
        // given: thiếu đúng 1 Credit so với mức phí
        long aiFee = aiSearchFee();
        long userId = insertUser(aiFee - 1, 0);

        // when
        Throwable thrown = catchThrowable(() -> creditService.chargeAiSearch(userId));

        // then
        assertBusinessError(thrown, WalletErrorCode.WALLET_INSUFFICIENT_BALANCE);
        assertThat(balanceOf(userId)).isEqualTo(aiFee - 1);
        assertThat(ledgerCountOf(userId)).isZero();
    }

    // ---------- refundAiSearch ----------

    @Test
    void refundAiSearch_whenChargeNotRefunded_restoresBalanceAndWritesPositiveLedger() {
        // given
        long aiFee = aiSearchFee();
        long userId = insertUser(20, 0);
        long ledgerId = creditService.chargeAiSearch(userId);

        // when
        creditService.refundAiSearch(userId, ledgerId);

        // then: số dư về lại như cũ, có thêm một dòng dương ghi id dòng gốc
        assertThat(balanceOf(userId)).isEqualTo(20);
        Map<String, Object> refundRow = jdbc.queryForMap(
                "SELECT amount, type, balance_after, note FROM credit_ledger WHERE user_id = ? AND amount > 0", userId);
        assertThat(refundRow.get("amount")).isEqualTo((int) aiFee);
        assertThat(refundRow.get("type")).isEqualTo("AI_SEARCH_FEE");
        assertThat(refundRow.get("balance_after")).isEqualTo(20L);
        assertThat(refundRow.get("note")).isEqualTo("Hoàn phí AI #" + ledgerId);
    }

    @Test
    void refundAiSearch_whenAlreadyRefunded_throwsAlreadyRefunded() {
        // given: dòng phí đã được hoàn một lần
        long userId = insertUser(20, 0);
        long ledgerId = creditService.chargeAiSearch(userId);
        creditService.refundAiSearch(userId, ledgerId);

        // when
        Throwable thrown = catchThrowable(() -> creditService.refundAiSearch(userId, ledgerId));

        // then: không cộng thêm lần nữa
        assertBusinessError(thrown, WalletErrorCode.WALLET_AI_FEE_ALREADY_REFUNDED);
        assertThat(balanceOf(userId)).isEqualTo(20);
        assertThat(ledgerCountOf(userId)).isEqualTo(2);
    }

    @Test
    void refundAiSearch_whenLedgerOfOtherUser_throwsAiFeeNotFound() {
        // given: owner bị trừ phí, người khác xin hoàn dòng đó
        long ownerId = insertUser(20, 0);
        long otherUserId = insertUser(0, 0);
        long ledgerId = creditService.chargeAiSearch(ownerId);

        // when
        Throwable thrown = catchThrowable(() -> creditService.refundAiSearch(otherUserId, ledgerId));

        // then
        assertBusinessError(thrown, WalletErrorCode.WALLET_AI_FEE_NOT_FOUND);
        assertThat(balanceOf(otherUserId)).isZero();
        assertThat(ledgerCountOf(otherUserId)).isZero();
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

    // ---------- topUp ----------

    @Test
    void topUp_whenOrderPending_addsCreditWritesLedgerAndSetsHasToppedUp() {
        // given
        long userId = insertUser(5, 2);
        long orderId = insertPaymentOrder(userId, 20, "PENDING");

        // when
        WalletResponse wallet = creditService.topUp(userId, orderId);

        // then: số dư cộng 20, held giữ nguyên, cờ đã nạp bật, đơn chốt SUCCESS
        assertThat(wallet.creditBalance()).isEqualTo(25);
        assertThat(wallet.heldCredit()).isEqualTo(2);
        assertThat(wallet.hasToppedUp()).isTrue();
        assertThat(balanceOf(userId)).isEqualTo(25);
        assertThat(hasToppedUpOf(userId)).isTrue();
        Map<String, Object> orderRow = jdbc.queryForMap(
                "SELECT status, signature_verified, paid_at FROM payment_orders WHERE id = ?", orderId);
        assertThat(orderRow.get("status")).isEqualTo("SUCCESS");
        assertThat(orderRow.get("signature_verified")).isEqualTo(true);
        assertThat(orderRow.get("paid_at")).isNotNull();
        Map<String, Object> ledgerRow = jdbc.queryForMap(
                "SELECT amount, type, balance_after, payment_order_id FROM credit_ledger WHERE user_id = ?", userId);
        assertThat(ledgerRow.get("amount")).isEqualTo(20);
        assertThat(ledgerRow.get("type")).isEqualTo("TOP_UP");
        assertThat(ledgerRow.get("balance_after")).isEqualTo(25L);
        assertThat(ledgerRow.get("payment_order_id")).isEqualTo(orderId);
    }

    @Test
    void topUp_whenOrderAlreadySuccess_returnsWalletWithoutAddingCredit() {
        // given: đơn đã được cộng ở lần gọi trước
        long userId = insertUser(0, 0);
        long orderId = insertPaymentOrder(userId, 20, "PENDING");
        creditService.topUp(userId, orderId);

        // when: gọi lại cho cùng đơn
        WalletResponse wallet = creditService.topUp(userId, orderId);

        // then: không lỗi, số dư không đổi, vẫn chỉ một dòng ledger
        assertThat(wallet.creditBalance()).isEqualTo(20);
        assertThat(balanceOf(userId)).isEqualTo(20);
        assertThat(ledgerCountOf(userId)).isEqualTo(1);
    }

    @Test
    void topUp_whenOrderExpired_throwsNotPayable() {
        // given
        long userId = insertUser(0, 0);
        long orderId = insertPaymentOrder(userId, 20, "EXPIRED");

        // when
        Throwable thrown = catchThrowable(() -> creditService.topUp(userId, orderId));

        // then
        assertBusinessError(thrown, WalletErrorCode.WALLET_PAYMENT_ORDER_NOT_PAYABLE);
        assertThat(balanceOf(userId)).isZero();
        assertThat(ledgerCountOf(userId)).isZero();
    }

    @Test
    void topUp_whenOrderOfOtherUser_throwsOrderNotFound() {
        // given: đơn thuộc về owner, nhưng người khác gọi topUp
        long ownerId = insertUser(0, 0);
        long otherUserId = insertUser(0, 0);
        long orderId = insertPaymentOrder(ownerId, 20, "PENDING");

        // when
        Throwable thrown = catchThrowable(() -> creditService.topUp(otherUserId, orderId));

        // then
        assertBusinessError(thrown, WalletErrorCode.WALLET_PAYMENT_ORDER_NOT_FOUND);
        assertThat(balanceOf(otherUserId)).isZero();
        assertThat(statusOfOrder(orderId)).isEqualTo("PENDING");
    }

    @Test
    void topUp_whenTwoOrdersConfirmedConcurrently_finalBalanceIncludesBoth() throws Exception {
        // given: một ví, hai đơn khác nhau 10 và 20 Credit
        long userId = insertUser(0, 0);
        long firstOrderId = insertPaymentOrder(userId, 10, "PENDING");
        long secondOrderId = insertPaymentOrder(userId, 20, "PENDING");

        // when: hai luồng cùng cộng tiền vào một ví một lúc
        List<Throwable> failures = runConcurrently(
                () -> creditService.topUp(userId, firstOrderId), () -> creditService.topUp(userId, secondOrderId));

        // then: không lần nào bị mất; nhờ khoá dòng users, số dư cuối là 10 + 20 chứ không phải 10 hoặc 20
        assertThat(failures).isEmpty();
        assertThat(balanceOf(userId)).isEqualTo(30);
        assertThat(ledgerCountOf(userId)).isEqualTo(2);
        Long lastBalanceAfter = jdbc.queryForObject(
                "SELECT max(balance_after) FROM credit_ledger WHERE user_id = ?", Long.class, userId);
        assertThat(lastBalanceAfter).isEqualTo(30L);
    }

    // ---------- getTransactions ----------

    @Test
    void getTransactions_returnsOnlyOwnEntriesNewestFirst() {
        // given: hai dòng của user, một dòng của người khác
        long userId = insertUser(0, 0);
        long otherUserId = insertUser(0, 0);
        insertLedger(userId, -5, "AI_SEARCH_FEE", "2026-10-01T08:00:00Z");
        insertLedger(userId, -2, "AI_SEARCH_FEE", "2026-10-03T08:00:00Z");
        insertLedger(otherUserId, -5, "AI_SEARCH_FEE", "2026-10-02T08:00:00Z");

        // when
        PageResponse<WalletTransactionResponse> result = creditService.getTransactions(userId, null, null, 0, 20);

        // then
        assertThat(result.totalElements()).isEqualTo(2);
        assertThat(result.items())
                .extracting(WalletTransactionResponse::createdAt)
                .containsExactly(Instant.parse("2026-10-03T08:00:00Z"), Instant.parse("2026-10-01T08:00:00Z"));
    }

    @Test
    void getTransactions_whenFromToGiven_returnsOnlyEntriesInRange() {
        // given: khoảng [01/10, 03/10) gồm dòng ngày 01 và 02, không gồm ngày 03
        long userId = insertUser(0, 0);
        insertLedger(userId, -1, "AI_SEARCH_FEE", "2026-09-30T23:59:59Z");
        insertLedger(userId, -2, "AI_SEARCH_FEE", "2026-10-01T00:00:00Z");
        insertLedger(userId, -3, "AI_SEARCH_FEE", "2026-10-02T12:00:00Z");
        insertLedger(userId, -4, "AI_SEARCH_FEE", "2026-10-03T00:00:00Z");

        // when
        PageResponse<WalletTransactionResponse> result = creditService.getTransactions(
                userId, Instant.parse("2026-10-01T00:00:00Z"), Instant.parse("2026-10-03T00:00:00Z"), 0, 20);

        // then
        assertThat(result.items()).extracting(WalletTransactionResponse::amount).containsExactly(-3, -2);
    }

    @Test
    void getMyTransactions_countsWholeRangeNotJustCurrentPage() {
        // given: trong khoảng có 1 nạp, 2 chi tiêu (phí AI), 1 hoàn phí AI (dương), 1 Admin điều chỉnh âm;
        // ngoài khoảng có thêm 1 dòng nạp và 1 dòng của người khác
        long userId = insertUser(0, 0);
        long otherUserId = insertUser(0, 0);
        insertLedger(userId, 20, "TOP_UP", "2026-10-02T08:00:00Z");
        insertLedger(userId, -5, "AI_SEARCH_FEE", "2026-10-03T08:00:00Z");
        insertLedger(userId, -5, "POST_FEE", "2026-10-04T08:00:00Z");
        insertLedger(userId, 5, "AI_SEARCH_FEE", "2026-10-05T08:00:00Z");
        insertLedger(userId, -3, "ADMIN_ADJUST", "2026-10-06T08:00:00Z");
        insertLedger(userId, 20, "TOP_UP", "2026-09-01T08:00:00Z");
        insertLedger(otherUserId, -5, "POST_FEE", "2026-10-04T08:00:00Z");

        // when: xem trang đầu chỉ 2 dòng
        WalletTransactionPage result = creditService.getMyTransactions(
                userId, Instant.parse("2026-10-01T00:00:00Z"), Instant.parse("2026-11-01T00:00:00Z"), 0, 2);

        // then: items theo trang, nhưng counts tính trên cả 5 dòng trong khoảng
        assertThat(result.items()).hasSize(2);
        assertThat(result.totalElements()).isEqualTo(5);
        assertThat(result.counts()).isEqualTo(new WalletHistoryCounts(5, 1, 2));
    }

    @Test
    void getMyTransactions_whenFromNotBeforeTo_throwsInvalidDateRange() {
        // given
        long userId = insertUser(0, 0);
        Instant sameMoment = Instant.parse("2026-10-01T00:00:00Z");

        // when
        Throwable thrown = catchThrowable(() -> creditService.getMyTransactions(userId, sameMoment, sameMoment, 0, 20));

        // then
        assertBusinessError(thrown, WalletErrorCode.WALLET_INVALID_DATE_RANGE);
    }

    @Test
    void getTransactions_whenFromNotBeforeTo_throwsInvalidDateRange() {
        // given
        long userId = insertUser(0, 0);
        Instant sameMoment = Instant.parse("2026-10-01T00:00:00Z");

        // when
        Throwable thrown = catchThrowable(() -> creditService.getTransactions(userId, sameMoment, sameMoment, 0, 20));

        // then
        assertBusinessError(thrown, WalletErrorCode.WALLET_INVALID_DATE_RANGE);
    }

    // ---------- hàm hỗ trợ ----------

    // Chạy hai việc cùng lúc trên hai luồng; trả về các BusinessException bắt được (rỗng nếu cả hai thành công).
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
            addIfNotNull(failures, first.get(30, TimeUnit.SECONDS));
            addIfNotNull(failures, second.get(30, TimeUnit.SECONDS));
            return failures;
        } finally {
            executor.shutdownNow();
        }
    }

    private Callable<Throwable> waitThenRun(Runnable task, CountDownLatch bothReady, CountDownLatch startSignal) {
        return () -> {
            bothReady.countDown();
            startSignal.await();
            try {
                task.run();
                return null;
            } catch (BusinessException ex) {
                return ex;
            }
        };
    }

    private void addIfNotNull(List<Throwable> failures, Throwable failure) {
        if (failure != null) {
            failures.add(failure);
        }
    }

    // amount_vnd = credits x 1.000 nên credits phải >= 10 để thoả CHECK amount_vnd >= 10000 của DB.
    private long insertPaymentOrder(long userId, int credits, String status) {
        String orderCode = "it-" + UUID.randomUUID().toString().replace("-", "");
        return jdbc.queryForObject(
                "INSERT INTO payment_orders (user_id, order_code, amount_vnd, credits, vnd_per_credit, provider, status)"
                        + " VALUES (?, ?, ?, ?, 1000, 'MOCK', ?) RETURNING id",
                Long.class,
                userId,
                orderCode,
                credits * 1000L,
                credits,
                status);
    }

    // Ghi thẳng dòng ledger với created_at chọn trước để kiểm lọc ngày; balance_after không quan trọng ở đây.
    private void insertLedger(long userId, int amount, String type, String createdAt) {
        jdbc.update(
                "INSERT INTO credit_ledger (user_id, amount, type, balance_after, created_at)"
                        + " VALUES (?, ?, ?, 0, ?::timestamptz)",
                userId,
                amount,
                type,
                createdAt);
    }

    // Đọc mức phí AI đang cấu hình thay vì viết cứng, để test vẫn đúng khi seed đổi giá trị.
    private long aiSearchFee() {
        return configService.getInt(ConfigKey.AI_SEARCH_FEE);
    }

    private boolean hasToppedUpOf(long userId) {
        return jdbc.queryForObject("SELECT has_topped_up FROM users WHERE id = ?", Boolean.class, userId);
    }

    private String statusOfOrder(long orderId) {
        return jdbc.queryForObject("SELECT status FROM payment_orders WHERE id = ?", String.class, orderId);
    }

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
