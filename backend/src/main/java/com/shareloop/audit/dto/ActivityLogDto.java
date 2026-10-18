package com.shareloop.audit.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.Map;

@Schema(description = "Một dòng nhật ký hành động")
public record ActivityLogDto(
        @Schema(description = "ID bản ghi nhật ký", example = "101")
        Long id,

        @Schema(description = "ID người thực hiện", example = "3")
        Long actorId,

        @Schema(description = "Loại hành động", example = "APPROVE_ITEM")
        String type,

        @Schema(description = "Loại đối tượng mục tiêu", example = "ITEM")
        String targetType,

        @Schema(description = "ID đối tượng mục tiêu", example = "42")
        Long targetId,

        @Schema(description = "Dữ liệu bổ sung dạng key-value")
        Map<String, Object> data,

        @Schema(description = "Lý do thao tác", example = "Đủ điều kiện kiểm duyệt")
        String reason,

        @Schema(description = "Chi tiết tóm tắt hành động", example = "Duyệt bài đăng #42")
        String detail,

        @Schema(description = "Thời điểm thực hiện", example = "2026-10-17T10:00:00Z")
        Instant createdAt) {

    public ActivityLogDto(Long id, Long actorId, String type, String detail, Instant createdAt) {
        this(id, actorId, type, null, null, null, null, detail, createdAt);
    }
}
