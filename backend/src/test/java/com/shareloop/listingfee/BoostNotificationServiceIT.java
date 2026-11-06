package com.shareloop.listingfee;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import com.shareloop.listingfee.service.BoostNotificationService;
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
 * Test BoostNotificationService (luật của BoostNotificationJob) cùng NotificationService thật trên PostgreSQL. Bean job
 * không được tạo ở profile test nên gọi thẳng service. Mốc "now" luôn là giờ thật: khoá chống trùng so created_at của
 * thông báo (giờ thật) với các mốc của đợt đẩy. Chỉ kiểm thông báo của user do test tạo, vì DB dùng chung.
 */
class BoostNotificationServiceIT extends IntegrationTest {

    private static final AtomicInteger PHONE_SEQUENCE = new AtomicInteger();
    private static final String ITEM_TITLE = "Áo khoác IT";
    private static final DateTimeFormatter TIME_FORMAT =
            DateTimeFormatter.ofPattern("HH:mm dd/MM").withZone(ZoneId.of("Asia/Ho_Chi_Minh"));

    @Autowired
    BoostNotificationService boostNotificationService;

    @Autowired
    ConfigService configService;

    @Autowired
    JdbcTemplate jdbc;

    private final List<Long> createdUserIds = new ArrayList<>();
    private final List<Long> createdCategoryIds = new ArrayList<>();

    // Thứ tự xoá: notifications và items tham chiếu users, items tham chiếu item_categories nên xoá trước.
    @AfterEach
    void cleanUp() {
        for (Long userId : createdUserIds) {
            jdbc.update("DELETE FROM notifications WHERE user_id = ?", userId);
            jdbc.update("DELETE FROM items WHERE donor_id = ?", userId);
            jdbc.update("DELETE FROM users WHERE id = ?", userId);
        }
        for (Long categoryId : createdCategoryIds) {
            jdbc.update("DELETE FROM item_categories WHERE id = ?", categoryId);
        }
        createdUserIds.clear();
        createdCategoryIds.clear();
    }

    // ---------- sắp hết đẩy ----------

    @Test
    void notifyEndingSoon_whenOneMorePackageFitsInChain_mentionsLatestExtendTime() {
        // given: đợt bắt đầu hôm nay, bài hết đẩy sau 10 giờ; mua thêm một gói vẫn nằm trong đợt
        Instant now = Instant.now();
        Instant chainStartedAt = now.truncatedTo(ChronoUnit.SECONDS);
        Instant boostedUntil = now.plus(10, ChronoUnit.HOURS).truncatedTo(ChronoUnit.SECONDS);
        long ownerId = insertUser();
        long itemId = insertBoostedItem(ownerId, true, boostedUntil, chainStartedAt);

        // when
        boostNotificationService.notifyEndingSoon(now);

        // then: báo giờ hết đẩy kèm mốc mua thêm muộn nhất của đợt
        Instant chainEndsAt = chainStartedAt.plus(boostMaxDays(), ChronoUnit.DAYS);
        assertThat(bodiesOf(ownerId, BoostNotificationService.BOOST_ENDING_SOON))
                .containsExactly("Bài «" + ITEM_TITLE + "» hết đẩy lúc " + format(boostedUntil)
                        + ". Bạn còn mua thêm được tới " + format(chainEndsAt) + ".");
        assertThat(linksOf(ownerId)).containsExactly("/items/" + itemId);
    }

    @Test
    void notifyEndingSoon_whenChainIsFull_omitsExtendSentence() {
        // given: bài hết đẩy đúng lúc đợt kết thúc (boost_started_at + boost_max_days), không mua thêm được nữa
        Instant now = Instant.now();
        Instant boostedUntil = now.plus(10, ChronoUnit.HOURS).truncatedTo(ChronoUnit.SECONDS);
        Instant chainStartedAt = boostedUntil.minus(boostMaxDays(), ChronoUnit.DAYS);
        long ownerId = insertUser();
        insertBoostedItem(ownerId, true, boostedUntil, chainStartedAt);

        // when
        boostNotificationService.notifyEndingSoon(now);

        // then
        assertThat(bodiesOf(ownerId, BoostNotificationService.BOOST_ENDING_SOON))
                .containsExactly("Bài «" + ITEM_TITLE + "» hết đẩy lúc " + format(boostedUntil) + ".");
    }

