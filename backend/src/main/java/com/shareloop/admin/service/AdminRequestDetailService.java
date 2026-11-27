package com.shareloop.admin.service;

import com.shareloop.admin.dto.AdminCancelRequest;
import com.shareloop.admin.dto.AdminChatMessage;
import com.shareloop.admin.dto.AdminRequestDetail;
import com.shareloop.admin.dto.AdminRequestItemInfo;
import com.shareloop.admin.dto.AdminRequestMessages;
import com.shareloop.admin.dto.AdminRequestParty;
import com.shareloop.admin.dto.AdminRequestPhoto;
import com.shareloop.admin.dto.AdminRequestPoints;
import com.shareloop.admin.dto.AdminRequestReportRow;
import com.shareloop.admin.dto.AdminReschedule;
import com.shareloop.admin.dto.AdminShippingLeg;
import com.shareloop.admin.dto.AdminTimelineEvent;
import com.shareloop.audit.ActivityType;
import com.shareloop.audit.service.ActivityLogService;
import com.shareloop.chat.dto.MessageResponse;
import com.shareloop.chat.service.ChatService;
import com.shareloop.common.exception.BusinessException;
import com.shareloop.item.dto.ItemCardDto;
import com.shareloop.item.dto.ItemSummary;
import com.shareloop.item.service.ItemQueryService;
import com.shareloop.report.dto.RequestReportBrief;
import com.shareloop.report.service.ReportService;
import com.shareloop.reputation.dto.ReputationSummary;
import com.shareloop.reputation.dto.RequestPointsAward;
import com.shareloop.reputation.service.ReputationService;
import com.shareloop.request.RequestErrorCode;
import com.shareloop.request.dto.AdminRequestCore;
import com.shareloop.request.dto.RequestMediaResponse;
import com.shareloop.request.entity.Request;
import com.shareloop.request.service.RequestQueryService;
import com.shareloop.request.service.RequestService;
import com.shareloop.user.dto.UserBrief;
import com.shareloop.user.service.UserService;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Trang chi tiết giao dịch của Admin, chỉ để xem: hai bên, hai món, giao nhận, dòng thời gian, ảnh, báo cáo, điểm và
 * (khi giao dịch có báo cáo hoặc khiếu nại) tin nhắn. Đặt ở module admin để module request không phải phụ thuộc vào
 * chat, report và reputation (chat đã phụ thuộc request nên chiều ngược lại sẽ tạo vòng).
 */
@Service
public class AdminRequestDetailService {

    // Giao dịch đã chọn người và chưa kết thúc: Admin huỷ được
    private static final Set<String> CANCELLABLE_STATUSES =
            Set.of("RESERVED", "AWAITING_LOGISTICS", "LOGISTICS_CONFIRMED", "AWAITING_HANDOVER", "DISPUTED");

    private final RequestQueryService requestQueryService;
    private final RequestService requestService;
    private final ItemQueryService itemQueryService;
    private final UserService userService;
    private final ReputationService reputationService;
    private final ReportService reportService;
    private final ChatService chatService;
    private final ActivityLogService activityLogService;

    public AdminRequestDetailService(
            RequestQueryService requestQueryService,
            RequestService requestService,
            ItemQueryService itemQueryService,
            UserService userService,
            ReputationService reputationService,
            ReportService reportService,
            ChatService chatService,
            ActivityLogService activityLogService) {
        this.requestQueryService = requestQueryService;
        this.requestService = requestService;
        this.itemQueryService = itemQueryService;
        this.userService = userService;
        this.reputationService = reputationService;
        this.reportService = reportService;
        this.chatService = chatService;
        this.activityLogService = activityLogService;
    }

