package com.shareloop.report.controller;

import com.shareloop.common.security.CurrentUserId;
import com.shareloop.report.dto.SystemReportDto;
import com.shareloop.report.dto.SystemReportDto.ResultPage;
import com.shareloop.report.service.SystemReportService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/me/system-reports")
@Validated
public class SystemReportController {
    private final SystemReportService reports;

    public SystemReportController(SystemReportService reports) {
        this.reports = reports;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public SystemReportDto submit(@CurrentUserId Long userId, @Valid @RequestBody SystemReportDto.Create input) {
        return reports.submit(userId, input);
    }

    @GetMapping
    public ResultPage list(
            @CurrentUserId Long userId,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return reports.listMine(userId, page, size);
    }

    @GetMapping("/{reportId}")
    public SystemReportDto detail(@CurrentUserId Long userId, @PathVariable @Min(1) long reportId) {
        return reports.getMine(userId, reportId);
    }
}
