package com.shareloop.report.dto;

import com.shareloop.report.entity.ReportReason;
import com.shareloop.report.entity.ReportStatus;
import com.shareloop.report.entity.TargetType;
import java.time.Instant;

/**
 * Một hồ sơ báo cáo hoặc khiếu nại trong tab Khiếu nại & Báo cáo của Member. Không có id người gửi và mô tả để bên bị
 * phản ánh không biết ai đã báo cáo mình.
 *
 * @param targetName tiêu đề bài đăng (report bài hoặc giao dịch) hoặc họ tên người bị báo cáo; null khi đã bị xoá
 * @param responseWindowOpen true khi còn chờ giải trình, chưa giải trình và chưa tới hạn
 * @param resolution ghi chú kết luận của Admin, null khi chưa kết luận
 */
public record MyReportSummary(
        Long id,
        TargetType targetType,
        Long itemId,
        Long requestId,
        ReportReason reason,
        ReportStatus status,
        String targetName,
        Instant createdAt,
        Instant responseDueAt,
        Instant respondedAt,
        boolean responseWindowOpen,
        String resolution,
        Instant resolvedAt) {}
