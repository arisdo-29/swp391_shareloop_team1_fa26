package com.shareloop.request.mapper;

import com.shareloop.request.dto.RequestResponse;
import com.shareloop.request.entity.Request;

/**
 * Mapper chuyển đổi giữa entity Request và DTO (viết tay theo quy ước nhóm).
 */
public final class RequestMapper {

    private RequestMapper() {}

    public static RequestResponse toResponse(Request request) {
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
                request.getCreatedAt());
    }
}
