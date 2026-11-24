package com.shareloop.review.repository;

import java.util.Collection;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

public interface ReviewQueueRepository extends Repository<com.shareloop.item.entity.Item, Long> {

    String QUEUED_AT = "COALESCE(i.review_submitted_at, i.edit_submitted_at, i.created_at)";

    String QUEUE_COLUMNS = """
            SELECT i.id AS id,
                   i.donor_id AS donorId,
                   i.title AS title,
                   i.ai_risk AS aiRisk,
                   COALESCE(i.ai_result ->> 'flag', i.ai_result ->> 'moderation', 'NONE') AS moderationFlag,
                   COALESCE(i.ai_result ->> 'reason', i.ai_result ->> 'moderationReason') AS flagReason,
                   u.trust_stars AS donorTrustStars,
                   i.created_at AS createdAt,
                   CAST(i.ai_result -> 'matchedTerms' AS TEXT) AS matchedTerms,
                   COALESCE(i.approved_edit_count, 0) AS approvedEditCount,
                   (i.approved_at IS NOT NULL) AS isEdited,
                   """ + QUEUED_AT + " AS queuedAt\n";

    String QUEUE_FROM = """
            FROM items i
            JOIN users u ON u.id = i.donor_id
            WHERE i.status = 'PENDING_REVIEW'
              AND i.is_deleted = FALSE
              AND u.is_deleted = FALSE
            """;

    String APPROVED_COUNT = """
            (SELECT COUNT(*) FROM items approved
             WHERE approved.donor_id = i.donor_id AND approved.approved_at IS NOT NULL)
            """;

    String QUEUE_FILTER = """
              AND (CAST(:categoryId AS bigint) IS NULL OR i.category_id = :categoryId)
              AND (:q = '' OR strpos(f_unaccent(lower(i.title)), f_unaccent(lower(:q))) > 0)
              AND (CAST(:edited AS boolean) IS NULL OR (i.approved_at IS NOT NULL) = :edited)
              AND (CAST(:maxStars AS integer) IS NULL OR u.trust_stars <= :maxStars)
              AND (:repeatedPair = FALSE OR i.donor_id IN (:repeatedDonorIds))
              AND (CAST(:newcomerMaxApproved AS integer) IS NULL OR
            """ + APPROVED_COUNT + " <= :newcomerMaxApproved)\n";

    // Thứ tự KD-06 cho endpoint tương thích cũ.
    @Query(value = QUEUE_COLUMNS + QUEUE_FROM + """
            ORDER BY u.trust_stars ASC,
                     CASE WHEN
            """ + APPROVED_COUNT + """
                     <= :newcomerMaxApproved
                          THEN 0 ELSE 1 END ASC,
                     i.created_at ASC,
                     i.id ASC
            """, nativeQuery = true)
    List<Object[]> findPendingReviewQueue(Pageable pageable, @Param("newcomerMaxApproved") int newcomerMaxApproved);

    @Query(
            value = QUEUE_COLUMNS + QUEUE_FROM + QUEUE_FILTER + " ORDER BY " + QUEUED_AT + ", i.id ASC",
            countQuery = "SELECT COUNT(*) " + QUEUE_FROM + QUEUE_FILTER,
            nativeQuery = true)
    Page<Object[]> findFilteredQueue(
            Pageable pageable,
            @Param("q") String q,
            @Param("categoryId") Long categoryId,
            @Param("edited") Boolean edited,
            @Param("maxStars") Integer maxStars,
            @Param("repeatedPair") boolean repeatedPair,
            @Param("repeatedDonorIds") Collection<Long> repeatedDonorIds,
            @Param("newcomerMaxApproved") Integer newcomerMaxApproved);

    // Thông tin kiểm duyệt của một bài ở mọi trạng thái (Admin xem chi tiết); cùng cách đọc ai_result như hàng chờ.
    @Query(value = """
            SELECT i.ai_risk AS aiRisk,
                   COALESCE(i.ai_result ->> 'flag', i.ai_result ->> 'moderation', 'NONE') AS moderationFlag,
                   COALESCE(i.ai_result ->> 'reason', i.ai_result ->> 'moderationReason') AS flagReason,
                   u.trust_stars AS donorTrustStars,
                   CAST(i.ai_result -> 'matchedTerms' AS TEXT) AS matchedTerms,
                   COALESCE(i.approved_edit_count, 0) AS approvedEditCount,
                   (i.approved_at IS NOT NULL) AS isEdited
            FROM items i
            JOIN users u ON u.id = i.donor_id
            WHERE i.id = :itemId
              AND i.is_deleted = FALSE
            """, nativeQuery = true)
    List<Object[]> findModerationInfo(@Param("itemId") long itemId);

    /**
     * Mọi bài đang chờ duyệt của một chủ bài, kể cả bài miễn phí (giữ phí 0) mà truy vấn giữ phí bỏ qua (L5). Dùng khi
     * khoá tài khoản để từ chối hết, không để bài nào còn trong hàng chờ.
     */
    @Query(value = """
            SELECT i.id
            FROM items i
            WHERE i.donor_id = :donorId
              AND i.status = 'PENDING_REVIEW'
              AND i.is_deleted = FALSE
            ORDER BY i.id
            """, nativeQuery = true)
    List<Long> findPendingReviewItemIdsByDonor(@Param("donorId") long donorId);
}
