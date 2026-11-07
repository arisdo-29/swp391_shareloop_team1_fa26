package com.shareloop.report.dto;

import com.shareloop.report.dto.AdminReportDetail.Evidence;
import com.shareloop.report.entity.ReportReason;
import com.shareloop.report.entity.ReportStatus;
import com.shareloop.report.entity.TargetType;
import java.time.Instant;
import java.util.List;

/**
 * Chi tiết hồ sơ cho bên bị phản ánh trên trang giải trình: biết mình bị phản ánh điều gì, ảnh nào, hạn tới khi nào.
 * Không có id hay tên người gửi báo cáo.
 *
 * @param description nội dung bên báo cáo viết
 * @param evidence ảnh của bên báo cáo; url trỏ tới GET /api/v1/reports/{id}/evidence/{mediaId}
 * @param requestId giao dịch bị khiếu nại (chỉ REQUEST), dùng để mở chat với đối phương
 * @param response nội dung giải trình tôi đã gửi, null khi chưa gửi
 * @param responseEvidence ảnh tôi đã gửi kèm giải trình
 */
public record RespondentReportDetail(
        Long id,
        TargetType targetType,
        Long itemId,
        Long requestId,
        ReportReason reason,
        String description,
        String targetName,
        ReportStatus status,
        Instant createdAt,
        Instant responseDueAt,
        boolean responseWindowOpen,
        List<Evidence> evidence,
        String response,
        Instant respondedAt,
        List<Evidence> responseEvidence,
        String resolution,
        Instant resolvedAt) {}
