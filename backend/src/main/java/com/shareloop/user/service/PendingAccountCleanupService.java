package com.shareloop.user.service;

import com.shareloop.setting.ConfigKey;
import com.shareloop.setting.service.ConfigService;
import com.shareloop.user.entity.User;
import com.shareloop.user.entity.UserStatus;
import com.shareloop.user.repository.UserRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Dọn tài khoản chưa xác thực (FR-117, BR-U06; ERD mục 6): tài khoản PENDING_VERIFICATION tạo quá
 * pending_account_ttl_hours giờ thì xoá mềm ({@code markDeleted()}, không xoá cứng). Index ux_users_email chỉ tính
 * dòng chưa xoá nên email đó đăng ký lại được ngay.
 *
 * <p>Người gọi: {@code PendingAccountCleanupJob} (mỗi giờ) và test.
 */
@Service
public class PendingAccountCleanupService {

    private static final Logger log = LoggerFactory.getLogger(PendingAccountCleanupService.class);

    // ERD mục 6: xử lý theo lô nhỏ. Tham số kỹ thuật, không phải con số nghiệp vụ, nên không đặt trong ConfigService.
    private static final int BATCH_SIZE = 100;

    private final UserRepository userRepository;
    private final ConfigService configService;
    private final TransactionTemplate transactionTemplate;

    public PendingAccountCleanupService(
            UserRepository userRepository, ConfigService configService, TransactionTemplate transactionTemplate) {
        this.userRepository = userRepository;
        this.configService = configService;
        this.transactionTemplate = transactionTemplate;
    }

    /**
     * Xoá mềm mọi tài khoản PENDING_VERIFICATION có created_at trước {@code now} − pending_account_ttl_hours (FR-117,
     * BR-U06). Tài khoản đang có OTP còn hạn được để lại tới lượt sau (người dùng có thể vừa gửi lại mã); tài khoản đã
     * xác thực không bao giờ bị đụng. Chạy lại nhiều lần không có tác dụng kép.
     *
     * <p>Transaction: hàm này không mở transaction; mỗi tài khoản một transaction riêng, một dòng lỗi (ghi log WARN)
     * không chặn các dòng còn lại. Không ném lỗi nghiệp vụ.
     *
     * @param now mốc tính hạn (job truyền giờ hiện tại, test truyền mốc tự chọn)
     * @return số tài khoản đã xoá mềm trong lượt này
     */
    public int deleteExpiredPendingAccounts(Instant now) {
        int ttlHours = configService.getInt(ConfigKey.PENDING_ACCOUNT_TTL_HOURS);
        Instant createdBefore = now.minus(ttlHours, ChronoUnit.HOURS);
        int deletedCount = 0;
        long lastSeenId = 0;
        List<Long> batch = findCandidates(createdBefore, now, lastSeenId);
        while (!batch.isEmpty()) {
            for (Long userId : batch) {
                if (deleteInOwnTransaction(userId, createdBefore, now)) {
                    deletedCount++;
                }
            }
            lastSeenId = batch.get(batch.size() - 1);
            batch = findCandidates(createdBefore, now, lastSeenId);
        }
        return deletedCount;
    }

    private List<Long> findCandidates(Instant createdBefore, Instant now, long afterId) {
        return userRepository.findCleanupCandidateIds(
                UserStatus.PENDING_VERIFICATION, createdBefore, now, afterId, Limit.of(BATCH_SIZE));
    }

    // Cùng khuôn với ItemExpiryService: TransactionTemplate mở một transaction cho đúng một tài khoản.
    private boolean deleteInOwnTransaction(long userId, Instant createdBefore, Instant now) {
        try {
            Boolean deleted = transactionTemplate.execute(status -> deleteIfStillExpired(userId, createdBefore, now));
            return Boolean.TRUE.equals(deleted);
        } catch (RuntimeException exception) {
            // Bắt mọi lỗi để một dòng hỏng không dừng cả lượt; dòng này chưa bị xoá nên lượt sau sẽ thử lại.
            log.warn("Pending account cleanup failed for user {}, will retry next run", userId, exception);
            return false;
        }
    }

    // Kiểm lại trong transaction: giữa lúc đọc danh sách id và lúc này, người dùng có thể vừa xác thực hoặc gửi lại
    // OTP.
    private boolean deleteIfStillExpired(long userId, Instant createdBefore, Instant now) {
        Optional<User> user = userRepository.findById(userId);
        if (user.isEmpty() || !isExpiredPendingAccount(user.get(), createdBefore, now)) {
            return false;
        }
        // Không cần save(): user đang được JPA theo dõi, is_deleted tự ghi xuống DB khi transaction commit.
        user.get().markDeleted();
        return true;
    }

    private boolean isExpiredPendingAccount(User user, Instant createdBefore, Instant now) {
        boolean stillPending = user.getStatus() == UserStatus.PENDING_VERIFICATION;
        boolean createdTooLongAgo = user.getCreatedAt().isBefore(createdBefore);
        boolean otpStillValid =
                user.getOtpExpiresAt() != null && user.getOtpExpiresAt().isAfter(now);
        return stillPending && createdTooLongAgo && !otpStillValid;
    }
}
