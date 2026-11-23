package com.shareloop.request.entity;

/**
 * Mã lý do của đề nghị huỷ giao dịch đã chốt lịch (GN-06). Nhãn tiếng Việt do backend giữ để FE không tự đặt câu.
 */
public enum CancelRequestCode {
    BUSY("Tôi bận đột xuất", "họ bận đột xuất"),
    NO_LONGER_NEEDED("Tôi không còn nhu cầu", "họ không còn nhu cầu"),
    OTHER("Lý do khác", null),
    COUNTERPART_NO_SHOW("Bên kia không đến điểm hẹn", "bạn không đến điểm hẹn"),
    COUNTERPART_NOT_SHIPPED("Bên kia chưa gửi hàng", "bạn chưa gửi hàng"),
    CANNOT_CONTACT("Không liên hệ được với bên kia", "không liên hệ được với bạn");

    private final String label;
    // Cách nói khi báo cho bên kia ("vì ..."); null với OTHER vì câu lấy từ lý do người dùng nhập.
    private final String phraseForOtherParty;

    CancelRequestCode(String label, String phraseForOtherParty) {
        this.label = label;
        this.phraseForOtherParty = phraseForOtherParty;
    }

    public String label() {
        return label;
    }

    /**
     * Lý do tố bên kia (không đến, chưa gửi hàng, không liên hệ được). Khi bên kia không đồng ý thì khiếu nại là tố
     * cáo và Admin chấp thuận sẽ trừ sao bên bị tố. Ba lý do còn lại không tố ai nên không bao giờ trừ sao bên từ chối.
     */
    public boolean isAccusation() {
        return this == COUNTERPART_NO_SHOW || this == COUNTERPART_NOT_SHIPPED || this == CANNOT_CONTACT;
    }

    /** Ba lý do dùng trước giờ hẹn (nút "Đề nghị huỷ giao dịch"). */
    public boolean isAllowedBeforeMeeting() {
        return this == BUSY || this == NO_LONGER_NEEDED || this == OTHER;
    }

    /** Lý do dùng từ giờ hẹn trở đi (nút "Giao nhận không thành công"). Không đến điểm hẹn chỉ có ở Gặp trực tiếp. */
    public boolean isAllowedAfterMeeting(boolean shipping) {
        if (this == COUNTERPART_NO_SHOW) {
            return !shipping;
        }
        if (this == COUNTERPART_NOT_SHIPPED) {
            return shipping;
        }
        return this == CANNOT_CONTACT || this == OTHER;
    }

    /** Câu "vì ..." gửi cho bên kia. Lý do khác thì dùng nguyên văn người dùng nhập. */
    public String phraseForOtherParty(String reason) {
        String text = reason == null ? "" : reason.trim();
        if (this == OTHER) {
            return text;
        }
        return text.isEmpty() ? phraseForOtherParty : phraseForOtherParty + ". " + text;
    }
}