    /**
     * Chi tiết một giao dịch ở mọi trạng thái.
     *
     * @throws BusinessException REQUEST_NOT_FOUND khi không có giao dịch
     */
    @Transactional(readOnly = true)
    public AdminRequestDetail getDetail(long requestId) {
        AdminRequestCore core = requestQueryService.getAdminCore(requestId);
        Long donorId = findDonorId(core.itemId());
        List<RequestMediaResponse> media = requestService.getMediaListForAdmin(requestId);
        List<RequestReportBrief> reports = reportService.findReportsOfRequest(requestId);
        List<RequestPointsAward> awards = reputationService.findPointsAwardedForRequest(requestId);

        Set<Long> userIds = new HashSet<>();
        userIds.add(core.receiverId());
        if (donorId != null) {
            userIds.add(donorId);
        }
        media.forEach(photo -> addIfPresent(userIds, photo.uploadedBy()));
        reports.forEach(report -> userIds.add(report.reporterId()));
        awards.forEach(award -> userIds.add(award.userId()));
        Map<Long, UserBrief> briefs = userService.findBriefs(userIds);
        Set<Long> lockedIds = userService.findLockedIds(userIds);

        return new AdminRequestDetail(
                core.id(),
                core.type(),
                core.status(),
                toParty(donorId, briefs, lockedIds),
                toParty(core.receiverId(), briefs, lockedIds),
                toItemInfo(core.itemId()),
                core.offeredItemId() == null ? null : toItemInfo(core.offeredItemId()),
                core.deliveryMethod(),
                core.meetingPlace(),
                core.meetingTime(),
                buildTimeline(core),
                toPhotos(media, briefs),
                toReportRows(reports, briefs),
                toPoints(awards, briefs),
                !reports.isEmpty(),
                CANCELLABLE_STATUSES.contains(core.status()),
                core.createdAt(),
                toCancelRequest(core),
                toReschedule(core),
                toShippingLegs(
                        core,
                        toItemInfo(core.itemId()),
                        core.offeredItemId() == null ? null : toItemInfo(core.offeredItemId())),
                core.deadlines(),
                core.autoConfirmed());
    }

    /**
     * Tin nhắn của giao dịch, chỉ trả khi giao dịch có báo cáo hoặc khiếu nại. Mỗi lần gọi ghi nhật ký
     * ADMIN_VIEWED_CHAT vì tin nhắn là dữ liệu riêng tư của hai bên.
     *
     * @throws BusinessException REQUEST_NOT_FOUND; REQUEST_MESSAGES_NOT_VIEWABLE khi giao dịch chưa có báo cáo hay khiếu nại
     */
    @Transactional
    public AdminRequestMessages getMessages(long adminId, long requestId) {
        AdminRequestCore core = requestQueryService.getAdminCore(requestId);
        if (!reportService.hasReportForRequest(requestId)) {
            throw new BusinessException(RequestErrorCode.REQUEST_MESSAGES_NOT_VIEWABLE);
        }
        Long donorId = findDonorId(core.itemId());
        List<MessageResponse> messages = chatService.findMessagesForAdmin(requestId);

        Set<Long> senderIds = new HashSet<>();
        messages.forEach(message -> addIfPresent(senderIds, message.senderId()));
        Map<Long, UserBrief> briefs = userService.findBriefs(senderIds);

        List<AdminChatMessage> rows = new ArrayList<>();
        for (MessageResponse message : messages) {
            rows.add(toChatMessage(requestId, message, donorId, core.receiverId(), briefs));
        }
        activityLogService.log(
                ActivityType.ADMIN_VIEWED_CHAT.code(),
                adminId,
                "REQUEST",
                requestId,
                Map.<String, Object>of("messageCount", rows.size()),
                "Xem tin nhắn của giao dịch có báo cáo hoặc khiếu nại");
        return new AdminRequestMessages(rows);
    }

    // Bài đã xoá hoặc không tra được thì không biết chủ bài, trang vẫn hiện được phần còn lại.
    private Long findDonorId(long itemId) {
        try {
            ItemSummary summary = itemQueryService.getSummary(itemId);
            return summary == null ? null : summary.donorId();
        } catch (BusinessException deleted) {
            return null;
        }
    }

