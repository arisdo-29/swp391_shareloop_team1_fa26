package com.shareloop.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.shareloop.auth.AuthErrorCode;
import com.shareloop.auth.dto.AuthResponse;
import com.shareloop.auth.dto.ChangePasswordRequest;
import com.shareloop.auth.dto.ForgotPasswordRequest;
import com.shareloop.auth.dto.LoginRequest;
import com.shareloop.auth.dto.OtpSentResponse;
import com.shareloop.auth.dto.ResetPasswordRequest;
import com.shareloop.auth.event.PasswordChangedEvent;
import com.shareloop.common.exception.BusinessException;
import com.shareloop.setting.ConfigKey;
import com.shareloop.setting.service.ConfigService;
import com.shareloop.user.UserErrorCode;
import com.shareloop.user.entity.OtpPurpose;
import com.shareloop.user.entity.User;
import com.shareloop.user.entity.UserStatus;
import com.shareloop.user.mapper.UserMapper;
import com.shareloop.user.repository.UserRepository;
import java.time.Clock;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

/** Unit test AuthService: Mockito, không khởi động Spring, không đụng DB. */
@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    private static final String EMAIL = "an@shareloop.local";
    private static final String PASSWORD = "Dev@12345";
    private static final String HASH = "hash-of-password";
    private static final String NEW_PASSWORD = "MatKhauMoi@123";
    private static final long TTL_MINUTES = 120;
    private static final String OTP = "123456";
    // Mọi nhánh của forgot-password phải trả đúng nội dung này (OTP 5 phút, cooldown 60 giây).
    private static final OtpSentResponse EXPECTED_OTP_SENT = new OtpSentResponse(EMAIL, 300, 60);

    @Mock
    UserRepository userRepository;

    @Mock
    PasswordEncoder passwordEncoder;

    @Mock
    JwtService jwtService;

    @Mock
    OtpService otpService;

    @Mock
    ConfigService configService;

    @Mock
    ApplicationEventPublisher eventPublisher;

    AuthService service;

    @BeforeEach
    void createService() {
        service = new AuthService(
                userRepository,
                passwordEncoder,
                jwtService,
                new UserMapper(),
                otpService,
                configService,
                new DisposableEmailDomains(),
                Clock.systemUTC(),
                eventPublisher,
                TTL_MINUTES);
    }

    @Test
    void login_whenEmailNotFound_throwsInvalidCredentials() {
        // given
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.empty());

        // when, then
        assertErrorCode(new LoginRequest(EMAIL, PASSWORD), AuthErrorCode.AUTH_INVALID_CREDENTIALS);
    }

    @Test
    void login_whenPasswordWrong_throwsInvalidCredentials() {
        // given
        givenUser(UserStatus.ACTIVE, true);
        when(passwordEncoder.matches(PASSWORD, HASH)).thenReturn(false);

        // when, then
        assertErrorCode(new LoginRequest(EMAIL, PASSWORD), AuthErrorCode.AUTH_INVALID_CREDENTIALS);
    }

    @Test
    void login_whenPasswordWrongAndAccountPending_throwsInvalidCredentialsNotUnverified() {
        // given: mật khẩu sai thì không được lộ trạng thái tài khoản
        givenUser(UserStatus.PENDING_VERIFICATION, true);
        when(passwordEncoder.matches(PASSWORD, HASH)).thenReturn(false);

        // when, then
        assertErrorCode(new LoginRequest(EMAIL, PASSWORD), AuthErrorCode.AUTH_INVALID_CREDENTIALS);
    }

    @Test
    void login_whenAccountPending_throwsEmailNotVerified() {
        // given
        givenUser(UserStatus.PENDING_VERIFICATION, true);
        when(passwordEncoder.matches(PASSWORD, HASH)).thenReturn(true);

        // when, then
        assertErrorCode(new LoginRequest(EMAIL, PASSWORD), AuthErrorCode.AUTH_EMAIL_NOT_VERIFIED);
    }

    @Test
    void login_whenAccountBanned_throwsAccountBanned() {
        // given
        givenUser(UserStatus.BANNED, true);
        when(passwordEncoder.matches(PASSWORD, HASH)).thenReturn(true);

        // when, then: cấm (hết sao, D-09) có mã riêng, tách khỏi khoá
        assertErrorCode(new LoginRequest(EMAIL, PASSWORD), AuthErrorCode.AUTH_ACCOUNT_BANNED);
    }

    @Test
    void login_whenAccountBannedAndInactive_throwsAccountBanned() {
        // given
        givenUser(UserStatus.BANNED, false);
        when(passwordEncoder.matches(PASSWORD, HASH)).thenReturn(true);

        // when, then: vừa bị khoá vừa bị cấm thì báo cấm
        assertErrorCode(new LoginRequest(EMAIL, PASSWORD), AuthErrorCode.AUTH_ACCOUNT_BANNED);
    }

    @Test
    void login_whenAccountInactive_throwsAccountLocked() {
        // given
        givenUser(UserStatus.ACTIVE, false);
        when(passwordEncoder.matches(PASSWORD, HASH)).thenReturn(true);

        // when, then
        assertErrorCode(new LoginRequest(EMAIL, PASSWORD), AuthErrorCode.AUTH_ACCOUNT_LOCKED);
    }

    @Test
    void login_whenActiveAndPasswordCorrect_returnsBearerTokenWithTtlInSeconds() {
        // given
        givenUser(UserStatus.ACTIVE, true);
        when(passwordEncoder.matches(PASSWORD, HASH)).thenReturn(true);
        when(jwtService.issue(7L, false)).thenReturn("jwt-token");

        // when
        AuthResponse response = service.login(new LoginRequest(EMAIL, PASSWORD));

        // then
        assertThat(response.accessToken()).isEqualTo("jwt-token");
        assertThat(response.tokenType()).isEqualTo("Bearer");
        assertThat(response.expiresIn()).isEqualTo(7200);
        assertThat(response.user().email()).isEqualTo(EMAIL);
    }

    @Test
    void login_whenEmailHasSpacesAndUpperCase_looksUpNormalizedEmail() {
        // given
        givenUser(UserStatus.ACTIVE, true);
        when(passwordEncoder.matches(PASSWORD, HASH)).thenReturn(true);
        when(jwtService.issue(7L, false)).thenReturn("jwt-token");

        // when
        AuthResponse response = service.login(new LoginRequest("  An@ShareLoop.local ", PASSWORD));

        // then
        assertThat(response.user().id()).isEqualTo(7L);
    }

    @Test
    void changePassword_whenUserNotFound_throwsUserNotFound() {
        // given
        when(userRepository.findById(7L)).thenReturn(Optional.empty());
        ChangePasswordRequest request = new ChangePasswordRequest(PASSWORD, NEW_PASSWORD);

        // when, then
        assertThatThrownBy(() -> service.changePassword(7L, request))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(UserErrorCode.USER_NOT_FOUND));
    }

    @Test
    void changePassword_whenCurrentPasswordWrong_throwsCurrentPasswordIncorrect() {
        // given
        User user = givenUserById();
        when(passwordEncoder.matches("Sai@12345", HASH)).thenReturn(false);
        ChangePasswordRequest request = new ChangePasswordRequest("Sai@12345", NEW_PASSWORD);

        // when, then
        assertThatThrownBy(() -> service.changePassword(7L, request))
                .isInstanceOfSatisfying(BusinessException.class, ex -> {
                    assertThat(ex.getErrorCode()).isEqualTo(AuthErrorCode.AUTH_CURRENT_PASSWORD_INCORRECT);
                    assertThat(ex.getField()).isEqualTo("currentPassword");
                });
        assertThat(user.getPasswordHash()).isEqualTo(HASH);
    }

    @Test
    void changePassword_whenNewPasswordSameAsCurrent_throwsSameAsCurrent() {
        // given: mật khẩu mới khớp với hash đang lưu
        User user = givenUserById();
        when(passwordEncoder.matches(PASSWORD, HASH)).thenReturn(true);
        ChangePasswordRequest request = new ChangePasswordRequest(PASSWORD, PASSWORD);

        // when, then
        assertThatThrownBy(() -> service.changePassword(7L, request))
                .isInstanceOfSatisfying(BusinessException.class, ex -> {
                    assertThat(ex.getErrorCode()).isEqualTo(AuthErrorCode.AUTH_NEW_PASSWORD_SAME_AS_CURRENT);
                    assertThat(ex.getField()).isEqualTo("newPassword");
                });
        assertThat(user.getPasswordHash()).isEqualTo(HASH);
    }

    @Test
    void changePassword_whenValid_storesEncodedNewPassword() {
        // given
        User user = givenUserById();
        when(passwordEncoder.matches(PASSWORD, HASH)).thenReturn(true);
        when(passwordEncoder.matches(NEW_PASSWORD, HASH)).thenReturn(false);
        when(passwordEncoder.encode(NEW_PASSWORD)).thenReturn("hash-of-new-password");

        // when
        service.changePassword(7L, new ChangePasswordRequest(PASSWORD, NEW_PASSWORD));

        // then
        assertThat(user.getPasswordHash()).isEqualTo("hash-of-new-password");
    }

    @Test
    void changePassword_whenValid_publishesPasswordChangedEventForUserEmail() {
        // given
        givenUserById();
        when(passwordEncoder.matches(PASSWORD, HASH)).thenReturn(true);
        when(passwordEncoder.matches(NEW_PASSWORD, HASH)).thenReturn(false);
        when(passwordEncoder.encode(NEW_PASSWORD)).thenReturn("hash-of-new-password");

        // when
        service.changePassword(7L, new ChangePasswordRequest(PASSWORD, NEW_PASSWORD));

        // then: sự kiện mang email của chủ tài khoản và thời điểm đổi, không mang mật khẩu
        ArgumentCaptor<PasswordChangedEvent> captor = ArgumentCaptor.forClass(PasswordChangedEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        assertThat(captor.getValue().email()).isEqualTo(EMAIL);
        assertThat(captor.getValue().changedAt()).isNotNull();
    }

    @Test
    void changePassword_whenCurrentPasswordWrong_publishesNoEvent() {
        // given
        givenUserById();
        when(passwordEncoder.matches("Sai@12345", HASH)).thenReturn(false);
        ChangePasswordRequest request = new ChangePasswordRequest("Sai@12345", NEW_PASSWORD);

        // when
        assertThatThrownBy(() -> service.changePassword(7L, request)).isInstanceOf(BusinessException.class);

        // then
        verifyNoInteractions(eventPublisher);
    }

    @Test
    void forgotPassword_whenEmailNotFound_returnsOtpSentResponseWithoutIssuingOtp() {
        // given
        givenOtpConfig();
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.empty());

        // when
        OtpSentResponse response = service.forgotPassword(new ForgotPasswordRequest(EMAIL));

        // then
        assertThat(response).isEqualTo(EXPECTED_OTP_SENT);
        verifyNoInteractions(otpService);
    }

    @Test
    void forgotPassword_whenAccountActive_issuesResetPasswordOtpAndReturnsSameResponse() {
        // given
        givenOtpConfig();
        User user = givenUser(UserStatus.ACTIVE, true);

        // when
        OtpSentResponse response = service.forgotPassword(new ForgotPasswordRequest(EMAIL));

        // then
        assertThat(response).isEqualTo(EXPECTED_OTP_SENT);
        verify(otpService).issue(user, OtpPurpose.RESET_PASSWORD);
    }

    @Test
    void forgotPassword_whenAccountPending_doesNotIssueOtp() {
        // given: tài khoản chưa xác thực dùng /resend-otp, không được đặt lại mật khẩu
        givenOtpConfig();
        givenUser(UserStatus.PENDING_VERIFICATION, true);

        // when
        OtpSentResponse response = service.forgotPassword(new ForgotPasswordRequest(EMAIL));

        // then
        assertThat(response).isEqualTo(EXPECTED_OTP_SENT);
        verifyNoInteractions(otpService);
    }

    @Test
    void forgotPassword_whenAccountLocked_doesNotIssueOtp() {
        // given
        givenOtpConfig();
        givenUser(UserStatus.ACTIVE, false);

        // when
        OtpSentResponse response = service.forgotPassword(new ForgotPasswordRequest(EMAIL));

        // then
        assertThat(response).isEqualTo(EXPECTED_OTP_SENT);
        verifyNoInteractions(otpService);
    }

    @Test
    void forgotPassword_whenStillInCooldown_returnsSameResponseInsteadOfTooSoon() {
        // given: 429 sẽ cho biết email có tài khoản, nên phải trả 200 như mọi trường hợp khác
        givenOtpConfig();
        User user = givenUser(UserStatus.ACTIVE, true);
        when(otpService.issue(user, OtpPurpose.RESET_PASSWORD))
                .thenThrow(new BusinessException(AuthErrorCode.AUTH_OTP_RESEND_TOO_SOON));

        // when
        OtpSentResponse response = service.forgotPassword(new ForgotPasswordRequest(EMAIL));

        // then
        assertThat(response).isEqualTo(EXPECTED_OTP_SENT);
    }

    @Test
    void resetPassword_whenEmailNotFound_throwsOtpExpired() {
        // given
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.empty());

        // when, then
        assertResetPasswordErrorCode(
                new ResetPasswordRequest(EMAIL, OTP, NEW_PASSWORD), AuthErrorCode.AUTH_OTP_EXPIRED);
        verifyNoInteractions(otpService);
    }

    @Test
    void resetPassword_whenAccountBanned_throwsOtpExpired() {
        // given
        givenUser(UserStatus.BANNED, true);

        // when, then
        assertResetPasswordErrorCode(
                new ResetPasswordRequest(EMAIL, OTP, NEW_PASSWORD), AuthErrorCode.AUTH_OTP_EXPIRED);
        verifyNoInteractions(otpService);
    }

    @Test
    void resetPassword_whenOtpWrong_keepsOldPasswordAndSkipsPasswordCheck() {
        // given: OTP sai phải dừng trước khi so mật khẩu, để không dò được mật khẩu hiện tại
        User user = givenUser(UserStatus.ACTIVE, true);
        doThrow(new BusinessException(AuthErrorCode.AUTH_OTP_INVALID))
                .when(otpService)
                .verify(user, OTP, OtpPurpose.RESET_PASSWORD);

        // when, then
        assertResetPasswordErrorCode(
                new ResetPasswordRequest(EMAIL, OTP, NEW_PASSWORD), AuthErrorCode.AUTH_OTP_INVALID);
        assertThat(user.getPasswordHash()).isEqualTo(HASH);
        verifyNoInteractions(passwordEncoder);
    }

    @Test
    void resetPassword_whenNewPasswordSameAsCurrent_throwsSameAsCurrent() {
        // given
        User user = givenUser(UserStatus.ACTIVE, true);
        when(passwordEncoder.matches(PASSWORD, HASH)).thenReturn(true);
        ResetPasswordRequest request = new ResetPasswordRequest(EMAIL, OTP, PASSWORD);

        // when, then
        assertThatThrownBy(() -> service.resetPassword(request)).isInstanceOfSatisfying(BusinessException.class, ex -> {
            assertThat(ex.getErrorCode()).isEqualTo(AuthErrorCode.AUTH_NEW_PASSWORD_SAME_AS_CURRENT);
            assertThat(ex.getField()).isEqualTo("newPassword");
        });
        assertThat(user.getPasswordHash()).isEqualTo(HASH);
    }

    @Test
    void resetPassword_whenOtpCorrect_verifiesResetOtpAndStoresEncodedNewPassword() {
        // given
        User user = givenUser(UserStatus.ACTIVE, true);
        when(passwordEncoder.matches(NEW_PASSWORD, HASH)).thenReturn(false);
        when(passwordEncoder.encode(NEW_PASSWORD)).thenReturn("hash-of-new-password");

        // when
        service.resetPassword(new ResetPasswordRequest("  An@ShareLoop.local ", OTP, NEW_PASSWORD));

        // then
        verify(otpService).verify(user, OTP, OtpPurpose.RESET_PASSWORD);
        assertThat(user.getPasswordHash()).isEqualTo("hash-of-new-password");
    }

    private void givenOtpConfig() {
        when(configService.getInt(ConfigKey.OTP_TTL_MINUTES)).thenReturn(5);
        when(configService.getInt(ConfigKey.OTP_RESEND_COOLDOWN_SECONDS)).thenReturn(60);
    }

    private void assertResetPasswordErrorCode(ResetPasswordRequest request, AuthErrorCode expected) {
        assertThatThrownBy(() -> service.resetPassword(request))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(expected));
    }

    private User givenUserById() {
        User user = new User(EMAIL, HASH, "Nguyễn Văn An", "0901000002", false, UserStatus.ACTIVE);
        ReflectionTestUtils.setField(user, "id", 7L);
        when(userRepository.findById(7L)).thenReturn(Optional.of(user));
        return user;
    }

    private User givenUser(UserStatus status, boolean active) {
        User user = new User(EMAIL, HASH, "Nguyễn Văn An", "0901000002", false, status);
        ReflectionTestUtils.setField(user, "id", 7L); // id do DB sinh, test phải gán tay
        if (!active) {
            user.deactivate();
        }
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));
        return user;
    }

    private void assertErrorCode(LoginRequest request, AuthErrorCode expected) {
        assertThatThrownBy(() -> service.login(request))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(expected));
    }
}
