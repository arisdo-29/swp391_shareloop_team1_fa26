package com.shareloop.wallet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import com.shareloop.item.entity.OfferType;
import com.shareloop.search.service.AiQuotaService.Reservation;
import com.shareloop.setting.ConfigKey;
import com.shareloop.wallet.dto.WalletResponse;
import org.junit.jupiter.api.Test;

/**
 * Đi qua mọi nhánh tiền theo từng bước (Issue #136, BR-P03, BR-P09): nạp, giữ phí, trừ khi duyệt, nhả khi từ chối
 * hoặc gỡ, sửa bài, gia hạn, đẩy bài, phí AI và hoàn phí AI. Sau MỖI ca gọi {@code assertWalletConsistent} để số dư,
 * sổ cái và khoản đang giữ luôn khớp nhau. Mỗi ca dùng user và bài riêng nên chạy độc lập, không phụ thuộc thứ tự.
 *
 * <p>Mọi con số phí đọc từ ConfigService, không viết cứng.
 */
class MoneyFlowIT extends MoneyFlowTestBase {

    // ---------- nạp MANUAL ----------

    @Test
    void manualTopUp_whenAdminApproves_addsExactlyOneTopUpLedgerRow() {
        // given
        long userId = createUser();
        int credits = minTopUpCredits();
        String orderCode = createSubmittedOrder(userId, credits);
        assertThat(balanceOf(userId)).isZero();

        // when
        manualTopUpService.approve(adminId(), orderCode, "Đã nhận tiền");

        // then
        assertThat(balanceOf(userId)).isEqualTo(credits);
        assertThat(ledgerCount(userId)).isEqualTo(1);
        assertThat(latestLedgerAmount(userId, "TOP_UP")).isEqualTo(credits);
        assertThat(creditService.getWallet(userId).hasToppedUp()).isTrue();
        assertWalletConsistent(userId);
    }

    @Test
    void manualTopUp_whenApprovedTwice_doesNotCreditAgainAndRejectAfterApproveIsRefused() {
        // given: đơn đã được duyệt một lần
        long userId = createUser();
        int credits = minTopUpCredits();
        String orderCode = createSubmittedOrder(userId, credits);
        manualTopUpService.approve(adminId(), orderCode, "lần một");

        // when: duyệt lần hai (idempotent) và thử từ chối đơn đã SUCCESS
        manualTopUpService.approve(adminId(), orderCode, "lần hai");
        Throwable rejectError = catchThrowable(() -> manualTopUpService.reject(adminId(), orderCode, "nhầm"));

        // then: ví không đổi, vẫn đúng một dòng TOP_UP
        assertBusinessError(rejectError, WalletErrorCode.WALLET_PAYMENT_ORDER_NOT_PAYABLE);
        assertThat(balanceOf(userId)).isEqualTo(credits);
        assertThat(ledgerCount(userId, "TOP_UP")).isEqualTo(1);
        assertWalletConsistent(userId);
    }

    @Test
    void manualTopUp_whenAdminRejects_leavesWalletAndLedgerUntouched() {
        // given
        long userId = createUser();
        String orderCode = createSubmittedOrder(userId, minTopUpCredits());

        // when
        manualTopUpService.reject(adminId(), orderCode, "Không thấy tiền về");

        // then
        assertThat(balanceOf(userId)).isZero();
        assertThat(ledgerCount(userId)).isZero();
        assertWalletConsistent(userId);
    }

    // ---------- phí đăng ----------

    @Test
    void swapPost_whenCreatedThenApproved_holdsThenChargesPostFeeIntoLedger() {
        // given
        int postFee = fee(ConfigKey.POST_FEE);
        long userId = createUser();
        grantCredit(userId, 3 * postFee);

        // when: đăng bài SWAP -> giữ phí
        long itemId = createItem(userId, OfferType.SWAP);

        // then: mới giữ chỗ, chưa mất tiền, chưa có dòng ledger POST_FEE
        assertThat(heldOf(userId)).isEqualTo(postFee);
        assertThat(balanceOf(userId)).isEqualTo(3L * postFee);
        assertThat(ledgerCount(userId, "POST_FEE")).isZero();
        assertWalletConsistent(userId);

        // when: Admin duyệt -> trừ
        approveItem(itemId);

        // then
        assertThat(heldOf(userId)).isZero();
        assertThat(balanceOf(userId)).isEqualTo(2L * postFee);
        assertThat(ledgerCount(userId, "POST_FEE")).isEqualTo(1);
        assertThat(latestLedgerAmount(userId, "POST_FEE")).isEqualTo(-postFee);
        assertWalletConsistent(userId);
    }

