package com.shareloop.report.repository;

import com.shareloop.report.entity.Report;
import com.shareloop.report.entity.ReportReason;
import com.shareloop.report.entity.ReportStatus;
import com.shareloop.report.entity.TargetType;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface ReportRepository extends JpaRepository<Report, Long> {

    /** Mọi báo cáo nhắm vào một bài (kể cả đã đóng), mới nhất trước, cho trang chi tiết bài của Admin. */
    java.util.List<Report> findByItemIdOrderByCreatedAtDescIdDesc(Long itemId);

    /** Mọi báo cáo và khiếu nại gắn với một giao dịch, mới nhất trước, cho trang chi tiết giao dịch của Admin. */
    java.util.List<Report> findByRequestIdOrderByCreatedAtDescIdDesc(Long requestId);

    @Query("SELECT r FROM Report r WHERE (:status IS NULL OR r.status = :status) "
            + "AND (:targetType IS NULL OR r.targetType = :targetType) "
            + "AND r.targetType <> com.shareloop.report.entity.TargetType.SYSTEM")
    Page<Report> findForAdmin(
            @Param("status") ReportStatus status, @Param("targetType") TargetType targetType, Pageable pageable);

    Page<Report> findByReporterIdAndTargetType(Long reporterId, TargetType targetType, Pageable pageable);

    Optional<Report> findByIdAndReporterIdAndTargetType(Long id, Long reporterId, TargetType targetType);

    Optional<Report> findByIdAndTargetType(Long id, TargetType targetType);

    @Query("SELECT r FROM Report r WHERE r.targetType = com.shareloop.report.entity.TargetType.SYSTEM "
            + "AND (:status IS NULL OR r.status = :status)")
    Page<Report> findSystemReports(@Param("status") ReportStatus status, Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT r FROM Report r WHERE r.id = :id")
    Optional<Report> findByIdForUpdate(@Param("id") Long id);

    boolean existsByReporterIdAndTargetTypeAndItemId(Long reporterId, TargetType targetType, Long itemId);

    boolean existsByReporterIdAndTargetTypeAndTargetUserId(Long reporterId, TargetType targetType, Long targetUserId);

    /**
     * Đếm số lượng bài đăng của cùng một chủ bài mà reporter đã báo cáo từ mốc thời gian since (BC-01).
     */
    long countByReporterIdAndTargetTypeAndTargetUserIdAndCreatedAtGreaterThanEqual(
            Long reporterId, TargetType targetType, Long targetUserId, Instant since);

    /**
     * Kiểm tra reporter đã báo cáo người dùng targetUserId từ mốc thời gian since hay chưa (BC-01).
     */
    boolean existsByReporterIdAndTargetTypeAndTargetUserIdAndCreatedAtGreaterThanEqual(
            Long reporterId, TargetType targetType, Long targetUserId, Instant since);

    boolean existsByReporterIdAndTargetTypeAndRequestId(Long reporterId, TargetType targetType, Long requestId);

    /** Giao dịch đã có report nào chưa; dùng để job tự hoàn tất bỏ qua giao dịch đang bị báo cáo (GN-09). */
    boolean existsByRequestId(Long requestId);

    Optional<Report> findFirstByTargetTypeAndRequestIdAndStatusInOrderByCreatedAtDesc(
            TargetType targetType, Long requestId, Collection<ReportStatus> statuses);

    List<Report> findByTargetTypeAndRequestIdInAndStatusIn(
            TargetType targetType, Collection<Long> requestIds, Collection<ReportStatus> statuses);

    /** Mọi khiếu nại của các giao dịch, cũ trước; dùng để hiện khiếu nại theo vai ở trang Tiến trình và khung chat. */
    List<Report> findByTargetTypeAndRequestIdInOrderByCreatedAtAsc(TargetType targetType, Collection<Long> requestIds);

    /** Đếm report của một bài theo loại đối tượng và trạng thái; dùng cho ngưỡng tự ẩn bài (BC-02). */
    long countByTargetTypeAndItemIdAndStatusIn(TargetType targetType, Long itemId, Collection<ReportStatus> statuses);

    /**
     * Các report khác của cùng bài theo trạng thái, bỏ report đang kết luận (D-07): chấp thuận thì đóng các report này,
     * bác bỏ thì chỉ mở lại bài khi danh sách rỗng.
     */
    List<Report> findByTargetTypeAndItemIdAndStatusInAndIdNot(
            TargetType targetType, Long itemId, Collection<ReportStatus> statuses, Long excludedId);

    /** Bài đã có report được chấp thuận chưa; có thì bác bỏ report khác không mở lại bài (D-07). */
    boolean existsByTargetTypeAndItemIdAndStatus(TargetType targetType, Long itemId, ReportStatus status);

    /** Bài có báo cáo được chấp thuận sau mốc cho trước không; dùng để bỏ qua báo cáo cũ hơn lần duyệt lại gần nhất. */
    boolean existsByTargetTypeAndItemIdAndStatusAndResolvedAtAfter(
            TargetType targetType, Long itemId, ReportStatus status, Instant resolvedAfter);

    /** Bài còn báo cáo từ chối nhận (gắn giao dịch) đang chờ không; có thì giữ bài ẩn tới khi Admin kết luận (GN-07). */
    boolean existsByTargetTypeAndItemIdAndReasonAndRequestIdIsNotNullAndStatusIn(
            TargetType targetType, Long itemId, ReportReason reason, Collection<ReportStatus> statuses);

    /** Các report một người đã gửi, mới nhất lên trước; dùng cho tab Khiếu nại và Báo cáo của Member. */
    @Query("SELECT r FROM Report r WHERE r.reporterId = :reporterId "
            + "AND r.targetType <> com.shareloop.report.entity.TargetType.SYSTEM ORDER BY r.createdAt DESC, r.id DESC")
    List<Report> findByReporterIdOrderByCreatedAtDescIdDesc(@Param("reporterId") Long reporterId);

    /**
     * Các report nhắm vào một người và đã có hạn giải trình, mới nhất lên trước. target_user_id luôn là bên bị
     * phản ánh (chủ bài với report bài đăng, đối tác với khiếu nại giao dịch). Report chưa có hạn là Admin chưa yêu
     * cầu giải trình nên chưa cho bên bị phản ánh thấy.
     */
    /**
     * Hồ sơ bên bị phản ánh được thấy: đã có hạn giải trình, hoặc là báo cáo từ chối nhận (báo cáo bài gắn giao dịch, lý
     * do Không đúng mô tả, GN-07). Mới nhất lên trước.
     */
    @Query("select r from Report r where r.targetUserId = :userId and (r.responseDueAt is not null"
            + " or (r.targetType = com.shareloop.report.entity.TargetType.ITEM and r.requestId is not null"
            + " and r.reason = com.shareloop.report.entity.ReportReason.NOT_AS_DESCRIBED))"
            + " order by r.createdAt desc, r.id desc")
    List<Report> findVisibleToRespondent(@Param("userId") Long userId);
}
