package com.shareloop.request.service;

import com.shareloop.request.dto.RequestDeadlines;
import com.shareloop.request.entity.Request;
import com.shareloop.request.entity.RequestStatus;
import com.shareloop.setting.ConfigKey;
import com.shareloop.setting.service.ConfigService;
import java.time.Instant;
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
                autoCancelAt(request),
                autoConfirmAt(request),
                cancelRequestExpiresAt(request),
                rescheduleExpiresAt(request),
                sendDeadlineAt(request),
                legAutoReceiveAt(request, 1),
                legAutoReceiveAt(request, 2));
    }

    // Mốc tự huỷ: chưa chốt lịch thì tính từ lúc chọn người nhận, đã chốt mà không ai làm gì thì tính từ giờ hẹn (Giao
    // hàng: từ hạn gửi). Có đề nghị huỷ đang chờ thì mốc là hạn phản hồi nên ở đây để null.
    Instant autoCancelAt(Request request) {
        RequestStatus status = request.getStatus();
        if ((status == RequestStatus.RESERVED || status == RequestStatus.AWAITING_LOGISTICS)
                && request.getReservedAt() != null) {
            int chatDays = configService.getInt(ConfigKey.CHAT_OPEN_DAYS);
            return request.getReservedAt().plus(chatDays, ChronoUnit.DAYS);
        }
        if (status == RequestStatus.LOGISTICS_CONFIRMED
                && request.getMeetingTime() != null
                && !request.hasPendingCancelRequest()) {
            return staleThreshold(request);
        }
        return null;
    }

    // Mốc tự huỷ giao dịch đã chốt lịch mà không ai làm gì (GN-12).
    Instant staleThreshold(Request request) {
        int staleDays = configService.getInt(ConfigKey.CONFIRMED_STALE_CANCEL_DAYS);
        return staleBase(request).plus(staleDays, ChronoUnit.DAYS);
    }

    Instant staleBase(Request request) {
        Instant base = request.isShipping() ? sendDeadlineAt(request) : request.getMeetingTime();
        return afterResumption(base, request);
    }

    // Mốc tự hoàn tất: Gặp trực tiếp tính từ lúc một bên bấm hoàn tất, Giao hàng là mốc sớm nhất trong các chiều.
    Instant autoConfirmAt(Request request) {
        if (request.getStatus() != RequestStatus.AWAITING_HANDOVER) {
            return null;
        }
        if (request.isShipping()) {
            Instant first = legAutoReceiveAt(request, 1);
            Instant second = legAutoReceiveAt(request, 2);
            if (first == null) {
                return second;
            }
            return second != null && second.isBefore(first) ? second : first;
        }
        boolean donorConfirmed = request.getDonorHandoverAt() != null;
        boolean receiverConfirmed = request.getReceiverHandoverAt() != null;
        if (donorConfirmed == receiverConfirmed) {
            return null;
        }
        Instant confirmedAt = donorConfirmed ? request.getDonorHandoverAt() : request.getReceiverHandoverAt();
        int days = configService.getInt(ConfigKey.AUTO_CONFIRM_DAYS);
        return confirmedAt.plus(days, ChronoUnit.DAYS);
    }

    // Giao hàng: chiều đã gửi mà chưa nhận thì tự ghi nhận Đã nhận sau auto_confirm_days_shipping ngày kể từ lúc gửi.
    Instant legAutoReceiveAt(Request request, int leg) {
        if (!request.isShipping()
                || request.getStatus() != RequestStatus.AWAITING_HANDOVER
                || leg > request.requiredLegs()
                || request.isLegReceived(leg)) {
            return null;
        }
        Instant shippedAt = leg == 1 ? request.getDonorShippedAt() : request.getReceiverShippedAt();
        if (shippedAt == null) {
            return null;
        }
        int days = configService.getInt(ConfigKey.AUTO_CONFIRM_DAYS_SHIPPING);
        return afterResumption(shippedAt, request).plus(days, ChronoUnit.DAYS);
    }

    private Instant afterResumption(Instant base, Request request) {
        Instant resumedAt = request.getResumedAt();
        return resumedAt != null && resumedAt.isAfter(base) ? resumedAt : base;
    }

    // Hạn gửi của mỗi chiều giao hàng: ngày gửi dự kiến cộng shipping_send_deadline_days ngày (GN-12). Chỉ có ở Giao
    // hàng.
    Instant sendDeadlineAt(Request request) {
        if (!request.isShipping() || request.getMeetingTime() == null) {
            return null;
        }
        int days = configService.getInt(ConfigKey.SHIPPING_SEND_DEADLINE_DAYS);
        return request.getMeetingTime().plus(days, ChronoUnit.DAYS);
    }

    // Hạn bên kia phản hồi đề nghị dời lịch: sớm hơn giữa giờ hẹn cũ và lúc đề xuất cộng số giờ phản hồi (GN-11).
    Instant rescheduleExpiresAt(Request request) {
        if (!request.hasPendingReschedule()) {
            return null;
        }
        int hours = configService.getInt(ConfigKey.CANCEL_REQUEST_RESPONSE_HOURS);
        Instant byResponse = request.getRescheduleProposedAt().plus(hours, ChronoUnit.HOURS);
        Instant meeting = request.getMeetingTime();
        boolean meetingIsDeadline = !request.isShipping() && meeting != null && meeting.isBefore(byResponse);
        return meetingIsDeadline ? meeting : byResponse;
    }

    // Hạn bên kia phản hồi đề nghị huỷ; null khi không có đề nghị nào đang chờ.
    Instant cancelRequestExpiresAt(Request request) {
        if (!request.hasPendingCancelRequest()) {
            return null;
        }
        int hours = configService.getInt(ConfigKey.CANCEL_REQUEST_RESPONSE_HOURS);
        return request.getCancelRequestedAt().plus(hours, ChronoUnit.HOURS);
    }
}
