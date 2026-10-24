package com.shareloop.request.event;

/**
 * Phát ra khi bên nhận chọn "Không đúng mô tả" lúc xác nhận giao nhận (FR-85, BR-R05).
 * Module report lắng nghe để mở report; request không gọi thẳng ReportService vì ReportService đã phụ thuộc
 * RequestService (sẽ thành vòng phụ thuộc).
 *
 * @param requestId giao dịch được xác nhận
 * @param receiverId bên nhận, cũng là người báo cáo
 * @param reason lý do bên nhận đã nhập, dùng làm mô tả của report
 */
public record NotAsDescribedReportedEvent(long requestId, long receiverId, String reason) {}
