package com.shareloop.user;

import static org.assertj.core.api.Assertions.assertThat;

import com.shareloop.support.IntegrationTest;
import com.shareloop.user.service.AdminUserService;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/** Khoá tài khoản trên PostgreSQL thật: huỷ giao dịch đang mở và nhả phí đang giữ trong cùng một lần khoá. */
class AdminUserLockCascadeIT extends IntegrationTest {

    private static final AtomicInteger SEQUENCE = new AtomicInteger();

    @Autowired
    AdminUserService adminUserService;

    @Autowired
    JdbcTemplate jdbc;

    private final List<Long> userIds = new ArrayList<>();

    @AfterEach
    void cleanUp() {
        for (Long userId : userIds) {
            jdbc.update(
                    "DELETE FROM activity_logs WHERE actor_id = ? OR (target_type = 'USER' AND target_id = ?)",
                    userId,
                    userId);
            jdbc.update("DELETE FROM notifications WHERE user_id = ?", userId);
            jdbc.update(
                    "DELETE FROM requests WHERE receiver_id = ? OR item_id IN (SELECT id FROM items WHERE donor_id = ?)",
                    userId,
                    userId);
            jdbc.update("DELETE FROM items WHERE donor_id = ?", userId);
        }
        for (Long userId : userIds) {
            jdbc.update("DELETE FROM users WHERE id = ?", userId);
        }
        userIds.clear();
    }

    @Test
    @DisplayName("Khoá người có 1 giao dịch đang mở và 1 bài đang giữ phí: giao dịch huỷ, bài nhả, Credit giữ về 0")
    void lock_whenUserHasOpenRequestAndHeldFee_cancelsRequestAndReleasesCredit() {
        // given: người bị khoá có 100 Credit, đang giữ 10 cho một bài chờ duyệt
        long adminId = insertUser(0, 0);
        long lockedUserId = insertUser(100, 10);
        long otherUserId = insertUser(0, 0);
        long pendingItemId = insertItem(lockedUserId, "SWAP", "PENDING_REVIEW", "HELD", 10);
        // và đang là người nhận trong một giao dịch của người khác
        long otherItemId = insertItem(otherUserId, "GIVE", "RESERVED", "NONE", 0);
        long requestId = insertRequest(otherItemId, lockedUserId, "RESERVED");

        // when
        adminUserService.lock(adminId, lockedUserId, "Vi phạm nhiều lần");

        // then
        assertThat(requestStatusOf(requestId)).isEqualTo("CANCELLED");
        assertThat(cancelReasonOf(requestId)).isEqualTo("[Hệ thống] Tài khoản bị khoá");
        assertThat(itemStatusOf(otherItemId)).isEqualTo("APPROVED");
        assertThat(itemStatusOf(pendingItemId)).isEqualTo("REJECTED");
        assertThat(heldCreditOf(lockedUserId)).isZero();
        // khả dụng = credit_balance - held_credit: từ 90 lên 100
        assertThat(creditBalanceOf(lockedUserId) - heldCreditOf(lockedUserId)).isEqualTo(100);
        assertThat(isActive(lockedUserId)).isFalse();
        assertThat(lockLogData(lockedUserId))
                .contains("\"cancelledRequests\": 1")
                .contains("\"releasedCredit\": 10");
    }

    @Test
    @DisplayName("Khoá ẩn mọi bài APPROVED (kể cả bài vừa được nhả); mở khoá chỉ hiện lại đúng các bài bị ẩn do khoá")
    void lockThenUnlock_hidesApprovedItemsAndRestoresOnlyThoseHiddenByLock() {
        // given: một bài đang hiển thị, một bài RESERVED có giao dịch mở, một bài đã bị ẩn từ trước
        long adminId = insertUser(0, 0);
        long lockedUserId = insertUser(0, 0);
        long receiverId = insertUser(0, 0);
        long visibleItemId = insertItem(lockedUserId, "GIVE", "APPROVED", "NONE", 0);
        long reservedItemId = insertItem(lockedUserId, "GIVE", "RESERVED", "NONE", 0);
        insertRequest(reservedItemId, receiverId, "RESERVED");
        long alreadyHiddenItemId = insertItem(lockedUserId, "GIVE", "APPROVED", "NONE", 0);
        jdbc.update("UPDATE items SET is_active = false WHERE id = ?", alreadyHiddenItemId);

        // when: khoá
        adminUserService.lock(adminId, lockedUserId, "Vi phạm");

        // then: bài nhả về APPROVED và mọi bài đều bị ẩn, status không đổi
        assertThat(itemStatusOf(reservedItemId)).isEqualTo("APPROVED");
        assertThat(itemActiveOf(visibleItemId)).isFalse();
        assertThat(itemActiveOf(reservedItemId)).isFalse();
        assertThat(itemStatusOf(visibleItemId)).isEqualTo("APPROVED");

        // when: mở khoá
        adminUserService.unlock(adminId, lockedUserId, "Đã giải trình");

        // then: chỉ hiện lại bài bị ẩn do khoá, bài Admin ẩn từ trước vẫn ẩn
        assertThat(itemActiveOf(visibleItemId)).isTrue();
        assertThat(itemActiveOf(reservedItemId)).isTrue();
        assertThat(itemActiveOf(alreadyHiddenItemId)).isFalse();
        assertThat(isActive(lockedUserId)).isTrue();
    }

