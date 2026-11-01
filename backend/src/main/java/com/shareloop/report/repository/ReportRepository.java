package com.shareloop.report.repository;

import com.shareloop.report.entity.Report;
import com.shareloop.report.entity.ReportStatus;
import com.shareloop.report.entity.TargetType;
import jakarta.persistence.LockModeType;
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

    @Query("SELECT r FROM Report r WHERE (:status IS NULL OR r.status = :status) "
            + "AND (:targetType IS NULL OR r.targetType = :targetType)")
    Page<Report> findForAdmin(
            @Param("status") ReportStatus status, @Param("targetType") TargetType targetType, Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT r FROM Report r WHERE r.id = :id")
    Optional<Report> findByIdForUpdate(@Param("id") Long id);

    boolean existsByReporterIdAndTargetTypeAndItemId(Long reporterId, TargetType targetType, Long itemId);

    boolean existsByReporterIdAndTargetTypeAndTargetUserId(Long reporterId, TargetType targetType, Long targetUserId);

    boolean existsByReporterIdAndTargetTypeAndRequestId(Long reporterId, TargetType targetType, Long requestId);

    /** Giao dịch đã có report nào chưa; dùng để job tự hoàn tất bỏ qua giao dịch đang bị báo cáo (FR-87). */
    boolean existsByRequestId(Long requestId);

    /** Đếm report của một bài theo loại đối tượng và trạng thái; dùng cho ngưỡng tự ẩn bài (BR-A03). */
    long countByTargetTypeAndItemIdAndStatusIn(TargetType targetType, Long itemId, Collection<ReportStatus> statuses);

    /**
     * Các report khác của cùng bài theo trạng thái, bỏ report đang kết luận (D-07): chấp thuận thì đóng các report này,
     * bác bỏ thì chỉ mở lại bài khi danh sách rỗng.
     */
    List<Report> findByTargetTypeAndItemIdAndStatusInAndIdNot(
            TargetType targetType, Long itemId, Collection<ReportStatus> statuses, Long excludedId);

    /** Bài đã có report được chấp thuận chưa; có thì bác bỏ report khác không mở lại bài (D-07). */
    boolean existsByTargetTypeAndItemIdAndStatus(TargetType targetType, Long itemId, ReportStatus status);
}
