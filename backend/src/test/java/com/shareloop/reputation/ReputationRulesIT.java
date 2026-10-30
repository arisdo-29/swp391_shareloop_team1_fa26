package com.shareloop.reputation;

import static org.assertj.core.api.Assertions.assertThat;

import com.shareloop.reputation.service.ReputationService;
import com.shareloop.support.IntegrationTest;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Test luật uy tín của ReputationService với PostgreSQL thật (Issue #150): câu đếm giao dịch giữa cùng cặp, hai cột
 * points_today / points_date và cấp sao ân hạn. Mỗi test tự tạo người dùng, bài và giao dịch riêng, xoá khi xong.
 */
class ReputationRulesIT extends IntegrationTest {

    private static final ZoneId VIETNAM_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final int PHONE_BASE = ThreadLocalRandom.current().nextInt(10_000_000, 90_000_000);
    private static final AtomicInteger PHONE_SEQUENCE = new AtomicInteger();

    @Autowired
    ReputationService reputationService;

    @Autowired
    JdbcTemplate jdbc;

    private final List<Long> userIds = new ArrayList<>();
    private final List<Long> itemIds = new ArrayList<>();
    private final List<Long> requestIds = new ArrayList<>();

    // Thứ tự xoá: activity_logs tham chiếu users; requests tham chiếu items và users.
    @AfterEach
    void cleanUp() {
        for (Long requestId : requestIds) {
            jdbc.update("DELETE FROM activity_logs WHERE target_type = 'REQUEST' AND target_id = ?", requestId);
        }
        for (Long userId : userIds) {
            jdbc.update(
                    "DELETE FROM activity_logs WHERE (target_type = 'USER' AND target_id = ?) OR actor_id = ?",
                    userId,
                    userId);
        }
        for (Long requestId : requestIds) {
            jdbc.update("DELETE FROM requests WHERE id = ?", requestId);
        }
        for (Long itemId : itemIds) {
            jdbc.update("DELETE FROM items WHERE id = ?", itemId);
        }
        for (Long userId : userIds) {
            jdbc.update("DELETE FROM users WHERE id = ?", userId);
        }
        requestIds.clear();
        itemIds.clear();
        userIds.clear();
    }

    @Test
    void onCompleted_pairRule_countsBothDirectionsWithinWindowOnly() {
        long a = insertUser();
        long b = insertUser();
        Instant now = Instant.now();
        // Ngoài cửa sổ 90 ngày: không tính. Chiều ngược lại (B cho, A nhận) trong cửa sổ: có tính.
        insertCompletedRequest(a, b, now.minus(Duration.ofDays(100)));
        insertCompletedRequest(b, a, now.minus(Duration.ofDays(10)));

        // Giao dịch thứ 2 trong cửa sổ: vẫn cộng đủ, A cho +10, B nhận +2
        long second = insertCompletedRequest(a, b, now);
        reputationService.onCompleted(second);
        assertThat(totalPoints(a)).isEqualTo(10);
        assertThat(totalPoints(b)).isEqualTo(2);

        // Giao dịch thứ 3: không cộng nhưng vẫn ghi POINTS 0 điểm và vẫn là giao dịch sạch
        long third = insertCompletedRequest(a, b, now);
        reputationService.onCompleted(third);
        assertThat(totalPoints(a)).isEqualTo(10);
        assertThat(totalPoints(b)).isEqualTo(2);
        assertThat(jdbc.queryForObject(
                        "SELECT (data->>'points') || '/' || (data->>'pairTransactionNo') FROM activity_logs"
                                + " WHERE type = 'POINTS' AND target_type = 'REQUEST' AND target_id = ? AND actor_id = ?",
                        String.class,
                        third,
                        a))
                .isEqualTo("0/3");
        assertThat(jdbc.queryForObject("SELECT clean_streak FROM users WHERE id = ?", Integer.class, a))
                .isEqualTo(2);
    }

    @Test
    void onCompleted_dailyCap_persistsPointsTodayAndGrantsOnlyRemaining() {
        long donor = insertUser();
        LocalDate todayVn = LocalDate.now(VIETNAM_ZONE);

        reputationService.onCompleted(insertCompletedRequest(donor, insertUser(), Instant.now()));
        assertThat(jdbc.queryForObject("SELECT points_today FROM users WHERE id = ?", Integer.class, donor))
                .isEqualTo(10);
        assertThat(jdbc.queryForObject("SELECT points_date FROM users WHERE id = ?", Date.class, donor)
                        .toLocalDate())
                .isEqualTo(todayVn);

        // Giả lập hôm nay đã được 25 điểm: lần hoàn tất kế tiếp (đối tác khác) chỉ còn 5 điểm dưới trần 30
        jdbc.update("UPDATE users SET points_today = 25 WHERE id = ?", donor);
        reputationService.onCompleted(insertCompletedRequest(donor, insertUser(), Instant.now()));

        assertThat(totalPoints(donor)).isEqualTo(15);
        assertThat(jdbc.queryForObject("SELECT points_today FROM users WHERE id = ?", Integer.class, donor))
                .isEqualTo(30);
    }

    private long insertUser() {
        Long id = jdbc.queryForObject(
                "INSERT INTO users (email, password_hash, full_name, phone, status)"
                        + " VALUES (?, 'not-a-real-hash', 'Reputation IT', ?, 'ACTIVE') RETURNING id",
                Long.class,
                "reputation-it-" + UUID.randomUUID() + "@test.local",
                "03" + (PHONE_BASE + PHONE_SEQUENCE.incrementAndGet()));
        userIds.add(id);
        return id;
    }

    private long insertCompletedRequest(long donorId, long receiverId, Instant completedAt) {
        Long categoryId =
                jdbc.queryForObject("SELECT id FROM item_categories WHERE is_active ORDER BY id LIMIT 1", Long.class);
        Long areaId = jdbc.queryForObject("SELECT id FROM areas WHERE level = 2 ORDER BY id LIMIT 1", Long.class);
        Long itemId = jdbc.queryForObject(
                "INSERT INTO items (donor_id, category_id, area_id, offer_type, title, description, condition)"
                        + " VALUES (?, ?, ?, 'GIVE', 'Reputation IT', 'Mô tả', 'GOOD') RETURNING id",
                Long.class,
                donorId,
                categoryId,
                areaId);
        itemIds.add(itemId);
        Long requestId = jdbc.queryForObject(
                "INSERT INTO requests (item_id, receiver_id, type, status, completed_at)"
                        + " VALUES (?, ?, 'GIVE', 'COMPLETED', ?) RETURNING id",
                Long.class,
                itemId,
                receiverId,
                Timestamp.from(completedAt));
        requestIds.add(requestId);
        return requestId;
    }

    private int totalPoints(long userId) {
        return jdbc.queryForObject("SELECT total_points FROM users WHERE id = ?", Integer.class, userId);
    }
}
