package com.shareloop.setting.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "Thông tin chi tiết một mục từ khóa trong từ điển")
public record AdminLexiconEntry(
        @Schema(description = "ID bản ghi", example = "1") Long id,

        @Schema(description = "Từ khóa kiểm duyệt", example = "súng nước")
        String keyword,

        @Schema(description = "Nhóm phân loại vi phạm", example = "WEAPONS")
        String group,

        @Schema(description = "Mức độ xử lý (BLOCK/REVIEW)", example = "REVIEW")
        String level,

        @Schema(description = "Phương thức khớp (WORD/PHRASE/REGEX)", example = "PHRASE")
        String matchMode,

        @Schema(description = "Danh sách ngoại lệ", example = "[]")
        List<String> exceptions,

        @Schema(description = "Phạm vi áp dụng (POST/CHAT/MESSAGE/BOTH)", example = "POST")
        String scope,

        @Schema(description = "Thứ tự sắp xếp", example = "1")
        int sortOrder,

        @Schema(description = "Trạng thái kích hoạt", example = "true")
        boolean active) {}