    private AdminRequestParty toParty(Long userId, Map<Long, UserBrief> briefs, Set<Long> lockedIds) {
        if (userId == null) {
            return null;
        }
        UserBrief brief = briefs.get(userId);
        ReputationSummary reputation = findReputation(userId);
        return new AdminRequestParty(
                userId,
                brief == null ? null : brief.fullName(),
                brief == null ? null : brief.email(),
                reputation == null ? null : reputation.trustStars(),
                reputation == null || reputation.rank() == null
                        ? null
                        : reputation.rank().name(),
                lockedIds.contains(userId));
    }

    private ReputationSummary findReputation(long userId) {
        try {
            return reputationService.getReputationSummary(userId);
        } catch (RuntimeException notAvailable) {
            return null;
        }
    }

    private AdminRequestItemInfo toItemInfo(long itemId) {
        try {
            ItemCardDto card = itemQueryService.getItemCard(itemId);
            return new AdminRequestItemInfo(itemId, card.title(), card.imageUrl(), card.status(), card.offerType());
        } catch (BusinessException deleted) {
            return new AdminRequestItemInfo(itemId, null, null, null, null);
        }
    }

    private static String roleOf(Long userId, AdminRequestCore core) {
        return userId != null && userId == core.receiverId() ? "Người nhận" : "Chủ bài";
    }

    // Đề nghị huỷ: đang chờ khi giao dịch còn mở, đã đồng ý khi giao dịch huỷ, bị từ chối khi sang sự cố giao nhận.
    static AdminCancelRequest toCancelRequest(AdminRequestCore core) {
        if (core.cancelRequest().cancelRequestedBy() == null) {
            return null;
        }
        String status = core.status();
        String state = "CANCELLED".equals(status)
                ? "ACCEPTED"
                : "DISPUTED".equals(status) || "COMPLETED".equals(status) ? "REJECTED" : "PENDING";
        return new AdminCancelRequest(
                state,
                roleOf(core.cancelRequest().cancelRequestedBy(), core),
                core.cancelRequest().cancelRequestedAt(),
                core.cancelRequest().cancelRequestLabel(),
                core.cancelRequest().cancelRequestReason(),
                "PENDING".equals(state) ? core.deadlines().cancelRequestExpiresAt() : null);
    }

    static AdminReschedule toReschedule(AdminRequestCore core) {
        var reschedule = core.reschedule();
        int count = reschedule.rescheduleCount() == null ? 0 : reschedule.rescheduleCount();
        boolean pending = reschedule.rescheduleProposedBy() != null
                && ("LOGISTICS_CONFIRMED".equals(core.status()) || "AWAITING_HANDOVER".equals(core.status()));
        if (count == 0 && !pending) {
            return null;
        }
        return new AdminReschedule(
                count,
                pending,
                pending ? roleOf(reschedule.rescheduleProposedBy(), core) : null,
                pending ? reschedule.rescheduleProposedAt() : null,
                pending ? reschedule.rescheduleMeetingTime() : null,
                pending ? reschedule.rescheduleMeetingPlace() : null,
                pending ? core.deadlines().rescheduleExpiresAt() : null);
    }

    // Giao hàng: mỗi chiều có địa chỉ, ngày gửi, ngày nhận. Admin thấy đủ địa chỉ (GN-12).
    static List<AdminShippingLeg> toShippingLegs(
            AdminRequestCore core, AdminRequestItemInfo mainItem, AdminRequestItemInfo offeredItem) {
        if (!"SHIPPING".equals(core.deliveryMethod())) {
            return List.of();
        }
        var shipping = core.shipping();
        List<AdminShippingLeg> legs = new ArrayList<>();
        legs.add(shippingLeg(
                1,
                mainItem == null ? null : mainItem.title(),
                "Chủ bài",
                "Người nhận",
                shipping.receiverAddress(),
                shipping.donorShippedAt(),
                core.receiverHandoverAt()));
        if (core.offeredItemId() != null) {
            legs.add(shippingLeg(
                    2,
                    offeredItem == null ? null : offeredItem.title(),
                    "Người nhận",
                    "Chủ bài",
                    shipping.donorAddress(),
                    shipping.receiverShippedAt(),
                    core.donorHandoverAt()));
        }
        return legs;
    }

