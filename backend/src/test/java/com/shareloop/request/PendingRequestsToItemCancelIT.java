package com.shareloop.request;

import static org.assertj.core.api.Assertions.assertThat;

import com.shareloop.item.service.ItemService;
import com.shareloop.support.IntegrationTest;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.annotation.Transactional;

/**
 * C1: bài hết hạn, bị ẩn hoặc bản sửa bị từ chối thì các yêu cầu PENDING tới bài tự huỷ ngay, lý do ghi đúng nguyên
 * nhân. Chạy thật qua ItemService, ItemUnavailableEvent và listener của module request.
 */
@Transactional
class PendingRequestsToItemCancelIT extends IntegrationTest {

    @Autowired
    ItemService itemService;

    @Autowired
    JdbcClient jdbc;

    @Autowired
    EntityManager entityManager;

    @Test
    void markExpired_cancelsPendingRequestsWithExpiredReason() {
        // given
        long requestId = pendingRequestToItem("c1-exp", "APPROVED");

        // when
        itemService.markExpired(itemIdOf(requestId));
        entityManager.flush();

        // then
        assertCancelled(requestId, "Bài đã hết thời gian hiển thị");
    }

    @Test
    void hide_cancelsPendingRequestsWithHiddenReason() {
        // given
        long requestId = pendingRequestToItem("c1-hide", "APPROVED");

        // when
        itemService.hide(itemIdOf(requestId));
        entityManager.flush();

        // then
        assertCancelled(requestId, "Bài đang tạm ẩn");
    }

    @Test
    void hideByAdmin_cancelsPendingRequestsWithAdminReason() {
        // given
        long requestId = pendingRequestToItem("c1-admin", "APPROVED");

        // when
        itemService.hideByAdmin(itemIdOf(requestId));
        entityManager.flush();

        // then
        assertCancelled(requestId, "Bài đã bị quản trị viên ẩn");
    }

    @Test
    void hideAllOfUser_cancelsPendingRequestsWithLockedAccountReason() {
        // given
        long requestId = pendingRequestToItem("c1-lock", "APPROVED");
        long donorId = jdbc.sql("SELECT donor_id FROM items WHERE id = :id")
                .param("id", itemIdOf(requestId))
                .query(Long.class)
                .single();

        // when
        itemService.hideAllOfUser(donorId);
        entityManager.flush();

        // then
        assertCancelled(requestId, "Chủ bài đang bị khoá tài khoản");
    }

    @Test
    void markRejected_whenEditRejected_cancelsPendingRequestsWithEditingReason() {
        // given: bài đã sửa và đang chờ duyệt lại, người xin vẫn còn yêu cầu PENDING
        long requestId = pendingRequestToItem("c1-rej", "PENDING_REVIEW");

        // when
        itemService.markRejected(itemIdOf(requestId));
        entityManager.flush();

        // then
        assertCancelled(requestId, "Bài đang được chủ bài chỉnh sửa và chưa được duyệt");
        assertThat(noticeCount(requestId)).isEqualTo(1);
    }

    @Test
    void markExpired_whenRequestAlreadyReserved_keepsTheTransaction() {
        // given: yêu cầu đã được chọn (RESERVED) không bị lấy ra
        long requestId = pendingRequestToItem("c1-keep", "APPROVED");
        jdbc.sql("UPDATE requests SET status = 'RESERVED', reserved_at = now() WHERE id = :id")
                .param("id", requestId)
                .update();

        // when
        itemService.markExpired(itemIdOf(requestId));
        entityManager.flush();

        // then
        assertThat(statusOf(requestId)).isEqualTo("RESERVED");
    }

    private void assertCancelled(long requestId, String expectedReason) {
        assertThat(statusOf(requestId)).isEqualTo("CANCELLED");
        String reason = jdbc.sql("SELECT cancel_reason FROM requests WHERE id = :id")
                .param("id", requestId)
                .query(String.class)
                .single();
        assertThat(reason).contains(expectedReason);
    }

    private long noticeCount(long requestId) {
        return jdbc.sql("SELECT count(*) FROM notifications WHERE type = 'REQUEST_CANCELLED' AND link = :link")
                .param("link", "/requests/" + requestId)
                .query(Long.class)
                .single();
    }

    private String statusOf(long requestId) {
        return jdbc.sql("SELECT status FROM requests WHERE id = :id")
                .param("id", requestId)
                .query(String.class)
                .single();
    }

    private long itemIdOf(long requestId) {
        return jdbc.sql("SELECT item_id FROM requests WHERE id = :id")
                .param("id", requestId)
                .query(Long.class)
                .single();
    }

    private long pendingRequestToItem(String prefix, String itemStatus) {
        long donorId = user(prefix + "-d@shareloop.local", phone(prefix, 1));
        long receiverId = user(prefix + "-r@shareloop.local", phone(prefix, 2));
        long categoryId = jdbc.sql("SELECT id FROM item_categories WHERE is_active ORDER BY id LIMIT 1")
                .query(Long.class)
                .single();
        long areaId = jdbc.sql("SELECT id FROM areas WHERE level = 2 ORDER BY id LIMIT 1")
                .query(Long.class)
                .single();
        long itemId = jdbc.sql("INSERT INTO items (donor_id, category_id, area_id, offer_type, title, description, "
                        + "condition, status, approved_at, post_fee_paid, fee_state, free_edit_used) "
                        + "VALUES (:donorId, :categoryId, :areaId, 'GIVE', 'Bài được xin', 'Mô tả', 'GOOD', "
                        + ":status, now(), true, 'CHARGED', true) RETURNING id")
                .param("donorId", donorId)
                .param("categoryId", categoryId)
                .param("areaId", areaId)
                .param("status", itemStatus)
                .query(Long.class)
                .single();
        return jdbc.sql("INSERT INTO requests (item_id, receiver_id, type, status) "
                        + "VALUES (:itemId, :receiverId, 'GIVE', 'PENDING') RETURNING id")
                .param("itemId", itemId)
                .param("receiverId", receiverId)
                .query(Long.class)
                .single();
    }

    // Số điện thoại duy nhất theo tên test: băm tên rồi ghép một chữ số phân biệt người cho và người xin.
    private String phone(String prefix, int role) {
        return String.format("0905%05d%d", Math.abs(prefix.hashCode()) % 100000, role);
    }

    private long user(String email, String phone) {
        return jdbc.sql("INSERT INTO users (email, password_hash, full_name, phone, status, credit_balance) "
                        + "VALUES (:email, 'unused', 'Test', :phone, 'ACTIVE', 20) RETURNING id")
                .param("email", email)
                .param("phone", phone)
                .query(Long.class)
                .single();
    }
}
