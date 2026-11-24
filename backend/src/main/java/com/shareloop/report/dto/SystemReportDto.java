package com.shareloop.report.dto;

import com.shareloop.report.entity.ReportStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;

public record SystemReportDto(
        long id,
        String title,
        String description,
        ReportStatus status,
        Instant createdAt,
        String resolution,
        Instant resolvedAt,
        String reporterName) {

    public record Create(
            @NotBlank(message = "Vui lòng nhập tiêu đề.")
            @Size(max = 150, message = "Tiêu đề không được vượt quá 150 ký tự.")
            String title,

            @NotBlank(message = "Vui lòng mô tả lỗi.")
            @Size(max = 2000, message = "Mô tả lỗi không được vượt quá 2.000 ký tự.")
            String description) {}

    public record Resolve(
            @NotBlank(message = "Vui lòng nhập kết quả xử lý.")
            @Size(max = 2000, message = "Kết quả xử lý không được vượt quá 2.000 ký tự.")
            String resolution) {}

    public record ResultPage(List<SystemReportDto> items, int page, int totalPages, long totalElements) {}
}