    private static AdminShippingLeg shippingLeg(
            int leg,
            String title,
            String senderRole,
            String receiverRole,
            String address,
            Instant shippedAt,
            Instant receivedAt) {
        String step = receivedAt != null ? "Đã nhận" : shippedAt != null ? "Đã gửi" : "Chờ gửi";
        return new AdminShippingLeg(leg, title, senderRole, receiverRole, address, shippedAt, receivedAt, step);
    }

    /**
     * Dòng thời gian dựng từ các cột mốc của giao dịch, sắp theo thời gian. Người huỷ lấy từ tiền tố của lý do huỷ. Mốc
     * huỷ dùng lần cập nhật cuối vì giao dịch không lưu riêng thời điểm huỷ.
     */
    static List<AdminTimelineEvent> buildTimeline(AdminRequestCore core) {
        List<AdminTimelineEvent> events = new ArrayList<>();
        add(events, "Người nhận gửi yêu cầu", core.createdAt(), null);
        add(events, "Chủ bài chọn người nhận", core.reservedAt(), null);
        add(events, "Chủ bài cam kết giao nhận", core.donorWaiverAt(), null);
        add(events, "Người nhận cam kết giao nhận", core.receiverWaiverAt(), null);
        add(events, "Chủ bài đồng ý lịch hẹn", core.donorScheduleOkAt(), null);
        add(events, "Người nhận đồng ý lịch hẹn", core.receiverScheduleOkAt(), null);
        add(events, "Mở thông tin liên hệ cho hai bên", core.contactRevealedAt(), null);
        boolean shipping = "SHIPPING".equals(core.deliveryMethod());
        if (shipping) {
            add(events, "Chủ bài gửi hàng (chiều 1)", core.shipping().donorShippedAt(), null);
            add(events, "Người nhận gửi hàng (chiều 2)", core.shipping().receiverShippedAt(), null);
            add(events, "Chủ bài nhận được hàng (chiều 2)", core.donorHandoverAt(), null);
            add(events, "Người nhận nhận được hàng (chiều 1)", core.receiverHandoverAt(), null);
        } else {
            add(events, "Chủ bài xác nhận đã giao nhận", core.donorHandoverAt(), null);
            add(events, "Người nhận xác nhận đã giao nhận", core.receiverHandoverAt(), null);
        }
        if (core.cancelRequest().cancelRequestedBy() != null) {
            String requester = roleOf(core.cancelRequest().cancelRequestedBy(), core);
            String reason = core.cancelRequest().cancelRequestLabel() == null
                    ? null
                    : core.cancelRequest().cancelRequestLabel()
                            + (core.cancelRequest().cancelRequestReason() == null
                                    ? ""
                                    : ". " + core.cancelRequest().cancelRequestReason());
            add(
                    events,
                    requester + " đề nghị huỷ giao dịch",
                    core.cancelRequest().cancelRequestedAt(),
                    reason);
        }
        if (core.reschedule().rescheduleProposedBy() != null) {
            add(
                    events,
                    roleOf(core.reschedule().rescheduleProposedBy(), core) + " đề nghị dời lịch",
                    core.reschedule().rescheduleProposedAt(),
                    null);
        }
        if ("COMPLETED".equals(core.status())) {
            add(
                    events,
                    core.autoConfirmed() ? "Giao dịch tự hoàn tất" : "Giao dịch hoàn tất",
                    core.completedAt(),
                    null);
        }
        if ("CANCELLED".equals(core.status())) {
            events.add(cancelEvent(core));
        }
        if ("REJECTED".equals(core.status())) {
            add(events, "Chủ bài chọn người nhận khác", core.updatedAt(), null);
        }
        events.sort(Comparator.comparing(AdminTimelineEvent::at));
        return events;
    }

