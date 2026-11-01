package com.shareloop.setting.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/** Một mục checklist duyệt bài đang dùng; Admin tích khi bài đạt điều kiện trong statement. */
@Schema(description = "Mục checklist duyệt bài đang dùng")
public record ChecklistItem(
        @Schema(description = "ID mục checklist, dùng để gửi đáp án khi duyệt", example = "11")
        Long id,

        @Schema(description = "Mã ổn định của mục checklist", example = "category_match")
        String key,

        @Schema(
                description = "Điều kiện đạt: bài thoả điều kiện này thì Admin tích",
                example = "Danh mục đúng với món đồ.")
        String statement,

        @Schema(
                description = "Mô tả điều kiện đạt và khi nào từ chối (Q01); null khi mục chưa có mô tả",
                example = "Đạt khi món đồ được gán đúng danh mục. Từ chối nếu chọn bừa danh mục không liên quan.")
        String description) {

    public ChecklistItem(Long id, String key, String statement) {
        this(id, key, statement, null);
    }
}
