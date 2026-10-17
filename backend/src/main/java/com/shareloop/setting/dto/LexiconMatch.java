package com.shareloop.setting.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/** A lexicon rule that matched text, including whether diacritic normalization was required. */
@Schema(description = "Kết quả khớp từ khóa trong văn bản")
public record LexiconMatch(
        @Schema(description = "Từ khóa khớp", example = "súng nước")
        String keyword,

        @Schema(description = "Mức độ xử lý (BLOCK/REVIEW)", example = "REVIEW")
        String level,

        @Schema(description = "Nhóm phân loại", example = "WEAPONS")
        String group,

        @Schema(
                description = "Dạng khớp: EXACT (khớp nguyên văn) hoặc NORMALIZED (khớp sau khi chuẩn hóa bỏ dấu)",
                example = "EXACT")
        MatchedAs matchedAs) {

    public enum MatchedAs {
        EXACT,
        NORMALIZED
    }
}
