package com.shareloop.reputation.repository;

import com.shareloop.reputation.dto.RequestParticipantProjection;
import com.shareloop.reputation.entity.UserReputation;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * Repository thao tác với lát cắt UserReputation của bảng users.
 */
@Repository
public interface UserReputationRepository extends JpaRepository<UserReputation, Long> {

    /**
     * Khoá dòng để cập nhật điểm hoặc trừ sao, tránh ghi đè khi có nhiều giao dịch hoàn tất song song.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT u FROM UserReputation u WHERE u.id = :id")
    Optional<UserReputation> findByIdForUpdate(@Param("id") Long id);

    /**
     * Lấy thông tin hai bên tham gia giao dịch và loại yêu cầu (Cho hoặc Trao đổi) khi hoàn tất.
     */
    @Query(value = """
            SELECT i.donor_id AS donorId, r.receiver_id AS receiverId, r.type AS type, r.status AS status
            FROM requests r
            JOIN items i ON r.item_id = i.id
            WHERE r.id = :requestId
            """, nativeQuery = true)
    RequestParticipantProjection findRequestParticipant(@Param("requestId") Long requestId);

    /**
     * Kiểm tra xem giao dịch đã được ghi nhận cộng điểm uy tín hay chưa qua bảng activity_logs (Idempotency).
     */
    @Query(value = """
            SELECT COUNT(1) > 0
            FROM activity_logs
            WHERE target_type = 'REQUEST' AND target_id = :requestId AND type = 'POINTS'
            """, nativeQuery = true)
    boolean isReputationAppliedForRequest(@Param("requestId") Long requestId);

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