    // ---------- Tiện ích hỗ trợ test ----------

    private long insertUser(int creditBalance, int heldCredit) {
        int sequence = SEQUENCE.incrementAndGet();
        Long userId = jdbc.queryForObject(
                "INSERT INTO users (email, password_hash, full_name, phone, status, credit_balance, held_credit)"
                        + " VALUES (?, 'not-a-real-hash', 'Lock IT', ?, 'ACTIVE', ?, ?) RETURNING id",
                Long.class,
                "lock-cascade-it-" + sequence + "-" + System.nanoTime() + "@test.local",
                String.format("0996%06d", sequence),
                creditBalance,
                heldCredit);
        userIds.add(userId);
        return userId;
    }

    // desired_item bắt buộc với SWAP (ck_items_swap_desired), để null với GIVE.
    private long insertItem(long donorId, String offerType, String status, String feeState, int pendingFee) {
        String desiredItem = "SWAP".equals(offerType) ? "Món muốn đổi" : null;
        return jdbc.queryForObject(
                "INSERT INTO items (donor_id, category_id, area_id, offer_type, title, description, desired_item,"
                        + " condition, status, fee_state, pending_fee, pending_fee_type)"
                        + " VALUES (?, (SELECT id FROM item_categories ORDER BY id LIMIT 1),"
                        + " (SELECT id FROM areas WHERE level = 2 ORDER BY id LIMIT 1),"
                        + " ?, 'Lock IT item', 'Mô tả', ?, 'NEW', ?, ?, ?, ?) RETURNING id",
                Long.class,
                donorId,
                offerType,
                desiredItem,
                status,
                feeState,
                pendingFee,
                pendingFee > 0 ? "POST" : null);
    }

    private long insertRequest(long itemId, long receiverId, String status) {
        return jdbc.queryForObject(
                "INSERT INTO requests (item_id, receiver_id, type, status, reserved_at)"
                        + " VALUES (?, ?, 'GIVE', ?, now()) RETURNING id",
                Long.class,
                itemId,
                receiverId,
                status);
    }

    private String requestStatusOf(long requestId) {
        return jdbc.queryForObject("SELECT status FROM requests WHERE id = ?", String.class, requestId);
    }

    private String cancelReasonOf(long requestId) {
        return jdbc.queryForObject("SELECT cancel_reason FROM requests WHERE id = ?", String.class, requestId);
    }

    private String itemStatusOf(long itemId) {
        return jdbc.queryForObject("SELECT status FROM items WHERE id = ?", String.class, itemId);
    }

    private boolean itemActiveOf(long itemId) {
        return jdbc.queryForObject("SELECT is_active FROM items WHERE id = ?", Boolean.class, itemId);
    }

    private int heldCreditOf(long userId) {
        return jdbc.queryForObject("SELECT held_credit FROM users WHERE id = ?", Integer.class, userId);
    }

    private int creditBalanceOf(long userId) {
        return jdbc.queryForObject("SELECT credit_balance FROM users WHERE id = ?", Integer.class, userId);
    }

    private boolean isActive(long userId) {
        return jdbc.queryForObject("SELECT is_active FROM users WHERE id = ?", Boolean.class, userId);
    }

    private String lockLogData(long userId) {
        return jdbc.queryForObject(
                "SELECT data::text FROM activity_logs WHERE type = 'USER_LOCK' AND target_id = ?",
                String.class,
                userId);
    }
}
