package com.shareloop.setting.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;

@Schema(description = "Yêu cầu thêm mới hoặc cập nhật từ khóa trong từ điển")
public record AdminLexiconRequest(
        @NotBlank(message = "Keyword không được để trống.")
        @Schema(description = "Từ khóa cần kiểm duyệt", example = "súng nước")
        String keyword,

        @NotBlank(message = "Group không được để trống.")
        @Size(max = 80)
        @Schema(description = "Nhóm phân loại vi phạm", example = "WEAPONS")
        String group,

        @NotBlank(message = "Level không được để trống.")
        @Schema(description = "Mức độ xử lý: BLOCK (chặn ngay) hoặc REVIEW (gắn cờ chờ duyệt)", example = "REVIEW")
        String level,

        @Schema(
                description =
                        "Phương thức khớp: WORD (từng từ), PHRASE (cụm từ), REGEX (biểu thức chính quy). Mặc định: WORD",
                example = "PHRASE")
        String matchMode,

        @Schema(description = "Danh sách cụm từ ngoại lệ không bị coi là vi phạm", example = "[]")
        List<@NotBlank(message = "Exception không được để trống.") String> exceptions,

        @NotBlank(message = "Scope không được để trống.")
        @Schema(description = "Phạm vi áp dụng: POST, CHAT, MESSAGE hoặc BOTH", example = "POST")
        String scope) {}
