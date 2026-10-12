package com.shareloop.review.service;

import com.shareloop.audit.service.ActivityLogService;
import com.shareloop.common.exception.BusinessException;
import com.shareloop.review.ReviewErrorCode;
import com.shareloop.review.dto.AuthenticityDecisionResponse;
import com.shareloop.review.dto.AuthenticityReviewRequest;
import com.shareloop.review.dto.ReviewQueueItem;
import com.shareloop.review.dto.RevokeAuthenticityRequest;
import com.shareloop.review.entity.AuthenticityStatus;
import com.shareloop.review.repository.AuthenticityRepository;
import com.shareloop.review.repository.ReviewQueueRepository;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class ReviewService {

    private final ReviewQueueRepository repository;
    private final AuthenticityRepository authenticityRepository;
    private final ActivityLogService activityLogService;

    public ReviewService(
            ReviewQueueRepository repository,
            AuthenticityRepository authenticityRepository,
            ActivityLogService activityLogService) {
        this.repository = repository;
        this.authenticityRepository = authenticityRepository;
        this.activityLogService = activityLogService;
    }

    public List<ReviewQueueItem> listPending(int page, int size) {
        int offset = Math.multiplyExact(page, size);
        return repository.findPendingReviewQueue(offset, size).stream()
                .map(row -> new ReviewQueueItem(
                        ((Number) row[0]).longValue(),
                        ((Number) row[1]).longValue(),
                        (String) row[2],
                        (String) row[3],
                        (String) row[4],
                        (String) row[5],
                        ((Number) row[6]).shortValue(),
                        toInstant(row[7])))
                .toList();
    }

    @Transactional
    public AuthenticityDecisionResponse reviewAuthenticity(
            long itemId, long adminId, AuthenticityReviewRequest request) {
        AuthenticityStatus currentStatus = findStatus(itemId);
        if (currentStatus != AuthenticityStatus.PENDING) {
            throw new BusinessException(ReviewErrorCode.REVIEW_AUTH_INVALID_STATE_TRANSITION);
        }
        AuthenticityStatus nextStatus = request.approved() ? AuthenticityStatus.VERIFIED : AuthenticityStatus.REJECTED;
        if (request.approved()) {
            requireActiveProof(itemId);
        }
        authenticityRepository.updateStatus(itemId, nextStatus);
        String action = request.approved() ? "AUTH_VERIFIED" : "AUTH_REJECTED";
        activityLogService.log(
                adminId,
                action,
                "ITEM",
                itemId,
                Map.of("previousStatus", currentStatus.name(), "status", nextStatus.name()),
                request.reason());
        return new AuthenticityDecisionResponse(itemId, nextStatus, request.reason());
    }

    @Transactional
    public AuthenticityDecisionResponse revokeAuthenticity(
            long itemId, long adminId, RevokeAuthenticityRequest request) {
        AuthenticityStatus currentStatus = findStatus(itemId);
        if (currentStatus != AuthenticityStatus.VERIFIED) {
            throw new BusinessException(ReviewErrorCode.REVIEW_AUTH_INVALID_STATE_TRANSITION);
        }

        authenticityRepository.updateStatus(itemId, AuthenticityStatus.REVOKED);
        activityLogService.log(
                adminId,
                "AUTH_REVOKED",
                "ITEM",
                itemId,
                Map.of("previousStatus", currentStatus.name(), "status", AuthenticityStatus.REVOKED.name()),
                request.reason());
        return new AuthenticityDecisionResponse(itemId, AuthenticityStatus.REVOKED, request.reason());
    }

    private AuthenticityStatus findStatus(long itemId) {
        AuthenticityStatus status = authenticityRepository.findStatusForUpdate(itemId);
        if (status == null) {
            throw new BusinessException(ReviewErrorCode.REVIEW_ITEM_NOT_FOUND);
        }
        return status;
    }

    private void requireActiveProof(long itemId) {
        if (authenticityRepository.countActiveProofs(itemId) == 0) {
            throw new BusinessException(ReviewErrorCode.REVIEW_AUTH_PROOF_NOT_FOUND);
        }
    }

    private Instant toInstant(Object value) {
        if (value instanceof Instant instant) {
            return instant;
        }
        if (value instanceof OffsetDateTime offsetDateTime) {
            return offsetDateTime.toInstant();
        }
        if (value instanceof java.sql.Timestamp timestamp) {
            return timestamp.toInstant();
        }
        throw new IllegalStateException(
                "Unsupported timestamp result type: " + value.getClass().getName());
    }
}
