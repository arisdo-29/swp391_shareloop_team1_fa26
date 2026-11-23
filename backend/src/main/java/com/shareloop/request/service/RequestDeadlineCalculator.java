package com.shareloop.request.service;

import com.shareloop.request.dto.RequestDeadlines;
import com.shareloop.request.entity.Request;
import com.shareloop.setting.ConfigKey;
import com.shareloop.setting.service.ConfigService;
import java.time.temporal.ChronoUnit;

/**
 * Tính các mốc hạn của giao dịch (GN-13) ở một chỗ duy nhất, dùng chung cho người dùng và Admin. Mọi con số đọc từ
 * ConfigService, FE chỉ hiện chứ không tự cộng ngày.
 */
final class RequestDeadlineCalculator {

    private final ConfigService configService;

    RequestDeadlineCalculator(ConfigService configService) {
        this.configService = configService;
    }

    RequestDeadlines of(Request request) {
        return new RequestDeadlines(
                null, null, cancelRequestExpiresAt(request), rescheduleExpiresAt(request), sendDeadlineAt(request));
    }

    // Hạn gửi của mỗi chiều giao hàng: ngày gửi dự kiến cộng shipping_send_deadline_days ngày (GN-12). Chỉ có ở Giao
    // hàng.
    java.time.Instant sendDeadlineAt(Request request) {
        if (!request.isShipping() || request.getMeetingTime() == null) {
            return null;
        }
        int days = configService.getInt(ConfigKey.SHIPPING_SEND_DEADLINE_DAYS);
        return request.getMeetingTime().plus(days, ChronoUnit.DAYS);
    }

    // Hạn bên kia phản hồi đề nghị dời lịch: sớm hơn giữa giờ hẹn cũ và lúc đề xuất cộng số giờ phản hồi (GN-11).
    private java.time.Instant rescheduleExpiresAt(Request request) {
        if (!request.hasPendingReschedule()) {
            return null;
        }
        int hours = configService.getInt(ConfigKey.CANCEL_REQUEST_RESPONSE_HOURS);
        java.time.Instant byResponse = request.getRescheduleProposedAt().plus(hours, ChronoUnit.HOURS);
        java.time.Instant meeting = request.getMeetingTime();
        boolean meetingIsDeadline = !request.isShipping() && meeting != null && meeting.isBefore(byResponse);
        return meetingIsDeadline ? meeting : byResponse;
    }

    // Hạn bên kia phản hồi đề nghị huỷ; null khi không có đề nghị nào đang chờ.
    private java.time.Instant cancelRequestExpiresAt(Request request) {
        if (!request.hasPendingCancelRequest()) {
            return null;
        }
        int hours = configService.getInt(ConfigKey.CANCEL_REQUEST_RESPONSE_HOURS);
        return request.getCancelRequestedAt().plus(hours, ChronoUnit.HOURS);
    }
}
