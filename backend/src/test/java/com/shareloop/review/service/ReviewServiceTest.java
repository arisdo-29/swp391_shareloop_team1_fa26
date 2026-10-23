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
import com.shareloop.catalog.service.AreaService;
import com.shareloop.catalog.service.CategoryAttributeService;
import com.shareloop.catalog.service.CategoryService;
import com.shareloop.common.exception.BusinessException;
import com.shareloop.item.ItemErrorCode;
import com.shareloop.item.service.ItemService;
import com.shareloop.listingfee.service.PostFeeService;
import com.shareloop.notification.service.NotificationService;
import com.shareloop.review.ReviewErrorCode;
import com.shareloop.review.dto.ChecklistAnswer;
import com.shareloop.review.entity.ReviewItem;
import com.shareloop.review.repository.ReviewItemRepository;
import com.shareloop.review.repository.ReviewQueueRepository;
import com.shareloop.setting.dto.ChecklistItem;
import com.shareloop.setting.service.ConfigService;
import com.shareloop.user.service.UserService;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import tools.jackson.databind.json.JsonMapper;

class ReviewServiceTest {

    private final ReviewQueueRepository queueRepository = mock(ReviewQueueRepository.class);
    private final ReviewItemRepository reviewItemRepository = mock(ReviewItemRepository.class);
    private final ConfigService configService = mock(ConfigService.class);
    private final PostFeeService postFeeService = mock(PostFeeService.class);
    private final ItemService itemService = mock(ItemService.class);
    private final NotificationService notificationService = mock(NotificationService.class);
    private final ActivityLogService activityLogService = mock(ActivityLogService.class);
    private final UserService userService = mock(UserService.class);
    private final CategoryService categoryService = mock(CategoryService.class);
    private final AreaService areaService = mock(AreaService.class);
    private final CategoryAttributeService categoryAttributeService = mock(CategoryAttributeService.class);
    private final ReviewItem reviewItem = mock(ReviewItem.class);
    private final ReviewService service = new ReviewService(
            queueRepository,
            reviewItemRepository,
            configService,
            postFeeService,
            itemService,
            notificationService,
            activityLogService,
            userService,
            categoryService,
            areaService,
            categoryAttributeService,
            JsonMapper.builder().build());

    @BeforeEach
    void setUp() {
        when(configService.checklistItems())
                .thenReturn(List.of(
                        new ChecklistItem(11L, "photos", "Ảnh đúng món"),
                        new ChecklistItem(12L, "description", "Mô tả trung thực")));
        when(reviewItemRepository.findById(8L)).thenReturn(Optional.of(reviewItem));
        when(reviewItemRepository.findDonorIdByItemId(8L)).thenReturn(Optional.of(99L));
    }

    @Test
    void pendingResponseIncludesChecklist() {
        when(queueRepository.findPendingReviewQueue(any())).thenReturn(List.of());

        var result = service.listPendingWithChecklist(0, 20);

        assertThat(result.items()).isEmpty();
        assertThat(result.checklist())
                .containsExactly(
                        new ChecklistItem(11L, "photos", "Ảnh đúng món"),
                        new ChecklistItem(12L, "description", "Mô tả trung thực"));
    }

    @Test
    void approveChangesItemStatusThenChargesFeeAndStoresSnapshotAndLogsActivity() {
        service.approve(8L, List.of(new ChecklistAnswer(11L, true), new ChecklistAnswer(12L, true)), 1L);

        InOrder order = inOrder(postFeeService, itemService, reviewItemRepository, reviewItem, activityLogService);
        order.verify(reviewItemRepository).findById(8L);
        order.verify(itemService).markApproved(8L);
        order.verify(postFeeService).chargeHeldFee(8L);
        order.verify(reviewItem).approve(anyMap(), any(Instant.class), eq(1L));
        order.verify(activityLogService).log(eq("REVIEW_APPROVE"), eq(1L), eq("ITEM"), eq(8L), anyMap(), isNull());
    }

    @Test
    void incompleteChecklistDoesNotChargeOrApproveOrLog() {
        assertThatThrownBy(() -> service.approve(8L, List.of(new ChecklistAnswer(11L, true)), 1L))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(ReviewErrorCode.REVIEW_CHECKLIST_INCOMPLETE);

        verifyNoInteractions(postFeeService, itemService, activityLogService);
    }

