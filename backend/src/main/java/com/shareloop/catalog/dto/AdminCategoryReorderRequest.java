package com.shareloop.catalog.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;

/** Danh sách id của mọi danh mục theo thứ tự hiển thị mới mong muốn. */
public record AdminCategoryReorderRequest(@NotEmpty List<@NotNull Long> categoryIds) {}
