package com.shareloop.request.event;

/**
 * Phát ra khi bên kia không đồng ý đề nghị huỷ (GN-06) và giao dịch sang DISPUTED. Module report lắng nghe để mở khiếu
 * nại, người đề nghị là người khiếu nại. Request không gọi thẳng ReportService vì sẽ thành vòng phụ thuộc.
 *
 * @param note nội dung gồm lý do đề nghị huỷ và lời giải trình của bên kia
 * @param accusation true khi lý do đề nghị là tố bên kia (không đến, chưa gửi hàng, không liên hệ được)
 */
public record CancelRequestRejectedEvent(long requestId, long reporterId, String note, boolean accusation) {}
