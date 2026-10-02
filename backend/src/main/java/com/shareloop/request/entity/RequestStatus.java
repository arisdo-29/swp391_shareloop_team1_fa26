package com.shareloop.request.entity;

/**
 * Trạng thái của một Request giao dịch (ERD 5.1, Hình 21; Spike S8).
 * Quản lý vòng đời yêu cầu xin/đổi đồ và kiểm tra chuyển trạng thái hợp lệ.
 */
public enum RequestStatus {
    PENDING,
    RESERVED,
    AWAITING_LOGISTICS,
    LOGISTICS_CONFIRMED,
    AWAITING_HANDOVER,
    COMPLETED,
    DISPUTED,
    REJECTED,
    CANCELLED;

    /**
     * Kiểm tra có thể chuyển từ trạng thái {@code from} sang {@code to} theo sơ đồ vòng đời hay không.
     *
     * @param from trạng thái hiện tại
     * @param to trạng thái đích muốn chuyển
     * @return true nếu chuyển đổi hợp lệ
     */
    public static boolean canTransition(RequestStatus from, RequestStatus to) {
        if (from == null || to == null || from == to) {
            return false;
        }
        return switch (from) {
            case PENDING -> to == RESERVED || to == REJECTED || to == CANCELLED;
            case RESERVED -> to == AWAITING_LOGISTICS || to == CANCELLED;
            case AWAITING_LOGISTICS -> to == LOGISTICS_CONFIRMED || to == CANCELLED;
            case LOGISTICS_CONFIRMED -> to == AWAITING_HANDOVER || to == CANCELLED;
            case AWAITING_HANDOVER -> to == COMPLETED || to == DISPUTED;
            case DISPUTED -> to == COMPLETED || to == CANCELLED;
            case COMPLETED, REJECTED, CANCELLED -> false;
        };
    }
}
