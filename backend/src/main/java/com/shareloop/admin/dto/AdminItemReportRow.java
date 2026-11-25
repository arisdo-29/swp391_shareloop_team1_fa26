package com.shareloop.admin.dto;

import com.shareloop.report.entity.ReportReason;
import com.shareloop.report.entity.ReportStatus;
import java.time.Instant;

/** Một báo cáo nhắm vào bài trong trang chi tiết bài của Admin. */
public record AdminItemReportRow(long id, ReportReason reason, ReportStatus status, Instant createdAt) {}
