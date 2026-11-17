package com.shareloop.listingfee;

import static org.assertj.core.api.Assertions.assertThat;

import com.shareloop.listingfee.service.ItemExpiryNotificationService;
import com.shareloop.setting.ConfigKey;
import com.shareloop.setting.service.ConfigService;
import com.shareloop.support.IntegrationTest;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Test nhắc gia hạn bài sắp hết hạn (B19) với NotificationService thật trên PostgreSQL. Bean job không được tạo ở
 * profile test nên gọi thẳng service. Chỉ kiểm thông báo của user do test tạo, vì DB dùng chung.
 */
class ItemExpiryNotificationServiceIT extends IntegrationTest {

    private static final AtomicInteger PHONE_SEQUENCE = new AtomicInteger();
    private static final String ITEM_TITLE = "Nồi cơm IT";
    private static final DateTimeFormatter TIME_FORMAT =
            DateTimeFormatter.ofPattern("HH:mm dd/MM").withZone(ZoneId.of("Asia/Ho_Chi_Minh"));

    @Autowired
    ItemExpiryNotificationService itemExpiryNotificationService;

    @Autowired
    ConfigService configService;

    @Autowired
    JdbcTemplate jdbc;

    private final List<Long> createdUserIds = new ArrayList<>();

    // notifications và items tham chiếu users nên xoá trước.
    @AfterEach
    void cleanUp() {
        for (Long userId : createdUserIds) {
            jdbc.update("DELETE FROM notifications WHERE user_id = ?", userId);
            jdbc.update("DELETE FROM items WHERE donor_id = ?", userId);
            jdbc.update("DELETE FROM users WHERE id = ?", userId);
        }
        createdUserIds.clear();
    }

    @Test
    void notifyExpiringSoon_whenWithin48Hours_sendsFirstReminderWithRenewOffer() {
        // given: bài còn 40 giờ là hết hạn
        Instant now = Instant.now();
        Instant expireAt = now.plus(40, ChronoUnit.HOURS).truncatedTo(ChronoUnit.SECONDS);
        long ownerId = insertUser();
        long itemId = insertItem(ownerId, "APPROVED", true, expireAt);

        // when
        int sent = itemExpiryNotificationService.notifyExpiringSoon(now);

        // then: nội dung ghi giờ hết hạn, số ngày và phí gia hạn lấy từ cấu hình; link mở hộp Gia hạn của bài
        assertThat(sent).isEqualTo(1);
        assertThat(bodiesOf(ownerId, ItemExpiryNotificationService.ITEM_EXPIRING_48H))
                .containsExactly("Bài «" + ITEM_TITLE + "» sẽ hết hạn lúc " + format(expireAt) + ". Gia hạn "
                        + configService.getInt(ConfigKey.POST_DISPLAY_DAYS) + " ngày với "
                        + configService.getInt(ConfigKey.RENEW_FEE) + " Credit.");
        assertThat(linksOf(ownerId))
                .containsExactly("/activities?renew=" + itemId + "&until=" + expireAt.getEpochSecond());
        assertThat(bodiesOf(ownerId, ItemExpiryNotificationService.ITEM_EXPIRING_24H))
                .isEmpty();
    }

    @Test
    void notifyExpiringSoon_whenWithin24Hours_sendsLastReminderOnly() {
        // given: bài còn 10 giờ (job lỡ mốc 48 giờ)
        Instant now = Instant.now();
        long ownerId = insertUser();
        insertItem(ownerId, "APPROVED", true, now.plus(10, ChronoUnit.HOURS));

        // when
        itemExpiryNotificationService.notifyExpiringSoon(now);

        // then
        assertThat(bodiesOf(ownerId, ItemExpiryNotificationService.ITEM_EXPIRING_24H))
                .hasSize(1);
        assertThat(bodiesOf(ownerId, ItemExpiryNotificationService.ITEM_EXPIRING_48H))
                .isEmpty();
    }

    @Test
    void notifyExpiringSoon_whenRunAgainAcrossBothMilestones_sendsEachMilestoneOncePerExpireAt() {
        // given: bài còn 40 giờ, job chạy hai lần ở mốc 48 giờ rồi hai lần ở mốc 24 giờ
        Instant now = Instant.now();
        long ownerId = insertUser();
        insertItem(ownerId, "APPROVED", true, now.plus(40, ChronoUnit.HOURS));

        // when
        itemExpiryNotificationService.notifyExpiringSoon(now);
        itemExpiryNotificationService.notifyExpiringSoon(now);
        Instant later = now.plus(20, ChronoUnit.HOURS);
        itemExpiryNotificationService.notifyExpiringSoon(later);
        itemExpiryNotificationService.notifyExpiringSoon(later);

        // then: mỗi mốc đúng một thông báo
        assertThat(bodiesOf(ownerId, ItemExpiryNotificationService.ITEM_EXPIRING_48H))
                .hasSize(1);
        assertThat(bodiesOf(ownerId, ItemExpiryNotificationService.ITEM_EXPIRING_24H))
                .hasSize(1);
    }

