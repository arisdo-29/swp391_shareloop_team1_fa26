package com.shareloop.review.repository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.List;
import org.springframework.stereotype.Repository;

@Repository
public class ReviewQueueRepository {

    private static final String PENDING_REVIEW_QUERY = """
            SELECT i.id,
                   i.donor_id,
                   i.title,
                   i.ai_risk,
                   COALESCE(i.ai_result ->> 'flag', 'NONE'),
                   i.ai_result ->> 'reason',
                   u.trust_stars,
                   i.created_at
            FROM items i
            JOIN users u ON u.id = i.donor_id
            WHERE i.status = 'PENDING_REVIEW'
              AND i.is_deleted = FALSE
              AND u.is_deleted = FALSE
            ORDER BY CASE WHEN i.ai_result ->> 'flag' = 'REVIEW' THEN 0 ELSE 1 END,
                     u.trust_stars ASC,
                     i.created_at ASC,
                     i.id ASC
            """;

    @PersistenceContext
    private EntityManager entityManager;

    public List<Object[]> findPendingReviewQueue(int offset, int limit) {
        return entityManager
                .createNativeQuery(PENDING_REVIEW_QUERY)
                .setFirstResult(offset)
                .setMaxResults(limit)
                .getResultList();
    }
}
