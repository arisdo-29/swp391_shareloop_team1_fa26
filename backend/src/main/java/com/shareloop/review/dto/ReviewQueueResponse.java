package com.shareloop.review.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "Dữ liệu hàng chờ duyệt kèm checklist kiểm duyệt hiện hành")
public record ReviewQueueResponse(
        @Schema(description = "Danh sách bài đăng đang chờ duyệt")
        List<ReviewQueueItem> items,

        @Schema(description = "Danh sách câu hỏi checklist kiểm duyệt hiện hành")
        List<String> checklist,

        @Schema(description = "Số trang hiện tại (bắt đầu từ 0)", example = "0")
        int page,

        @Schema(description = "Số lượng bản ghi mỗi trang", example = "20")
        int size) {}
