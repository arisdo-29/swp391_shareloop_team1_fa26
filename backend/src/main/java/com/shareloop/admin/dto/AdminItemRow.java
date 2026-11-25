package com.shareloop.admin.dto;

import com.shareloop.item.entity.ItemStatus;
import com.shareloop.item.entity.OfferType;
import java.time.Instant;

/**
 * Một dòng trong danh sách Quản lý bài đăng của Admin.
 *
 * @param active false khi bài đang bị ẩn
 * @param hiddenSource ai ẩn bài: ADMIN, OWNER_LOCKED (chủ bài bị khoá) hoặc OTHER (báo cáo hoặc hệ thống); null khi bài đang hiện
 * @param postFeePaid tổng phí đăng đã thu, tính theo Credit dương
 * @param pendingRequestCount số yêu cầu đang chờ chọn
 * @param expireAt hạn hiển thị, null khi chưa có
 * @param boostedUntil hạn đẩy, null khi không đẩy
 * @param boosting true khi bài đang được đẩy và còn hạn
 */
public record AdminItemRow(
        long id,
        String title,
        String imageUrl,
        long ownerId,
        String ownerName,
        String ownerEmail,
        String categoryName,
        OfferType offerType,
        ItemStatus status,
        boolean active,
        String hiddenSource,
        long postFeePaid,
        long pendingRequestCount,
        Instant createdAt,
        Instant approvedAt,
        Instant expireAt,
        Instant boostedUntil,
        boolean boosting) {}
