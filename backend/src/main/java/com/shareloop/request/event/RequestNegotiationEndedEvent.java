package com.shareloop.request.event;

/**
 * Phát ra khi giao dịch rời giai đoạn thương lượng (RESERVED, AWAITING_LOGISTICS): đã chốt lịch (LOGISTICS_CONFIRMED)
 * hoặc bị huỷ. Module chat lắng nghe để tự huỷ đề nghị đổi món còn PENDING; request không gọi thẳng ChatService vì
 * ChatService đã phụ thuộc RequestService (sẽ thành vòng phụ thuộc).
 *
 * @param requestId giao dịch vừa rời giai đoạn thương lượng
 */
public record RequestNegotiationEndedEvent(long requestId) {}
