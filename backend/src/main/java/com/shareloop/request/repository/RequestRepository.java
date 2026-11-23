package com.shareloop.request.repository;

import com.shareloop.request.entity.Request;
import com.shareloop.request.entity.RequestStatus;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RequestRepository extends JpaRepository<Request, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT r FROM Request r WHERE r.id = :id")
    Optional<Request> findByIdForScheduleUpdate(@Param("id") long id);

    /** Khoá dòng khi đổi trạng thái giao dịch đã chốt lịch (đề nghị huỷ, dời lịch, gửi hàng, nhận hàng, job). */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT r FROM Request r WHERE r.id = :id")
    Optional<Request> findByIdForUpdate(@Param("id") long id);

    /**
     * Đề nghị huỷ đã quá hạn phản hồi (GN-06). Phân trang kiểu keyset theo id như các việc nền khác.
     */
    @Query("""
        SELECT r FROM Request r
        WHERE r.cancelRequestedAt IS NOT NULL
        AND r.cancelRequestedAt < :cutoff
        AND r.status IN (com.shareloop.request.entity.RequestStatus.LOGISTICS_CONFIRMED,
                         com.shareloop.request.entity.RequestStatus.AWAITING_HANDOVER)
        AND r.id > :afterId
        ORDER BY r.id ASC
    """)
    List<Request> findExpiredCancelRequests(
            @Param("cutoff") Instant cutoff,
            @Param("afterId") long afterId,
            org.springframework.data.domain.Pageable pageable);

    /**
     * Đề nghị dời lịch đã hết hạn (GN-11): quá số giờ phản hồi hoặc đã tới giờ hẹn cũ, tuỳ cái nào tới trước.
     */
    @Query("""
        SELECT r FROM Request r
        WHERE r.rescheduleProposedAt IS NOT NULL
        AND r.status IN (com.shareloop.request.entity.RequestStatus.LOGISTICS_CONFIRMED,
                         com.shareloop.request.entity.RequestStatus.AWAITING_HANDOVER)
        AND (r.rescheduleProposedAt < :cutoff
             OR (r.meetingTime <= :now AND (r.deliveryMethod IS NULL OR r.deliveryMethod <> 'SHIPPING')))
        AND r.id > :afterId
        ORDER BY r.id ASC
    """)
    List<Request> findExpiredReschedules(
            @Param("now") Instant now,
            @Param("cutoff") Instant cutoff,
            @Param("afterId") long afterId,
            org.springframework.data.domain.Pageable pageable);

    /**
     * Giao dịch Gặp trực tiếp đã chốt lịch, qua giờ hẹn trước cutoff mà chưa bên nào bấm gì và không có đề nghị huỷ
     * đang chờ (GN-12). Dùng cho cả việc nhắc và việc tự huỷ.
     */
    @Query("""
        SELECT r FROM Request r
        WHERE r.status = com.shareloop.request.entity.RequestStatus.LOGISTICS_CONFIRMED
        AND (r.deliveryMethod IS NULL OR r.deliveryMethod <> 'SHIPPING')
        AND r.cancelRequestedAt IS NULL
        AND r.meetingTime < :cutoff
        AND r.id > :afterId
        ORDER BY r.id ASC
    """)
    List<Request> findConfirmedPastMeeting(
            @Param("cutoff") Instant cutoff,
            @Param("afterId") long afterId,
            org.springframework.data.domain.Pageable pageable);

    /**
     * Giao dịch Giao hàng có chiều đã gửi trước cutoff mà bên nhận chưa xác nhận (GN-09, GN-12). Việc nền tự ghi nhận Đã
     * nhận cho từng chiều như vậy.
     */
    @Query("""
        SELECT r FROM Request r
        WHERE r.status = com.shareloop.request.entity.RequestStatus.AWAITING_HANDOVER
        AND r.deliveryMethod = 'SHIPPING'
        AND ((r.donorShippedAt < :cutoff AND r.receiverHandoverAt IS NULL)
             OR (r.receiverShippedAt < :cutoff AND r.donorHandoverAt IS NULL))
        AND r.id > :afterId
        ORDER BY r.id ASC
    """)
    List<Request> findShippingLegsOverdue(
            @Param("cutoff") Instant cutoff,
            @Param("afterId") long afterId,
            org.springframework.data.domain.Pageable pageable);

    /**
     * Giao dịch Giao hàng còn chiều chưa gửi mà ngày gửi dự kiến trước cutoff (GN-12). Dùng cho việc nhắc hạn gửi.
     */
    @Query("""
        SELECT r FROM Request r
        WHERE r.status IN (com.shareloop.request.entity.RequestStatus.LOGISTICS_CONFIRMED,
                           com.shareloop.request.entity.RequestStatus.AWAITING_HANDOVER)
        AND r.deliveryMethod = 'SHIPPING'
        AND r.meetingTime < :cutoff
        AND (r.donorShippedAt IS NULL OR (r.offeredItemId IS NOT NULL AND r.receiverShippedAt IS NULL))
        AND r.id > :afterId
        ORDER BY r.id ASC
    """)
    List<Request> findShippingWithUnsentLeg(
            @Param("cutoff") Instant cutoff,
            @Param("afterId") long afterId,
            org.springframework.data.domain.Pageable pageable);

    /**
     * Giao dịch Giao hàng đã chốt lịch, chưa chiều nào gửi, ngày gửi dự kiến trước cutoff và không có đề nghị huỷ đang
     * chờ (GN-12). Dùng cho việc nhắc hạn gửi và việc tự huỷ.
     */
    @Query("""
        SELECT r FROM Request r
        WHERE r.status = com.shareloop.request.entity.RequestStatus.LOGISTICS_CONFIRMED
        AND r.deliveryMethod = 'SHIPPING'
        AND r.cancelRequestedAt IS NULL
        AND r.meetingTime < :cutoff
        AND r.id > :afterId
        ORDER BY r.id ASC
    """)
    List<Request> findShippingNothingSent(
            @Param("cutoff") Instant cutoff,
            @Param("afterId") long afterId,
            org.springframework.data.domain.Pageable pageable);

    /**
     * Giao dịch Giao hàng đã có chiều Đã nhận mà chiều còn lại chưa gửi, ngày gửi dự kiến trước cutoff (GN-12): kẹt một
     * chiều, việc nền sẽ chuyển sang DISPUTED để Admin xử lý.
     */
    @Query("""
        SELECT r FROM Request r
        WHERE r.status = com.shareloop.request.entity.RequestStatus.AWAITING_HANDOVER
        AND r.deliveryMethod = 'SHIPPING'
        AND r.offeredItemId IS NOT NULL
        AND ((r.receiverHandoverAt IS NOT NULL AND r.receiverShippedAt IS NULL)
             OR (r.donorHandoverAt IS NOT NULL AND r.donorShippedAt IS NULL))
        AND r.meetingTime < :cutoff
        AND r.id > :afterId
        ORDER BY r.id ASC
    """)
    List<Request> findShippingOneLegStuck(
            @Param("cutoff") Instant cutoff,
            @Param("afterId") long afterId,
            org.springframework.data.domain.Pageable pageable);

    /** Giao dịch chưa chốt lịch (RESERVED, AWAITING_LOGISTICS) được chọn trước cutoff, cho việc nhắc trước khi tự huỷ. */
    @Query("""
        SELECT r FROM Request r
        WHERE r.status IN (com.shareloop.request.entity.RequestStatus.RESERVED,
                           com.shareloop.request.entity.RequestStatus.AWAITING_LOGISTICS)
        AND r.reservedAt < :cutoff
        AND r.id > :afterId
        ORDER BY r.id ASC
    """)
    List<Request> findReservedBefore(
            @Param("cutoff") Instant cutoff,
            @Param("afterId") long afterId,
            org.springframework.data.domain.Pageable pageable);

    /** Giao dịch có đề nghị huỷ đang chờ phản hồi, cho việc nhắc trước hạn. */
    @Query("""
        SELECT r FROM Request r
        WHERE r.cancelRequestedAt IS NOT NULL
        AND r.status IN (com.shareloop.request.entity.RequestStatus.LOGISTICS_CONFIRMED,
                         com.shareloop.request.entity.RequestStatus.AWAITING_HANDOVER)
        AND r.id > :afterId
        ORDER BY r.id ASC
    """)
    List<Request> findWithPendingCancelRequest(
            @Param("afterId") long afterId, org.springframework.data.domain.Pageable pageable);

    /** Giao dịch có đề nghị dời lịch đang chờ phản hồi, cho việc nhắc trước hạn. */
    @Query("""
        SELECT r FROM Request r
        WHERE r.rescheduleProposedAt IS NOT NULL
        AND r.status IN (com.shareloop.request.entity.RequestStatus.LOGISTICS_CONFIRMED,
                         com.shareloop.request.entity.RequestStatus.AWAITING_HANDOVER)
        AND r.id > :afterId
        ORDER BY r.id ASC
    """)
    List<Request> findWithPendingReschedule(
            @Param("afterId") long afterId, org.springframework.data.domain.Pageable pageable);

    /** Giao dịch Gặp trực tiếp đã chốt lịch có giờ hẹn trong khoảng [from, to), cho việc nhắc trước giờ hẹn. */
    @Query("""
        SELECT r FROM Request r
        WHERE r.status = com.shareloop.request.entity.RequestStatus.LOGISTICS_CONFIRMED
        AND (r.deliveryMethod IS NULL OR r.deliveryMethod <> 'SHIPPING')
        AND r.meetingTime >= :from AND r.meetingTime < :to
        AND r.id > :afterId
        ORDER BY r.id ASC
    """)
    List<Request> findMeetingBetween(
            @Param("from") Instant from,
            @Param("to") Instant to,
            @Param("afterId") long afterId,
            org.springframework.data.domain.Pageable pageable);

    /** Giao dịch đang chờ giao nhận, cho việc nhắc trước khi tự hoàn tất. */
    @Query("""
        SELECT r FROM Request r
        WHERE r.status = com.shareloop.request.entity.RequestStatus.AWAITING_HANDOVER
        AND r.id > :afterId
        ORDER BY r.id ASC
    """)
    List<Request> findAwaitingHandover(
            @Param("afterId") long afterId, org.springframework.data.domain.Pageable pageable);

    /**
     * Người đề nghị huỷ từ threshold giao dịch trở lên (đã huỷ vì chính đề nghị đó, đã thấy liên hệ) kể từ since.
     */
    @Query("""
        SELECT new com.shareloop.request.dto.RepeatedCancelerRow(
            r.cancelRequestedBy, COUNT(r), MAX(r.cancelRequestedAt))
        FROM Request r
        WHERE r.status = com.shareloop.request.entity.RequestStatus.CANCELLED
        AND r.cancelRequestedBy IS NOT NULL
        AND r.contactRevealedAt IS NOT NULL
        AND r.cancelRequestedAt >= :since
        GROUP BY r.cancelRequestedBy
        HAVING COUNT(r) >= :threshold
        ORDER BY COUNT(r) DESC
    """)
    List<com.shareloop.request.dto.RepeatedCancelerRow> findRepeatedCancelers(
            @Param("since") Instant since, @Param("threshold") long threshold);

    /**
     * Đếm số lượng yêu cầu đang ở trạng thái PENDING của một người (YC-03: tối đa 5).
     */
    long countByReceiverIdAndStatus(Long receiverId, RequestStatus status);

    /** Lần tự huỷ gần nhất của người nhận cho một bài, để tính thời gian chờ gửi lại (C5). */
    Optional<Request> findFirstByItemIdAndReceiverIdAndStatusAndCancelReasonOrderByUpdatedAtDesc(
            Long itemId, Long receiverId, RequestStatus status, String cancelReason);

    /** Đếm giao dịch đang chạy của người nhận (C4): các trạng thái từ RESERVED tới AWAITING_HANDOVER. */
    long countByReceiverIdAndStatusIn(Long receiverId, Collection<RequestStatus> statuses);

    /**
     * Kiểm tra người dùng đã có yêu cầu đang hoạt động cho bài này chưa (YC-03, ux_requests_active).
     */
    boolean existsByItemIdAndReceiverIdAndStatusIn(Long itemId, Long receiverId, Collection<RequestStatus> statuses);

    /**
     * Người dùng đã từng gửi yêu cầu tới bài này chưa, ở bất kỳ trạng thái nào (quyền xem lại bài ở chế độ chỉ xem).
     */
    boolean existsByItemIdAndReceiverId(Long itemId, Long receiverId);

    /**
     * Danh sách yêu cầu gửi tới một bài đăng, sắp theo thời gian tạo mới nhất lên trước.
     */
    List<Request> findByItemIdOrderByCreatedAtDesc(Long itemId);

    /**
     * Danh sách yêu cầu gửi tới một bài đăng theo trạng thái cụ thể.
     */
    List<Request> findByItemIdAndStatus(Long itemId, RequestStatus status);

    /**
     * Danh sách yêu cầu đang dùng một món đề nghị theo trạng thái cụ thể (YC-07).
     */
    List<Request> findByOfferedItemIdAndStatus(Long offeredItemId, RequestStatus status);

    List<Request> findByOfferedItemIdAndStatusIn(Long offeredItemId, Collection<RequestStatus> statuses);

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
     * Trang giao dịch cho Admin, lọc và chia trang ngay trong câu truy vấn (G2). type và delivery truyền chuỗi rỗng là không lọc,
     * from và to luôn có giá trị (người gọi truyền mốc rất xa khi không lọc ngày). Thứ tự do Pageable quyết định.
     */
    @org.springframework.data.jpa.repository.Query(
            "select r from Request r where r.status in :statuses and (:type = '' or r.type = :type)"
                    + " and (:delivery = '' or r.deliveryMethod = :delivery) and r.createdAt >= :from and r.createdAt < :to")
    org.springframework.data.domain.Page<Request> findForAdmin(
            @org.springframework.data.repository.query.Param("statuses") Collection<RequestStatus> statuses,
            @org.springframework.data.repository.query.Param("type") String type,
            @org.springframework.data.repository.query.Param("delivery") String delivery,
            @org.springframework.data.repository.query.Param("from") Instant from,
            @org.springframework.data.repository.query.Param("to") Instant to,
            org.springframework.data.domain.Pageable pageable);

    /**
     * Yêu cầu theo trạng thái, mới nhất trước, cho danh sách giao dịch của Admin.
     */
    List<Request> findByStatusOrderByCreatedAtDesc(RequestStatus status);

    /**
     * Kiểm tra bài đăng đã có yêu cầu ở trạng thái RESERVED trở lên hay chưa (F07 bước 1, BD-10).
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
     * Kiểm tra người dùng (vai donor hoặc receiver) có đang có giao dịch trong giai đoạn giao nhận hay không (F03, TK-11).
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
     * Kiểm tra người dùng có phải là một bên tham gia trong yêu cầu đã được chọn của bài đăng hay không (G06).
     * Áp dụng cho cả bài chính (item_id) và món đề nghị trao đổi (offered_item_id).
     */
    @Query(value = """
        SELECT EXISTS (
            SELECT 1 FROM requests r
            WHERE (r.item_id = :itemId OR r.offered_item_id = :itemId)
            AND (
                r.receiver_id = :userId
                OR EXISTS (
                    SELECT 1 FROM items i
                    WHERE i.id = r.item_id AND i.donor_id = :userId AND i.is_deleted = false
                )
            )
            AND r.status IN ('RESERVED', 'AWAITING_LOGISTICS', 'LOGISTICS_CONFIRMED', 'AWAITING_HANDOVER', 'DISPUTED')
            AND r.is_deleted = false
        )
    """, nativeQuery = true)
    boolean isParticipantInActiveRequest(@Param("itemId") long itemId, @Param("userId") long userId);

    /**
     * Tìm các yêu cầu ở trạng thái cụ thể được tạo trước một mốc thời gian (YC-05, TD-02).
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
     * Tìm các yêu cầu RESERVED hoặc AWAITING_LOGISTICS quá hạn chưa chốt lịch (GN-05, TD-02, CH-01).
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
     * Tìm các yêu cầu AWAITING_HANDOVER có đúng một bên xác nhận quá hạn im lặng (GN-09).
     * Hạn tính theo hình thức giao nhận: Giao hàng dùng shippingCutoff, Gặp trực tiếp dùng inPersonCutoff.
     * Phân trang kiểu keyset theo id > afterId, tối đa limit bản ghi (Mục Q).
     */
    @Query("""
        SELECT r FROM Request r
        WHERE r.status = com.shareloop.request.entity.RequestStatus.AWAITING_HANDOVER
        AND (
            (r.donorHandoverAt IS NOT NULL AND r.receiverHandoverAt IS NULL)
            OR
            (r.receiverHandoverAt IS NOT NULL AND r.donorHandoverAt IS NULL)
        )
        AND (r.deliveryMethod IS NULL OR r.deliveryMethod <> 'SHIPPING')
        AND COALESCE(r.donorHandoverAt, r.receiverHandoverAt) < :inPersonCutoff
        AND r.id > :afterId
        ORDER BY r.id ASC
    """)
    List<Request> findOverdueHandoverRequests(
            @Param("inPersonCutoff") java.time.Instant inPersonCutoff,
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
