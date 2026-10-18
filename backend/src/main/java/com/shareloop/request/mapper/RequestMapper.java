package com.shareloop.request.mapper;

import com.shareloop.request.dto.RequestResponse;
import com.shareloop.request.entity.Request;

/**
 * Mapper chuyển đổi giữa entity Request và DTO (viết tay theo quy ước nhóm).
 */
public final class RequestMapper {

    private RequestMapper() {}

    public static RequestResponse toResponse(Request request) {
        return toResponse(request, null);
    }

    public static RequestResponse toResponse(Request request, String receiverName) {
        return toResponse(request, receiverName, null, null, null);
    }

    // Map đầy đủ họ tên và thông tin uy tín (TrustStars, hạng, tỉ lệ đúng mô tả) (FR-65, Issue #110).
    public static RequestResponse toResponse(
            Request request,
            String receiverName,
            Integer receiverTrustStars,
            String receiverRank,
            Double receiverAsDescribedRate) {
        if (request == null) {
            return null;
        }
        return new RequestResponse(
                request.getId(),
                request.getItemId(),
                request.getReceiverId(),
                request.getOfferedItemId(),
                request.getType(),
                request.getStatus().name(),
                request.getDeliveryMethod(),
                request.getMeetingPlace(),
                request.getMeetingTime(),
                request.getDonorScheduleOkAt(),
                request.getReceiverScheduleOkAt(),
                request.getDonorWaiverAt(),
                request.getReceiverWaiverAt(),
                request.getContactRevealedAt(),
                request.getDonorHandoverAt(),
                request.getReceiverHandoverAt(),
                request.getAsDescribed(),
                request.getCompletedAt(),
                request.getCreatedAt(),
                receiverName,
                receiverTrustStars,
                receiverRank,
                receiverAsDescribedRate);
    }
}
