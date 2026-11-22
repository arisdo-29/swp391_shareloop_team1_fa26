package com.shareloop.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.shareloop.admin.dto.AdminRequestDetail;
import com.shareloop.admin.dto.AdminRequestMessages;
import com.shareloop.admin.dto.AdminTimelineEvent;
import com.shareloop.admin.service.AdminRequestDetailService;
import com.shareloop.common.exception.BusinessException;
import com.shareloop.request.RequestErrorCode;
import com.shareloop.support.IntegrationTest;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

/**
 * Chi tiết giao dịch của Admin trên PostgreSQL thật: dòng thời gian, hai bên, báo cáo, điểm và tin nhắn chỉ xem được khi
 * giao dịch có báo cáo hoặc khiếu nại. @Transactional: mỗi test rollback nên không cần tự dọn.
 */
@Transactional
class AdminRequestDetailIT extends IntegrationTest {

    @Autowired
    AdminRequestDetailService adminRequestDetailService;

    @Autowired
    JdbcTemplate jdbc;

    @PersistenceContext
    EntityManager entityManager;

    @Test
    void getDetail_forCompletedRequest_returnsPartiesItemsTimelineReportsAndPoints() {
        // given: giao dịch Cho tặng đã hoàn tất, có một khiếu nại và điểm tích luỹ đã cộng cho chủ bài
        long admin = user("Quản trị");
        long donor = user("An Chủ Bài");
        long receiver = user("Bình Người Nhận");
        long item = item(donor, "Xe đạp cũ");
        long request = request(item, receiver, null, "GIVE", "COMPLETED");
        jdbc.update(
                "UPDATE requests SET created_at = now() - interval '6 days', delivery_method = 'IN_PERSON', meeting_place = 'Cổng trường',"
                        + " meeting_time = now() - interval '1 day', reserved_at = now() - interval '5 days',"
                        + " donor_schedule_ok_at = now() - interval '4 days',"
                        + " receiver_schedule_ok_at = now() - interval '3 days',"
                        + " contact_revealed_at = now() - interval '3 days',"
                        + " donor_handover_at = now() - interval '1 day', receiver_handover_at = now() - interval '23 hours',"
                        + " completed_at = now() - interval '22 hours' WHERE id = ?",
                request);
        report(receiver, request);
        jdbc.update(
                "INSERT INTO activity_logs (actor_id, type, target_type, target_id, data, created_at)"
                        + " VALUES (?, 'POINTS', 'REQUEST', ?, '{\"points\": 10, \"role\": \"DONOR\"}'::jsonb, now())",
                donor,
                request);
        entityManager.flush();

        // when
        AdminRequestDetail detail = adminRequestDetailService.getDetail(request);

        // then
        assertThat(detail.donor().name()).isEqualTo("An Chủ Bài");
        assertThat(detail.receiver().name()).isEqualTo("Bình Người Nhận");
        assertThat(detail.donor().trustStars()).isNotNull();
        assertThat(detail.item().title()).isEqualTo("Xe đạp cũ");
        assertThat(detail.offeredItem()).isNull();
        assertThat(detail.deliveryMethod()).isEqualTo("IN_PERSON");
        assertThat(detail.meetingPlace()).isEqualTo("Cổng trường");
        assertThat(detail.timeline())
                .extracting(AdminTimelineEvent::label)
                .containsExactly(
                        "Người nhận gửi yêu cầu",
                        "Chủ bài chọn người nhận",
                        "Chủ bài đồng ý lịch hẹn",
                        "Người nhận đồng ý lịch hẹn",
                        "Mở thông tin liên hệ cho hai bên",
                        "Chủ bài xác nhận đã giao nhận",
                        "Người nhận xác nhận đã giao nhận",
                        "Giao dịch hoàn tất");
        assertThat(detail.reports()).hasSize(1);
        assertThat(detail.reports().get(0).reporterName()).isEqualTo("Bình Người Nhận");
        assertThat(detail.messagesViewable()).isTrue();
        assertThat(detail.cancellable()).isFalse();
        assertThat(detail.points()).hasSize(1);
        assertThat(detail.points().get(0).userName()).isEqualTo("An Chủ Bài");
        assertThat(detail.points().get(0).points()).isEqualTo(10);
        assertThat(admin).isPositive();
    }

    @Test
    void getDetail_forSwapRequestCancelledByDonor_showsOfferedItemAndWhoCancelled() {
        // given
        long donor = user("An Chủ Bài");
        long receiver = user("Bình Người Nhận");
        long item = item(donor, "Bàn học");
        long offered = item(receiver, "Ghế xoay");
        long request = request(item, receiver, offered, "SWAP", "CANCELLED");
        jdbc.update("UPDATE requests SET cancel_reason = '[Chủ bài] Không liên lạc được' WHERE id = ?", request);
        entityManager.flush();

        // when
        AdminRequestDetail detail = adminRequestDetailService.getDetail(request);

        // then
        assertThat(detail.offeredItem().title()).isEqualTo("Ghế xoay");
        AdminTimelineEvent last = detail.timeline().get(detail.timeline().size() - 1);
        assertThat(last.label()).isEqualTo("Do chủ bài huỷ");
        assertThat(last.note()).isEqualTo("Không liên lạc được");
        assertThat(detail.messagesViewable()).isFalse();
        assertThat(detail.cancellable()).isFalse();
    }

