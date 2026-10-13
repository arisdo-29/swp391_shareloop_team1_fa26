package com.shareloop.review.service;

import com.shareloop.review.dto.ReviewQueueItem;
import com.shareloop.review.repository.ReviewQueueRepository;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class ReviewService {

    private final ReviewQueueRepository repository;

    public ReviewService(ReviewQueueRepository repository) {
        this.repository = repository;
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
