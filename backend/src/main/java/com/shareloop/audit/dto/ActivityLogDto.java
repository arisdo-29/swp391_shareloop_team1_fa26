package com.shareloop.audit.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.Map;

@Schema(description = "Một dòng nhật ký hành động")
public record ActivityLogDto(
        @Schema(description = "ID bản ghi nhật ký", example = "101")
        Long id,

        @Schema(description = "ID người thực hiện; null nếu hệ thống tự thực hiện", example = "3")
        Long actorId,

        @Schema(description = "Tên người thực hiện; \"Hệ thống\" khi actorId null", example = "Nguyễn Văn A")
        String actorName,

        @Schema(
                description = "Email người thực hiện; null khi là hệ thống hoặc không tìm thấy",
                example = "a@fpt.edu.vn")
        String actorEmail,

        @Schema(description = "Loại hành động", example = "REVIEW_APPROVE")
        String type,

        @Schema(description = "Nhãn tiếng Việt của loại hành động", example = "Duyệt bài")
        String typeLabel,

        @Schema(description = "Loại đối tượng mục tiêu", example = "ITEM")
        String targetType,

        @Schema(description = "ID đối tượng mục tiêu", example = "42")
        Long targetId,

        @Schema(description = "Tên hiển thị của đối tượng mục tiêu", example = "Áo khoác size M")
        String targetTitle,

        @Schema(description = "Dữ liệu bổ sung dạng key-value")
        Map<String, Object> data,

        @Schema(description = "Lý do thao tác", example = "Đủ điều kiện kiểm duyệt")
        String reason,

        @Schema(description = "Chi tiết tóm tắt hành động", example = "Duyệt bài")
        String detail,

        @Schema(description = "Thời điểm thực hiện", example = "2026-10-17T10:00:00Z")
        Instant createdAt) {}
