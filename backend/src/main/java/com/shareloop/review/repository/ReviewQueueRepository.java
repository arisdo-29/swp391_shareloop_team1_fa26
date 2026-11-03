package com.shareloop.review.repository;

import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

public interface ReviewQueueRepository extends Repository<com.shareloop.item.entity.Item, Long> {

    // BR-A08: không xét cờ AI. Ít sao uy tín trước; cùng số sao thì người mới (số bài đã từng được duyệt, tức
    // approved_at khác rỗng, không quá :newcomerMaxApproved) trước; rồi bài chờ lâu trước.
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
            ORDER BY u.trust_stars ASC,
                     CASE WHEN (SELECT COUNT(*)
                                FROM items approved
                                WHERE approved.donor_id = i.donor_id
                                  AND approved.approved_at IS NOT NULL) <= :newcomerMaxApproved
                          THEN 0 ELSE 1 END ASC,
                     i.created_at ASC,
                     i.id ASC
            """, nativeQuery = true)
    List<Object[]> findPendingReviewQueue(Pageable pageable, @Param("newcomerMaxApproved") int newcomerMaxApproved);

    // Thông tin kiểm duyệt của một bài ở mọi trạng thái (Admin xem chi tiết); cùng cách đọc ai_result như hàng chờ.
    @Query(value = """
            SELECT i.ai_risk AS aiRisk,
                   COALESCE(i.ai_result ->> 'flag', i.ai_result ->> 'moderation', 'NONE') AS moderationFlag,
                   COALESCE(i.ai_result ->> 'reason', i.ai_result ->> 'moderationReason') AS flagReason,
                   u.trust_stars AS donorTrustStars,
                   CAST(i.ai_result -> 'matchedTerms' AS TEXT) AS matchedTerms
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
