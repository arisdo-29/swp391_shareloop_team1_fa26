package com.shareloop.reputation.dto;

/**
 * Điểm tích luỹ đã cộng cho một người nhờ một giao dịch hoàn tất.
 *
 * @param role vai trò khi nhận điểm, theo nhật ký (chủ bài hoặc người nhận); null khi nhật ký không ghi
 * @param points điểm thực cộng, có thể 0 khi đã chạm trần trong ngày
 */
public record RequestPointsAward(long userId, String role, int points) {}
