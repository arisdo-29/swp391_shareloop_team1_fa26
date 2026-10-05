package com.shareloop.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.shareloop.auth.AuthErrorCode;
import com.shareloop.auth.event.OtpIssuedEvent;
import com.shareloop.common.exception.BusinessException;
import com.shareloop.setting.ConfigKey;
import com.shareloop.setting.service.ConfigService;
import com.shareloop.user.entity.OtpPurpose;
import com.shareloop.user.entity.User;
import com.shareloop.user.entity.UserStatus;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;

/** Unit test OtpService: Mockito và Clock cố định, không khởi động Spring, không đụng DB. */
@ExtendWith(MockitoExtension.class)
class OtpServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-05T10:00:00Z");
    private static final int TTL_MINUTES = 5;
    private static final int COOLDOWN_SECONDS = 60;
    private static final int MAX_FAILED = 5;
    private static final String HASH = "hashed-code";
    private static final String CORRECT_CODE = "123456";
    private static final String WRONG_CODE = "000000";

    @Mock
    ConfigService configService;

    @Mock
    PasswordEncoder passwordEncoder;

    @Mock
    ApplicationEventPublisher eventPublisher;

    OtpService service;

    @BeforeEach
    void createService() {
        // lenient: mỗi test chỉ dùng một phần cấu hình nên không bắt buộc phải gọi hết các stub này
        lenient().when(configService.getInt(ConfigKey.OTP_TTL_MINUTES)).thenReturn(TTL_MINUTES);
        lenient()
                .when(configService.getInt(ConfigKey.OTP_RESEND_COOLDOWN_SECONDS))
                .thenReturn(COOLDOWN_SECONDS);
        lenient().when(configService.getInt(ConfigKey.OTP_MAX_FAILED)).thenReturn(MAX_FAILED);
        Clock fixedClock = Clock.fixed(NOW, ZoneOffset.UTC);
        service = new OtpService(configService, passwordEncoder, eventPublisher, fixedClock);
    }

    @Test
    void issue_whenFirstTime_returnsSixDigitCodeAndStoresHashWithExpiry() {
        // given
        User user = newPendingUser();
        when(passwordEncoder.encode(anyString())).thenReturn(HASH);

        // when
        String code = service.issue(user);

        // then
        assertThat(code).matches("\\d{6}");
        assertThat(user.getOtpCodeHash()).isEqualTo(HASH);
        assertThat(user.getOtpPurpose()).isEqualTo(OtpPurpose.REGISTER);
        assertThat(user.getOtpExpiresAt()).isEqualTo(NOW.plus(Duration.ofMinutes(TTL_MINUTES)));
        assertThat(user.getOtpFailedCount()).isZero();
    }

    @Test
    void issue_whenFirstTime_publishesEventWithRawCodeForEmail() {
        // given
        User user = newPendingUser();
        when(passwordEncoder.encode(anyString())).thenReturn(HASH);

        // when
        String code = service.issue(user);

        // then
        ArgumentCaptor<OtpIssuedEvent> captor = ArgumentCaptor.forClass(OtpIssuedEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        assertThat(captor.getValue()).isEqualTo(new OtpIssuedEvent(user.getEmail(), code, TTL_MINUTES));
    }

    @Test
    void issue_whenResentTooSoon_throwsResendTooSoonWithRetryAfterSeconds() {
        // given: lần gửi trước cách đây 20 giây, cooldown 60 giây nên còn phải chờ 40 giây
        User user = newPendingUser();
        user.startOtp(HASH, OtpPurpose.REGISTER, sentSecondsAgo(20));

        // when, then
        assertThatThrownBy(() -> service.issue(user)).isInstanceOfSatisfying(BusinessException.class, ex -> {
            assertThat(ex.getErrorCode()).isEqualTo(AuthErrorCode.AUTH_OTP_RESEND_TOO_SOON);
            assertThat(ex.getDetails()).containsEntry("retryAfterSeconds", 40L);
        });
        verifyNoInteractions(eventPublisher);
    }

    @Test
    void issue_whenCooldownPassed_issuesNewCodeAndResetsFailedCount() {
        // given: lần gửi trước cách đây 61 giây và đã nhập sai 2 lần
        User user = newPendingUser();
        user.startOtp("old-hash", OtpPurpose.REGISTER, sentSecondsAgo(61));
        user.increaseOtpFailedCount();
        user.increaseOtpFailedCount();
        when(passwordEncoder.encode(anyString())).thenReturn(HASH);

        // when
        service.issue(user);

        // then
        assertThat(user.getOtpCodeHash()).isEqualTo(HASH);
        assertThat(user.getOtpFailedCount()).isZero();
    }

    @Test
    void verify_whenCodeCorrect_clearsOtpColumns() {
        // given
        User user = givenUserWithValidOtp();
        when(passwordEncoder.matches(CORRECT_CODE, HASH)).thenReturn(true);

        // when
        service.verify(user, CORRECT_CODE);

        // then
        assertThat(user.getOtpCodeHash()).isNull();
        assertThat(user.getOtpPurpose()).isNull();
        assertThat(user.getOtpExpiresAt()).isNull();
        assertThat(user.getOtpFailedCount()).isZero();
    }

    @Test
    void verify_whenCodeWrong_throwsInvalidWithRemainingAttempts() {
        // given
        User user = givenUserWithValidOtp();
        when(passwordEncoder.matches(WRONG_CODE, HASH)).thenReturn(false);

        // when, then
        assertThatThrownBy(() -> service.verify(user, WRONG_CODE))
                .isInstanceOfSatisfying(BusinessException.class, ex -> {
                    assertThat(ex.getErrorCode()).isEqualTo(AuthErrorCode.AUTH_OTP_INVALID);
                    assertThat(ex.getDetails()).containsEntry("remainingAttempts", 4);
                });
        assertThat(user.getOtpFailedCount()).isEqualTo(1);
    }

    @Test
    void verify_whenFifthWrongCode_clearsCodeAndThrowsTooManyAttempts() {
        // given: đã sai 4 lần, lần này là lần thứ 5
        User user = givenUserWithValidOtp();
        for (int i = 0; i < MAX_FAILED - 1; i++) {
            user.increaseOtpFailedCount();
        }
        when(passwordEncoder.matches(WRONG_CODE, HASH)).thenReturn(false);

        // when, then
        assertThatThrownBy(() -> service.verify(user, WRONG_CODE))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(AuthErrorCode.AUTH_OTP_TOO_MANY_ATTEMPTS));
        assertThat(user.getOtpCodeHash()).isNull();
    }

    @Test
    void verify_whenCodeExpired_throwsExpired() {
        // given: hạn mã đã qua 1 giây
        User user = newPendingUser();
        user.startOtp(HASH, OtpPurpose.REGISTER, NOW.minusSeconds(1));

        // when, then
        assertThatThrownBy(() -> service.verify(user, CORRECT_CODE))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(AuthErrorCode.AUTH_OTP_EXPIRED));
    }

    @Test
    void verify_whenNoCodeStored_throwsExpired() {
        // given: user chưa có mã (hoặc mã đã bị xoá)
        User user = newPendingUser();

        // when, then
        assertThatThrownBy(() -> service.verify(user, CORRECT_CODE))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(AuthErrorCode.AUTH_OTP_EXPIRED));
    }

    private User newPendingUser() {
        return new User(
                "an@shareloop.local",
                "password-hash",
                "Nguyễn Văn An",
                "0901000002",
                false,
                UserStatus.PENDING_VERIFICATION);
    }

    private User givenUserWithValidOtp() {
        User user = newPendingUser();
        user.startOtp(HASH, OtpPurpose.REGISTER, NOW.plus(Duration.ofMinutes(TTL_MINUTES)));
        return user;
    }

    // Hạn mã = lúc gửi + TTL, nên hạn của mã gửi cách đây N giây là NOW − N giây + TTL.
    private Instant sentSecondsAgo(long seconds) {
        return NOW.minusSeconds(seconds).plus(Duration.ofMinutes(TTL_MINUTES));
    }
}
