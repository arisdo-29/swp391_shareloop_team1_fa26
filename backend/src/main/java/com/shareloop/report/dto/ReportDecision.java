package com.shareloop.report.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Quyết định kết luận báo cáo của Admin (BC-12, UT-02, BC-06, Issue #113).
 */
@Schema(description = "Quyết định của Admin đối với báo cáo: UPHOLD (có cơ sở) hoặc DISMISS (không có cơ sở)")
public enum ReportDecision {
    UPHOLD,
    DISMISS
}
