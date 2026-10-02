package com.shareloop.request.repository;

import com.shareloop.request.entity.Request;
import com.shareloop.request.entity.RequestStatus;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RequestRepository extends JpaRepository<Request, Long> {

    /**
     * Đếm số lượng yêu cầu đang ở trạng thái PENDING của một người (BR-U02: tối đa 5).
     */
    long countByReceiverIdAndStatus(Long receiverId, RequestStatus status);

    /**
     * Kiểm tra người dùng đã có yêu cầu đang hoạt động cho bài này chưa (BR-R02, ux_requests_active).
     */
    boolean existsByItemIdAndReceiverIdAndStatusIn(Long itemId, Long receiverId, Collection<RequestStatus> statuses);

    /**
     * Danh sách yêu cầu gửi tới một bài đăng, sắp theo thời gian tạo mới nhất lên trước.
     */
    List<Request> findByItemIdOrderByCreatedAtDesc(Long itemId);

    /**
     * Danh sách yêu cầu do người dùng gửi đi, sắp theo thời gian tạo mới nhất lên trước.
     */
    List<Request> findByReceiverIdOrderByCreatedAtDesc(Long receiverId);

    /**
     * Kiểm tra bài đăng đã có yêu cầu ở trạng thái RESERVED trở lên hay chưa (F07 bước 1, BR-D04).
     */
    @Query("""
        SELECT COUNT(r) > 0 FROM Request r
        WHERE r.itemId = :itemId
        AND r.status IN (
            com.shareloop.request.entity.RequestStatus.RESERVED,
            com.shareloop.request.entity.RequestStatus.AWAITING_LOGISTICS,
            com.shareloop.request.entity.RequestStatus.LOGISTICS_CONFIRMED,
            com.shareloop.request.entity.RequestStatus.AWAITING_HANDOVER,
            com.shareloop.request.entity.RequestStatus.DISPUTED
        )
    """)
    boolean hasReservedOrHigherRequest(@Param("itemId") long itemId);

    /**
     * Kiểm tra người dùng (vai donor hoặc receiver) có đang có giao dịch trong giai đoạn giao nhận hay không (F03, BR-U10).
     */
    @Query(value = """
        SELECT EXISTS (
            SELECT 1 FROM requests r
            WHERE (r.receiver_id = :userId OR EXISTS (
                SELECT 1 FROM items i WHERE i.id = r.item_id AND i.donor_id = :userId AND i.is_deleted = false
            ))
            AND r.status IN ('LOGISTICS_CONFIRMED', 'AWAITING_HANDOVER')
            AND r.is_deleted = false
        )
    """, nativeQuery = true)
    boolean hasActiveDelivery(@Param("userId") long userId);
}