    @Test
    void notifyEndingSoon_whenBoostedUntilMovedToNewEnd_sendsAgainForNewEnd() {
        // given: đã báo sắp hết đẩy cho mốc cũ (lùi created_at về 3 ngày trước, như thông báo của lần mua trước)
        Instant now = Instant.now();
        long ownerId = insertUser();
        long itemId = insertBoostedItem(
                ownerId,
                true,
                now.plus(10, ChronoUnit.HOURS).truncatedTo(ChronoUnit.SECONDS),
                now.truncatedTo(ChronoUnit.SECONDS));
        boostNotificationService.notifyEndingSoon(now);
        jdbc.update(
                "UPDATE notifications SET created_at = ? WHERE user_id = ?",
                Timestamp.from(now.minus(3, ChronoUnit.DAYS)),
                ownerId);
        Instant newBoostedUntil = now.plus(12, ChronoUnit.HOURS).truncatedTo(ChronoUnit.SECONDS);
        jdbc.update("UPDATE items SET boosted_until = ? WHERE id = ?", Timestamp.from(newBoostedUntil), itemId);

        // when: mốc hết đẩy đã đổi
        boostNotificationService.notifyEndingSoon(now);

        // then: mốc mới được báo thêm một lần
        assertThat(bodiesOf(ownerId, BoostNotificationService.BOOST_ENDING_SOON))
                .hasSize(2)
                .last()
                .asString()
                .contains("hết đẩy lúc " + format(newBoostedUntil));
    }

    // ---------- đã hết đẩy ----------

    @Test
    void notifyEnded_whenBoostEndedRecently_tellsWhenBoostAgainAllowed() {
        // given: đợt đẩy vừa hết 2 giờ trước
        Instant now = Instant.now();
        Instant boostedUntil = now.minus(2, ChronoUnit.HOURS).truncatedTo(ChronoUnit.SECONDS);
        long ownerId = insertUser();
        insertBoostedItem(ownerId, true, boostedUntil, boostedUntil.minus(boostDays(), ChronoUnit.DAYS));

        // when
        boostNotificationService.notifyEnded(now);

        // then: mốc được đẩy lại = boosted_until + boost_cooldown_days
        Instant boostAgainAt = boostedUntil.plus(boostCooldownDays(), ChronoUnit.DAYS);
        assertThat(bodiesOf(ownerId, BoostNotificationService.BOOST_ENDED))
                .containsExactly("Bài «" + ITEM_TITLE + "» đã hết thời gian đẩy. Có thể đẩy lại từ "
                        + format(boostAgainAt) + ".");
    }

    // ---------- bài bị ẩn ----------

    @Test
    void stopHiddenBoosts_whenBoostingItemHidden_cutsBoostedUntilToNowAndNotifiesOwner() {
        // given: bài đang đẩy thêm 2 ngày nữa nhưng đã bị ẩn
        Instant now = Instant.now();
        long ownerId = insertUser();
        long itemId = insertBoostedItem(
                ownerId,
                false,
                now.plus(2, ChronoUnit.DAYS),
                now.minus(1, ChronoUnit.DAYS).truncatedTo(ChronoUnit.SECONDS));

        // when
        boostNotificationService.stopHiddenBoosts(now);

        // then: mất thời gian đẩy còn lại (boosted_until = now) và chủ bài được báo
        assertThat(boostedUntilOf(itemId)).isCloseTo(now, within(1, ChronoUnit.MILLIS));
        assertThat(bodiesOf(ownerId, BoostNotificationService.BOOST_STOPPED))
                .containsExactly(
                        "Bài «" + ITEM_TITLE + "» bị ẩn nên đã dừng đẩy; thời gian đẩy còn lại không được hoàn phí.");
    }

    // ---------- chạy lại không gửi trùng ----------

