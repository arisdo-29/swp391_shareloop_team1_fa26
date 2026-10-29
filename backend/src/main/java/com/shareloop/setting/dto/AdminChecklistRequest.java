package com.shareloop.setting.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "Yêu cầu thêm mới hoặc cập nhật câu hỏi checklist")
public record AdminChecklistRequest(
        @NotBlank(message = "Nội dung câu hỏi không được để trống.")
        @Size(max = 255, message = "Nội dung câu hỏi không được vượt quá 255 ký tự.")
        @Schema(description = "Nội dung câu hỏi kiểm duyệt", example = "Ảnh là ảnh thật của đúng món đồ?")
        String question,

        @Size(max = 1000, message = "Mô tả điều kiện đạt không được vượt quá 1000 ký tự.")
        @Schema(
                description = "Mô tả điều kiện đạt (Q01). Bỏ trống (null) thì giữ mô tả cũ, chuỗi rỗng thì xoá mô tả",
                example = "Đạt khi có từ 3 ảnh chụp thật. Từ chối nếu dùng ảnh mạng.")
        String description) {

    public AdminChecklistRequest(String question) {
        this(question, null);
    }
}
