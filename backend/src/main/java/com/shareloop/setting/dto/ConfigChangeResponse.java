package com.shareloop.setting.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(
        description =
                "Một cấu hình sẽ đổi giá trị. Cấu hình đồng bộ có causedByKey và note, cấu hình Admin tự gửi thì không")
public record ConfigChangeResponse(
        @Schema(description = "Key của cấu hình", example = "boost_max_days")
        String key,

        @Schema(description = "Tên tiếng Việt để hiển thị", example = "Số ngày đẩy tối đa")
        String label,

        @Schema(description = "Giá trị đang lưu", example = "6")
        String oldValue,

        @Schema(description = "Giá trị sau khi đổi", example = "8")
        String newValue,

        @Schema(
                description = "Key của cấu hình làm phát sinh thay đổi này, null nếu do Admin tự gửi",
                example = "boost_days")
        String causedByKey,

        @Schema(description = "Tên tiếng Việt của cấu hình nguồn", example = "Số ngày mỗi gói đẩy bài")
        String causedByLabel,

        @Schema(description = "Ghi chú lý do đồng bộ", example = "Đồng bộ theo Số ngày mỗi gói đẩy bài")
        String note) {}
