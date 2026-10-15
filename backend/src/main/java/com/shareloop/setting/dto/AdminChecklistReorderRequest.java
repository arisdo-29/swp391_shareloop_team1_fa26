package com.shareloop.setting.dto;

import jakarta.validation.constraints.NotEmpty;
import java.util.List;

public record AdminChecklistReorderRequest(
        @NotEmpty(message = "Danh sách id sắp xếp không được để trống.")
        List<Long> itemIds) {}
