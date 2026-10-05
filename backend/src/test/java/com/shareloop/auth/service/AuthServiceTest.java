package com.shareloop.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.shareloop.auth.AuthErrorCode;
import com.shareloop.auth.dto.AuthResponse;
import com.shareloop.auth.dto.LoginRequest;
import com.shareloop.common.exception.BusinessException;
import com.shareloop.setting.service.ConfigService;
import com.shareloop.user.entity.User;
import com.shareloop.user.entity.UserStatus;
import com.shareloop.user.mapper.UserMapper;
import com.shareloop.user.repository.UserRepository;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

/** Unit test AuthService: Mockito, không khởi động Spring, không đụng DB. */
@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    private static final String EMAIL = "an@shareloop.local";
    private static final String PASSWORD = "Dev@12345";
    private static final String HASH = "hash-of-password";
    private static final long TTL_MINUTES = 120;

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

    AuthService service;

    @BeforeEach
    void createService() {
        service = new AuthService(
                userRepository, passwordEncoder, jwtService, new UserMapper(), otpService, configService, TTL_MINUTES);
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
    void login_whenAccountBanned_throwsAccountLocked() {
        // given
        givenUser(UserStatus.BANNED, true);
        when(passwordEncoder.matches(PASSWORD, HASH)).thenReturn(true);

        // when, then
        assertErrorCode(new LoginRequest(EMAIL, PASSWORD), AuthErrorCode.AUTH_ACCOUNT_LOCKED);
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

    private void givenUser(UserStatus status, boolean active) {
        User user = new User(EMAIL, HASH, "Nguyễn Văn An", "0901000002", false, status);
        ReflectionTestUtils.setField(user, "id", 7L); // id do DB sinh, test phải gán tay
        if (!active) {
            user.deactivate();
        }
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));
    }

    private void assertErrorCode(LoginRequest request, AuthErrorCode expected) {
        assertThatThrownBy(() -> service.login(request))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(expected));
    }
}
