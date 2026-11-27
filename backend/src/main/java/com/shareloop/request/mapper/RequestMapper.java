package com.shareloop.request.mapper;

import com.shareloop.request.dto.CancelRequestInfo;
import com.shareloop.request.dto.RequestItemInfo;
import com.shareloop.request.dto.RequestResponse;
import com.shareloop.request.dto.RescheduleInfo;
import com.shareloop.request.dto.ShippingInfo;
import com.shareloop.request.entity.CancelRequestCode;
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
        return toResponse(request, receiverName, null, null);
    }

    // Map họ tên và thông tin uy tín (TrustStars, hạng) (YC-06, Issue #110), chưa có tên món đồ.
    public static RequestResponse toResponse(
            Request request, String receiverName, Integer receiverTrustStars, String receiverRank) {
        return toResponse(request, receiverName, receiverTrustStars, receiverRank, RequestItemInfo.EMPTY);
    }

    // Map đầy đủ, kèm tên món đồ và tên chủ bài để giao diện không phải hiện id (U2).
    public static RequestResponse toResponse(
            Request request,
            String receiverName,
            Integer receiverTrustStars,
            String receiverRank,
            RequestItemInfo itemInfo) {
        if (request == null) {
            return null;
        }
        // Hạn khiếu nại cần đọc ConfigService nên do RequestService tính và truyền vào overload đầy đủ (U08).
        return toResponse(request, receiverName, receiverTrustStars, receiverRank, itemInfo, null, null);
    }

    // Map đầy đủ kèm thời hạn khiếu nại (U08) và thời gian tin nhắn mới nhất (U03).
    public static RequestResponse toResponse(
            Request request,
            String receiverName,
            Integer receiverTrustStars,
            String receiverRank,
            RequestItemInfo itemInfo,
            java.time.Instant complaintDeadline,
            java.time.Instant lastMessageAt) {
        return toResponse(
                request,
                receiverName,
                receiverTrustStars,
                receiverRank,
                itemInfo,
                complaintDeadline,
                lastMessageAt,
                null);
    }

    // Map đầy đủ kèm thời hạn khiếu nại (U08), tin nhắn mới nhất (U03) và hạn giải trình khiếu nại (U5-BE).
    public static RequestResponse toResponse(
            Request request,
            String receiverName,
            Integer receiverTrustStars,
            String receiverRank,
            RequestItemInfo itemInfo,
            java.time.Instant complaintDeadline,
            java.time.Instant lastMessageAt,
            java.time.Instant disputeResponseDueAt) {
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
                request.getCompletedAt(),
                request.getCreatedAt(),
                receiverName,
                receiverTrustStars,
                receiverRank,
                itemInfo.itemTitle(),
                itemInfo.itemImage(),
                itemInfo.donorId(),
                itemInfo.donorName(),
                itemInfo.offeredItemTitle(),
                request.getCancelReason(),
                complaintDeadline,
                lastMessageAt,
                disputeResponseDueAt,
                // Ảnh đại diện, cờ hết hạn khiếu nại và ảnh giao nhận do RequestService.addExtraInfo gắn sau.
                null,
                null,
                null,
                null,
                itemInfo.itemOfferType(),
                // Phí đọc ConfigService nên mapper (static) không tính được; RequestService.addExtraInfo điền.
                null,
                // Khiếu nại theo vai và báo cáo từ chối nhận cần biết người xem nên RequestService gắn sau bằng
                // withReports.
                null,
                null,
                // Cờ do RequestService gắn sau; đề nghị huỷ đang chờ lấy thẳng từ giao dịch (GN-06).
                null,
                toCancelRequestInfo(request),
                toRescheduleInfo(request),
                toShippingInfo(request),
                null,
                itemInfo.offeredItemReferencePrice(),
                itemInfo.offeredItemAreaId(),
                request.getScheduleRevision());
    }

    // Tiến độ giao hàng hai chiều (GN-12). Địa chỉ đầy đủ ở đây, RequestService ẩn địa chỉ của bên kia khi chưa chốt
    // lịch.
    private static ShippingInfo toShippingInfo(Request request) {
        if (!request.isShipping()) {
            return ShippingInfo.EMPTY;
        }
        return new ShippingInfo(
                request.getReceiverAddress(),
                request.getDonorAddress(),
                request.getDonorShippedAt(),
                request.getDonorTrackingCode(),
                request.getReceiverShippedAt(),
                request.getReceiverTrackingCode(),
                isFilled(request.getReceiverAddress()),
                isFilled(request.getDonorAddress()));
    }

    private static boolean isFilled(String text) {
        return text != null && !text.isBlank();
    }

    // Đề nghị dời lịch đang chờ và số lần đã dời (GN-11).
    private static RescheduleInfo toRescheduleInfo(Request request) {
        if (!request.hasPendingReschedule()) {
            return new RescheduleInfo(null, null, null, null, request.getRescheduleCount());
        }
        return new RescheduleInfo(
                request.getRescheduleProposedBy(),
                request.getRescheduleProposedAt(),
                request.getRescheduleMeetingTime(),
                request.getRescheduleMeetingPlace(),
                request.getRescheduleCount());
    }

    // Đề nghị huỷ đang chờ bên kia phản hồi; mọi trường null khi không có.
    private static CancelRequestInfo toCancelRequestInfo(Request request) {
        if (!request.hasPendingCancelRequest()) {
            return CancelRequestInfo.EMPTY;
        }
        CancelRequestCode code = request.getCancelRequestCode();
        return new CancelRequestInfo(
                request.getCancelRequestedBy(),
                request.getCancelRequestedAt(),
                code == null ? null : code.name(),
                code == null ? null : code.label(),
                request.getCancelRequestReason());
    }
}
