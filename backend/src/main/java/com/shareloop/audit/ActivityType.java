package com.shareloop.audit;

/**
 * Danh sách loại hành động ghi vào activity_logs (FR-115). Cột type lưu đúng tên hằng (name()); nhãn tiếng Việt
 * dùng để hiển thị trên trang nhật ký và bộ lọc của Admin.
 */
public enum ActivityType {
    REVIEW_APPROVE("Duyệt bài"),
    REVIEW_REJECT("Từ chối bài"),
    CONFIG_UPDATE("Sửa cấu hình"),
    USER_LOCK("Khoá tài khoản"),
    USER_UNLOCK("Mở khoá tài khoản"),
    REQUEST_CANCEL("Huỷ giao dịch"),
    REPORT_DISMISSED("Bác bỏ báo cáo"),
    REPORT_UPHELD("Chấp nhận báo cáo"),
    POINTS("Cộng điểm"),
    STARS("Trừ sao"),
    AI_USAGE("Gọi AI kiểm duyệt"),
    LEXICON_CREATE("Thêm từ khoá cấm"),
    LEXICON_UPDATE("Sửa từ khoá cấm"),
    LEXICON_TOGGLE("Bật/tắt từ khoá cấm"),
    CHECKLIST_CREATE("Thêm câu hỏi kiểm duyệt"),
    CHECKLIST_UPDATE("Sửa câu hỏi kiểm duyệt"),
    CHECKLIST_TOGGLE("Bật/tắt câu hỏi kiểm duyệt"),
    CHECKLIST_REORDER("Sắp xếp câu hỏi kiểm duyệt"),
    CATEGORY_CREATE("Thêm danh mục"),
    CATEGORY_UPDATE("Sửa danh mục"),
    CATEGORY_TOGGLE("Bật/tắt danh mục"),
    CATEGORY_REORDER("Sắp xếp danh mục"),
    CATEGORY_ATTRIBUTE_CREATE("Thêm thuộc tính danh mục"),
    CATEGORY_ATTRIBUTE_UPDATE("Sửa thuộc tính danh mục"),
    CATEGORY_ATTRIBUTE_TOGGLE("Bật/tắt thuộc tính danh mục"),
    AREA_CREATE("Thêm khu vực"),
    AREA_UPDATE("Sửa khu vực"),
    AREA_TOGGLE("Bật/tắt khu vực"),
    AUTH_GRANT("Cấp nhãn chính hãng"),
    AUTH_REJECT("Từ chối nhãn chính hãng"),
    AUTH_REVOKE("Thu hồi nhãn chính hãng"),
    GRACE_STAR("Cấp sao ân hạn"),
    STAR_RESTORED("Tự hồi sao");

    private final String label;

    ActivityType(String label) {
        this.label = label;
    }

    /** Mã lưu trong cột activity_logs.type, chính là tên hằng. */
    public String code() {
        return name();
    }

    public String label() {
        return label;
    }

    /**
     * Nhãn tiếng Việt của một mã type đọc từ DB. Dữ liệu cũ có thể chứa mã không còn trong enum, khi đó trả lại
     * chính mã đó để trang nhật ký vẫn hiện được.
     */
    public static String labelOf(String code) {
        if ("AiUsage".equals(code)) {
            return AI_USAGE.label;
        }
        for (ActivityType type : values()) {
            if (type.name().equals(code)) {
                return type.label;
            }
        }
        return code;
    }
}