    @Test
    void swapPost_whenRejected_releasesHoldWithoutNewLedgerRow() {
        // given
        int postFee = fee(ConfigKey.POST_FEE);
        long userId = createUser();
        grantCredit(userId, 3 * postFee);
        long itemId = createItem(userId, OfferType.SWAP);
        long ledgerBefore = ledgerCount(userId);

        // when
        rejectItem(itemId);

        // then: nhả khoản giữ, số dư và sổ cái không đổi
        assertThat(heldOf(userId)).isZero();
        assertThat(balanceOf(userId)).isEqualTo(3L * postFee);
        assertThat(ledgerCount(userId)).isEqualTo(ledgerBefore);
        assertThat(feeStateOf(itemId)).isEqualTo("RELEASED");
        assertWalletConsistent(userId);
    }

    @Test
    void swapPost_whenResubmittedAfterReject_holdsPostFeeAgain() {
        // given: bài SWAP bị từ chối, phí đã nhả
        int postFee = fee(ConfigKey.POST_FEE);
        long userId = createUser();
        grantCredit(userId, 3 * postFee);
        long itemId = createItem(userId, OfferType.SWAP);
        rejectItem(itemId);

        // when: gửi lại bài
        editItem(itemId, userId, OfferType.SWAP, "Sách lập trình Java bản sửa");

        // then: giữ lại phí đăng, chưa trừ
        assertThat(heldOf(userId)).isEqualTo(postFee);
        assertThat(feeStateOf(itemId)).isEqualTo("HELD");
        assertThat(ledgerCount(userId, "POST_FEE")).isZero();
        assertWalletConsistent(userId);

        // when: lần này được duyệt
        approveItem(itemId);

        // then: trừ đúng một lần
        assertThat(balanceOf(userId)).isEqualTo(2L * postFee);
        assertThat(ledgerCount(userId, "POST_FEE")).isEqualTo(1);
        assertWalletConsistent(userId);
    }

    @Test
    void givePost_whenWalletIsEmpty_approvalLeavesWalletAndLedgerUnchanged() {
        // given: ví 0 Credit vẫn đăng được bài GIVE (miễn phí đăng)
        long userId = createUser();
        long itemId = createItem(userId, OfferType.GIVE);
        assertThat(heldOf(userId)).isZero();

        // when
        approveItem(itemId);

        // then
        assertThat(statusOf(itemId)).isEqualTo("APPROVED");
        assertThat(feeStateOf(itemId)).isEqualTo("CHARGED");
        assertThat(balanceOf(userId)).isZero();
        assertThat(ledgerCount(userId)).isZero();
        assertWalletConsistent(userId);
    }

    // ---------- sửa sau duyệt ----------

    @Test
    void firstEditAfterApproval_isFreeAndNeverTouchesWallet() {
        // given
        int editFee = fee(ConfigKey.EDIT_FEE);
        long userId = createUser();
        grantCredit(userId, 3 * editFee);
        long itemId = createItem(userId, OfferType.GIVE);
        approveItem(itemId);

        // when
        editItem(itemId, userId, OfferType.GIVE, "Sửa lần một");
        approveItem(itemId);

        // then: không giữ, không trừ, không dòng ledger mới
        assertThat(heldOf(userId)).isZero();
        assertThat(balanceOf(userId)).isEqualTo(3L * editFee);
        assertThat(ledgerCount(userId, "EDIT_FEE")).isZero();
        assertWalletConsistent(userId);
    }