    @Test
    void notifyExpiringSoon_afterRenewal_remindsAgainForNewExpireAt() {
        // given: đã nhắc 24 giờ cho hạn cũ, sau đó bài được gia hạn (expire_at đổi) và hạn mới lại sắp tới
        Instant now = Instant.now();
        long ownerId = insertUser();
        long itemId = insertItem(ownerId, "APPROVED", true, now.plus(10, ChronoUnit.HOURS));
        itemExpiryNotificationService.notifyExpiringSoon(now);
        Instant newExpireAt = now.plus(30, ChronoUnit.DAYS).plus(10, ChronoUnit.HOURS);
        jdbc.update("UPDATE items SET expire_at = ? WHERE id = ?", Timestamp.from(newExpireAt), itemId);

        // when: 30 ngày sau, hạn mới còn 10 giờ
        itemExpiryNotificationService.notifyExpiringSoon(now.plus(30, ChronoUnit.DAYS));

        // then
        assertThat(bodiesOf(ownerId, ItemExpiryNotificationService.ITEM_EXPIRING_24H))
                .hasSize(2);
    }

    @Test
    void notifyExpiringSoon_skipsFarAwayHiddenNotApprovedAndAlreadyExpiredItems() {
        // given
        Instant now = Instant.now();
        long ownerId = insertUser();
        insertItem(ownerId, "APPROVED", true, now.plus(72, ChronoUnit.HOURS)); // còn xa
        insertItem(ownerId, "APPROVED", false, now.plus(10, ChronoUnit.HOURS)); // đang bị ẩn
        insertItem(ownerId, "PENDING_REVIEW", true, now.plus(10, ChronoUnit.HOURS)); // chưa duyệt
        insertItem(ownerId, "APPROVED", true, now.minus(1, ChronoUnit.HOURS)); // đã quá hạn

        // when
        int sent = itemExpiryNotificationService.notifyExpiringSoon(now);

        // then
        assertThat(sent).isZero();
        assertThat(linksOf(ownerId)).isEmpty();
    }

    // ---------- hàm hỗ trợ ----------

    private static String format(Instant instant) {
        return TIME_FORMAT.format(instant);
    }

    private List<String> bodiesOf(long userId, String type) {
        return jdbc.queryForList(
                "SELECT body FROM notifications WHERE user_id = ? AND type = ? ORDER BY id",
                String.class,
                userId,
                type);
    }

    private List<String> linksOf(long userId) {
        return jdbc.queryForList("SELECT link FROM notifications WHERE user_id = ? ORDER BY id", String.class, userId);
    }

    private long insertUser() {
        // Email và phone có unique index nên mỗi user một giá trị riêng; đầu số 0973 khác các test khác.
        int sequence = PHONE_SEQUENCE.incrementAndGet();
        Long userId = jdbc.queryForObject(
                "INSERT INTO users (email, password_hash, full_name, phone, status, credit_balance, held_credit)"
                        + " VALUES (?, 'not-a-real-hash', 'Expiry Notice IT', ?, 'ACTIVE', 0, 0) RETURNING id",
                Long.class,
                "expiry-notice-it-" + sequence + "-" + System.nanoTime() + "@test.local",
                String.format("0973%06d", sequence));
        createdUserIds.add(userId);
        return userId;
    }

    private long insertItem(long donorId, String status, boolean active, Instant expireAt) {
        return jdbc.queryForObject(
                "INSERT INTO items (donor_id, category_id, area_id, offer_type, title, description, condition,"
                        + " status, is_active, post_fee_paid, fee_state, pending_fee, pending_fee_type, expire_at)"
                        + " VALUES (?, ?, ?, 'GIVE', ?, 'Mô tả', 'NEW', ?, ?, true, 'CHARGED', 5, 'POST', ?)"
                        + " RETURNING id",
                Long.class,
                donorId,
                seedCategoryId(),
                seedAreaId(),
                ITEM_TITLE,
                status,
                active,
                Timestamp.from(expireAt));
    }

    // Mượn danh mục và khu vực có sẵn từ seed (không tạo thêm, không xoá) để thoả khoá ngoại của items.
    private long seedCategoryId() {
        return jdbc.queryForObject(
                "SELECT id FROM item_categories WHERE is_deleted = false ORDER BY id LIMIT 1", Long.class);
    }

    private long seedAreaId() {
        return jdbc.queryForObject("SELECT id FROM areas WHERE level = 2 ORDER BY id LIMIT 1", Long.class);
    }
}
