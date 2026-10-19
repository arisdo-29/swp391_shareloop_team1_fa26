package com.shareloop.moderation;

/** Mã lý do kiểm duyệt tầng 1 kèm câu tiếng Việt để hiển thị cho Admin. */
public enum ModerationReason {
    LEXICON_REVIEW("Có từ khoá cần xem xét"),
    INVALID_INPUT("Thiếu tiêu đề/mô tả hoặc danh mục không hợp lệ, cần kiểm tay"),
    MODERATION_CHECK_FAILED("Kiểm tra tự động bị lỗi, cần kiểm tay"),
    INVALID_LEXICON_RULE("Quy tắc từ khoá đang cấu hình sai, cần kiểm tay"),
    CONTACT_INFO("Có thông tin liên hệ"),
    LEXICON_BLOCK("Có từ khoá bị cấm"),
    RESTRICTED_CATEGORY("Danh mục bị hạn chế");

    private final String description;

    ModerationReason(String description) {
        this.description = description;
    }

    public String description() {
        return description;
    }

    /**
     * Đổi mã lý do sang câu tiếng Việt. Lý do có thể là câu AI đã viết sẵn bằng tiếng Việt,
     * nên chuỗi không phải mã đã biết thì trả nguyên văn; null thì trả null.
     */
    public static String describe(String codeOrText) {
        if (codeOrText == null) {
            return null;
        }
        for (ModerationReason reason : values()) {
            if (reason.name().equals(codeOrText)) {
                return reason.description;
            }
        }
        return codeOrText;
    }
}