    @Test
    void secondEditAfterApproval_holdsEditFeeThenChargesOnApprove() {
        // given: lượt sửa miễn phí đã dùng
        int editFee = fee(ConfigKey.EDIT_FEE);
        long userId = createUser();
        grantCredit(userId, 3 * editFee);
        long itemId = giveItemWithFreeEditConsumed(userId);

        // when
        editItem(itemId, userId, OfferType.GIVE, "Sửa lần hai");

        // then: giữ EDIT_FEE
        assertThat(heldOf(userId)).isEqualTo(editFee);
        assertThat(balanceOf(userId)).isEqualTo(3L * editFee);
        assertWalletConsistent(userId);

        // when
        approveItem(itemId);

        // then: trừ EDIT_FEE vào sổ cái
        assertThat(heldOf(userId)).isZero();
        assertThat(balanceOf(userId)).isEqualTo(2L * editFee);
        assertThat(latestLedgerAmount(userId, "EDIT_FEE")).isEqualTo(-editFee);
        assertWalletConsistent(userId);
    }

    @Test
    void thirdEditAfterApproval_whenRejected_releasesEditFeeWithoutLedgerRow() {
        // given: đã sửa miễn phí và sửa có phí (được duyệt), bản sửa kế tiếp đang giữ EDIT_FEE
        int editFee = fee(ConfigKey.EDIT_FEE);
        long userId = createUser();
        grantCredit(userId, 3 * editFee);
        long itemId = giveItemWithFreeEditConsumed(userId);
        editItem(itemId, userId, OfferType.GIVE, "Sửa lần hai");
        approveItem(itemId);
        editItem(itemId, userId, OfferType.GIVE, "Sửa lần ba");
        assertThat(heldOf(userId)).isEqualTo(editFee);

        // when
        rejectItem(itemId);

        // then: nhả, chỉ có đúng một dòng EDIT_FEE (của lần hai)
        assertThat(heldOf(userId)).isZero();
        assertThat(balanceOf(userId)).isEqualTo(2L * editFee);
        assertThat(ledgerCount(userId, "EDIT_FEE")).isEqualTo(1);
        assertWalletConsistent(userId);
    }

    @Test
    void editWhilePendingReview_overwritesWithoutHoldingAgain() {
        // given: bản sửa có phí đang chờ duyệt
        int editFee = fee(ConfigKey.EDIT_FEE);
        long userId = createUser();
        grantCredit(userId, 3 * editFee);
        long itemId = giveItemWithFreeEditConsumed(userId);
        editItem(itemId, userId, OfferType.GIVE, "Sửa lần hai");

        // when: sửa đè khi chưa được duyệt
        editItem(itemId, userId, OfferType.GIVE, "Sửa đè lần hai");

        // then: vẫn chỉ giữ một khoản
        assertThat(heldOf(userId)).isEqualTo(editFee);
        assertWalletConsistent(userId);

        // when
        approveItem(itemId);

        // then: chỉ trừ một lần
        assertThat(ledgerCount(userId, "EDIT_FEE")).isEqualTo(1);
        assertThat(balanceOf(userId)).isEqualTo(2L * editFee);
        assertWalletConsistent(userId);
    }

    // ---------- gỡ bài đang giữ phí ----------

    @Test
    void removePendingSwapPost_releasesExactlyThePostFeeHeld() {
        // given
        int postFee = fee(ConfigKey.POST_FEE);
        long userId = createUser();
        grantCredit(userId, 3 * postFee);
        long itemId = createItem(userId, OfferType.SWAP);
        assertThat(heldOf(userId)).isEqualTo(postFee);

        // when
        itemService.remove(itemId, userId);

        // then
        assertThat(heldOf(userId)).isZero();
        assertThat(balanceOf(userId)).isEqualTo(3L * postFee);
        assertThat(ledgerCount(userId, "POST_FEE")).isZero();
        assertThat(feeStateOf(itemId)).isEqualTo("RELEASED");
        assertWalletConsistent(userId);
    }

