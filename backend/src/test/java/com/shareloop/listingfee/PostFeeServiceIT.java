package com.shareloop.listingfee;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import com.shareloop.common.exception.BusinessException;
import com.shareloop.common.exception.ErrorCode;
import com.shareloop.item.entity.OfferType;
import com.shareloop.listingfee.dto.EditQuote;
import com.shareloop.listingfee.service.PostFeeService;
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
 * Test PostFeeService cùng CreditService thật trên PostgreSQL (Testcontainers). Không @Transactional ở lớp test: mỗi
 * lời gọi service tự mở transaction và commit thật, nên dữ liệu tự tạo được dọn trong @AfterEach.
 */
class PostFeeServiceIT extends IntegrationTest {

    // Bằng giá trị post_fee, edit_fee seed trong V202610051200__setting_seed_config.sql.
    private static final int POST_FEE = 5;
    private static final int EDIT_FEE = 5;
    private static final int START_BALANCE = 20;
    private static final AtomicInteger PHONE_SEQUENCE = new AtomicInteger();

    @Autowired
    PostFeeService postFeeService;

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
    void sixthActiveSwapHoldsTenCreditsAndRejectionReleasesTen() {
        long ownerId = insertUser(START_BALANCE, 0);
        for (int index = 0; index < 5; index++) {
            insertItem(ownerId);
        }
        long sixthItemId = insertItem(ownerId);

        postFeeService.holdPostFee(sixthItemId, ownerId);
        assertThat(feeColumnsOf(sixthItemId).get("pending_fee")).isEqualTo(10);
        assertThat(heldOf(ownerId)).isEqualTo(10);

        postFeeService.releaseHeldFee(sixthItemId, "REJECTED");
        assertThat(heldOf(ownerId)).isZero();
        assertThat(balanceOf(ownerId)).isEqualTo(START_BALANCE);
        assertThat(ledgerCountOfItem(sixthItemId)).isZero();
    }

    @Test
    void expiredSwapNoLongerUsesRegularFeeSlot() {
        long ownerId = insertUser(START_BALANCE, 0);
        long expiredItemId = insertItem(ownerId);
        jdbc.update(
                "UPDATE items SET status = 'APPROVED', expire_at = now() - interval '1 day' WHERE id = ?",
                expiredItemId);
        for (int index = 0; index < 4; index++) {
            insertItem(ownerId);
        }
        long nextItemId = insertItem(ownerId);

        postFeeService.holdPostFee(nextItemId, ownerId);

        assertThat(feeColumnsOf(nextItemId).get("pending_fee")).isEqualTo(POST_FEE);
        assertThat(heldOf(ownerId)).isEqualTo(POST_FEE);
    }

    @Test
    void giveToSwapChargesPostFeeOnceWithoutResettingDisplayPeriod() {
        long ownerId = insertUser(START_BALANCE, 0);
        long itemId = insertApprovedItem(ownerId, false);
        Instant originalExpiry = Instant.parse("2030-01-15T10:00:00Z");
        jdbc.update("UPDATE items SET expire_at = ? WHERE id = ?", Timestamp.from(originalExpiry), itemId);

        EditQuote quote = postFeeService.quoteEdit(itemId, ownerId, OfferType.SWAP);
        assertThat(quote.amount()).isEqualTo(POST_FEE);
        postFeeService.holdEditFee(itemId, ownerId, OfferType.SWAP);
        assertThat(heldOf(ownerId)).isEqualTo(POST_FEE);
        postFeeService.chargeHeldFee(itemId);

        assertThat(balanceOf(ownerId)).isEqualTo(START_BALANCE - POST_FEE);
        assertThat(feeColumnsOf(itemId).get("post_fee_paid")).isEqualTo(true);
        assertThat(expireAtOf(itemId)).isEqualTo(originalExpiry);
        jdbc.update("UPDATE items SET offer_type = 'SWAP', desired_item = 'Món đổi' WHERE id = ?", itemId);
        assertThat(postFeeService.quoteEdit(itemId, ownerId, OfferType.GIVE).amount())
                .isZero();
        postFeeService.holdEditFee(itemId, ownerId, OfferType.GIVE);
        postFeeService.chargeHeldFee(itemId);
        jdbc.update("UPDATE items SET offer_type = 'GIVE', desired_item = NULL WHERE id = ?", itemId);
        assertThat(postFeeService.quoteEdit(itemId, ownerId, OfferType.SWAP).amount())
                .isZero();
        assertThat(balanceOf(ownerId)).isEqualTo(START_BALANCE - POST_FEE);
    }