    @Test
    void runningJobRulesTwice_sendsEachNoticeOnlyOnce() {
        // given: một bài sắp hết đẩy, một bài vừa hết đẩy, một bài đang đẩy nhưng bị ẩn
        Instant now = Instant.now();
        Instant today = now.truncatedTo(ChronoUnit.SECONDS);
        long ownerId = insertUser();
        insertBoostedItem(ownerId, true, today.plus(10, ChronoUnit.HOURS), today);
        insertBoostedItem(ownerId, true, today.minus(2, ChronoUnit.HOURS), today.minus(3, ChronoUnit.DAYS));
        insertBoostedItem(ownerId, false, today.plus(2, ChronoUnit.DAYS), today.minus(1, ChronoUnit.DAYS));

        // when: job chạy hai lượt với cùng dữ liệu
        runAllRules(now);
        runAllRules(now);

        // then: mỗi loại chỉ có một thông báo
        assertThat(bodiesOf(ownerId, BoostNotificationService.BOOST_ENDING_SOON))
                .hasSize(1);
        assertThat(bodiesOf(ownerId, BoostNotificationService.BOOST_ENDED)).hasSize(1);
        assertThat(bodiesOf(ownerId, BoostNotificationService.BOOST_STOPPED)).hasSize(1);
    }

    // ---------- hàm hỗ trợ ----------

    // Cùng thứ tự với BoostNotificationJob.run.
    private void runAllRules(Instant now) {
        boostNotificationService.stopHiddenBoosts(now);
        boostNotificationService.notifyEndingSoon(now);
        boostNotificationService.notifyEnded(now);
    }

    private int boostDays() {
        return configService.getInt(ConfigKey.BOOST_DAYS);
    }

    private int boostMaxDays() {
        return configService.getInt(ConfigKey.BOOST_MAX_DAYS);
    }

    private int boostCooldownDays() {
        return configService.getInt(ConfigKey.BOOST_COOLDOWN_DAYS);
    }

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
        // Email và phone có unique index nên mỗi user một giá trị riêng; đầu số 0979 khác các test khác.
        int sequence = PHONE_SEQUENCE.incrementAndGet();
        Long userId = jdbc.queryForObject(
                "INSERT INTO users (email, password_hash, full_name, phone, status, credit_balance, held_credit)"
                        + " VALUES (?, 'not-a-real-hash', 'Boost Notice IT', ?, 'ACTIVE', 0, 0) RETURNING id",
                Long.class,
                "boost-notice-it-" + sequence + "-" + System.nanoTime() + "@test.local",
                String.format("0979%06d", sequence));
        createdUserIds.add(userId);
        return userId;
    }

    // Bài APPROVED còn hạn hiển thị 30 ngày; boost_started_at và last_boosted_at cùng là mốc bắt đầu đợt.
    private long insertBoostedItem(long donorId, boolean active, Instant boostedUntil, Instant boostStartedAt) {
        return jdbc.queryForObject(
                "INSERT INTO items (donor_id, category_id, area_id, offer_type, title, description, condition,"
                        + " status, is_active, post_fee_paid, fee_state, pending_fee, pending_fee_type, expire_at,"
                        + " boosted_until, boost_started_at, last_boosted_at) VALUES (?, ?, ?, 'GIVE', ?, 'Mô tả',"
                        + " 'NEW', 'APPROVED', ?, true, 'CHARGED', 5, 'POST', ?, ?, ?, ?) RETURNING id",
                Long.class,
                donorId,
                insertCategory(),
                seedAreaId(),
                ITEM_TITLE,
                active,
                Timestamp.from(Instant.now().plus(30, ChronoUnit.DAYS)),
                Timestamp.from(boostedUntil),
                Timestamp.from(boostStartedAt),
                Timestamp.from(boostStartedAt));
    }

    private long insertCategory() {
        Long categoryId = jdbc.queryForObject(
                "INSERT INTO item_categories (name, is_boostable) VALUES (?, true) RETURNING id",
                Long.class,
                "Boost Notice IT " + System.nanoTime());
        createdCategoryIds.add(categoryId);
        return categoryId;
    }

    // Mượn khu vực có sẵn từ seed (không tạo thêm, không xoá) để thoả khoá ngoại của items.
    private long seedAreaId() {
        return jdbc.queryForObject("SELECT id FROM areas WHERE level = 2 ORDER BY id LIMIT 1", Long.class);
    }

    // Đọc qua Timestamp vì driver PostgreSQL không đổi thẳng TIMESTAMPTZ sang Instant.
    private Instant boostedUntilOf(long itemId) {
        return jdbc.queryForObject("SELECT boosted_until FROM items WHERE id = ?", Timestamp.class, itemId)
                .toInstant();
    }
}