    @Test
    void getDetail_forRunningRequest_isCancellable() {
        long donor = user("An Chủ Bài");
        long receiver = user("Bình Người Nhận");
        long request = request(item(donor, "Tủ sách"), receiver, null, "GIVE", "AWAITING_HANDOVER");
        entityManager.flush();

        assertThat(adminRequestDetailService.getDetail(request).cancellable()).isTrue();
    }

    @Test
    void getDetail_whenRequestMissing_throwsNotFound() {
        assertThatThrownBy(() -> adminRequestDetailService.getDetail(987_654_321L))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception ->
                                assertThat(exception.getErrorCode()).isEqualTo(RequestErrorCode.REQUEST_NOT_FOUND));
    }

    @Test
    void getMessages_whenNoReportOrDispute_throwsNotViewableAndDoesNotLog() {
        // given: giao dịch có tin nhắn nhưng chưa có báo cáo nào
        long admin = user("Quản trị");
        long donor = user("An Chủ Bài");
        long receiver = user("Bình Người Nhận");
        long request = request(item(donor, "Xe đạp"), receiver, null, "GIVE", "RESERVED");
        message(request, receiver, "Chào bạn");
        entityManager.flush();

        // when, then
        assertThatThrownBy(() -> adminRequestDetailService.getMessages(admin, request))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(RequestErrorCode.REQUEST_MESSAGES_NOT_VIEWABLE));
        assertThat(viewedChatLogs(request)).isZero();
    }

    @Test
    void getMessages_whenRequestHasReport_returnsMessagesWithRolesAndLogsEveryView() {
        // given
        long admin = user("Quản trị");
        long donor = user("An Chủ Bài");
        long receiver = user("Bình Người Nhận");
        long request = request(item(donor, "Xe đạp"), receiver, null, "GIVE", "DISPUTED");
        report(receiver, request);
        message(request, receiver, "Mình đến trễ chút nhé");
        message(request, donor, "Được bạn");
        jdbc.update(
                "INSERT INTO messages (request_id, sender_id, kind, content, moderation) VALUES (?, NULL, 'SYSTEM', 'Giao dịch có khiếu nại', 'PASS')",
                request);
        jdbc.update(
                "INSERT INTO messages (request_id, sender_id, kind, content, moderation) VALUES (?, ?, 'TEXT', 'Tin bị chặn', 'BLOCKED')",
                request,
                receiver);
        entityManager.flush();

        // when: Admin mở phần tin nhắn hai lần
        AdminRequestMessages first = adminRequestDetailService.getMessages(admin, request);
        adminRequestDetailService.getMessages(admin, request);

        // then: ba tin đã qua kiểm duyệt theo thứ tự cũ trước, đúng vai, mỗi lần mở có một dòng nhật ký
        assertThat(first.items()).hasSize(3);
        assertThat(first.items().get(0).senderRole()).isEqualTo("Người nhận");
        assertThat(first.items().get(0).senderName()).isEqualTo("Bình Người Nhận");
        assertThat(first.items().get(1).senderRole()).isEqualTo("Chủ bài");
        assertThat(first.items().get(2).senderRole()).isEqualTo("Hệ thống");
        assertThat(first.items()).noneMatch(message -> "Tin bị chặn".equals(message.content()));
        assertThat(viewedChatLogs(request)).isEqualTo(2);
    }

    // ---------- Helper ----------

    private long viewedChatLogs(long requestId) {
        entityManager.flush();
        return jdbc.queryForObject(
                "SELECT count(*) FROM activity_logs WHERE type = 'ADMIN_VIEWED_CHAT' AND target_id = ?",
                Long.class,
                requestId);
    }

    private long user(String fullName) {
        return jdbc.queryForObject(
                """
                INSERT INTO users (email, password_hash, full_name, phone, status)
                VALUES (?, 'unused-test-hash', ?, ?, 'ACTIVE') RETURNING id
                """,
                Long.class,
                UUID.randomUUID() + "@example.test",
                fullName,
                UUID.randomUUID().toString().substring(0, 12));
    }

    private long item(long donor, String title) {
        return jdbc.queryForObject("""
                INSERT INTO items (donor_id, category_id, area_id, offer_type, title, description, condition, status)
                VALUES (?, (SELECT id FROM item_categories ORDER BY id LIMIT 1),
                        (SELECT id FROM areas WHERE level = 2 ORDER BY id LIMIT 1),
                        'GIVE', ?, 'Mô tả', 'GOOD', 'APPROVED') RETURNING id
                """, Long.class, donor, title);
    }

    private long request(long itemId, long receiver, Long offeredItemId, String type, String status) {
        return jdbc.queryForObject(
                "INSERT INTO requests (item_id, receiver_id, offered_item_id, type, status) VALUES (?, ?, ?, ?, ?) RETURNING id",
                Long.class,
                itemId,
                receiver,
                offeredItemId,
                type,
                status);
    }

    private void report(long reporter, long requestId) {
        jdbc.update(
                "INSERT INTO reports (reporter_id, target_type, request_id, reason, description, stage, status)"
                        + " VALUES (?, 'REQUEST', ?, 'NO_SHOW', 'Không đến hẹn', 'AFTER', 'OPEN')",
                reporter,
                requestId);
    }

    private void message(long requestId, long sender, String content) {
        jdbc.update(
                "INSERT INTO messages (request_id, sender_id, kind, content, moderation) VALUES (?, ?, 'TEXT', ?, 'PASS')",
                requestId,
                sender,
                content);
    }
}
