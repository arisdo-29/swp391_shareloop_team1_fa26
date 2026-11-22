package com.shareloop.admin.dto;

import com.shareloop.report.entity.ReportReason;
import com.shareloop.report.entity.ReportStage;
import com.shareloop.report.entity.ReportStatus;
import com.shareloop.report.entity.TargetType;
import java.time.Instant;

/** Một báo cáo hoặc khiếu nại gắn với giao dịch. */
public record AdminRequestReportRow(
        long id,
        String reporterName,
        TargetType targetType,
        ReportReason reason,
        ReportStage stage,
        ReportStatus status,
        Instant createdAt) {}
