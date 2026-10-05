package com.shareloop.auth.service;

import com.shareloop.auth.AuthErrorCode;
import com.shareloop.auth.event.OtpIssuedEvent;
import com.shareloop.common.exception.BusinessException;
import com.shareloop.setting.ConfigKey;
import com.shareloop.setting.service.ConfigService;
import com.shareloop.user.entity.OtpPurpose;
import com.shareloop.user.entity.User;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

/**
 * Cấp và kiểm tra OTP đăng ký.
 *
 * <p>Cố ý KHÔNG gắn @Transactional: các method chạy trong transaction của AuthService. Nếu gắn riêng, khi ném
 * BusinessException transaction chung bị đánh dấu rollback-only và AuthService sẽ gặp UnexpectedRollbackException.
 */
@Service
public class OtpService {

    private static final int CODE_BOUND = 1_000_000; // mã 000000..999999
    private static final long SECONDS_PER_MINUTE = 60;

    private final ConfigService configService;
    private final PasswordEncoder passwordEncoder;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;
    private final SecureRandom secureRandom = new SecureRandom();

    public OtpService(
            ConfigService configService,
            PasswordEncoder passwordEncoder,
            ApplicationEventPublisher eventPublisher,
            Clock clock) {
        this.configService = configService;
        this.passwordEncoder = passwordEncoder;
        this.eventPublisher = eventPublisher;
        this.clock = clock;
    }

    /**
     * Cấp OTP mới cho user (FR-02, BR-U06): lưu bản băm, đặt hạn, phát sự kiện để gửi email sau commit.
     *
     * @return mã thô 6 chữ số
     * @throws BusinessException AUTH_OTP_RESEND_TOO_SOON (kèm details.retryAfterSeconds) nếu lần gửi trước chưa đủ cooldown
     */
    public String issue(User user) {
        Instant now = clock.instant();
        int ttlMinutes = configService.getInt(ConfigKey.OTP_TTL_MINUTES);
        checkResendCooldown(user, now, ttlMinutes);

        String code = String.format("%06d", secureRandom.nextInt(CODE_BOUND));
        Instant expiresAt = now.plus(Duration.ofMinutes(ttlMinutes));
        user.startOtp(passwordEncoder.encode(code), OtpPurpose.REGISTER, expiresAt);

        eventPublisher.publishEvent(new OtpIssuedEvent(user.getEmail(), code, ttlMinutes));
        return code;
    }

    /**
     * Kiểm tra mã người dùng nhập (FR-02). Đúng thì xoá các cột otp_*.
     *
     * @throws BusinessException AUTH_OTP_EXPIRED, AUTH_OTP_INVALID (kèm details.remainingAttempts),
     *     AUTH_OTP_TOO_MANY_ATTEMPTS
     */
    public void verify(User user, String code) {
        if (isMissingOrExpired(user)) {
            throw new BusinessException(AuthErrorCode.AUTH_OTP_EXPIRED);
        }
        if (passwordEncoder.matches(code, user.getOtpCodeHash())) {
            user.clearOtp();
            return;
        }
        handleWrongCode(user);
    }

    private void checkResendCooldown(User user, Instant now, int ttlMinutes) {
        if (user.getOtpExpiresAt() == null) {
            return; // chưa từng gửi mã (hoặc mã đã bị xoá) nên không có cooldown
        }
        // Không có cột "thời điểm gửi", nên suy ra: lần gửi trước = hạn − TTL.
        Instant lastSentAt = user.getOtpExpiresAt().minus(Duration.ofMinutes(ttlMinutes));
        long cooldownSeconds = configService.getInt(ConfigKey.OTP_RESEND_COOLDOWN_SECONDS);
        long elapsedSeconds = Duration.between(lastSentAt, now).getSeconds();
        if (elapsedSeconds >= cooldownSeconds) {
            return;
        }
        long retryAfterSeconds = cooldownSeconds - elapsedSeconds;
        throw new BusinessException(
                AuthErrorCode.AUTH_OTP_RESEND_TOO_SOON, null, null, Map.of("retryAfterSeconds", retryAfterSeconds));
    }

    private boolean isMissingOrExpired(User user) {
        return user.getOtpCodeHash() == null
                || user.getOtpExpiresAt() == null
                || !clock.instant().isBefore(user.getOtpExpiresAt());
    }

    private void handleWrongCode(User user) {
        user.increaseOtpFailedCount();
        int maxFailed = configService.getInt(ConfigKey.OTP_MAX_FAILED);
        if (user.getOtpFailedCount() >= maxFailed) {
            user.clearOtp(); // hết lượt: xoá mã, người dùng phải bấm "Gửi lại mã"
            throw new BusinessException(AuthErrorCode.AUTH_OTP_TOO_MANY_ATTEMPTS);
        }
        int remainingAttempts = maxFailed - user.getOtpFailedCount();
        throw new BusinessException(
                AuthErrorCode.AUTH_OTP_INVALID, null, null, Map.of("remainingAttempts", remainingAttempts));
    }
}
