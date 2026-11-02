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
     * Danh sách yêu cầu gửi tới một bài đăng theo trạng thái cụ thể.
     */
    List<Request> findByItemIdAndStatus(Long itemId, RequestStatus status);

    /**
     * Danh sách yêu cầu đang dùng một món đề nghị theo trạng thái cụ thể (FR-66).
     */
    List<Request> findByOfferedItemIdAndStatus(Long offeredItemId, RequestStatus status);

    /**
     * Yêu cầu của một người nhận đang ở một trong các trạng thái cho trước (dọn dẹp khi khoá tài khoản).
     */
    List<Request> findByReceiverIdAndStatusIn(Long receiverId, Collection<RequestStatus> statuses);

    /**
     * Yêu cầu gửi tới một nhóm bài đăng đang ở một trong các trạng thái cho trước (dọn dẹp khi khoá chủ bài).
     */
    List<Request> findByItemIdInAndStatusIn(Collection<Long> itemIds, Collection<RequestStatus> statuses);

    /**
     * Danh sách yêu cầu do người dùng gửi đi, sắp theo thời gian tạo mới nhất lên trước.
     */
    List<Request> findByReceiverIdOrderByCreatedAtDesc(Long receiverId);

    /**
     * Mọi yêu cầu, mới nhất trước, cho danh sách giao dịch của Admin.
     */
    List<Request> findAllByOrderByCreatedAtDesc();

    /**
     * Yêu cầu theo trạng thái, mới nhất trước, cho danh sách giao dịch của Admin.
     */
    List<Request> findByStatusOrderByCreatedAtDesc(RequestStatus status);

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

    /**
     * Tìm các yêu cầu ở trạng thái cụ thể được tạo trước một mốc thời gian (FR-119, BR-R06).
     * Phân trang kiểu keyset theo id > afterId, tối đa limit bản ghi (Mục Q).
     */
    @Query("""
        SELECT r FROM Request r
        WHERE r.status = :status
        AND r.createdAt < :cutoff
        AND r.id > :afterId
        ORDER BY r.id ASC
    """)
    List<Request> findByStatusAndCreatedAtBefore(
            @Param("status") RequestStatus status,
            @Param("cutoff") java.time.Instant cutoff,
            @Param("afterId") long afterId,
            org.springframework.data.domain.Pageable pageable);

    /**
     * Tìm các yêu cầu RESERVED hoặc AWAITING_LOGISTICS quá hạn chưa chốt lịch (FR-120, BR-T10, FR-77, BR-C03).
     * Phân trang kiểu keyset theo id > afterId, tối đa limit bản ghi (Mục Q).
     */
    @Query("""
        SELECT r FROM Request r
        WHERE r.status IN (
            com.shareloop.request.entity.RequestStatus.RESERVED,
            com.shareloop.request.entity.RequestStatus.AWAITING_LOGISTICS
        )
        AND r.reservedAt IS NOT NULL
        AND r.reservedAt < :cutoff
        AND r.id > :afterId
        ORDER BY r.id ASC
    """)
    List<Request> findSilentReservedRequests(
            @Param("cutoff") java.time.Instant cutoff,
            @Param("afterId") long afterId,
            org.springframework.data.domain.Pageable pageable);

    /**
     * Tìm các yêu cầu AWAITING_HANDOVER có đúng một bên xác nhận quá hạn im lặng (FR-87, BR-T06).
     * Phân trang kiểu keyset theo id > afterId, tối đa limit bản ghi (Mục Q).
     */
    @Query("""
        SELECT r FROM Request r
        WHERE r.status = com.shareloop.request.entity.RequestStatus.AWAITING_HANDOVER
        AND (
            (r.donorHandoverAt IS NOT NULL AND r.receiverHandoverAt IS NULL AND r.donorHandoverAt < :cutoff)
            OR
            (r.receiverHandoverAt IS NOT NULL AND r.donorHandoverAt IS NULL AND r.receiverHandoverAt < :cutoff)
        )
        AND r.id > :afterId
        ORDER BY r.id ASC
    """)
    List<Request> findOverdueHandoverRequests(
            @Param("cutoff") java.time.Instant cutoff,
            @Param("afterId") long afterId,
            org.springframework.data.domain.Pageable pageable);

    /**
     * Đếm giao dịch COMPLETED mà người dùng tham gia, ở vai người nhận hoặc chủ bài (hồ sơ công khai).
     * Dùng native query vì donor_id nằm ở bảng items của module item, entity Request chỉ giữ item_id;
     * cùng cách với {@link #hasActiveDelivery}.
     */
    @Query(value = """
        SELECT COUNT(*) FROM requests r
        WHERE (r.receiver_id = :userId OR EXISTS (
            SELECT 1 FROM items i WHERE i.id = r.item_id AND i.donor_id = :userId
        ))
        AND r.status = 'COMPLETED'
        AND r.is_deleted = false
    """, nativeQuery = true)
    long countCompletedByUser(@Param("userId") long userId);

    /**
     * Các yêu cầu COMPLETED gắn với một nhóm bài, ở vai bài chính (item_id) hoặc món đề nghị khi trao đổi
     * (offered_item_id). Dùng để lấy thời điểm hoàn tất cho thẻ bài đã giao dịch.
     */
    @Query("""
        SELECT r FROM Request r
        WHERE r.status = com.shareloop.request.entity.RequestStatus.COMPLETED
        AND (r.itemId IN :itemIds OR r.offeredItemId IN :itemIds)
    """)
    List<Request> findCompletedByItemIds(@Param("itemIds") Collection<Long> itemIds);
}