    private static void add(List<AdminTimelineEvent> events, String label, Instant at, String note) {
        if (at != null) {
            events.add(new AdminTimelineEvent(label, at, note));
        }
    }

    private static AdminTimelineEvent cancelEvent(AdminRequestCore core) {
        String reason = core.cancelReason() == null ? "" : core.cancelReason();
        String label = "Giao dịch bị huỷ";
        if (reason.startsWith(Request.CANCEL_BY_DONOR)) {
            label = "Do chủ bài huỷ";
            reason = reason.substring(Request.CANCEL_BY_DONOR.length());
        } else if (reason.startsWith(Request.CANCEL_BY_RECEIVER)) {
            label = "Do người nhận huỷ";
            reason = reason.substring(Request.CANCEL_BY_RECEIVER.length());
        } else if (reason.startsWith(Request.CANCEL_BY_ADMIN)) {
            label = "Do Admin huỷ";
            reason = reason.substring(Request.CANCEL_BY_ADMIN.length());
        } else if (reason.startsWith(Request.CANCEL_BY_SYSTEM)) {
            label = "Do hệ thống huỷ";
            reason = reason.substring(Request.CANCEL_BY_SYSTEM.length());
        }
        return new AdminTimelineEvent(label, core.updatedAt(), reason.isBlank() ? null : reason.trim());
    }

    private static List<AdminRequestPhoto> toPhotos(List<RequestMediaResponse> media, Map<Long, UserBrief> briefs) {
        List<AdminRequestPhoto> photos = new ArrayList<>();
        for (RequestMediaResponse photo : media) {
            UserBrief uploader = photo.uploadedBy() == null ? null : briefs.get(photo.uploadedBy());
            photos.add(new AdminRequestPhoto(
                    photo.mediaId(),
                    photo.url(),
                    photo.stage(),
                    uploader == null ? null : uploader.fullName(),
                    photo.createdAt()));
        }
        return photos;
    }

    private static List<AdminRequestReportRow> toReportRows(
            List<RequestReportBrief> reports, Map<Long, UserBrief> briefs) {
        List<AdminRequestReportRow> rows = new ArrayList<>();
        for (RequestReportBrief report : reports) {
            UserBrief reporter = briefs.get(report.reporterId());
            rows.add(new AdminRequestReportRow(
                    report.id(),
                    reporter == null ? null : reporter.fullName(),
                    report.targetType(),
                    report.reason(),
                    report.stage(),
                    report.status(),
                    report.createdAt()));
        }
        return rows;
    }

    private static List<AdminRequestPoints> toPoints(List<RequestPointsAward> awards, Map<Long, UserBrief> briefs) {
        List<AdminRequestPoints> rows = new ArrayList<>();
        for (RequestPointsAward award : awards) {
            UserBrief user = briefs.get(award.userId());
            rows.add(new AdminRequestPoints(user == null ? null : user.fullName(), award.role(), award.points()));
        }
        return rows;
    }

    private static AdminChatMessage toChatMessage(
            long requestId, MessageResponse message, Long donorId, long receiverId, Map<Long, UserBrief> briefs) {
        Long senderId = message.senderId();
        String role = "Hệ thống";
        if (senderId != null) {
            role = senderId.equals(donorId) ? "Chủ bài" : senderId == receiverId ? "Người nhận" : "Người dùng";
        }
        UserBrief sender = senderId == null ? null : briefs.get(senderId);
        return new AdminChatMessage(
                message.id(),
                sender == null ? null : sender.fullName(),
                role,
                message.kind(),
                message.content(),
                // Ảnh chat riêng tư: đọc qua endpoint của giao dịch, Admin được phép (D2)
                message.mediaId() == null ? null : "/api/v1/requests/" + requestId + "/media/" + message.mediaId(),
                message.itemCard() == null ? null : message.itemCard().title(),
                message.createdAt());
    }

    private static void addIfPresent(Set<Long> ids, Long id) {
        if (id != null) {
            ids.add(id);
        }
    }
}
