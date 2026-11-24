package com.shareloop.report.controller;

import com.shareloop.common.security.CurrentUserId;
import com.shareloop.report.dto.SystemReportDto;
import com.shareloop.report.dto.SystemReportDto.ResultPage;
import com.shareloop.report.entity.ReportStatus;
import com.shareloop.report.service.SystemReportService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/system-reports")
@Validated
public class AdminSystemReportController {
    private final SystemReportService reports;

    public AdminSystemReportController(SystemReportService reports) {
        this.reports = reports;
    }

    @GetMapping
    public ResultPage list(
            @RequestParam(required = false) ReportStatus status,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return reports.listForAdmin(status, page, size);
    }

    @GetMapping("/{reportId}")
    public SystemReportDto detail(@PathVariable @Min(1) long reportId) {
        return reports.getForAdmin(reportId);
    }

    @PostMapping("/{reportId}/resolve")
    public SystemReportDto resolve(
            @CurrentUserId Long adminId,
            @PathVariable @Min(1) long reportId,
            @Valid @RequestBody SystemReportDto.Resolve input) {
        return reports.resolve(adminId, reportId, input);
    }
}
