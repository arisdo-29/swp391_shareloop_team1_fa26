package com.shareloop.report.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Quyết định kết luận báo cáo của Admin (FR-91, FR-93, Issue #113).
 */
@Schema(description = "Quyết định của Admin đối với báo cáo: UPHOLD (có cơ sở) hoặc DISMISS (không có cơ sở)")
public enum ReportDecision {
    UPHOLD,
    DISMISS
}
