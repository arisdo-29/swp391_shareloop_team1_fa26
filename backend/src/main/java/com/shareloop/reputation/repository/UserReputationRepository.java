package com.shareloop.reputation.repository;

import com.shareloop.reputation.dto.RequestParticipantProjection;
import com.shareloop.reputation.entity.UserReputation;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
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
            SELECT i.donor_id AS donorId, r.receiver_id AS receiverId, r.type AS type, r.status AS status,
                   i.id AS itemId, i.title AS itemTitle
            FROM requests r
            JOIN items i ON r.item_id = i.id
            WHERE r.id = :requestId
            """, nativeQuery = true)
    RequestParticipantProjection findRequestParticipant(@Param("requestId") Long requestId);

    /**
     * Đếm giao dịch COMPLETED giữa hai người dùng, tính cả hai chiều cho và nhận, hoàn tất từ mốc {@code since}
     * (BR-U08). Bỏ qua request đang xét để kết quả không phụ thuộc việc trạng thái của nó đã ghi xuống DB hay chưa.
     *
     * @param userA một bên của cặp
     * @param userB bên còn lại
     * @param since mốc bắt đầu cửa sổ tính cặp (gồm)
     * @param excludeRequestId request đang được cộng điểm
     * @return số giao dịch hoàn tất trước đó của cặp trong cửa sổ
     */
    @Query(value = """
            SELECT COUNT(*)
            FROM requests r
            JOIN items i ON r.item_id = i.id
            WHERE r.status = 'COMPLETED'
              AND r.is_deleted = FALSE
              AND r.id <> :excludeRequestId
              AND r.completed_at >= :since
              AND ((i.donor_id = :userA AND r.receiver_id = :userB)
                OR (i.donor_id = :userB AND r.receiver_id = :userA))
            """, nativeQuery = true)
    long countCompletedBetween(
            @Param("userA") Long userA,
            @Param("userB") Long userB,
            @Param("since") Instant since,
            @Param("excludeRequestId") Long excludeRequestId);

    /**
     * Các cặp người dùng có hơn {@code threshold} giao dịch COMPLETED với nhau, tính cả hai chiều cho và nhận, hoàn tất
     * từ mốc {@code since} (FR-113, BR-U08). Cùng cách đếm cặp với {@link #countCompletedBetween}.
     *
     * @param since mốc bắt đầu cửa sổ tính cặp (gồm)
     * @param threshold chỉ lấy cặp có số giao dịch lớn hơn số này
     * @return mỗi dòng gồm id nhỏ hơn, id lớn hơn, số giao dịch, thời điểm hoàn tất gần nhất; nhiều giao dịch nhất
     *     lên trước
     */
    @Query(value = """
            SELECT LEAST(i.donor_id, r.receiver_id) AS user_a_id,
                   GREATEST(i.donor_id, r.receiver_id) AS user_b_id,
                   COUNT(*) AS completed_count,
                   MAX(r.completed_at) AS last_completed_at
            FROM requests r
            JOIN items i ON r.item_id = i.id
            WHERE r.status = 'COMPLETED'
              AND r.is_deleted = FALSE
              AND r.completed_at >= :since
            GROUP BY LEAST(i.donor_id, r.receiver_id), GREATEST(i.donor_id, r.receiver_id)
            HAVING COUNT(*) > :threshold
            ORDER BY completed_count DESC, last_completed_at DESC
            """, nativeQuery = true)
    List<Object[]> findRepeatedPairs(@Param("since") Instant since, @Param("threshold") long threshold);

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

    /**
     * Đếm số giao dịch COMPLETED mà người dùng làm chủ bài và bên nhận đã trả lời "đúng mô tả?". Dùng để chỉ hiện tỉ lệ
     * đúng mô tả khi đủ số giao dịch tối thiểu (SRS 6.12).
     */
    @Query(value = """
            SELECT COUNT(*)
            FROM requests r
            JOIN items i ON r.item_id = i.id
            WHERE i.donor_id = :userId AND r.status = 'COMPLETED' AND r.as_described IS NOT NULL
            """, nativeQuery = true)
    long countRatedCompletedAsDonor(@Param("userId") Long userId);
}
