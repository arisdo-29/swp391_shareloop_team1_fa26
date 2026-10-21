package com.shareloop.review.repository;

import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

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

    /**
     * Đếm bài trong hàng chờ duyệt đã chờ tới mốc {@code cutoff} (gồm cả đúng mốc) mà chưa được duyệt (FR-121).
     * Thời gian chờ tính từ updated_at: bài sửa xong quay lại PENDING_REVIEW được tính lại từ lúc gửi bản sửa.
     *
     * @param cutoff now trừ số giờ SLA
     * @return số bài quá SLA
     */
    @Query(value = """
            SELECT COUNT(*)
            FROM items i
            JOIN users u ON u.id = i.donor_id
            WHERE i.status = 'PENDING_REVIEW'
              AND i.is_deleted = FALSE
              AND u.is_deleted = FALSE
              AND i.updated_at <= :cutoff
            """, nativeQuery = true)
    long countPendingReviewWaitingSince(@Param("cutoff") Instant cutoff);
}
