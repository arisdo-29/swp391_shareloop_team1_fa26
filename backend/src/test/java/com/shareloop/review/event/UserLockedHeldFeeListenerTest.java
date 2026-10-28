package com.shareloop.review.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.shareloop.listingfee.dto.HeldFeeItem;
import com.shareloop.listingfee.service.ListingFeeQueryService;
import com.shareloop.review.service.ReviewService;
import com.shareloop.user.event.UserLockCleanup;
import com.shareloop.user.event.UserLockedEvent;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class UserLockedHeldFeeListenerTest {

    @Mock
    ListingFeeQueryService listingFeeQueryService;

    @Mock
    ReviewService reviewService;

    @InjectMocks
    UserLockedHeldFeeListener listener;

    @Test
    void onUserLocked_rejectsPendingReviewItemsAndCountsReleasedCredit() {
        // given
        when(listingFeeQueryService.findHeldFees(10L))
                .thenReturn(List.of(
                        new HeldFeeItem(1L, "Bài A", "SWAP", "PENDING_REVIEW", 5, "POST"),
                        new HeldFeeItem(2L, "Bài B", "SWAP", "PENDING_REVIEW", 2, "EDIT")));
        UserLockCleanup cleanup = new UserLockCleanup();

        // when
        listener.onUserLocked(new UserLockedEvent(99L, 10L, cleanup));

        // then
        verify(reviewService).reject(1L, "Tài khoản bị khoá", 99L);
        verify(reviewService).reject(2L, "Tài khoản bị khoá", 99L);
        assertThat(cleanup.getReleasedCredit()).isEqualTo(7);
    }

    @Test
    void onUserLocked_skipsItemsNotPendingReview() {
        // given
        when(listingFeeQueryService.findHeldFees(10L))
                .thenReturn(List.of(new HeldFeeItem(3L, "Bài C", "SWAP", "APPROVED", 5, "POST")));
        UserLockCleanup cleanup = new UserLockCleanup();

        // when
        listener.onUserLocked(new UserLockedEvent(99L, 10L, cleanup));

        // then
        verify(reviewService, never()).reject(anyLong(), anyString(), eq(99L));
        assertThat(cleanup.getReleasedCredit()).isZero();
    }
}
