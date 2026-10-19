package com.shareloop.report.dto;

import java.time.Instant;
import java.util.List;

public record AdminReportDetail(
        ReportResponse report,
        Participant reporter,
        Participant reportedUser,
        Long relatedItemId,
        String response,
        Instant respondedAt,
        Instant responseDueAt,
        List<Evidence> evidence,
        List<Evidence> responseEvidence,
        Long resolvedBy,
        String resolution,
        short starsDeducted,
        Instant resolvedAt) {
    public record Participant(Long id, String fullName) {}

    public record Evidence(Long id, String url, boolean available) {}
}
