package com.shareloop.item.entity;

/**
 * Trạng thái bài đăng (khớp CHECK trong V1__init.sql).
 */
public enum ItemStatus {
    PENDING_REVIEW,
    APPROVED,
    REJECTED,
    RESERVED,
    TRADED,
    EXPIRED;

    public static boolean canTransition(ItemStatus from, ItemStatus to) {
        if (from == null || to == null || from == to) {
            return false;
        }
        return switch (from) {
            case PENDING_REVIEW -> to == APPROVED || to == REJECTED;
            case APPROVED -> to == RESERVED || to == PENDING_REVIEW || to == TRADED || to == EXPIRED;
            case RESERVED -> to == APPROVED || to == TRADED;
            case REJECTED -> to == PENDING_REVIEW;
            case TRADED, EXPIRED -> false;
        };
    }
}