    @Test
    void removeItemWithPendingPaidEdit_releasesExactlyTheEditFeeHeld() {
        // given: bài đã duyệt, bản sửa có phí đang chờ duyệt
        int editFee = fee(ConfigKey.EDIT_FEE);
        long userId = createUser();
        grantCredit(userId, 3 * editFee);
        long itemId = giveItemWithFreeEditConsumed(userId);
        editItem(itemId, userId, OfferType.GIVE, "Sửa lần hai");
        assertThat(heldOf(userId)).isEqualTo(editFee);

        // when
        itemService.remove(itemId, userId);

        // then
        assertThat(heldOf(userId)).isZero();
        assertThat(balanceOf(userId)).isEqualTo(3L * editFee);
        assertWalletConsistent(userId);
    }

    // ---------- gia hạn và đẩy bài ----------

    @Test
    void renew_chargesRenewFeeImmediatelyIntoLedger() {
        // given
        int renewFee = fee(ConfigKey.RENEW_FEE);
        long userId = createUser();
        grantCredit(userId, 3 * renewFee);
        long itemId = insertApprovedSwapItem(userId);

        // when
        renewService.renew(itemId, userId);

        // then
        assertThat(balanceOf(userId)).isEqualTo(2L * renewFee);
        assertThat(latestLedgerAmount(userId, "RENEW_FEE")).isEqualTo(-renewFee);
        assertWalletConsistent(userId);
    }

    @Test
    void renew_whenBalanceNotEnough_throwsInsufficientAndWalletUnchanged() {
        // given: thiếu đúng 1 Credit
        int renewFee = fee(ConfigKey.RENEW_FEE);
        assertThat(renewFee).isGreaterThan(1);
        long userId = createUser();
        grantCredit(userId, renewFee - 1);
        long itemId = insertApprovedSwapItem(userId);

        // when
        Throwable thrown = catchThrowable(() -> renewService.renew(itemId, userId));

        // then
        assertInsufficientBalance(thrown);
        assertThat(balanceOf(userId)).isEqualTo(renewFee - 1);
        assertThat(ledgerCount(userId, "RENEW_FEE")).isZero();
        assertWalletConsistent(userId);
    }

    @Test
    void renew_whenCreditIsHeldForPendingPost_throwsInsufficientBecauseOnlyAvailableIsUsable() {
        // given: có đúng POST_FEE Credit và toàn bộ đang giữ cho bài SWAP chờ duyệt
        int postFee = fee(ConfigKey.POST_FEE);
        long userId = createUser();
        grantCredit(userId, postFee);
        createItem(userId, OfferType.SWAP);
        long approvedItemId = insertApprovedSwapItem(userId);

        // when
        Throwable thrown = catchThrowable(() -> renewService.renew(approvedItemId, userId));

        // then: khoản đang giữ không bị dùng cho gia hạn
        assertInsufficientBalance(thrown);
        assertThat(balanceOf(userId)).isEqualTo(postFee);
        assertThat(heldOf(userId)).isEqualTo(postFee);
        assertWalletConsistent(userId);
    }

    @Test
    void boost_chargesBoostFeeImmediatelyIntoLedger() {
        // given
        int boostFee = fee(ConfigKey.BOOST_FEE);
        long userId = createUser();
        grantCredit(userId, 3 * boostFee);
        long itemId = insertApprovedItem(userId);

        // when
        boostService.boost(itemId, userId);

        // then
        assertThat(balanceOf(userId)).isEqualTo(2L * boostFee);
        assertThat(latestLedgerAmount(userId, "BOOST_FEE")).isEqualTo(-boostFee);
        assertWalletConsistent(userId);
    }

    @Test
    void boost_whenBalanceNotEnough_throwsInsufficientAndWalletUnchanged() {
        // given
        int boostFee = fee(ConfigKey.BOOST_FEE);
        assertThat(boostFee).isGreaterThan(1);
        long userId = createUser();
        grantCredit(userId, boostFee - 1);
        long itemId = insertApprovedItem(userId);

        // when
        Throwable thrown = catchThrowable(() -> boostService.boost(itemId, userId));

        // then
        assertInsufficientBalance(thrown);
        assertThat(balanceOf(userId)).isEqualTo(boostFee - 1);
        assertThat(ledgerCount(userId, "BOOST_FEE")).isZero();
        assertWalletConsistent(userId);
    }

