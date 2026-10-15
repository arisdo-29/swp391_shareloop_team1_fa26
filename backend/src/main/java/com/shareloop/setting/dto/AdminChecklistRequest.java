package com.shareloop.setting.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AdminChecklistRequest(
        @NotBlank(message = "Nội dung câu hỏi không được để trống.")
        @Size(max = 255, message = "Nội dung câu hỏi không được vượt quá 255 ký tự.")
        String question) {}
