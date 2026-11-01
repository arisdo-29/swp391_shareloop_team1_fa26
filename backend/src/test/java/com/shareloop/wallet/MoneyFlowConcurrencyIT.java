package com.shareloop.wallet;

import static org.assertj.core.api.Assertions.assertThat;

import com.shareloop.common.exception.BusinessException;
import com.shareloop.item.entity.OfferType;
import com.shareloop.setting.ConfigKey;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/**
 * Các ca tiền chạy đồng thời (Issue #136): mỗi luồng gọi service qua bean thật nên có transaction riêng, không dùng
 * @Transactional ở lớp test. Sau mỗi ca gọi {@code assertWalletConsistent}. Mỗi ca có @Timeout(30) để không treo CI.
 */
class MoneyFlowConcurrencyIT extends MoneyFlowTestBase {

    @Test
    @Timeout(30)
    void twoSwapPostsAtOnce_whenWalletHasExactlyOnePostFee_onlyOneSucceedsAndOtherGets402() throws Exception {
        // given: ví có đúng POST_FEE Credit; mỗi luồng có bộ ảnh riêng
        int postFee = fee(ConfigKey.POST_FEE);
        long userId = createUser();
        grantCredit(userId, postFee);
        List<String> firstImages = uploadedImages(userId);
        List<String> secondImages = uploadedImages(userId);

        // when
        List<Outcome> outcomes = runConcurrently(List.of(
                () -> createItem(userId, OfferType.SWAP, firstImages),
                () -> createItem(userId, OfferType.SWAP, secondImages)));

        // then: đúng một luồng thành công, luồng kia bị 402 và bài của nó không được lưu
        assertThat(countSucceeded(outcomes)).isEqualTo(1);
        Outcome loser = outcomes.stream()
                .filter(outcome -> !outcome.succeeded())
                .findFirst()
                .orElseThrow();
        assertInsufficientBalance(loser.error());
        assertThat(heldOf(userId)).isEqualTo(postFee);
        assertThat(balanceOf(userId)).isEqualTo(postFee);
        assertThat(heldItemCount(userId)).isEqualTo(1);
        assertWalletConsistent(userId);
    }

    @Test
    @Timeout(30)
    void renewBoostAndPostAtOnce_whenWalletHasTwoFees_atMostTwoSucceedAndWalletStaysConsistent() throws Exception {
        // given: ví có 2 × phí; ba thao tác đều cần một phí
        int postFee = fee(ConfigKey.POST_FEE);
        int renewFee = fee(ConfigKey.RENEW_FEE);
        int boostFee = fee(ConfigKey.BOOST_FEE);
        assertThat(List.of(renewFee, boostFee)).containsOnly(postFee);
        long userId = createUser();
        grantCredit(userId, 2 * postFee);
        long renewItemId = insertApprovedSwapItem(userId);
        long boostItemId = insertApprovedItem(userId);
        List<String> images = uploadedImages(userId);

        // when
        List<Outcome> outcomes = runConcurrently(List.of(
                () -> renewService.renew(renewItemId, userId),
                () -> boostService.boost(boostItemId, userId),
                () -> createItem(userId, OfferType.SWAP, images)));

        // then: ví đủ cho đúng hai thao tác; thao tác thứ ba bị 402
        assertThat(countSucceeded(outcomes)).isLessThanOrEqualTo(2);
        assertThat(countSucceeded(outcomes)).isEqualTo(2);
        outcomes.stream()
                .filter(outcome -> !outcome.succeeded())
                .forEach(outcome -> assertInsufficientBalance(outcome.error()));

        // số dư và khoản giữ khớp đúng với những thao tác đã thành công
        long spendRows = ledgerCount(userId, "RENEW_FEE") + ledgerCount(userId, "BOOST_FEE");
        assertThat(balanceOf(userId)).isEqualTo(2L * postFee - spendRows * postFee);
        assertThat(heldOf(userId)).isEqualTo(heldItemCount(userId) * (long) postFee);
        assertThat(spendRows + heldItemCount(userId)).isEqualTo(2);
        assertThat(balanceOf(userId)).isGreaterThanOrEqualTo(0);
        assertWalletConsistent(userId);
    }

    @Test
    @Timeout(30)
    void sameManualTopUpApprovedFromTwoThreads_creditsOnlyOnce() throws Exception {
        // given: một đơn nạp MANUAL đã báo chuyển khoản
        long userId = createUser();
        int credits = minTopUpCredits();
        String orderCode = createSubmittedOrder(userId, credits);

        // when: hai Admin cùng bấm duyệt
        List<Outcome> outcomes = runConcurrently(List.of(
                () -> manualTopUpService.approve(adminId(), orderCode, "admin một"),
                () -> manualTopUpService.approve(adminId(), orderCode, "admin hai")));

        // then: không luồng nào hỏng (duyệt lần hai là idempotent) và chỉ cộng một lần
        assertThat(outcomes).allMatch(Outcome::succeeded);
        assertThat(balanceOf(userId)).isEqualTo(credits);
        assertThat(ledgerCount(userId, "TOP_UP")).isEqualTo(1);
        assertWalletConsistent(userId);
    }

    @Test
    @Timeout(30)
    void approveAndRejectSamePostAtOnce_exactlyOneWinsAndWalletMatchesTheWinner() throws Exception {
        // given: bài SWAP đang chờ duyệt, giữ POST_FEE
        int postFee = fee(ConfigKey.POST_FEE);
        long userId = createUser();
        grantCredit(userId, 3 * postFee);
        long itemId = createItem(userId, OfferType.SWAP);

        // when
        List<Outcome> outcomes = runConcurrently(List.of(() -> approveItem(itemId), () -> rejectItem(itemId)));

        // then: đúng một thao tác thành công, thao tác kia bị từ chối bằng lỗi nghiệp vụ
        assertThat(countSucceeded(outcomes)).isEqualTo(1);
        Outcome loser = outcomes.stream()
                .filter(outcome -> !outcome.succeeded())
                .findFirst()
                .orElseThrow();
        assertThat(loser.error()).isInstanceOf(BusinessException.class);

        // ví khớp với thao tác thắng
        boolean approveWon = outcomes.get(0).succeeded();
        assertThat(heldOf(userId)).isZero();
        if (approveWon) {
            assertThat(statusOf(itemId)).isEqualTo("APPROVED");
            assertThat(balanceOf(userId)).isEqualTo(2L * postFee);
            assertThat(ledgerCount(userId, "POST_FEE")).isEqualTo(1);
        } else {
            assertThat(statusOf(itemId)).isEqualTo("REJECTED");
            assertThat(balanceOf(userId)).isEqualTo(3L * postFee);
            assertThat(ledgerCount(userId, "POST_FEE")).isZero();
        }
        assertWalletConsistent(userId);
    }

    private long heldItemCount(long userId) {
        return jdbc.sql("SELECT count(*) FROM items WHERE donor_id = :id AND fee_state = 'HELD' AND pending_fee > 0")
                .param("id", userId)
                .query(Long.class)
                .single();
    }
}
