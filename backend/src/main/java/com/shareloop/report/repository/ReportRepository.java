package com.shareloop.report.repository;

import com.shareloop.report.entity.Report;
import com.shareloop.report.entity.TargetType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ReportRepository extends JpaRepository<Report, Long> {

    boolean existsByReporterIdAndTargetTypeAndItemId(Long reporterId, TargetType targetType, Long itemId);

    boolean existsByReporterIdAndTargetTypeAndTargetUserId(Long reporterId, TargetType targetType, Long targetUserId);

    boolean existsByReporterIdAndTargetTypeAndRequestId(Long reporterId, TargetType targetType, Long requestId);

    long countByItemId(Long itemId);
}
