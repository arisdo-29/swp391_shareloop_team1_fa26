package com.shareloop.setting.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Thông tin một mục câu hỏi checklist")
public record AdminChecklistEntry(
        @Schema(description = "ID bản ghi", example = "1") Long id,

        @Schema(description = "Key định danh câu hỏi", example = "checklist_item_real")
        String key,

        @Schema(description = "Nội dung câu hỏi kiểm duyệt", example = "Ảnh là ảnh thật của đúng món đồ?")
        String question,

        @Schema(description = "Thứ tự sắp xếp", example = "1")
        int sortOrder,

        @Schema(description = "Trạng thái kích hoạt", example = "true")
        boolean active) {}
