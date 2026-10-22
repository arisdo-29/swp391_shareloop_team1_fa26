package com.shareloop.reputation.dto;

/**
 * Kết quả cấp sao ân hạn (FR-95).
 *
 * @param userId người được cấp
 * @param trustStars số sao sau khi cấp, luôn là 1
 * @param graceStarUsed luôn true: mỗi tài khoản chỉ được cấp một lần
 */
public record GraceStarResponse(Long userId, int trustStars, boolean graceStarUsed) {}
