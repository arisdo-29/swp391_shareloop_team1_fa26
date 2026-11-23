package com.shareloop.report.entity;

public enum ReportReason {
    NOT_AS_DESCRIBED,
    COUNTERFEIT,
    PROHIBITED_ITEM,
    NO_SHOW,
    WRONG_CONTACT_INFO,
    HARASSMENT,
    OTHER;

    /**
     * Lý do hợp lệ theo loại đối tượng báo cáo (F7). Frontend dùng cùng bảng này để chỉ hiện lý do phù hợp
     * (frontend/src/utils/disputeErrors.ts, reportReasonsByTarget).
     */
    public static java.util.Set<ReportReason> allowedFor(TargetType targetType) {
        return switch (targetType) {
            case ITEM -> java.util.EnumSet.of(NOT_AS_DESCRIBED, COUNTERFEIT, PROHIBITED_ITEM, OTHER);
            case USER -> java.util.EnumSet.of(NO_SHOW, WRONG_CONTACT_INFO, HARASSMENT, OTHER);
            case REQUEST ->
                java.util.EnumSet.of(NOT_AS_DESCRIBED, COUNTERFEIT, NO_SHOW, WRONG_CONTACT_INFO, HARASSMENT, OTHER);
        };
    }
}
