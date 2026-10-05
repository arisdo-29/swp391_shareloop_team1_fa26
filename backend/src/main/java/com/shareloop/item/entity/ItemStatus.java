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
    EXPIRED
}
