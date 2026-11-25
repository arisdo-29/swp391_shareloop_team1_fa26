package com.shareloop.user.dto;

import com.shareloop.reputation.entity.Rank;
import java.time.Instant;

/**
 * Hồ sơ công khai của một người dùng, cho người khác xem (GET /api/v1/users/{id}/profile).
 *
 * <p>Cố ý KHÔNG có email, số điện thoại, số dư Credit, điểm thưởng, địa chỉ chi tiết. Không thêm các trường đó vào đây.
 *
 * @param avatarUrl null khi chưa có ảnh đại diện
 * @param avatarInitials chữ viết tắt họ tên để hiện thay ảnh, ví dụ "NA" cho "Nguyễn Văn An"
 * @param district tên khu vực mặc định; null khi người dùng chưa chọn khu vực
 * @param joinedAt thời điểm tạo tài khoản
 */
public record PublicProfileResponse(
        Long id,
        String fullName,
        String avatarUrl,
        String avatarInitials,
        String district,
        Rank rank,
        Integer trustStars,
        long completedTransactions,
        Instant joinedAt) {}
