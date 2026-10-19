package com.shareloop.review.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.shareloop.audit.ActivityType;
import com.shareloop.audit.service.ActivityLogService;
import com.shareloop.common.exception.BusinessException;
import com.shareloop.review.ReviewErrorCode;
import com.shareloop.review.entity.AuthenticityStatus;
import com.shareloop.review.entity.ReviewItem;
import com.shareloop.review.repository.ReviewItemRepository;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;

class AuthenticityServiceTest {

    private ReviewItemRepository reviewItemRepository;
    private ActivityLogService activityLogService;
    private AuthenticityService authenticityService;

    @BeforeEach
    void setUp() {
        reviewItemRepository = mock(ReviewItemRepository.class);
        activityLogService = mock(ActivityLogService.class);
        authenticityService = new AuthenticityService(reviewItemRepository, activityLogService);
    }

    @Test
    @DisplayName("grant: cấp nhãn thành công khi bài ở trạng thái PENDING")
    void grant_success() {
        long itemId = 42L;
        Long adminId = 1L;
        ReviewItem item = new ReviewItem(itemId, AuthenticityStatus.PENDING);
        when(reviewItemRepository.findByIdForUpdate(itemId)).thenReturn(Optional.of(item));

        authenticityService.grant(itemId, adminId);

        assertThat(item.getAuthStatus()).isEqualTo(AuthenticityStatus.VERIFIED);
        verify(activityLogService)
                .log(
                        eq(ActivityType.AUTH_GRANT.name()),
                        eq(adminId),
                        eq("ITEM"),
                        eq(itemId),
                        ArgumentMatchers.<Map<String, Object>>isNull(),
                        ArgumentMatchers.<String>isNull());
    }

    @Test
    @DisplayName("grant: ném 404 khi không tìm thấy bài đăng")
    void grant_notFound_throwsException() {
        when(reviewItemRepository.findByIdForUpdate(anyLong())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authenticityService.grant(999L, 1L))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ReviewErrorCode.AUTHENTICITY_ITEM_NOT_FOUND);

        verifyNoInteractions(activityLogService);
    }

    @Test
    @DisplayName("grant: ném 409 khi bài không ở trạng thái PENDING")
    void grant_notPending_throwsException() {
        long itemId = 42L;
        ReviewItem item = new ReviewItem(itemId, AuthenticityStatus.NONE);
        when(reviewItemRepository.findByIdForUpdate(itemId)).thenReturn(Optional.of(item));

        assertThatThrownBy(() -> authenticityService.grant(itemId, 1L))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ReviewErrorCode.AUTHENTICITY_INVALID_STATUS);

        assertThat(item.getAuthStatus()).isEqualTo(AuthenticityStatus.NONE);
        verifyNoInteractions(activityLogService);
    }

    @Test
    @DisplayName("reject: từ chối nhãn thành công khi bài ở PENDING kèm lý do")
    void reject_success() {
        long itemId = 42L;
        Long adminId = 1L;
        String reason = "Chứng từ không rõ nguồn gốc";
        ReviewItem item = new ReviewItem(itemId, AuthenticityStatus.PENDING);
        when(reviewItemRepository.findByIdForUpdate(itemId)).thenReturn(Optional.of(item));

        authenticityService.reject(itemId, reason, adminId);

        assertThat(item.getAuthStatus()).isEqualTo(AuthenticityStatus.REJECTED);
        verify(activityLogService)
                .log(
                        eq(ActivityType.AUTH_REJECT.name()),
                        eq(adminId),
                        eq("ITEM"),
                        eq(itemId),
                        ArgumentMatchers.<Map<String, Object>>isNull(),
                        eq(reason));
    }

    @Test
    @DisplayName("reject: ném 400 khi lý do null hoặc rỗng")
    void reject_emptyReason_throwsException() {
        assertThatThrownBy(() -> authenticityService.reject(42L, "   ", 1L))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ReviewErrorCode.AUTHENTICITY_REASON_REQUIRED);

        verify(reviewItemRepository, never()).findByIdForUpdate(anyLong());
        verifyNoInteractions(activityLogService);
    }

    @Test
    @DisplayName("reject: ném 409 khi bài không ở trạng thái PENDING")
    void reject_notPending_throwsException() {
        long itemId = 42L;
        ReviewItem item = new ReviewItem(itemId, AuthenticityStatus.VERIFIED);
        when(reviewItemRepository.findByIdForUpdate(itemId)).thenReturn(Optional.of(item));

        assertThatThrownBy(() -> authenticityService.reject(itemId, "Lý do từ chối", 1L))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ReviewErrorCode.AUTHENTICITY_INVALID_STATUS);

        assertThat(item.getAuthStatus()).isEqualTo(AuthenticityStatus.VERIFIED);
        verifyNoInteractions(activityLogService);
    }

    @Test
    @DisplayName("revoke: thu hồi nhãn thành công khi bài đang VERIFIED kèm lý do")
    void revoke_success() {
        long itemId = 42L;
        Long adminId = 1L;
        String reason = "Phát hiện chứng từ giả mạo";
        ReviewItem item = new ReviewItem(itemId, AuthenticityStatus.VERIFIED);
        when(reviewItemRepository.findByIdForUpdate(itemId)).thenReturn(Optional.of(item));

        authenticityService.revoke(itemId, reason, adminId);

        assertThat(item.getAuthStatus()).isEqualTo(AuthenticityStatus.REVOKED);
        verify(activityLogService)
                .log(
                        eq(ActivityType.AUTH_REVOKE.name()),
                        eq(adminId),
                        eq("ITEM"),
                        eq(itemId),
                        ArgumentMatchers.<Map<String, Object>>isNull(),
                        eq(reason));
    }

    @Test
    @DisplayName("revoke: ném 400 khi lý do null hoặc rỗng")
    void revoke_emptyReason_throwsException() {
        assertThatThrownBy(() -> authenticityService.revoke(42L, "", 1L))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ReviewErrorCode.AUTHENTICITY_REASON_REQUIRED);

        verify(reviewItemRepository, never()).findByIdForUpdate(anyLong());
        verifyNoInteractions(activityLogService);
    }

    @Test
    @DisplayName("revoke: ném 409 khi bài chưa được cấp nhãn chính hãng (không phải VERIFIED)")
    void revoke_notVerified_throwsException() {
        long itemId = 42L;
        ReviewItem item = new ReviewItem(itemId, AuthenticityStatus.PENDING);
        when(reviewItemRepository.findByIdForUpdate(itemId)).thenReturn(Optional.of(item));

        assertThatThrownBy(() -> authenticityService.revoke(itemId, "Thu hồi nhãn", 1L))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ReviewErrorCode.AUTHENTICITY_NOT_VERIFIED);

        assertThat(item.getAuthStatus()).isEqualTo(AuthenticityStatus.PENDING);
        verifyNoInteractions(activityLogService);
    }
}
