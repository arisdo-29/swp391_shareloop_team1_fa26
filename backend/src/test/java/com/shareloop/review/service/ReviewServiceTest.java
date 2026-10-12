package com.shareloop.review.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.shareloop.audit.service.ActivityLogService;
import com.shareloop.common.exception.BusinessException;
import com.shareloop.review.ReviewErrorCode;
import com.shareloop.review.dto.AuthenticityReviewRequest;
import com.shareloop.review.dto.RevokeAuthenticityRequest;
import com.shareloop.review.entity.AuthenticityStatus;
import com.shareloop.review.repository.AuthenticityRepository;
import com.shareloop.review.repository.ReviewQueueRepository;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ReviewServiceTest {

    private final ReviewQueueRepository queueRepository = mock(ReviewQueueRepository.class);
    private final AuthenticityRepository authenticityRepository = mock(AuthenticityRepository.class);
    private final ActivityLogService activityLogService = mock(ActivityLogService.class);
    private final ReviewService service =
            new ReviewService(queueRepository, authenticityRepository, activityLogService);

    @Test
    void approvalChangesStatusAndWritesAuditLog() {
        when(authenticityRepository.findStatusForUpdate(24L)).thenReturn(AuthenticityStatus.PENDING);
        when(authenticityRepository.countActiveProofs(24L)).thenReturn(1);

        var response = service.reviewAuthenticity(24L, 7L, new AuthenticityReviewRequest(true, "Invoice matches"));

        assertThat(response.status()).isEqualTo(AuthenticityStatus.VERIFIED);
        verify(authenticityRepository).updateStatus(24L, AuthenticityStatus.VERIFIED);
        verify(activityLogService)
                .log(
                        7L,
                        "AUTH_VERIFIED",
                        "ITEM",
                        24L,
                        Map.of("previousStatus", "PENDING", "status", "VERIFIED"),
                        "Invoice matches");
    }

    @Test
    void rejectionChangesStatusAndKeepsReasonInAuditLog() {
        when(authenticityRepository.findStatusForUpdate(24L)).thenReturn(AuthenticityStatus.PENDING);

        var response = service.reviewAuthenticity(24L, 7L, new AuthenticityReviewRequest(false, "Receipt is unclear"));

        assertThat(response.status()).isEqualTo(AuthenticityStatus.REJECTED);
        verify(activityLogService)
                .log(
                        7L,
                        "AUTH_REJECTED",
                        "ITEM",
                        24L,
                        Map.of("previousStatus", "PENDING", "status", "REJECTED"),
                        "Receipt is unclear");
    }

    @Test
    void verificationRequiresAnAttachedPrivateProof() {
        when(authenticityRepository.findStatusForUpdate(24L)).thenReturn(AuthenticityStatus.PENDING);
        when(authenticityRepository.countActiveProofs(24L)).thenReturn(0);

        assertThatThrownBy(() ->
                        service.reviewAuthenticity(24L, 7L, new AuthenticityReviewRequest(true, "Looks authentic")))
                .isInstanceOf(BusinessException.class)
                .extracting(error -> ((BusinessException) error).getErrorCode())
                .isEqualTo(ReviewErrorCode.REVIEW_AUTH_PROOF_NOT_FOUND);
        verify(authenticityRepository, never()).updateStatus(24L, AuthenticityStatus.VERIFIED);
        verify(activityLogService, never())
                .log(
                        org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.anyString(),
                        org.mockito.ArgumentMatchers.anyString(),
                        org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.anyMap(),
                        org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void reviewRejectsItemsOutsidePendingAuthenticityState() {
        when(authenticityRepository.findStatusForUpdate(24L)).thenReturn(AuthenticityStatus.VERIFIED);

        assertThatThrownBy(() -> service.reviewAuthenticity(24L, 7L, new AuthenticityReviewRequest(false, "Reason")))
                .isInstanceOf(BusinessException.class)
                .extracting(error -> ((BusinessException) error).getErrorCode())
                .isEqualTo(ReviewErrorCode.REVIEW_AUTH_INVALID_STATE_TRANSITION);
        verify(authenticityRepository, never()).countActiveProofs(24L);
    }

    @Test
    void fakeProofRevocationChangesStatusAndWritesReason() {
        when(authenticityRepository.findStatusForUpdate(24L)).thenReturn(AuthenticityStatus.VERIFIED);

        var response = service.revokeAuthenticity(24L, 7L, new RevokeAuthenticityRequest("Proof is forged"));

        assertThat(response.status()).isEqualTo(AuthenticityStatus.REVOKED);
        verify(authenticityRepository).updateStatus(24L, AuthenticityStatus.REVOKED);
        verify(activityLogService)
                .log(
                        7L,
                        "AUTH_REVOKED",
                        "ITEM",
                        24L,
                        Map.of("previousStatus", "VERIFIED", "status", "REVOKED"),
                        "Proof is forged");
    }

    @Test
    void revocationRequiresVerifiedState() {
        when(authenticityRepository.findStatusForUpdate(24L)).thenReturn(AuthenticityStatus.REJECTED);

        assertThatThrownBy(() -> service.revokeAuthenticity(24L, 7L, new RevokeAuthenticityRequest("Fake proof")))
                .isInstanceOf(BusinessException.class)
                .extracting(error -> ((BusinessException) error).getErrorCode())
                .isEqualTo(ReviewErrorCode.REVIEW_AUTH_INVALID_STATE_TRANSITION);
        verify(authenticityRepository, never()).updateStatus(24L, AuthenticityStatus.REVOKED);
    }
}
