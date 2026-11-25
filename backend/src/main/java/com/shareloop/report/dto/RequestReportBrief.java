package com.shareloop.report.dto;

import com.shareloop.report.entity.ReportReason;
import com.shareloop.report.entity.ReportStage;
import com.shareloop.report.entity.ReportStatus;
import com.shareloop.report.entity.TargetType;
import java.time.Instant;

/**
 * Tóm tắt một báo cáo hoặc khiếu nại gắn với một giao dịch, cho trang chi tiết giao dịch của Admin.
 *
 * @param reporterId người gửi báo cáo
 * @param targetType đối tượng bị báo cáo (bài, người dùng hoặc giao dịch)
 */
public record RequestReportBrief(
        long id,
        long reporterId,
        TargetType targetType,
        ReportReason reason,
        ReportStage stage,
        ReportStatus status,
        Instant createdAt) {}