    @Test
    void giveToSwapBeyondBronzeRegularLimitHoldsTenCredits() {
        long ownerId = insertUser(START_BALANCE, 0);
        long giveItemId = insertApprovedItem(ownerId, false);
        for (int index = 0; index < 5; index++) {
            insertItem(ownerId);
        }

        EditQuote quote = postFeeService.quoteEdit(giveItemId, ownerId, OfferType.SWAP);
        assertThat(quote.amount()).isEqualTo(10);
        postFeeService.holdEditFee(giveItemId, ownerId, OfferType.SWAP);
        assertThat(heldOf(ownerId)).isEqualTo(10);

        postFeeService.releaseHeldFee(giveItemId, "REJECTED");
        assertThat(heldOf(ownerId)).isZero();
        assertThat(balanceOf(ownerId)).isEqualTo(START_BALANCE);
    }

    @Test
    void rejectedGiveToSwapConversionStillRequiresPostFeeOnResubmission() {
        long ownerId = insertUser(START_BALANCE, 0);
        long itemId = insertApprovedItem(ownerId, false);
        postFeeService.holdEditFee(itemId, ownerId, OfferType.SWAP);
        jdbc.update("UPDATE items SET offer_type = 'SWAP', desired_item = 'Món đổi' WHERE id = ?", itemId);
        postFeeService.releaseHeldFee(itemId, "REJECTED");
        jdbc.update("UPDATE items SET status = 'REJECTED' WHERE id = ?", itemId);

        EditQuote quote = postFeeService.quoteEdit(itemId, ownerId, OfferType.SWAP);
        assertThat(quote.amount()).isEqualTo(POST_FEE);
        postFeeService.holdEditFee(itemId, ownerId, OfferType.SWAP);
        assertThat(heldOf(ownerId)).isEqualTo(POST_FEE);
    }

