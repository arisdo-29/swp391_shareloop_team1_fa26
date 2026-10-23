package com.shareloop.setting;

/**
 * Nhóm hiển thị của các khoá cấu hình trên trang Admin. Chỉ để gom cho dễ đọc, không lưu trong DB: mỗi
 * {@link ConfigKey} khai báo nhóm của mình ngay trong enum.
 */
public enum ConfigGroup {
    FEES("Phí và nạp tiền"),
    POSTING("Đăng bài và hiển thị"),
    AI("AI trợ lý và sàng lọc"),
    MODERATION("Kiểm duyệt và báo cáo"),
    ACCOUNT("Tài khoản và OTP"),
    TRANSACTION("Yêu cầu và giao dịch"),
    REPUTATION("Uy tín và hạng");

    private final String label;

    ConfigGroup(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
