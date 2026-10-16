package com.shareloop.reputation.repository;

import com.shareloop.reputation.entity.UserReputation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * Repository thao tác với lát cắt UserReputation của bảng users.
 */
@Repository
public interface UserReputationRepository extends JpaRepository<UserReputation, Long> {

    /**
     * Tính tỉ lệ đúng mô tả (%) của người dùng khi làm chủ bài (donor) trong các giao dịch COMPLETED.
     *
     * @param userId id người dùng cần tính tỉ lệ
     * @return tỉ lệ đúng mô tả từ 0.0 đến 100.0, hoặc null nếu chưa có giao dịch nào được đánh giá
     */
    @Query(value = """
            SELECT CAST(
                COUNT(CASE WHEN r.as_described = TRUE THEN 1 END) * 100.0 /
                NULLIF(COUNT(CASE WHEN r.as_described IS NOT NULL THEN 1 END), 0)
                AS DOUBLE PRECISION)
            FROM requests r
            JOIN items i ON r.item_id = i.id
            WHERE i.donor_id = :userId AND r.status = 'COMPLETED'
            """, nativeQuery = true)
    Double calculateAsDescribedRate(@Param("userId") Long userId);
}