    @Test
    void pendingSwapChangedToGiveReleasesHeldPostFee() {
        long ownerId = insertUser(START_BALANCE, 0);
        long itemId = insertItem(ownerId);
        postFeeService.holdPostFee(itemId, ownerId);

        postFeeService.holdEditFee(itemId, ownerId, OfferType.GIVE);

        assertThat(heldOf(ownerId)).isZero();
        assertThat(feeColumnsOf(itemId).get("pending_fee")).isEqualTo(0);
        assertThat(feeColumnsOf(itemId).get("fee_state")).isEqualTo("HELD");
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

    @Test
    void holdPostFee_whenPostFeeConfiguredZero_marksHeldWithoutTouchingWallet() {
        // given: Admin cấu hình phí đăng = 0; ví không có Credit nào
        setConfig("post_fee", "0");
        long ownerId = insertUser(0, 0);
        long itemId = insertItem(ownerId);

        // when
        postFeeService.holdPostFee(itemId, ownerId);

        // then
        assertThat(heldOf(ownerId)).isZero();
        Map<String, Object> fee = feeColumnsOf(itemId);
        assertThat(fee.get("fee_state")).isEqualTo("HELD");
        assertThat(fee.get("pending_fee")).isEqualTo(0);
        assertThat(fee.get("pending_fee_type")).isEqualTo("POST");
    }

    @Test
    void holdPostFee_whenGiveItemAndZeroBalance_marksHeldWithZeroWithoutTouchingWallet() {
        // given: bài Cho tặng miễn phí đăng; ví không có Credit nào
        long ownerId = insertUser(0, 0);
        long itemId = insertItem(ownerId, "GIVE");

        // when
        postFeeService.holdPostFee(itemId, ownerId);

        // then: vẫn HELD loại POST để lúc duyệt chargeHeldFee chạy được, nhưng ví không đổi
        assertThat(heldOf(ownerId)).isZero();
        assertThat(balanceOf(ownerId)).isZero();
        Map<String, Object> fee = feeColumnsOf(itemId);
        assertThat(fee.get("fee_state")).isEqualTo("HELD");
        assertThat(fee.get("pending_fee")).isEqualTo(0);
        assertThat(fee.get("pending_fee_type")).isEqualTo("POST");
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
    void chargeHeldFee_whenPostFeeApproved_setsExpireAtToNowPlusDisplayDays() {
        // given: đổi post_display_days = 10 (khác mặc định 30) để chắc số ngày đọc từ cấu hình
        setConfig("post_display_days", "10");
        long ownerId = insertUser(START_BALANCE, 0);
        long itemId = insertItem(ownerId);
        postFeeService.holdPostFee(itemId, ownerId);
        Instant before = Instant.now().truncatedTo(ChronoUnit.SECONDS);

        // when
        postFeeService.chargeHeldFee(itemId);

        // then: expire_at = lúc duyệt + 10 ngày; lúc duyệt nằm giữa before và after
        Instant after = Instant.now().plusSeconds(1);
        assertThat(expireAtOf(itemId)).isBetween(before.plus(10, ChronoUnit.DAYS), after.plus(10, ChronoUnit.DAYS));
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

    // ---------- quoteEdit ----------

    @Test
    void quoteEdit_whenNeverApprovedAndPostFeeHeld_returnsFreeNotYetApproved() {
        // given: bài mới đang chờ duyệt lần đầu, phí đăng đang giữ
        long ownerId = insertUser(START_BALANCE, POST_FEE);
        long itemId = insertHeldItem(ownerId, POST_FEE);

        // when
        EditQuote quote = postFeeService.quoteEdit(itemId, ownerId);

        // then
        assertThat(quote).isEqualTo(new EditQuote(true, 0, EditQuote.Reason.NOT_YET_APPROVED));
    }

    @Test
    void quoteEdit_whenRejectedBeforeFirstApproval_returnsRepostFee() {
        // given: bài bị từ chối ở lần duyệt đầu, phí đăng đã nhả
        long ownerId = insertUser(START_BALANCE, 0);
        long itemId = insertRejectedItem(ownerId);

        // when
        EditQuote quote = postFeeService.quoteEdit(itemId, ownerId);

        // then
        assertThat(quote).isEqualTo(new EditQuote(false, POST_FEE, EditQuote.Reason.REPOST_FEE));
    }

    @Test
    void quoteEdit_whenGiveItemRejectedBeforeFirstApproval_returnsFreeRepostFee() {
        // given: bài Cho tặng bị từ chối ở lần duyệt đầu (phí đăng đã giữ là 0)
        long ownerId = insertUser(0, 0);
        long itemId = insertRejectedItem(ownerId, "GIVE", 0);

        // when
        EditQuote quote = postFeeService.quoteEdit(itemId, ownerId);

        // then: gửi lại bài GIVE vẫn miễn phí, giữ lý do REPOST_FEE
        assertThat(quote).isEqualTo(new EditQuote(true, 0, EditQuote.Reason.REPOST_FEE));
    }

    @Test
    void quoteEdit_whenApprovedAndFreeEditUnused_returnsFirstFreeEdit() {
        // given
        long ownerId = insertUser(START_BALANCE, 0);
        long itemId = insertApprovedItem(ownerId, false);

        // when
        EditQuote quote = postFeeService.quoteEdit(itemId, ownerId);

        // then
        assertThat(quote).isEqualTo(new EditQuote(true, 0, EditQuote.Reason.FIRST_FREE_EDIT));
    }

    @Test
    void quoteEdit_whenFreeEditUsed_returnsPaidEditFromConfig() {
        // given
        long ownerId = insertUser(START_BALANCE, 0);
        long itemId = insertApprovedItem(ownerId, true);

        // when
        EditQuote quote = postFeeService.quoteEdit(itemId, ownerId);

        // then
        assertThat(quote).isEqualTo(new EditQuote(false, EDIT_FEE, EditQuote.Reason.PAID_EDIT));
    }

    @Test
    void quoteEdit_whenEditPendingReview_returnsFreeOverwrite() {
        // given: bản sửa có phí đã gửi và đang chờ duyệt
        long ownerId = insertUser(START_BALANCE, 0);
        long itemId = insertApprovedItem(ownerId, true);
        postFeeService.holdEditFee(itemId, ownerId);

        // when
        EditQuote quote = postFeeService.quoteEdit(itemId, ownerId);

        // then
        assertThat(quote).isEqualTo(new EditQuote(true, 0, EditQuote.Reason.PENDING_REVIEW_OVERWRITE));
    }

    @Test
    void quoteEdit_whenCallerIsNotOwner_throwsNotOwner() {
        // given
        long ownerId = insertUser(START_BALANCE, 0);
        long otherUserId = insertUser(START_BALANCE, 0);
        long itemId = insertApprovedItem(ownerId, true);

        // when
        Throwable thrown = catchThrowable(() -> postFeeService.quoteEdit(itemId, otherUserId));

        // then
        assertBusinessError(thrown, ListingFeeErrorCode.LISTINGFEE_NOT_OWNER);
    }

    // ---------- holdEditFee ----------

    @Test
    void holdEditFee_whenPaidEdit_holdsEditFee() {
        // given
        long ownerId = insertUser(START_BALANCE, 0);
        long itemId = insertApprovedItem(ownerId, true);

        // when
        postFeeService.holdEditFee(itemId, ownerId);

        // then
        assertThat(heldOf(ownerId)).isEqualTo(EDIT_FEE);
        assertThat(balanceOf(ownerId)).isEqualTo(START_BALANCE);
        Map<String, Object> fee = feeColumnsOf(itemId);
        assertThat(fee.get("fee_state")).isEqualTo("HELD");
        assertThat(fee.get("pending_fee")).isEqualTo(EDIT_FEE);
        assertThat(fee.get("pending_fee_type")).isEqualTo("EDIT");
    }

    @Test
    void holdEditFee_whenFirstFreeEdit_marksHeldWithZeroAndKeepsFreeEditUnused() {
        // given
        long ownerId = insertUser(START_BALANCE, 0);
        long itemId = insertApprovedItem(ownerId, false);

        // when
        postFeeService.holdEditFee(itemId, ownerId);

        // then: không đụng ví; lượt miễn phí chưa bị tính cho tới khi Admin duyệt
        assertThat(heldOf(ownerId)).isZero();
        Map<String, Object> fee = feeColumnsOf(itemId);
        assertThat(fee.get("fee_state")).isEqualTo("HELD");
        assertThat(fee.get("pending_fee")).isEqualTo(0);
        assertThat(fee.get("pending_fee_type")).isEqualTo("EDIT");
        assertThat(fee.get("free_edit_used")).isEqualTo(false);
    }

    @Test
    void holdEditFee_whenRejectedBeforeFirstApproval_holdsExactlyQuotedPostFee() {
        // given
        long ownerId = insertUser(START_BALANCE, 0);
        long itemId = insertRejectedItem(ownerId);
        EditQuote quote = postFeeService.quoteEdit(itemId, ownerId);

        // when
        postFeeService.holdEditFee(itemId, ownerId);

        // then: số giữ đúng bằng số đã báo, loại POST vì bài chưa từng trả phí đăng
        assertThat(quote.amount()).isEqualTo(POST_FEE);
        assertThat(heldOf(ownerId)).isEqualTo(quote.amount());
        Map<String, Object> fee = feeColumnsOf(itemId);
        assertThat(fee.get("fee_state")).isEqualTo("HELD");
        assertThat(fee.get("pending_fee")).isEqualTo(POST_FEE);
        assertThat(fee.get("pending_fee_type")).isEqualTo("POST");
    }

    @Test
    void holdEditFee_whenAlreadyHeld_overwritesWithoutHoldingAgain() {
        // given: bản sửa có phí đang chờ duyệt
        long ownerId = insertUser(START_BALANCE, 0);
        long itemId = insertApprovedItem(ownerId, true);
        postFeeService.holdEditFee(itemId, ownerId);

        // when: sửa đè lần nữa
        postFeeService.holdEditFee(itemId, ownerId);

        // then: không lỗi, không giữ thêm
        assertThat(heldOf(ownerId)).isEqualTo(EDIT_FEE);
        assertThat(feeColumnsOf(itemId).get("pending_fee")).isEqualTo(EDIT_FEE);
    }

    @Test
    void holdEditFee_whenBalanceNotEnough_throwsInsufficientBalanceAndKeepsFeeState() {
        // given: chỉ có 3 Credit, phí sửa là 5
        long ownerId = insertUser(3, 0);
        long itemId = insertApprovedItem(ownerId, true);

        // when
        Throwable thrown = catchThrowable(() -> postFeeService.holdEditFee(itemId, ownerId));

        // then
        assertBusinessError(thrown, WalletErrorCode.WALLET_INSUFFICIENT_BALANCE);
        assertThat(heldOf(ownerId)).isZero();
        assertThat(feeColumnsOf(itemId).get("fee_state")).isEqualTo("CHARGED");
    }

    @Test
    void holdEditFee_whenCallerIsNotOwner_throwsNotOwner() {
        // given
        long ownerId = insertUser(START_BALANCE, 0);
        long otherUserId = insertUser(START_BALANCE, 0);
        long itemId = insertApprovedItem(ownerId, true);

        // when
        Throwable thrown = catchThrowable(() -> postFeeService.holdEditFee(itemId, otherUserId));

        // then
        assertBusinessError(thrown, ListingFeeErrorCode.LISTINGFEE_NOT_OWNER);
        assertThat(heldOf(otherUserId)).isZero();
        assertThat(feeColumnsOf(itemId).get("fee_state")).isEqualTo("CHARGED");
    }

    @Test
    void holdEditFee_whenEditFeeConfiguredZero_marksHeldWithoutTouchingWallet() {
        // given: Admin cấu hình phí sửa = 0; ví không có Credit nào
        setConfig("edit_fee", "0");
        long ownerId = insertUser(0, 0);
        long itemId = insertApprovedItem(ownerId, true);

        // when
        postFeeService.holdEditFee(itemId, ownerId);

        // then
        assertThat(heldOf(ownerId)).isZero();
        Map<String, Object> fee = feeColumnsOf(itemId);
        assertThat(fee.get("fee_state")).isEqualTo("HELD");
        assertThat(fee.get("pending_fee")).isEqualTo(0);
        assertThat(fee.get("pending_fee_type")).isEqualTo("EDIT");
    }

    // ---------- vòng đời bản sửa: duyệt / từ chối ----------

    @Test
    void releaseHeldFee_whenFreeEditRejected_keepsFreeEditForNextEdit() {
        // given: gửi bản sửa miễn phí, Admin từ chối (status REJECTED do ItemService đặt, ở đây giả lập bằng SQL)
        long ownerId = insertUser(START_BALANCE, 0);
        long itemId = insertApprovedItem(ownerId, false);
        postFeeService.holdEditFee(itemId, ownerId);

        // when
        postFeeService.releaseHeldFee(itemId, "REJECTED");
        jdbc.update("UPDATE items SET status = 'REJECTED' WHERE id = ?", itemId);

        // then: R36 — xét approved_at chứ không xét status, lượt miễn phí vẫn còn
        assertThat(feeColumnsOf(itemId).get("free_edit_used")).isEqualTo(false);
        assertThat(postFeeService.quoteEdit(itemId, ownerId))
                .isEqualTo(new EditQuote(true, 0, EditQuote.Reason.FIRST_FREE_EDIT));
    }

    @Test
    void chargeHeldFee_whenFreeEditApproved_marksFreeEditUsedWithoutLedger() {
        // given
        long ownerId = insertUser(START_BALANCE, 0);
        long itemId = insertApprovedItem(ownerId, false);
        postFeeService.holdEditFee(itemId, ownerId);

        // when
        postFeeService.chargeHeldFee(itemId);

        // then: không mất Credit, không có dòng ledger; lần sửa sau có phí
        assertThat(balanceOf(ownerId)).isEqualTo(START_BALANCE);
        assertThat(ledgerCountOfItem(itemId)).isZero();
        Map<String, Object> fee = feeColumnsOf(itemId);
        assertThat(fee.get("fee_state")).isEqualTo("CHARGED");
        assertThat(fee.get("free_edit_used")).isEqualTo(true);
        assertThat(postFeeService.quoteEdit(itemId, ownerId).reason()).isEqualTo(EditQuote.Reason.PAID_EDIT);
    }

    @Test
    void chargeHeldFee_whenEditApproved_keepsExpireAtUnchanged() {
        // given: bài đã duyệt, đang hiển thị tới một mốc cố định; gửi bản sửa có phí
        Instant oldExpireAt = Instant.parse("2030-01-15T10:00:00Z");
        long ownerId = insertUser(START_BALANCE, 0);
        long itemId = insertApprovedItem(ownerId, true);
        jdbc.update("UPDATE items SET expire_at = ? WHERE id = ?", Timestamp.from(oldExpireAt), itemId);
        postFeeService.holdEditFee(itemId, ownerId);

        // when
        postFeeService.chargeHeldFee(itemId);

        // then: chỉ lần duyệt đầu (loại POST) mới đặt expire_at
        assertThat(feeColumnsOf(itemId).get("fee_state")).isEqualTo("CHARGED");
        assertThat(expireAtOf(itemId)).isEqualTo(oldExpireAt);
    }

    @Test
    void chargeHeldFee_whenPaidEditApproved_chargesEditFeeIntoLedger() {
        // given
        long ownerId = insertUser(START_BALANCE, 0);
        long itemId = insertApprovedItem(ownerId, true);
        postFeeService.holdEditFee(itemId, ownerId);

        // when
        postFeeService.chargeHeldFee(itemId);

        // then
        assertThat(balanceOf(ownerId)).isEqualTo(START_BALANCE - EDIT_FEE);
        assertThat(heldOf(ownerId)).isZero();
        Map<String, Object> ledgerRow =
                jdbc.queryForMap("SELECT amount, type FROM credit_ledger WHERE item_id = ?", itemId);
        assertThat(ledgerRow.get("amount")).isEqualTo(-EDIT_FEE);
        assertThat(ledgerRow.get("type")).isEqualTo("EDIT_FEE");
    }

    // ---------- hai luồng cùng lúc ----------

    @Test
    void holdEditFee_whenCalledTwiceConcurrently_holdsOnceAndBothSucceed() throws Exception {
        // given: bản sửa có phí, đủ tiền cho cả hai lần nếu không có khoá
        long ownerId = insertUser(START_BALANCE, 0);
        long itemId = insertApprovedItem(ownerId, true);

        // when
        List<Throwable> errors = raceTwoEditHolds(itemId, ownerId);

        // then: luồng sau chờ khoá ví, rồi thấy bài đã HELD nên sửa đè, không giữ thêm
        assertThat(errors).containsOnlyNulls();
        assertThat(heldOf(ownerId)).isEqualTo(EDIT_FEE);
        Map<String, Object> fee = feeColumnsOf(itemId);
        assertThat(fee.get("fee_state")).isEqualTo("HELD");
        assertThat(fee.get("pending_fee")).isEqualTo(EDIT_FEE);
    }

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

    // Hai luồng cùng gọi holdEditFee cho một bài; trả lỗi của từng luồng (null là thành công).
    // Dùng Arrays.asList vì List.of không nhận phần tử null.
    private List<Throwable> raceTwoEditHolds(long itemId, long ownerId) throws Exception {
        CountDownLatch bothReady = new CountDownLatch(2);
        CountDownLatch startSignal = new CountDownLatch(1);
        Callable<Throwable> holdEdit =
                () -> runAfterSignal(bothReady, startSignal, () -> postFeeService.holdEditFee(itemId, ownerId));

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<Throwable> firstFuture = executor.submit(holdEdit);
            Future<Throwable> secondFuture = executor.submit(holdEdit);
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

    // Bài Trao đổi mới tạo (loại bài mất phí đăng): các cột phí lấy mặc định của DB (fee_state = NONE, pending_fee =
    // 0).
    private long insertItem(long donorId) {
        return insertItem(donorId, "SWAP");
    }

    // desired_item bắt buộc với SWAP (ck_items_swap_desired), để null với GIVE.
    private long insertItem(long donorId, String offerType) {
        return jdbc.queryForObject(
                "INSERT INTO items (donor_id, category_id, area_id, offer_type, title, description, desired_item,"
                        + " condition) VALUES (?, ?, ?, ?, 'ListingFee IT item', 'Mô tả', ?, 'NEW') RETURNING id",
                Long.class,
                donorId,
                seedCategoryId(),
                seedAreaId(),
                offerType,
                desiredItemFor(offerType));
    }

    private String desiredItemFor(String offerType) {
        return "SWAP".equals(offerType) ? "Món muốn đổi" : null;
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

    // Bài đã được duyệt và đã trừ phí đăng; freeEditUsed cho biết đã dùng lượt sửa miễn phí hay chưa.
    private long insertApprovedItem(long donorId, boolean freeEditUsed) {
        return jdbc.queryForObject(
                "INSERT INTO items (donor_id, category_id, area_id, offer_type, title, description, condition,"
                        + " status, approved_at, post_fee_paid, fee_state, pending_fee, pending_fee_type,"
                        + " free_edit_used)"
                        + " VALUES (?, ?, ?, 'GIVE', 'ListingFee IT item', 'Mô tả', 'NEW',"
                        + " 'APPROVED', now(), false, 'CHARGED', ?, 'POST', ?) RETURNING id",
                Long.class,
                donorId,
                seedCategoryId(),
                seedAreaId(),
                POST_FEE,
                freeEditUsed);
    }

    // Bài Trao đổi bị từ chối ngay ở lần duyệt đầu: chưa từng duyệt (approved_at null), phí đăng đã nhả.
    private long insertRejectedItem(long donorId) {
        return insertRejectedItem(donorId, "SWAP", POST_FEE);
    }

    // releasedFee: số phí đăng đã giữ rồi nhả lúc bị từ chối (bài GIVE là 0).
    private long insertRejectedItem(long donorId, String offerType, int releasedFee) {
        return jdbc.queryForObject(
                "INSERT INTO items (donor_id, category_id, area_id, offer_type, title, description, desired_item,"
                        + " condition, status, fee_state, pending_fee, pending_fee_type)"
                        + " VALUES (?, ?, ?, ?, 'ListingFee IT item', 'Mô tả', ?, 'NEW',"
                        + " 'REJECTED', 'RELEASED', ?, 'POST') RETURNING id",
                Long.class,
                donorId,
                seedCategoryId(),
                seedAreaId(),
                offerType,
                desiredItemFor(offerType),
                releasedFee);
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

    private Map<String, Object> feeColumnsOf(long itemId) {
        return jdbc.queryForMap(
                "SELECT fee_state, pending_fee, pending_fee_type, post_fee_paid, free_edit_used FROM items WHERE id = ?",
                itemId);
    }

    // Đọc qua Timestamp vì driver PostgreSQL không đổi thẳng TIMESTAMPTZ sang Instant.
    private Instant expireAtOf(long itemId) {
        Timestamp expireAt = jdbc.queryForObject("SELECT expire_at FROM items WHERE id = ?", Timestamp.class, itemId);
        return expireAt == null ? null : expireAt.toInstant();
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
