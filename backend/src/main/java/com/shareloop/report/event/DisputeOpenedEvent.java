package com.shareloop.report.event;

/**
 * Phát ra sau khi một khiếu nại giao dịch được mở (khiếu nại sau hoàn tất, từ chối giao nhận, giao nhận không thành công).
 * Module chat lắng nghe để thêm một dòng tin hệ thống vào khung chat của giao dịch (D-04).
 *
 * @param requestId giao dịch bị khiếu nại
 * @param responseHours số giờ bên bị khiếu nại được giải trình (response_window_hours)
 */
public record DisputeOpenedEvent(long requestId, int responseHours) {}