    @Test
    void snapshotUsesConfiguredOrderEvenWhenAnswersAreReversed() {
        service.approve(8L, List.of(new ChecklistAnswer(12L, true), new ChecklistAnswer(11L, true)), 1L);

        Map<String, Object> snapshot = Map.of(
                "checks",
                List.of(
                        Map.of("id", 11L, "key", "photos", "statement", "Ảnh đúng món", "passed", true),
                        Map.of("id", 12L, "key", "description", "statement", "Mô tả trung thực", "passed", true)));
        verify(reviewItem).approve(eq(snapshot), any(Instant.class), eq(1L));
        verify(activityLogService).log(eq("REVIEW_APPROVE"), eq(1L), eq("ITEM"), eq(8L), eq(snapshot), isNull());
    }

    @Test
    void duplicateIdsAreIncomplete() {
        assertApprovalFails(
                List.of(new ChecklistAnswer(11L, true), new ChecklistAnswer(11L, true)),
                ReviewErrorCode.REVIEW_CHECKLIST_INCOMPLETE);
    }

    @Test
    void unknownIdRequiresChecklistReload() {
        assertApprovalFails(
                List.of(new ChecklistAnswer(11L, true), new ChecklistAnswer(99L, true)),
                ReviewErrorCode.REVIEW_CHECKLIST_OUTDATED);
    }

    @Test
    void failedConditionIsIncomplete() {
        assertApprovalFails(
                List.of(new ChecklistAnswer(11L, true), new ChecklistAnswer(12L, false)),
                ReviewErrorCode.REVIEW_CHECKLIST_INCOMPLETE);
    }

    @Test
    void missingChecklistConfigurationPreventsApproval() {
        when(configService.checklistItems()).thenReturn(List.of());
        assertApprovalFails(List.of(new ChecklistAnswer(11L, true)), ReviewErrorCode.REVIEW_CHECKLIST_NOT_CONFIGURED);
    }

    @Test
    void nullAndEmptyAnswersAreIncomplete() {
        assertApprovalFails(null, ReviewErrorCode.REVIEW_CHECKLIST_INCOMPLETE);
        assertApprovalFails(List.of(), ReviewErrorCode.REVIEW_CHECKLIST_INCOMPLETE);
        assertApprovalFails(
                java.util.Arrays.asList((ChecklistAnswer) null), ReviewErrorCode.REVIEW_CHECKLIST_INCOMPLETE);
        assertApprovalFails(List.of(new ChecklistAnswer(null, true)), ReviewErrorCode.REVIEW_CHECKLIST_INCOMPLETE);
        assertApprovalFails(
                List.of(new ChecklistAnswer(11L, null), new ChecklistAnswer(12L, true)),
                ReviewErrorCode.REVIEW_CHECKLIST_INCOMPLETE);
    }

    @Test
    void jsonNullMatchedTermsIsAnEmptyList() {
        Object[] row = {
            8L, 99L, "Áo", "LOW", "REVIEW", "LEXICON_REVIEW", (short) 3, Instant.parse("2026-10-19T00:00:00Z"), "null"
        };
        when(queueRepository.findPendingReviewQueue(any())).thenReturn(java.util.Collections.singletonList(row));

        var item = service.listPending(0, 20).getFirst();

        assertThat(item.flagReason()).isEqualTo("Có từ khoá cần xem xét");
        assertThat(item.matchedTerms()).isEmpty();
    }

    private void assertApprovalFails(List<ChecklistAnswer> answers, ReviewErrorCode errorCode) {
        assertThatThrownBy(() -> service.approve(8L, answers, 1L))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(errorCode);
        verifyNoInteractions(postFeeService, itemService, activityLogService);
    }

