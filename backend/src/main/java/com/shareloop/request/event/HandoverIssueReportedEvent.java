package com.shareloop.request.event;

import java.util.List;

/**
 * Phát ra khi một bên báo chưa nhận được hoặc chưa giao được đồ dù bên kia đã xác nhận bàn giao.
 * Module report lắng nghe để mở khiếu nại; request không gọi thẳng ReportService vì ReportService đã phụ thuộc
 * RequestService (sẽ thành vòng phụ thuộc).
 *
 * @param requestId giao dịch vừa chuyển sang DISPUTED
 * @param reporterId bên báo sự cố, cũng là người khiếu nại
 * @param note mô tả sự cố, dùng làm nội dung khiếu nại
 * @param evidenceMediaIds id media minh chứng, có thể rỗng hoặc null
 */
public record HandoverIssueReportedEvent(long requestId, long reporterId, String note, List<Long> evidenceMediaIds) {}
