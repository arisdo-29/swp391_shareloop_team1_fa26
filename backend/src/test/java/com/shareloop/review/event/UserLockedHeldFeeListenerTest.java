package com.shareloop.review.event;

import static org.assertj.core.api.Assertions.assertThat;
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
    void onUserLocked_rejectsEveryPendingItemAndCountsOnlyHeldFees() {
        // given: bài 1, 2 có phí; bài 3 là bài Cho tặng giữ phí 0 nên không có trong danh sách giữ phí
        when(listingFeeQueryService.findHeldFees(10L))
                .thenReturn(List.of(
                        new HeldFeeItem(1L, "Bài A", "SWAP", "PENDING_REVIEW", 5, "POST"),
                        new HeldFeeItem(2L, "Bài B", "SWAP", "PENDING_REVIEW", 2, "EDIT"),
                        new HeldFeeItem(4L, "Bài D", "SWAP", "APPROVED", 9, "EDIT")));
        when(reviewService.rejectPendingItemsOfUser(10L, "[Hệ thống] Tài khoản bị khoá", 99L))
                .thenReturn(List.of(1L, 2L, 3L));
        UserLockCleanup cleanup = new UserLockCleanup();

        // when
        listener.onUserLocked(new UserLockedEvent(99L, 10L, cleanup));

        // then: L5 từ chối cả bài miễn phí; Credit nhả chỉ tính bài thật sự giữ phí và vừa bị từ chối
        verify(reviewService).rejectPendingItemsOfUser(10L, "[Hệ thống] Tài khoản bị khoá", 99L);
        assertThat(cleanup.getReleasedCredit()).isEqualTo(7);
    }

    @Test
    void onUserLocked_whenNoPendingItems_releasesNothing() {
        // given
        when(listingFeeQueryService.findHeldFees(10L)).thenReturn(List.of());
        when(reviewService.rejectPendingItemsOfUser(10L, UserLockedHeldFeeListener.LOCK_REASON, null))
                .thenReturn(List.of());
        UserLockCleanup cleanup = new UserLockCleanup();

        // when: cấm tự động khi hết sao không có Admin thực hiện
        listener.onUserLocked(new UserLockedEvent(null, 10L, cleanup));

        // then
        assertThat(cleanup.getReleasedCredit()).isZero();
    }
}
