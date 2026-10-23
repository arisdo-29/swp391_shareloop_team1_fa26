package com.shareloop.request.mapper;

import com.shareloop.request.dto.RequestItemInfo;
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

    // Map họ tên và thông tin uy tín (TrustStars, hạng, tỉ lệ đúng mô tả) (FR-65, Issue #110), chưa có tên món đồ.
    public static RequestResponse toResponse(
            Request request,
            String receiverName,
            Integer receiverTrustStars,
            String receiverRank,
            Double receiverAsDescribedRate) {
        return toResponse(
                request,
                receiverName,
                receiverTrustStars,
                receiverRank,
                receiverAsDescribedRate,
                RequestItemInfo.EMPTY);
    }

    // Map đầy đủ, kèm tên món đồ và tên chủ bài để giao diện không phải hiện id (U2).
    public static RequestResponse toResponse(
            Request request,
            String receiverName,
            Integer receiverTrustStars,
            String receiverRank,
            Double receiverAsDescribedRate,
            RequestItemInfo itemInfo) {
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
                receiverAsDescribedRate,
                itemInfo.itemTitle(),
                itemInfo.itemImage(),
                itemInfo.donorId(),
                itemInfo.donorName(),
                itemInfo.offeredItemTitle());
    }
}