    // ---------- phí AI ----------

    @Test
    void aiSearch_whenFreeUsesExhausted_chargesAiFeeIntoLedger() {
        // given: user chưa nạp chỉ có 1 lượt dùng thử, đã dùng hết
        int aiFee = fee(ConfigKey.AI_SEARCH_FEE);
        long userId = createUser();
        Reservation freeUse = aiQuotaService.reserve(userId);
        assertThat(freeUse.paid()).isFalse();
        grantCredit(userId, 5 * aiFee);

        // when
        Reservation paidUse = aiQuotaService.reserve(userId);

        // then
        assertThat(paidUse.paid()).isTrue();
        assertThat(balanceOf(userId)).isEqualTo(4L * aiFee);
        assertThat(latestLedgerAmount(userId, "AI_SEARCH_FEE")).isEqualTo(-aiFee);
        assertWalletConsistent(userId);
    }

    @Test
    void aiSearch_whenFreeUsesExhaustedAndNoCredit_throwsInsufficientWithoutLedgerRow() {
        // given
        long userId = createUser();
        aiQuotaService.reserve(userId);

        // when
        Throwable thrown = catchThrowable(() -> aiQuotaService.reserve(userId));

        // then
        assertInsufficientBalance(thrown);
        assertThat(ledgerCount(userId)).isZero();
        assertWalletConsistent(userId);
    }

    @Test
    void aiSearch_whenLlmCallFails_refundsExactlyOnceAndSecondRefundIsBlocked() {
        // given: hết lượt miễn phí, có Credit, và một lượt AI đã bị trừ phí
        int aiFee = fee(ConfigKey.AI_SEARCH_FEE);
        long userId = createUser();
        aiQuotaService.reserve(userId);
        grantCredit(userId, 5 * aiFee);
        Reservation paidUse = aiQuotaService.reserve(userId);

        // when: gọi LLM lỗi -> AiSearchService hoàn phí bằng đúng lời gọi refund này
        aiQuotaService.refund(userId, paidUse);

        // then: một dòng trừ (âm), một dòng hoàn (dương), số dư về như cũ
        assertThat(balanceOf(userId)).isEqualTo(5L * aiFee);
        assertThat(ledgerCount(userId, "AI_SEARCH_FEE")).isEqualTo(2);
        assertThat(latestLedgerAmount(userId, "AI_SEARCH_FEE")).isEqualTo(aiFee);
        assertWalletConsistent(userId);

        // when: hoàn lần hai cho cùng dòng gốc
        long chargeLedgerId = jdbc.sql(
                        "SELECT id FROM credit_ledger WHERE user_id = :id AND type = 'AI_SEARCH_FEE' AND amount < 0")
                .param("id", userId)
                .query(Long.class)
                .single();
        Throwable secondRefund = catchThrowable(() -> creditService.refundAiSearch(userId, chargeLedgerId));

        // then: bị chặn, ví và sổ cái không đổi
        assertBusinessError(secondRefund, WalletErrorCode.WALLET_AI_FEE_ALREADY_REFUNDED);
        assertThat(balanceOf(userId)).isEqualTo(5L * aiFee);
        assertThat(ledgerCount(userId, "AI_SEARCH_FEE")).isEqualTo(2);
        assertWalletConsistent(userId);
    }

    @Test
    void aiQuotaRefund_whenReservationWasPaid_refundsExactChargeAndRefundingAgainIsBlocked() {
        // given
        int aiFee = fee(ConfigKey.AI_SEARCH_FEE);
        long userId = createUser();
        aiQuotaService.reserve(userId);
        grantCredit(userId, 5 * aiFee);
        Reservation paidUse = aiQuotaService.reserve(userId);

        // when
        aiQuotaService.refund(userId, paidUse);
        Throwable secondRefund = catchThrowable(() -> aiQuotaService.refund(userId, paidUse));

        // then
        assertBusinessError(secondRefund, WalletErrorCode.WALLET_AI_FEE_ALREADY_REFUNDED);
        assertThat(balanceOf(userId)).isEqualTo(5L * aiFee);
        assertWalletConsistent(userId);
    }

