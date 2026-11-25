package com.shareloop.report;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.shareloop.auth.service.JwtService;
import com.shareloop.support.IntegrationTest;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

/** F2: chỉ báo cáo của tài khoản đủ tin cậy mới tính vào ngưỡng tự ẩn bài. Chạy thật trên PostgreSQL. */
@AutoConfigureMockMvc
class ReportTrustedReporterIT extends IntegrationTest {
    private static final AtomicInteger SEQ = new AtomicInteger();

    @Autowired
    MockMvc mockMvc;

    @Autowired
    JwtService jwtService;

    @Autowired
    JdbcTemplate jdbc;

    private final List<Long> itemIds = new ArrayList<>();
    private final List<Long> userIds = new ArrayList<>();

    @AfterEach
    void cleanUp() {
        for (Long itemId : itemIds) {
            jdbc.update("DELETE FROM reports WHERE item_id = ?", itemId);
            jdbc.update("DELETE FROM requests WHERE item_id = ?", itemId);
            jdbc.update("DELETE FROM items WHERE id = ?", itemId);
        }
        for (Long userId : userIds) {
            jdbc.update("DELETE FROM notifications WHERE user_id = ?", userId);
            jdbc.update("DELETE FROM activity_logs WHERE actor_id = ? OR target_id = ?", userId, userId);
            jdbc.update("DELETE FROM users WHERE id = ?", userId);
        }
        itemIds.clear();
        userIds.clear();
    }

    @Test
    void create_whenOnlyNewAccountsReport_itemIsNotAutoHiddenUntilTrustedReportersJoin() throws Exception {
        // given: bài đang hiển thị
        long donorId = insertUser(30);
        long itemId = insertItem(donorId);

        // when: ba tài khoản mới (1 ngày tuổi, chưa có giao dịch hoàn tất) báo cáo
        for (int i = 0; i < 3; i++) {
            report(insertUser(1), itemId);
        }

        // then: báo cáo vẫn lưu vào hàng chờ Admin nhưng bài chưa tự ẩn
        assertThat(pendingReportCount(itemId)).isEqualTo(3);
        assertThat(itemActive(itemId)).isTrue();

        // when: thêm ba tài khoản đủ tin cậy (30 ngày tuổi) báo cáo
        for (int i = 0; i < 3; i++) {
            report(insertUser(30), itemId);
        }

        // then: đủ ngưỡng theo báo cáo của người đáng tin nên bài tự ẩn
        assertThat(itemActive(itemId)).isFalse();
    }

    @Test
    void create_whenNewAccountReporterHasCompletedTransaction_reportCountsTowardThreshold() throws Exception {
        // given: ba tài khoản mới nhưng mỗi người đã có một giao dịch hoàn tất
        long donorId = insertUser(30);
        long itemId = insertItem(donorId);
        for (int i = 0; i < 3; i++) {
            long reporterId = insertUser(1);
            insertCompletedTransaction(reporterId);

            // when
            report(reporterId, itemId);
        }

        // then
        assertThat(itemActive(itemId)).isFalse();
    }

    private void report(long reporterId, long itemId) throws Exception {
        mockMvc.perform(post("/api/v1/reports")
                        .header("Authorization", "Bearer " + jwtService.issue(reporterId, false))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"targetType\":\"ITEM\",\"itemId\":" + itemId
                                + ",\"reason\":\"OTHER\",\"description\":\"Mô tả có vấn đề\"}"))
                .andExpect(status().isCreated());
    }

    private int pendingReportCount(long itemId) {
        return jdbc.queryForObject(
                "SELECT count(*) FROM reports WHERE item_id = ? AND status = 'OPEN'", Integer.class, itemId);
    }

    private boolean itemActive(long itemId) {
        return jdbc.queryForObject("SELECT is_active FROM items WHERE id = ?", Boolean.class, itemId);
    }

    private long insertUser(int ageDays) {
        int seq = SEQ.incrementAndGet();
        Long userId = jdbc.queryForObject(
                "INSERT INTO users (email, password_hash, full_name, phone, status, credit_balance, held_credit,"
                        + " created_at) VALUES (?, 'hash', 'Trusted IT', ?, 'ACTIVE', 20, 0,"
                        + " now() - (? || ' days')::interval) RETURNING id",
                Long.class,
                "report-trust-" + seq + "-" + System.nanoTime() + "@test.local",
                String.format("0995%06d", seq),
                String.valueOf(ageDays));
        userIds.add(userId);
        return userId;
    }

    private long insertItem(long donorId) {
        long categoryId = jdbc.queryForObject("SELECT id FROM item_categories ORDER BY id LIMIT 1", Long.class);
        long areaId = jdbc.queryForObject("SELECT id FROM areas WHERE level = 2 ORDER BY id LIMIT 1", Long.class);
        Long itemId = jdbc.queryForObject(
                "INSERT INTO items (donor_id, category_id, area_id, offer_type, title, description, condition, status,"
                        + " fee_state, pending_fee, pending_fee_type)"
                        + " VALUES (?, ?, ?, 'GIVE', 'Bài bị báo cáo', 'Mô tả', 'GOOD', 'APPROVED', 'CHARGED', 0, NULL)"
                        + " RETURNING id",
                Long.class,
                donorId,
                categoryId,
                areaId);
        itemIds.add(itemId);
        return itemId;
    }

    // Giao dịch đã hoàn tất của người nhận trên một bài khác, chỉ để người báo có "giao dịch hoàn tất".
    private void insertCompletedTransaction(long receiverId) {
        long otherDonor = insertUser(30);
        long otherItem = insertItem(otherDonor);
        jdbc.update(
                "INSERT INTO requests (item_id, receiver_id, type, status, reserved_at, completed_at)"
                        + " VALUES (?, ?, 'GIVE', 'COMPLETED', now(), now())",
                otherItem,
                receiverId);
    }
}
