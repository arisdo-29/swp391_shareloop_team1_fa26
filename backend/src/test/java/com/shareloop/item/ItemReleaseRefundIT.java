package com.shareloop.item;

import static org.assertj.core.api.Assertions.assertThat;

import com.shareloop.item.service.ItemService;
import com.shareloop.listingfee.service.ItemExpiryService;
import com.shareloop.support.IntegrationTest;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/** B2: bài được nhả sau giao dịch huỷ thì được cộng lại thời gian đã nằm trong giao dịch. */
class ItemReleaseRefundIT extends IntegrationTest {

    private static final AtomicInteger SEQUENCE = new AtomicInteger();

    @Autowired
    ItemService itemService;

    @Autowired
    ItemExpiryService itemExpiryService;

    @Autowired
    JdbcTemplate jdbc;

    private final List<Long> userIds = new ArrayList<>();

    @AfterEach
    void cleanUp() {
        for (Long userId : userIds) {
            jdbc.update(
                    "DELETE FROM activity_logs WHERE target_type = 'ITEM' AND target_id IN"
                            + " (SELECT id FROM items WHERE donor_id = ?)",
                    userId);
            jdbc.update("DELETE FROM notifications WHERE user_id = ?", userId);
            jdbc.update("DELETE FROM items WHERE donor_id = ?", userId);
            jdbc.update("DELETE FROM users WHERE id = ?", userId);
        }
        userIds.clear();
    }

    @Test
    void releaseAfterCancel_whenHeldLongerThanExpiryGap_itemDoesNotExpireInNextJobRun() {
        // given: hết hạn cách đây 3 ngày (tương đương 30/11), bài nằm trong giao dịch 8 ngày (25/11 tới 03/12)
        Instant now = Instant.now();
        long itemId = insertReservedItem(now.minus(3, ChronoUnit.DAYS));
        Instant reservedAt = now.minus(8, ChronoUnit.DAYS);

        // when: huỷ giao dịch, nhả bài, rồi job hết hạn chạy
        itemService.releaseAfterCancel(itemId, reservedAt);
        int expired = itemExpiryService.expireOverdueItems(now);

        // then: cộng tối đa 7 ngày nên hạn mới còn sau hiện tại, bài vẫn đang hiển thị
        assertThat(expired).isZero();
        assertThat(statusOf(itemId)).isEqualTo("APPROVED");
        assertThat(expireAtOf(itemId)).isAfter(now);
    }

    @Test
    void releaseAfterCancel_whenStillOverdueAfterRefund_setsExpireToReleaseTimePlusGraceHours() {
        // given: hết hạn cách đây 20 ngày, chỉ nằm trong giao dịch 2 ngày
        Instant now = Instant.now();
        long itemId = insertReservedItem(now.minus(20, ChronoUnit.DAYS));

        // when
        itemService.releaseAfterCancel(itemId, now.minus(2, ChronoUnit.DAYS));

        // then: hạn mới khoảng 24 giờ sau lúc nhả để chủ bài kịp gia hạn
        assertThat(expireAtOf(itemId)).isBetween(now.plus(23, ChronoUnit.HOURS), now.plus(25, ChronoUnit.HOURS));
    }

    @Test
    void releaseAfterCancel_whenItemWasBoosted_extendsBoostByHeldTime() {
        // given: đang đẩy tới 3 ngày nữa, bài nằm trong giao dịch 2 ngày
        Instant now = Instant.now();
        long itemId = insertReservedItem(now.plus(10, ChronoUnit.DAYS));
        jdbc.update(
                "UPDATE items SET boosted_until = ? WHERE id = ?",
                Timestamp.from(now.plus(3, ChronoUnit.DAYS)),
                itemId);

        // when
        itemService.releaseAfterCancel(itemId, now.minus(2, ChronoUnit.DAYS));

        // then
        Timestamp boostedUntil =
                jdbc.queryForObject("SELECT boosted_until FROM items WHERE id = ?", Timestamp.class, itemId);
        assertThat(boostedUntil.toInstant())
                .isBetween(
                        now.plus(5, ChronoUnit.DAYS).minusSeconds(5),
                        now.plus(5, ChronoUnit.DAYS).plusSeconds(5));
    }

    @Test
    void releaseAfterCancel_notifiesOwnerAboutNewExpireTime() {
        // given
        Instant now = Instant.now();
        long itemId = insertReservedItem(now.plus(10, ChronoUnit.DAYS));

        // when
        itemService.releaseAfterCancel(itemId, now.minus(1, ChronoUnit.DAYS));

        // then
        Integer notices = jdbc.queryForObject(
                "SELECT count(*) FROM notifications WHERE type = 'ITEM_DISPLAY_EXTENDED'"
                        + " AND user_id = (SELECT donor_id FROM items WHERE id = ?)",
                Integer.class,
                itemId);
        assertThat(notices).isEqualTo(1);
    }

    private long insertReservedItem(Instant expireAt) {
        int sequence = SEQUENCE.incrementAndGet();
        Long userId = jdbc.queryForObject(
                "INSERT INTO users (email, password_hash, full_name, phone, status)"
                        + " VALUES (?, 'not-a-real-hash', 'Refund IT', ?, 'ACTIVE') RETURNING id",
                Long.class,
                "refund-it-" + sequence + "-" + System.nanoTime() + "@test.local",
                String.format("0997%06d", sequence));
        userIds.add(userId);
        return jdbc.queryForObject(
                "INSERT INTO items (donor_id, category_id, area_id, offer_type, title, description, condition,"
                        + " status, approved_at, expire_at, fee_state)"
                        + " VALUES (?, (SELECT id FROM item_categories ORDER BY id LIMIT 1),"
                        + " (SELECT id FROM areas WHERE level = 2 ORDER BY id LIMIT 1), 'GIVE', 'Bài hoàn thời gian',"
                        + " 'Mô tả', 'NEW', 'RESERVED', now(), ?, 'CHARGED') RETURNING id",
                Long.class,
                userId,
                Timestamp.from(expireAt));
    }

    private String statusOf(long itemId) {
        return jdbc.queryForObject("SELECT status FROM items WHERE id = ?", String.class, itemId);
    }

    private Instant expireAtOf(long itemId) {
        return jdbc.queryForObject("SELECT expire_at FROM items WHERE id = ?", Timestamp.class, itemId)
                .toInstant();
    }
}
