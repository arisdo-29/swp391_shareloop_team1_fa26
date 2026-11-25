package com.shareloop.report.dto;

import com.shareloop.report.entity.ReportReason;
import com.shareloop.report.entity.ReportStatus;
import java.time.Instant;

/** Tóm tắt một báo cáo nhắm vào một bài, cho trang chi tiết bài của Admin. */
public record ItemReportBrief(long id, long reporterId, ReportReason reason, ReportStatus status, Instant createdAt) {}
