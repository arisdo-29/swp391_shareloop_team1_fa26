package com.shareloop.report.repository;

import com.shareloop.report.entity.Report;
import com.shareloop.report.entity.ReportStatus;
import com.shareloop.report.entity.TargetType;
import jakarta.persistence.LockModeType;
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

    long countByItemId(Long itemId);
}
