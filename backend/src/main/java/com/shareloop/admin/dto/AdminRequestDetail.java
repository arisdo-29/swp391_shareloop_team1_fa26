package com.shareloop.admin.dto;

import java.time.Instant;
import java.util.List;

/**
 * Trang chi tiết giao dịch của Admin, chỉ để xem. Thao tác duy nhất là huỷ giao dịch (đã có ở danh sách).
 *
 * @param type GIVE (Cho tặng) hoặc SWAP (Trao đổi)
 * @param deliveryMethod hình thức giao nhận, null khi chưa chọn
 * @param meetingPlace địa điểm hoặc địa chỉ giao nhận, null khi chưa có
 * @param timeline các mốc theo thứ tự thời gian
 * @param photos ảnh giao nhận
 * @param reports báo cáo và khiếu nại gắn với giao dịch
 * @param points điểm tích luỹ đã cộng khi giao dịch hoàn tất
 * @param messagesViewable true khi giao dịch có báo cáo hoặc khiếu nại nên Admin được xem tin nhắn
 * @param cancellable true khi giao dịch đã chọn người và chưa kết thúc nên Admin huỷ được
 */
public record AdminRequestDetail(
        long id,
        String type,
        String status,
        AdminRequestParty donor,
        AdminRequestParty receiver,
        AdminRequestItemInfo item,
        AdminRequestItemInfo offeredItem,
        String deliveryMethod,
        String meetingPlace,
        Instant meetingTime,
        List<AdminTimelineEvent> timeline,
        List<AdminRequestPhoto> photos,
        List<AdminRequestReportRow> reports,
        List<AdminRequestPoints> points,
        boolean messagesViewable,
        boolean cancellable,
        Instant createdAt) {}
