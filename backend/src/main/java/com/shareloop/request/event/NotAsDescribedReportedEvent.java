package com.shareloop.request.event;

import java.util.List;

/**
 * Phát ra khi một bên từ chối nhận món đồ lúc giao nhận (GN-07, BC-09). Giao dịch đã huỷ và món bị từ chối đã tạm ẩn;
 * module report lắng nghe để mở báo cáo vi phạm "Không đúng mô tả" cho Admin xem xét. Request không gọi thẳng
 * ReportService vì ReportService đã phụ thuộc RequestService (sẽ thành vòng phụ thuộc).
 *
 * @param requestId giao dịch vừa bị huỷ vì từ chối nhận
 * @param reporterId bên từ chối, cũng là người báo cáo
 * @param refusedItemId món bị từ chối (bài chính khi người nhận từ chối, món đổi khi chủ bài từ chối)
 * @param reportedUserId chủ của món bị từ chối, là bên bị báo cáo
 * @param reason lý do bên từ chối đã nhập, dùng làm mô tả của báo cáo
 * @param evidenceMediaIds id media minh chứng, có thể rỗng hoặc null
 */
public record NotAsDescribedReportedEvent(
        long requestId,
        long reporterId,
        long refusedItemId,
        long reportedUserId,
        String reason,
        List<Long> evidenceMediaIds) {}
