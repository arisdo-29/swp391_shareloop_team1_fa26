package com.shareloop.admin.dto;

import java.time.Instant;

/**
 * Dời lịch của giao dịch nhìn từ Admin (GN-11): số lần đã dời được đồng ý và đề nghị đang chờ (nếu có).
 *
 * @param count số lần dời đã được đồng ý
 * @param pending true khi đang có đề nghị chờ bên kia phản hồi
 * @param proposedByRole "Chủ bài" hoặc "Người nhận", null khi không có đề nghị chờ
 * @param expiresAt hạn phản hồi, null khi không có đề nghị chờ
 */
public record AdminReschedule(
        int count,
        boolean pending,
        String proposedByRole,
        Instant proposedAt,
        Instant proposedMeetingTime,
        String proposedMeetingPlace,
        Instant expiresAt) {}
