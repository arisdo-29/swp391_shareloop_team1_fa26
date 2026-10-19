package com.shareloop.review.repository;

import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;

public interface ReviewQueueRepository extends Repository<com.shareloop.item.entity.Item, Long> {

    @Query(value = """
            SELECT i.id AS id,
                   i.donor_id AS donorId,
                   i.title AS title,
                   i.ai_risk AS aiRisk,
                   COALESCE(i.ai_result ->> 'flag', i.ai_result ->> 'moderation', 'NONE') AS moderationFlag,
                   COALESCE(i.ai_result ->> 'reason', i.ai_result ->> 'moderationReason') AS flagReason,
                   u.trust_stars AS donorTrustStars,
                   i.created_at AS createdAt,
                   CAST(i.ai_result -> 'matchedTerms' AS TEXT) AS matchedTerms
            FROM items i
            JOIN users u ON u.id = i.donor_id
            WHERE i.status = 'PENDING_REVIEW'
              AND i.is_deleted = FALSE
              AND u.is_deleted = FALSE
            ORDER BY CASE WHEN COALESCE(i.ai_result ->> 'flag', i.ai_result ->> 'moderation') = 'REVIEW'
                          THEN 0 ELSE 1 END,
                     u.trust_stars ASC,
                     u.created_at DESC,
                     i.created_at ASC,
                     i.id ASC
            """, nativeQuery = true)
    List<Object[]> findPendingReviewQueue(Pageable pageable);
}
