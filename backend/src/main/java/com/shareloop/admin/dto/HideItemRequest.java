package com.shareloop.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Lý do Admin ẩn hoặc hiện lại một bài, 5 đến 500 ký tự. */
public record HideItemRequest(
        @NotBlank @Size(min = 5, max = 500) String reason) {}
