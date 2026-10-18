package com.shareloop.report.repository;

import com.shareloop.report.entity.Report;
import com.shareloop.report.entity.TargetType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ReportRepository extends JpaRepository<Report, Long> {

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("SELECT r FROM Report r WHERE r.id = :id")
    java.util.Optional<Report> findByIdForUpdate(@org.springframework.data.repository.query.Param("id") Long id);

    boolean existsByReporterIdAndTargetTypeAndItemId(Long reporterId, TargetType targetType, Long itemId);

    boolean existsByReporterIdAndTargetTypeAndTargetUserId(Long reporterId, TargetType targetType, Long targetUserId);

    boolean existsByReporterIdAndTargetTypeAndRequestId(Long reporterId, TargetType targetType, Long requestId);

    long countByItemId(Long itemId);
}
