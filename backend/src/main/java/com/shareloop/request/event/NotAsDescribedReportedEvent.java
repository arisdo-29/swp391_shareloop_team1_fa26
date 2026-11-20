package com.shareloop.request.event;

import java.util.List;

/**
 * Phát ra khi một bên từ chối nhận món đồ lúc giao nhận (GN-07).
 * Module report lắng nghe để mở report; request không gọi thẳng ReportService vì ReportService đã phụ thuộc
 * RequestService (sẽ thành vòng phụ thuộc).
 *
 * @param requestId giao dịch vừa chuyển sang DISPUTED
 * @param reporterId bên từ chối, cũng là người khiếu nại; bên bị báo là chủ của món bị từ chối
 * @param reason lý do bên từ chối đã nhập, dùng làm mô tả của report
 * @param evidenceMediaIds id media minh chứng, có thể rỗng hoặc null
 */
public record NotAsDescribedReportedEvent(
        long requestId, long reporterId, String reason, List<Long> evidenceMediaIds) {}
