package com.shareloop.report.service;

import com.shareloop.common.exception.BusinessException;
import com.shareloop.notification.service.NotificationService;
import com.shareloop.report.ReportErrorCode;
import com.shareloop.report.dto.SystemReportDto;
import com.shareloop.report.dto.SystemReportDto.ResultPage;
import com.shareloop.report.entity.Report;
import com.shareloop.report.entity.ReportStatus;
import com.shareloop.report.entity.TargetType;
import com.shareloop.report.repository.ReportRepository;
import com.shareloop.user.dto.UserBrief;
import com.shareloop.user.service.UserService;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SystemReportService {
    private final ReportRepository reports;
    private final UserService users;
    private final NotificationService notifications;
    private final Clock clock;

    public SystemReportService(
            ReportRepository reports, UserService users, NotificationService notifications, Clock clock) {
        this.reports = reports;
        this.users = users;
        this.notifications = notifications;
        this.clock = clock;
    }

    @Transactional
    public SystemReportDto submit(long userId, SystemReportDto.Create input) {
        Report report = reports.save(Report.createSystemReport(
                userId, input.title().strip(), input.description().strip()));
        notifications.notifyAdmins(
                "SYSTEM_REPORT_CREATED",
                "Có báo cáo hệ thống mới",
                "Người dùng vừa gửi một báo cáo cần xem xét.",
                "/admin/system-reports/" + report.getId());
        return toDto(report, Map.of());
    }

    @Transactional(readOnly = true)
    public ResultPage listMine(long userId, int page, int size) {
        return toPage(
                reports.findByReporterIdAndTargetType(userId, TargetType.SYSTEM, pageRequest(page, size)), Map.of());
    }

    @Transactional(readOnly = true)
    public SystemReportDto getMine(long userId, long reportId) {
        return reports.findByIdAndReporterIdAndTargetType(reportId, userId, TargetType.SYSTEM)
                .map(report -> toDto(report, Map.of()))
                .orElseThrow(() -> new BusinessException(ReportErrorCode.SYSTEM_REPORT_NOT_FOUND));
    }

    @Transactional(readOnly = true)
    public ResultPage listForAdmin(ReportStatus status, int page, int size) {
        Page<Report> result = reports.findSystemReports(status, pageRequest(page, size));
        return toPage(
                result,
                users.findBriefs(
                        result.stream().map(Report::getReporterId).distinct().toList()));
    }

    @Transactional(readOnly = true)
    public SystemReportDto getForAdmin(long reportId) {
        Report report = reports.findByIdAndTargetType(reportId, TargetType.SYSTEM)
                .orElseThrow(() -> new BusinessException(ReportErrorCode.SYSTEM_REPORT_NOT_FOUND));
        return toDto(report, users.findBriefs(List.of(report.getReporterId())));
    }

    @Transactional
    public SystemReportDto resolve(long adminId, long reportId, SystemReportDto.Resolve input) {
        Report report = reports.findByIdForUpdate(reportId)
                .filter(found -> found.getTargetType() == TargetType.SYSTEM)
                .orElseThrow(() -> new BusinessException(ReportErrorCode.SYSTEM_REPORT_NOT_FOUND));
        if (report.getStatus() != ReportStatus.OPEN) {
            throw new BusinessException(ReportErrorCode.SYSTEM_REPORT_ALREADY_RESOLVED);
        }

        report.resolveSystemReport(adminId, input.resolution().strip(), Instant.now(clock));
        notifications.notify(
                report.getReporterId(),
                "SYSTEM_REPORT_RESOLVED",
                "Báo cáo hệ thống đã được xử lý",
                "Bạn có thể xem kết quả xử lý trong báo cáo đã gửi.",
                "/me/system-reports/" + report.getId());
        return toDto(report, users.findBriefs(List.of(report.getReporterId())));
    }

    private static PageRequest pageRequest(int page, int size) {
        return PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt", "id"));
    }

    private static ResultPage toPage(Page<Report> result, Map<Long, UserBrief> reporters) {
        return new ResultPage(
                result.stream().map(report -> toDto(report, reporters)).toList(),
                result.getNumber(),
                result.getTotalPages(),
                result.getTotalElements());
    }

    private static SystemReportDto toDto(Report report, Map<Long, UserBrief> reporters) {
        UserBrief reporter = reporters.get(report.getReporterId());
        return new SystemReportDto(
                report.getId(),
                report.getSystemTitle(),
                report.getDescription(),
                report.getStatus(),
                report.getCreatedAt(),
                report.getResolution(),
                report.getResolvedAt(),
                reporter == null ? null : reporter.fullName());
    }
}