    @Test
    void pendingQueueDescribesReasonAndIncludesMatchedTerms() {
        Object[] row = {
            8L,
            99L,
            "Áo",
            "LOW",
            "REVIEW",
            "LEXICON_REVIEW",
            (short) 3,
            Instant.parse("2026-10-19T00:00:00Z"),
            "[\"zalo\",\"liên hệ\"]"
        };
        when(queueRepository.findPendingReviewQueue(any())).thenReturn(java.util.Collections.singletonList(row));

        var item = service.listPending(0, 20).getFirst();

        assertThat(item.flagReasonCode()).isEqualTo("LEXICON_REVIEW");
        assertThat(item.flagReason()).isEqualTo("Có từ khoá cần xem xét: zalo, liên hệ");
        assertThat(item.matchedTerms()).containsExactly("zalo", "liên hệ");
    }

    @Test
    void legacyQueueWithoutMatchedTermsPreservesFreeTextReason() {
        Object[] row = {
            8L, 99L, "Áo", "LOW", "REVIEW", "Ảnh cần kiểm tra", (short) 3, Instant.parse("2026-10-19T00:00:00Z"), null
        };
        when(queueRepository.findPendingReviewQueue(any())).thenReturn(java.util.Collections.singletonList(row));

        var item = service.listPending(0, 20).getFirst();

        assertThat(item.flagReason()).isEqualTo("Ảnh cần kiểm tra");
        assertThat(item.matchedTerms()).isEmpty();
    }

    @Test
    void chargeFailureDoesNotChangeItemOrSaveSnapshotOrLog() {
        org.mockito.Mockito.doThrow(new IllegalStateException("charge failed"))
                .when(postFeeService)
                .chargeHeldFee(8L);

        assertThatThrownBy(() -> service.approve(
                        8L, List.of(new ChecklistAnswer(11L, true), new ChecklistAnswer(12L, true)), 1L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("charge failed");

        verifyNoInteractions(activityLogService);
        verify(reviewItem, never()).approve(anyMap(), any(Instant.class), eq(1L));
    }

    @Test
    void itemInWrongStatusAbortsApprovalBeforeSavingSnapshotOrLog() {
        org.mockito.Mockito.doThrow(new BusinessException(ItemErrorCode.ITEM_INVALID_STATUS_TRANSITION))
                .when(itemService)
                .markApproved(8L);

        assertThatThrownBy(() -> service.approve(
                        8L, List.of(new ChecklistAnswer(11L, true), new ChecklistAnswer(12L, true)), 1L))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(ItemErrorCode.ITEM_INVALID_STATUS_TRANSITION);

        verify(postFeeService, never()).chargeHeldFee(8L);
        verify(reviewItem, never()).approve(anyMap(), any(Instant.class), eq(1L));
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

        verify(reviewItem, never()).reject(anyString(), any(Instant.class), anyLong());
        verify(notificationService, never()).notify(anyLong(), anyString(), anyString(), anyString(), anyString());
        verifyNoInteractions(activityLogService);
    }

    @Test
    void rejectReleasesFeeThenChangesItemAndStoresReasonAndNotifiesDonorAndLogsActivity() {
        service.reject(8L, "  Ảnh không đúng món  ", 1L);

        InOrder order = inOrder(
                postFeeService, itemService, reviewItemRepository, reviewItem, notificationService, activityLogService);
        order.verify(reviewItemRepository).findById(8L);
        order.verify(itemService).markRejected(8L);
        order.verify(postFeeService).releaseHeldFee(8L, "REJECTED");
        order.verify(reviewItem).reject(eq("Ảnh không đúng món"), any(Instant.class), eq(1L));
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
        verify(reviewItem).reject(eq("Ảnh không đúng món"), any(Instant.class), eq(1L));
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

    @Test
    void rejectWhenItemAlreadyProcessedDoesNotReleaseFee() {
        org.mockito.Mockito.doThrow(new BusinessException(ItemErrorCode.ITEM_INVALID_STATUS_TRANSITION))
                .when(itemService)
                .markRejected(8L);

        assertThatThrownBy(() -> service.reject(8L, "Ảnh mờ", 1L))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(ItemErrorCode.ITEM_INVALID_STATUS_TRANSITION);

        verifyNoInteractions(postFeeService, notificationService, activityLogService);
    }

    @Test
    void getDetailWhenItemMissingThrowsReviewItemNotFound() {
        when(reviewItemRepository.findById(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getDetail(404L))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(ReviewErrorCode.REVIEW_ITEM_NOT_FOUND);
    }
}
