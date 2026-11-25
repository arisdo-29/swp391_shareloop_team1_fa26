package com.shareloop.setting.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;

@Schema(description = "Yêu cầu cập nhật nhiều cấu hình trong một lần, hoặc cả lô thành công hoặc không lưu gì")
public record BatchUpdateConfigRequest(
        @NotEmpty(message = "Cần ít nhất một thay đổi cấu hình.")
        @Valid
        @Schema(description = "Các thay đổi do Admin chọn, mỗi key chỉ xuất hiện một lần")
        List<ConfigChangeItem> changes,

        @NotBlank(message = "Lý do cập nhật là bắt buộc.")
        @Schema(description = "Lý do dùng chung cho mọi dòng nhật ký", example = "Điều chỉnh gói đẩy bài")
        String reason,

        // Boolean (không phải boolean): Jackson 3 từ chối body thiếu trường kiểu nguyên thuỷ. Thiếu thì coi là false.
        @Schema(
                description = "Admin đã đọc và chấp nhận các cảnh báo (nếu có), thiếu thì coi là false",
                example = "false")
        Boolean acknowledgedWarnings) {}
