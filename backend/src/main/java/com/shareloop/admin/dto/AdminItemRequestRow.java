package com.shareloop.admin.dto;

import java.time.Instant;

/**
 * Một yêu cầu tới bài trong trang chi tiết bài của Admin.
 *
 * @param running true khi giao dịch đã được chọn và đang chạy (chưa hoàn tất hoặc huỷ)
 */
public record AdminItemRequestRow(
        long id,
        String receiverName,
        String type,
        String status,
        String offeredItemTitle,
        boolean running,
        Instant createdAt) {}
