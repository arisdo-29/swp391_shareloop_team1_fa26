package com.shareloop.report.dto;

import java.time.Instant;
import java.util.List;

/**
 * Chi tiết một hồ sơ báo cáo hoặc khiếu nại cho Admin.
 *
 * @param relatedItemTitle tiêu đề bài đăng liên quan, null khi không có hoặc bài đã bị xoá
 * @param resolvedByName họ tên Admin đã kết luận, null khi chưa kết luận
 */
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
        Instant resolvedAt,
        String relatedItemTitle,
        String resolvedByName) {
    public record Participant(Long id, String fullName) {}

    public record Evidence(Long id, String url, boolean available) {}
}
