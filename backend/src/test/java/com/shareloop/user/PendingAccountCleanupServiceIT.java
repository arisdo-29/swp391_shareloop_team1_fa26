package com.shareloop.user;

import static org.assertj.core.api.Assertions.assertThat;

import com.shareloop.support.IntegrationTest;
import com.shareloop.user.service.PendingAccountCleanupService;
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

/**
 * Test job dọn tài khoản chưa xác thực (FR-117, BR-U06) trên PostgreSQL thật, với pending_account_ttl_hours = 24 seed
 * sẵn. Gọi thẳng service với mốc {@code now} tự chọn (profile test tắt app.jobs.enabled). DB dùng chung với test khác
 * nên chỉ kiểm các tài khoản do test này tạo.
 */
class PendingAccountCleanupServiceIT extends IntegrationTest {

    private static final AtomicInteger PHONE_SEQUENCE = new AtomicInteger();

    // Trigger set_updated_at đổi updated_at ở MỌI câu UPDATE, nên cột còn giữ mốc này nghĩa là job không ghi vào dòng.
    private static final Instant OLD_UPDATED_AT = Instant.parse("2026-01-01T00:00:00Z");

    @Autowired
    PendingAccountCleanupService pendingAccountCleanupService;

    @Autowired
    JdbcTemplate jdbc;

    private final List<Long> createdUserIds = new ArrayList<>();

    // Dọn bằng câu DELETE thật: chỉ để xoá dữ liệu test, kể cả dòng đã bị job xoá mềm.
    @AfterEach
    void cleanUp() {
        for (Long userId : createdUserIds) {
            jdbc.update("DELETE FROM users WHERE id = ?", userId);
        }
        createdUserIds.clear();
    }

    @Test
    void deleteExpiredPendingAccounts_whenPendingOverTtlAndOtpExpired_softDeletes() {
        // given: tạo 25 giờ trước, OTP đã hết hạn từ lâu
        Instant now = Instant.now();
        long userId =
                insertUser("PENDING_VERIFICATION", now.minus(25, ChronoUnit.HOURS), now.minus(1, ChronoUnit.DAYS));

        // when
        int deletedCount = pendingAccountCleanupService.deleteExpiredPendingAccounts(now);

        // then: xoá mềm, dòng vẫn còn trong bảng
        assertThat(isDeleted(userId)).isTrue();
        assertThat(deletedCount).isGreaterThanOrEqualTo(1);
    }

    @Test
    void deleteExpiredPendingAccounts_whenPendingWithinTtl_keepsAccount() {
        // given: mới tạo 23 giờ trước
        Instant now = Instant.now();
        long userId = insertUser("PENDING_VERIFICATION", now.minus(23, ChronoUnit.HOURS), null);

        // when
        pendingAccountCleanupService.deleteExpiredPendingAccounts(now);

        // then
        assertThat(isDeleted(userId)).isFalse();
        assertThat(updatedAtOf(userId)).isEqualTo(OLD_UPDATED_AT);
    }

    @Test
    void deleteExpiredPendingAccounts_whenActiveAccountOverTtl_isNotTouched() {
        // given: tài khoản đã xác thực, tạo từ lâu
        Instant now = Instant.now();
        long userId = insertUser("ACTIVE", now.minus(30, ChronoUnit.DAYS), null);

        // when
        pendingAccountCleanupService.deleteExpiredPendingAccounts(now);

        // then
        assertThat(isDeleted(userId)).isFalse();
        assertThat(updatedAtOf(userId)).isEqualTo(OLD_UPDATED_AT);
    }

    @Test
    void deleteExpiredPendingAccounts_whenOtpStillValid_keepsAccount() {
        // given: quá 24 giờ nhưng người dùng vừa gửi lại OTP, mã còn hạn 3 phút
        Instant now = Instant.now();
        long userId =
                insertUser("PENDING_VERIFICATION", now.minus(25, ChronoUnit.HOURS), now.plus(3, ChronoUnit.MINUTES));

        // when
        pendingAccountCleanupService.deleteExpiredPendingAccounts(now);

        // then: lượt này để lại; khi OTP hết hạn thì rơi vào trường hợp của test xoá mềm ở trên
        assertThat(isDeleted(userId)).isFalse();
        assertThat(updatedAtOf(userId)).isEqualTo(OLD_UPDATED_AT);
    }

    @Test
    void deleteExpiredPendingAccounts_whenAlreadySoftDeleted_leavesRowUntouched() {
        // given
        Instant now = Instant.now();
        long userId = insertUser("PENDING_VERIFICATION", now.minus(25, ChronoUnit.HOURS), null);
        jdbc.update(
                "UPDATE users SET is_deleted = true, updated_at = ? WHERE id = ?",
                Timestamp.from(OLD_UPDATED_AT),
                userId);
        Instant updatedAtBefore = updatedAtOf(userId);

        // when
        pendingAccountCleanupService.deleteExpiredPendingAccounts(now);

        // then
        assertThat(isDeleted(userId)).isTrue();
        assertThat(updatedAtOf(userId)).isEqualTo(updatedAtBefore);
    }

    @Test
    void deleteExpiredPendingAccounts_whenRunTwice_secondRunChangesNothing() {
        // given: lượt đầu đã xoá mềm tài khoản
        Instant now = Instant.now();
        long userId = insertUser("PENDING_VERIFICATION", now.minus(25, ChronoUnit.HOURS), null);
        pendingAccountCleanupService.deleteExpiredPendingAccounts(now);
        Instant updatedAtAfterFirstRun = updatedAtOf(userId);

        // when
        pendingAccountCleanupService.deleteExpiredPendingAccounts(now);

        // then
        assertThat(isDeleted(userId)).isTrue();
        assertThat(updatedAtOf(userId)).isEqualTo(updatedAtAfterFirstRun);
    }

    // ---------- tiện ích ----------

    // otpExpiresAt null: chưa có OTP hoặc đã bị xoá (nhập sai quá số lần).
    private long insertUser(String status, Instant createdAt, Instant otpExpiresAt) {
        // Email và phone có unique index nên mỗi user một giá trị riêng; đầu số 0993 khác các test khác.
        int sequence = PHONE_SEQUENCE.incrementAndGet();
        String email = "cleanup-it-" + sequence + "-" + System.nanoTime() + "@test.local";
        String phone = String.format("0993%06d", sequence);
        Timestamp otpExpiresAtValue = otpExpiresAt == null ? null : Timestamp.from(otpExpiresAt);
        Long userId = jdbc.queryForObject(
                "INSERT INTO users (email, password_hash, full_name, phone, status, credit_balance, held_credit,"
                        + " otp_expires_at, created_at, updated_at)"
                        + " VALUES (?, 'not-a-real-hash', 'Cleanup IT', ?, ?, 0, 0, ?, ?, ?) RETURNING id",
                Long.class,
                email,
                phone,
                status,
                otpExpiresAtValue,
                Timestamp.from(createdAt),
                Timestamp.from(OLD_UPDATED_AT));
        createdUserIds.add(userId);
        return userId;
    }

    private boolean isDeleted(long userId) {
        return jdbc.queryForObject("SELECT is_deleted FROM users WHERE id = ?", Boolean.class, userId);
    }

    // Đọc qua Timestamp vì driver PostgreSQL không đổi thẳng TIMESTAMPTZ sang Instant.
    private Instant updatedAtOf(long userId) {
        return jdbc.queryForObject("SELECT updated_at FROM users WHERE id = ?", Timestamp.class, userId)
                .toInstant();
    }
}
