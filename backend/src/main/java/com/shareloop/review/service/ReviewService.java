package com.shareloop.review.service;

import com.shareloop.common.exception.BusinessException;
import com.shareloop.item.service.ItemService;
import com.shareloop.listingfee.service.PostFeeService;
import com.shareloop.review.ReviewErrorCode;
import com.shareloop.review.dto.ReviewQueueItem;
import com.shareloop.review.dto.ReviewQueueResponse;
import com.shareloop.review.entity.ReviewItem;
import com.shareloop.review.repository.ReviewItemRepository;
import com.shareloop.review.repository.ReviewQueueRepository;
import com.shareloop.setting.service.ConfigService;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class ReviewService {

    private final ReviewQueueRepository repository;
    private final ReviewItemRepository reviewItemRepository;
    private final ConfigService configService;
    private final PostFeeService postFeeService;
    private final ItemService itemService;

    public ReviewService(
            ReviewQueueRepository repository,
            ReviewItemRepository reviewItemRepository,
            ConfigService configService,
            PostFeeService postFeeService,
            ItemService itemService) {
        this.repository = repository;
        this.reviewItemRepository = reviewItemRepository;
        this.configService = configService;
        this.postFeeService = postFeeService;
        this.itemService = itemService;
    }

    public List<ReviewQueueItem> listPending(int page, int size) {
        return repository.findPendingReviewQueue(PageRequest.of(page, size)).stream()
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

    public ReviewQueueResponse listPendingWithChecklist(int page, int size) {
        return new ReviewQueueResponse(listPending(page, size), configService.checklist(), page, size);
    }

    @Transactional
    public void approve(long itemId, List<Boolean> answers) {
        List<String> questions = configService.checklist();
        if (questions.isEmpty()) {
            throw new BusinessException(ReviewErrorCode.REVIEW_CHECKLIST_NOT_CONFIGURED);
        }
        if (answers == null
                || answers.size() != questions.size()
                || answers.stream().anyMatch(answer -> !Boolean.TRUE.equals(answer))) {
            throw new BusinessException(ReviewErrorCode.REVIEW_CHECKLIST_INCOMPLETE);
        }

        postFeeService.chargeHeldFee(itemId);
        itemService.markApproved(itemId);

        ReviewItem reviewItem = requireReviewItem(itemId);
        List<Map<String, Object>> checks = new ArrayList<>(questions.size());
        for (int index = 0; index < questions.size(); index++) {
            Map<String, Object> check = new HashMap<>();
            check.put("question", questions.get(index));
            check.put("passed", answers.get(index));
            checks.add(check);
        }
        reviewItem.approve(Map.of("checks", checks), Instant.now());
    }

    @Transactional
    public void reject(long itemId, String reason) {
        if (reason == null || reason.isBlank()) {
            throw new BusinessException(ReviewErrorCode.REVIEW_REJECT_REASON_REQUIRED);
        }

        postFeeService.releaseHeldFee(itemId, "REJECTED");
        itemService.markRejected(itemId);
        requireReviewItem(itemId).reject(reason.trim());
    }

    private ReviewItem requireReviewItem(long itemId) {
        return reviewItemRepository
                .findById(itemId)
                .orElseThrow(() -> new BusinessException(ReviewErrorCode.REVIEW_ITEM_NOT_FOUND));
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
