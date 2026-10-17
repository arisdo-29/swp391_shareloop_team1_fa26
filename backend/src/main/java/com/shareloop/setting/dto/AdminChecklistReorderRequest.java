package com.shareloop.setting.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;

@Schema(description = "Yêu cầu sắp xếp lại thứ tự câu hỏi checklist")
public record AdminChecklistReorderRequest(
        @NotEmpty(message = "Danh sách id sắp xếp không được để trống.")
        @Schema(description = "Danh sách ID của các câu hỏi checklist theo thứ tự mới mong muốn", example = "[3, 1, 2]")
        List<Long> itemIds) {}