    // ---------- chuỗi dài trên cùng một user ----------

    @Test
    void longChain_topUpPostEditRenewBoostAiAndRefund_keepsWalletConsistentAfterEveryStep() {
        // given: phí đọc từ cấu hình; nạp đủ rộng để mọi bước đều được
        int postFee = fee(ConfigKey.POST_FEE);
        int editFee = fee(ConfigKey.EDIT_FEE);
        int renewFee = fee(ConfigKey.RENEW_FEE);
        int boostFee = fee(ConfigKey.BOOST_FEE);
        int aiFee = fee(ConfigKey.AI_SEARCH_FEE);
        long userId = createUser();
        long expected = Math.max(minTopUpCredits(), 10 * (postFee + editFee + renewFee + boostFee + aiFee));

        // nạp MANUAL
        topUpManually(userId, (int) expected);
        assertStep(userId, expected, 0);

        // đăng SWAP: giữ phí
        long itemId = createItem(userId, OfferType.SWAP);
        assertStep(userId, expected, postFee);

        // duyệt: trừ phí đăng
        approveItem(itemId);
        expected -= postFee;
        assertStep(userId, expected, 0);

        // gia hạn: trừ ngay
        renewService.renew(itemId, userId);
        expected -= renewFee;
        assertStep(userId, expected, 0);

        // đẩy bài: trừ ngay
        boostService.boost(itemId, userId);
        expected -= boostFee;
        assertStep(userId, expected, 0);

        // sửa lần 1 (miễn phí) rồi duyệt
        editItem(itemId, userId, OfferType.SWAP, "Sửa miễn phí");
        assertStep(userId, expected, 0);
        approveItem(itemId);
        assertStep(userId, expected, 0);

        // sửa lần 2 (có phí): giữ rồi duyệt -> trừ
        editItem(itemId, userId, OfferType.SWAP, "Sửa có phí");
        assertStep(userId, expected, editFee);
        approveItem(itemId);
        expected -= editFee;
        assertStep(userId, expected, 0);

        // sửa lần 3 (có phí): giữ rồi từ chối -> nhả
        editItem(itemId, userId, OfferType.SWAP, "Sửa rồi bị từ chối");
        assertStep(userId, expected, editFee);
        rejectItem(itemId);
        assertStep(userId, expected, 0);

        // AI: user đã nạp có 5 lượt miễn phí mỗi ngày
        int freePerDay = fee(ConfigKey.AI_FREE_PER_DAY);
        for (int i = 0; i < freePerDay; i++) {
            assertThat(aiQuotaService.reserve(userId).paid()).isFalse();
            assertStep(userId, expected, 0);
        }

        // hết lượt miễn phí: trừ phí AI rồi hoàn khi lỗi
        Reservation paidUse = aiQuotaService.reserve(userId);
        assertThat(paidUse.paid()).isTrue();
        assertStep(userId, expected - aiFee, 0);
        aiQuotaService.refund(userId, paidUse);
        assertStep(userId, expected, 0);

        // lần hoàn thứ hai bị chặn, ví không đổi
        Throwable secondRefund = catchThrowable(() -> aiQuotaService.refund(userId, paidUse));
        assertBusinessError(secondRefund, WalletErrorCode.WALLET_AI_FEE_ALREADY_REFUNDED);
        assertStep(userId, expected, 0);

        // ví cuối cùng khớp với cái nhìn của API ví
        WalletResponse wallet = creditService.getWallet(userId);
        assertThat(wallet.creditBalance()).isEqualTo(expected);
        assertThat(wallet.hasToppedUp()).isTrue();
    }

    // Sau mỗi bước: số dư và số đang giữ đúng kỳ vọng, và bốn bất biến của ví đều đúng.
    private void assertStep(long userId, long expectedBalance, long expectedHeld) {
        assertThat(balanceOf(userId)).as("credit_balance sau bước này").isEqualTo(expectedBalance);
        assertThat(heldOf(userId)).as("held_credit sau bước này").isEqualTo(expectedHeld);
        assertWalletConsistent(userId);
    }
}
