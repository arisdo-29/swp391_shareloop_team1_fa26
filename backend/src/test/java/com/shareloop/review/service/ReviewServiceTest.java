package com.shareloop.review.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.shareloop.audit.service.ActivityLogService;
import com.shareloop.common.exception.BusinessException;
import com.shareloop.item.ItemErrorCode;
import com.shareloop.item.service.ItemService;
import com.shareloop.listingfee.service.PostFeeService;
import com.shareloop.notification.service.NotificationService;
import com.shareloop.review.ReviewErrorCode;
import com.shareloop.review.entity.ReviewItem;
import com.shareloop.review.repository.ReviewItemRepository;
import com.shareloop.review.repository.ReviewQueueRepository;
import com.shareloop.setting.service.ConfigService;
import java.time.Instant;
import java.util.List;
import java.util.Map;
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
    private final NotificationService notificationService = mock(NotificationService.class);
    private final ActivityLogService activityLogService = mock(ActivityLogService.class);
    private final ReviewItem reviewItem = mock(ReviewItem.class);
    private final ReviewService service = new ReviewService(
            queueRepository,
            reviewItemRepository,
            configService,
            postFeeService,
            itemService,
            notificationService,
            activityLogService);

    @BeforeEach
    void setUp() {
        when(configService.checklist()).thenReturn(List.of("Ảnh đúng món", "Mô tả trung thực"));
        when(reviewItemRepository.findById(8L)).thenReturn(Optional.of(reviewItem));
        when(reviewItemRepository.findDonorIdByItemId(8L)).thenReturn(Optional.of(99L));
    }

    @Test
    void pendingResponseIncludesChecklist() {
        when(queueRepository.findPendingReviewQueue(any())).thenReturn(List.of());

        var result = service.listPendingWithChecklist(0, 20);

        assertThat(result.items()).isEmpty();
        assertThat(result.checklist()).containsExactly("Ảnh đúng món", "Mô tả trung thực");
    }

    @Test
    void approveChargesFeeThenChangesItemAndStoresSnapshotAndLogsActivity() {
        service.approve(8L, List.of(true, true), 1L);

        InOrder order = inOrder(postFeeService, itemService, reviewItemRepository, reviewItem, activityLogService);
        order.verify(postFeeService).chargeHeldFee(8L);
        order.verify(itemService).markApproved(8L);
        order.verify(reviewItemRepository).findById(8L);
        order.verify(reviewItem).approve(anyMap(), any(Instant.class));
        order.verify(activityLogService).log(eq("REVIEW_APPROVE"), eq(1L), eq("ITEM"), eq(8L), anyMap(), isNull());
    }

    @Test
    void incompleteChecklistDoesNotChargeOrApproveOrLog() {
        assertThatThrownBy(() -> service.approve(8L, List.of(true), 1L))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(ReviewErrorCode.REVIEW_CHECKLIST_INCOMPLETE);

        verifyNoInteractions(postFeeService, itemService, reviewItemRepository, activityLogService);
    }

    @Test
    void chargeFailureDoesNotChangeItemOrSaveSnapshotOrLog() {
        org.mockito.Mockito.doThrow(new IllegalStateException("charge failed"))
                .when(postFeeService)
                .chargeHeldFee(8L);

        assertThatThrownBy(() -> service.approve(8L, List.of(true, true), 1L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("charge failed");

        verify(itemService, never()).markApproved(8L);
        verifyNoInteractions(reviewItemRepository, activityLogService);
        verify(reviewItem, never()).approve(anyMap(), any(Instant.class));
    }

    @Test
    void itemInWrongStatusAbortsApprovalBeforeSavingSnapshotOrLog() {
        org.mockito.Mockito.doThrow(new BusinessException(ItemErrorCode.ITEM_INVALID_STATUS_TRANSITION))
                .when(itemService)
                .markApproved(8L);

        assertThatThrownBy(() -> service.approve(8L, List.of(true, true), 1L))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(ItemErrorCode.ITEM_INVALID_STATUS_TRANSITION);

        verify(postFeeService).chargeHeldFee(8L);
        verify(reviewItemRepository, never()).findById(8L);
        verify(reviewItem, never()).approve(anyMap(), any(Instant.class));
        verifyNoInteractions(activityLogService);
    }

    @Test
    void listRejectReasonsDelegatesToConfigService() {
        when(configService.rejectReasons()).thenReturn(List.of("Ảnh mờ", "Sai danh mục"));

        assertThat(service.listRejectReasons()).containsExactly("Ảnh mờ", "Sai danh mục");
    }

    @Test
    void rejectRequiresReasonBeforeReleasingFee() {
        assertThatThrownBy(() -> service.reject(8L, "  ", 1L))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(ReviewErrorCode.REVIEW_REJECT_REASON_REQUIRED);

        verifyNoInteractions(
                postFeeService, itemService, reviewItemRepository, notificationService, activityLogService);
    }

    @Test
    void releaseFeeFailureAbortsBeforeNotifyAndItemChangeAndLog() {
        org.mockito.Mockito.doThrow(new IllegalStateException("fee release failed"))
                .when(postFeeService)
                .releaseHeldFee(8L, "REJECTED");

        assertThatThrownBy(() -> service.reject(8L, "Ảnh không đúng món", 1L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("fee release failed");

        verify(itemService, never()).markRejected(8L);
        verify(reviewItemRepository, never()).findById(8L);
        verify(notificationService, never()).notify(anyLong(), anyString(), anyString(), anyString(), anyString());
        verifyNoInteractions(activityLogService);
    }

    @Test
    void rejectReleasesFeeThenChangesItemAndStoresReasonAndNotifiesDonorAndLogsActivity() {
        service.reject(8L, "  Ảnh không đúng món  ", 1L);

        InOrder order = inOrder(
                postFeeService, itemService, reviewItemRepository, reviewItem, notificationService, activityLogService);
        order.verify(postFeeService).releaseHeldFee(8L, "REJECTED");
        order.verify(itemService).markRejected(8L);
        order.verify(reviewItemRepository).findById(8L);
        order.verify(reviewItem).reject("Ảnh không đúng món");
        order.verify(reviewItemRepository).findDonorIdByItemId(8L);
        order.verify(notificationService)
                .notify(99L, "POST_REJECTED", "Bài đăng bị từ chối", "Ảnh không đúng món", "/items/8");
        order.verify(activityLogService)
                .log(
                        eq("REVIEW_REJECT"),
                        eq(1L),
                        eq("ITEM"),
                        eq(8L),
                        (Map<String, Object>) isNull(),
                        eq("Ảnh không đúng món"));
    }

    @Test
    void rejectWhenDonorNotFoundDoesNotFailOrNotifyButLogsActivity() {
        when(reviewItemRepository.findDonorIdByItemId(8L)).thenReturn(Optional.empty());

        service.reject(8L, "Ảnh không đúng món", 1L);

        verify(postFeeService).releaseHeldFee(8L, "REJECTED");
        verify(itemService).markRejected(8L);
        verify(reviewItem).reject("Ảnh không đúng món");
        verify(notificationService, never()).notify(anyLong(), anyString(), anyString(), anyString(), anyString());
        verify(activityLogService)
                .log(
                        eq("REVIEW_REJECT"),
                        eq(1L),
                        eq("ITEM"),
                        eq(8L),
                        (Map<String, Object>) isNull(),
                        eq("Ảnh không đúng món"));
    }
}
