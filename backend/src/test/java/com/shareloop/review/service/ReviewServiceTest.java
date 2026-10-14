package com.shareloop.review.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.shareloop.common.exception.BusinessException;
import com.shareloop.item.ItemErrorCode;
import com.shareloop.item.service.ItemService;
import com.shareloop.listingfee.service.PostFeeService;
import com.shareloop.review.ReviewErrorCode;
import com.shareloop.review.entity.ReviewItem;
import com.shareloop.review.repository.ReviewItemRepository;
import com.shareloop.review.repository.ReviewQueueRepository;
import com.shareloop.setting.service.ConfigService;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

class ReviewServiceTest {

    private final ReviewQueueRepository queueRepository = mock(ReviewQueueRepository.class);
    private final ReviewItemRepository reviewItemRepository = mock(ReviewItemRepository.class);
    private final ConfigService configService = mock(ConfigService.class);
    private final PostFeeService postFeeService = mock(PostFeeService.class);
    private final ItemService itemService = mock(ItemService.class);
    private final ReviewItem reviewItem = mock(ReviewItem.class);
    private final ReviewService service =
            new ReviewService(queueRepository, reviewItemRepository, configService, postFeeService, itemService);

    @BeforeEach
    void setUp() {
        when(configService.checklist()).thenReturn(List.of("Ảnh đúng món", "Mô tả trung thực"));
        when(reviewItemRepository.findById(8L)).thenReturn(Optional.of(reviewItem));
    }

    @Test
    void pendingResponseIncludesChecklist() {
        when(queueRepository.findPendingReviewQueue(any())).thenReturn(List.of());

        var result = service.listPendingWithChecklist(0, 20);

        assertThat(result.items()).isEmpty();
        assertThat(result.checklist()).containsExactly("Ảnh đúng món", "Mô tả trung thực");
    }

    @Test
    void approveChargesFeeThenChangesItemAndStoresSnapshot() {
        service.approve(8L, List.of(true, true));

        InOrder order = inOrder(postFeeService, itemService, reviewItemRepository, reviewItem);
        order.verify(postFeeService).chargeHeldFee(8L);
        order.verify(itemService).markApproved(8L);
        order.verify(reviewItemRepository).findById(8L);
        order.verify(reviewItem).approve(anyMap(), any(Instant.class));
    }

    @Test
    void incompleteChecklistDoesNotChargeOrApprove() {
        assertThatThrownBy(() -> service.approve(8L, List.of(true)))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(ReviewErrorCode.REVIEW_CHECKLIST_INCOMPLETE);

        verifyNoInteractions(postFeeService, itemService, reviewItemRepository);
    }

    @Test
    void chargeFailureDoesNotChangeItemOrSaveSnapshot() {
        org.mockito.Mockito.doThrow(new IllegalStateException("charge failed"))
                .when(postFeeService)
                .chargeHeldFee(8L);

        assertThatThrownBy(() -> service.approve(8L, List.of(true, true)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("charge failed");

        verify(itemService, never()).markApproved(8L);
        verifyNoInteractions(reviewItemRepository);
        verify(reviewItem, never()).approve(anyMap(), any(Instant.class));
    }

    @Test
    void itemInWrongStatusAbortsApprovalBeforeSavingSnapshot() {
        org.mockito.Mockito.doThrow(new BusinessException(ItemErrorCode.ITEM_INVALID_STATUS_TRANSITION))
                .when(itemService)
                .markApproved(8L);

        assertThatThrownBy(() -> service.approve(8L, List.of(true, true)))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(ItemErrorCode.ITEM_INVALID_STATUS_TRANSITION);

        verify(postFeeService).chargeHeldFee(8L);
        verify(reviewItemRepository, never()).findById(8L);
        verify(reviewItem, never()).approve(anyMap(), any(Instant.class));
    }

    @Test
    void rejectRequiresReasonBeforeReleasingFee() {
        assertThatThrownBy(() -> service.reject(8L, "  "))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(ReviewErrorCode.REVIEW_REJECT_REASON_REQUIRED);

        verifyNoInteractions(postFeeService, itemService, reviewItemRepository);
    }

    @Test
    void rejectReleasesFeeThenChangesItemAndStoresReason() {
        service.reject(8L, "  Ảnh không đúng món  ");

        InOrder order = inOrder(postFeeService, itemService, reviewItemRepository, reviewItem);
        order.verify(postFeeService).releaseHeldFee(8L, "REJECTED");
        order.verify(itemService).markRejected(8L);
        order.verify(reviewItemRepository).findById(8L);
        order.verify(reviewItem).reject("Ảnh không đúng món");
    }
}
