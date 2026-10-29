package com.shareloop.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.shareloop.auth.AuthErrorCode;
import com.shareloop.auth.service.OtpService;
import com.shareloop.catalog.service.AreaService;
import com.shareloop.common.api.PageResponse;
import com.shareloop.common.exception.BusinessException;
import com.shareloop.media.MediaErrorCode;
import com.shareloop.media.service.MediaService;
import com.shareloop.request.service.RequestQueryService;
import com.shareloop.setting.ConfigKey;
import com.shareloop.setting.service.ConfigService;
import com.shareloop.user.UserErrorCode;
import com.shareloop.user.dto.AdminAccountRole;
import com.shareloop.user.dto.AdminAccountState;
import com.shareloop.user.dto.AdminUserSummary;
import com.shareloop.user.dto.ChangePhoneOtpResponse;
import com.shareloop.user.dto.ChangePhoneRequest;
import com.shareloop.user.dto.ContactInfo;
import com.shareloop.user.dto.MeResponse;
import com.shareloop.user.dto.SendChangePhoneOtpRequest;
import com.shareloop.user.dto.UpdateProfileRequest;
import com.shareloop.user.entity.OtpPurpose;
import com.shareloop.user.entity.User;
import com.shareloop.user.entity.UserStatus;
import com.shareloop.user.event.UserLockCleanup;
import com.shareloop.user.event.UserLockedEvent;
import com.shareloop.user.mapper.UserMapper;
import com.shareloop.user.repository.UserRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

/** Unit test UserService: Mockito, không khởi động Spring, không đụng DB. */
@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    private static final long USER_ID = 7L;
    private static final String EMAIL = "an@shareloop.local";
    private static final String PHONE = "0901000002";

    @Mock
    UserRepository userRepository;

    @Mock
    AreaService areaService;

    @Mock
    MediaService mediaService;

    @Mock
    OtpService otpService;

    @Mock
    ConfigService configService;

    @Mock
    RequestQueryService requestQueryService;

    @Mock
    ApplicationEventPublisher eventPublisher;

    UserService service;

    @BeforeEach
    void createService() {
        service = new UserService(
                userRepository,
                new UserMapper(),
                areaService,
                mediaService,
                otpService,
                configService,
                requestQueryService,
                eventPublisher);
    }

    @Test
    void updateProfile_whenValid_updatesNameAreaAvatarAndKeepsEmailPhone() {
        // given
        User user = givenUser();
        when(areaService.isActiveArea(12L)).thenReturn(true);
        UpdateProfileRequest request = new UpdateProfileRequest("  Trần Thị Bình  ", 12L, 30L);

        // when
        MeResponse response = service.updateProfile(USER_ID, request);

        // then
        verify(mediaService).attach(List.of(30L), USER_ID);
        assertThat(response.fullName()).isEqualTo("Trần Thị Bình");
        assertThat(response.areaId()).isEqualTo(12L);
        assertThat(response.avatarMediaId()).isEqualTo(30L);
        assertThat(response.avatarUrl()).isEqualTo("/api/v1/media/30");
        assertThat(user.getEmail()).isEqualTo(EMAIL);
        assertThat(user.getPhone()).isEqualTo(PHONE);
    }

    @Test
    void updateProfile_whenAreaInactiveOrMissing_throwsAreaInvalid() {
        // given: AreaService trả false cho cả khu vực không tồn tại lẫn đã ngừng hoạt động
        User user = givenUser();
        when(areaService.isActiveArea(99L)).thenReturn(false);
        UpdateProfileRequest request = new UpdateProfileRequest("Trần Thị Bình", 99L, null);

        // when, then
        assertThatThrownBy(() -> service.updateProfile(USER_ID, request))
                .isInstanceOfSatisfying(BusinessException.class, ex -> {
                    assertThat(ex.getErrorCode()).isEqualTo(UserErrorCode.USER_AREA_INVALID);
                    assertThat(ex.getField()).isEqualTo("areaId");
                });
        assertThat(user.getFullName()).isEqualTo("Nguyễn Văn An");
    }

    @Test
    void updateProfile_whenAreaNull_clearsDefaultAreaWithoutCallingCatalog() {
        // given
        User user = givenUser();
        user.updateProfile("Nguyễn Văn An", 12L, null);

        // when
        MeResponse response = service.updateProfile(USER_ID, new UpdateProfileRequest("Nguyễn Văn An", null, null));

        // then
        assertThat(response.areaId()).isNull();
        verify(areaService, never()).isActiveArea(anyLong());
    }

    @Test
    void updateProfile_whenAvatarUnchanged_doesNotAttachAgain() {
        // given: ảnh 30 đã gắn làm ảnh đại diện từ lần trước
        User user = givenUser();
        user.updateProfile("Nguyễn Văn An", null, 30L);

        // when
        MeResponse response = service.updateProfile(USER_ID, new UpdateProfileRequest("Nguyễn Văn An", null, 30L));

        // then
        assertThat(response.avatarMediaId()).isEqualTo(30L);
        verify(mediaService, never()).attach(anyList(), anyLong());
    }

    @Test
    void updateProfile_whenAvatarNotOwned_throwsMediaNotOwner() {
        // given
        User user = givenUser();
        doThrow(new BusinessException(MediaErrorCode.MEDIA_NOT_OWNER))
                .when(mediaService)
                .attach(List.of(31L), USER_ID);
        UpdateProfileRequest request = new UpdateProfileRequest("Nguyễn Văn An", null, 31L);

        // when, then
        assertThatThrownBy(() -> service.updateProfile(USER_ID, request))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(MediaErrorCode.MEDIA_NOT_OWNER));
        assertThat(user.getAvatarMediaId()).isNull();
    }

    @Test
    void updateProfile_whenUserNotFound_throwsUserNotFound() {
        // given
        when(userRepository.findById(USER_ID)).thenReturn(Optional.empty());
        UpdateProfileRequest request = new UpdateProfileRequest("Nguyễn Văn An", null, null);

        // when, then
        assertThatThrownBy(() -> service.updateProfile(USER_ID, request))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(UserErrorCode.USER_NOT_FOUND));
    }

    @Test
    void lock_whenActive_setsInactiveAndKeepsStatus() {
        // given
        User user = givenUser();

        // when
        service.lock(USER_ID, "Spam nhiều lần");

        // then
        assertThat(user.isActive()).isFalse();
        assertThat(user.getStatus()).isEqualTo(UserStatus.ACTIVE);
    }

    @Test
    void lock_whenAlreadyLocked_throwsUserAlreadyLocked() {
        // given
        User user = givenUser();
        user.deactivate();

        // when, then
        assertErrorCode(() -> service.lock(USER_ID, null), UserErrorCode.USER_ALREADY_LOCKED);
    }

    @Test
    void lock_whenUserNotFound_throwsUserNotFound() {
        // given
        when(userRepository.findById(USER_ID)).thenReturn(Optional.empty());

        // when, then
        assertErrorCode(() -> service.lock(USER_ID, null), UserErrorCode.USER_NOT_FOUND);
    }

    @Test
    void unlock_whenLocked_setsActiveAndKeepsStatus() {
        // given
        User user = givenUser();
        user.deactivate();

        // when
        service.unlock(USER_ID);

        // then
        assertThat(user.isActive()).isTrue();
        assertThat(user.getStatus()).isEqualTo(UserStatus.ACTIVE);
    }

    @Test
    void unlock_whenActive_throwsUserNotLocked() {
        // given
        givenUser();

        // when, then
        assertErrorCode(() -> service.unlock(USER_ID), UserErrorCode.USER_NOT_LOCKED);
    }

    @Test
    void unlock_whenUserNotFound_throwsUserNotFound() {
        // given
        when(userRepository.findById(USER_ID)).thenReturn(Optional.empty());

        // when, then
        assertErrorCode(() -> service.unlock(USER_ID), UserErrorCode.USER_NOT_FOUND);
    }

    @Test
    void ban_whenActive_setsBannedAndPublishesLockedEventForCleanup() {
        // given
        User user = givenUser();

        // when
        UserLockCleanup cleanup = service.ban(USER_ID);

        // then: status đổi sang BANNED; dọn dẹp chạy qua cùng UserLockedEvent với khoá, không có Admin thực hiện
        assertThat(user.getStatus()).isEqualTo(UserStatus.BANNED);
        assertThat(cleanup).isNotNull();
        ArgumentCaptor<UserLockedEvent> event = ArgumentCaptor.forClass(UserLockedEvent.class);
        verify(eventPublisher).publishEvent(event.capture());
        assertThat(event.getValue().userId()).isEqualTo(USER_ID);
        assertThat(event.getValue().adminId()).isNull();
        assertThat(event.getValue().cleanup()).isSameAs(cleanup);
    }

    @Test
    void ban_whenAlreadyBanned_returnsNullAndDoesNotCleanUpAgain() {
        // given
        User user = givenUser();
        user.ban();

        // when
        UserLockCleanup cleanup = service.ban(USER_ID);

        // then
        assertThat(cleanup).isNull();
        verify(eventPublisher, never()).publishEvent(any(Object.class));
    }

    @Test
    void ban_whenUserNotFound_throwsUserNotFound() {
        // given
        when(userRepository.findById(USER_ID)).thenReturn(Optional.empty());

        // when, then
        assertErrorCode(() -> service.ban(USER_ID), UserErrorCode.USER_NOT_FOUND);
    }

    @Test
    void isBanned_trueOnlyForBannedStatus() {
        // given
        User user = givenUser();

        // when, then: khoá (is_active = false) không phải cấm
        user.deactivate();
        assertThat(service.isBanned(USER_ID)).isFalse();
        user.ban();
        assertThat(service.isBanned(USER_ID)).isTrue();
    }

    @Test
    void findContact_whenUserExists_returnsIdNameEmailPhone() {
        // given
        givenUser();

        // when
        ContactInfo contact = service.findContact(USER_ID);

        // then
        assertThat(contact).isEqualTo(new ContactInfo(USER_ID, "Nguyễn Văn An", EMAIL, PHONE));
    }

    @Test
    void findContact_whenUserLocked_stillReturnsContact() {
        // given: BR-T12 đã trao thì không ẩn lại, kể cả khi đối tác bị khoá sau đó
        User user = givenUser();
        user.deactivate();

        // when
        ContactInfo contact = service.findContact(USER_ID);

        // then
        assertThat(contact.email()).isEqualTo(EMAIL);
    }

    @Test
    void findContact_whenUserNotFound_throwsUserNotFound() {
        // given
        when(userRepository.findById(USER_ID)).thenReturn(Optional.empty());

        // when, then
        assertErrorCode(() -> service.findContact(USER_ID), UserErrorCode.USER_NOT_FOUND);
    }

    @Test
    void isLocked_whenInactive_returnsTrue() {
        // given
        User user = givenUser();
        user.deactivate();

        // when, then
        assertThat(service.isLocked(USER_ID)).isTrue();
    }

    @Test
    void isLocked_whenBanned_returnsTrue() {
        // given: BANNED cũng bị chặn, cùng luật với đăng nhập
        User user = new User(EMAIL, "hash", "Nguyễn Văn An", PHONE, false, UserStatus.BANNED);
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));

        // when, then
        assertThat(service.isLocked(USER_ID)).isTrue();
    }

    @Test
    void isLocked_whenActive_returnsFalse() {
        // given
        givenUser();

        // when, then
        assertThat(service.isLocked(USER_ID)).isFalse();
    }

    @Test
    void isLocked_whenUserNotFound_returnsFalse() {
        // given
        when(userRepository.findById(USER_ID)).thenReturn(Optional.empty());

        // when, then
        assertThat(service.isLocked(USER_ID)).isFalse();
    }

    // ==================== sendChangePhoneOtp (FR-07, BR-U10) ====================

    @Test
    void sendChangePhoneOtp_whenValid_issuesOtpAndReturnsResponse() {
        // given
        User user = givenUser();
        when(userRepository.existsByPhoneAndStatus("0987654321", UserStatus.ACTIVE))
                .thenReturn(false);
        when(requestQueryService.hasActiveDelivery(USER_ID)).thenReturn(false);
        when(configService.getInt(ConfigKey.OTP_TTL_MINUTES)).thenReturn(5);
        when(configService.getInt(ConfigKey.OTP_RESEND_COOLDOWN_SECONDS)).thenReturn(60);

        SendChangePhoneOtpRequest request = new SendChangePhoneOtpRequest("+84987654321");

        // when
        ChangePhoneOtpResponse response = service.sendChangePhoneOtp(USER_ID, request);

        // then
        assertThat(user.getPendingPhone()).isEqualTo("0987654321");
        verify(otpService).issue(user, OtpPurpose.CHANGE_PHONE);
        assertThat(response.email()).isEqualTo(EMAIL);
        assertThat(response.otpExpiresInSeconds()).isEqualTo(300);
        assertThat(response.resendCooldownSeconds()).isEqualTo(60);
    }

    @Test
    void sendChangePhoneOtp_whenSameAsCurrentPhone_throwsPhoneSameAsCurrent() {
        // given
        givenUser();
        SendChangePhoneOtpRequest request = new SendChangePhoneOtpRequest("+84901000002");

        // when, then
        assertErrorCode(() -> service.sendChangePhoneOtp(USER_ID, request), UserErrorCode.USER_PHONE_SAME_AS_CURRENT);
        verify(otpService, never()).issue(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void sendChangePhoneOtp_whenPhoneAlreadyUsedByActiveUser_throwsPhoneAlreadyUsed() {
        // given
        givenUser();
        when(userRepository.existsByPhoneAndStatus("0987654321", UserStatus.ACTIVE))
                .thenReturn(true);
        SendChangePhoneOtpRequest request = new SendChangePhoneOtpRequest("0987654321");

        // when, then
        assertErrorCode(() -> service.sendChangePhoneOtp(USER_ID, request), UserErrorCode.USER_PHONE_ALREADY_USED);
        verify(otpService, never()).issue(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void sendChangePhoneOtp_whenWithinCooldown_throwsPhoneChangeCooldown() {
        // given
        User user = givenUser();
        user.setPhoneChangedAt(Instant.now().minus(10, ChronoUnit.DAYS));
        when(configService.getInt(ConfigKey.PHONE_CHANGE_INTERVAL_DAYS)).thenReturn(30);

        SendChangePhoneOtpRequest request = new SendChangePhoneOtpRequest("0987654321");

        // when, then
        assertErrorCode(() -> service.sendChangePhoneOtp(USER_ID, request), UserErrorCode.USER_PHONE_CHANGE_COOLDOWN);
        verify(otpService, never()).issue(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void sendChangePhoneOtp_whenHasActiveDelivery_throwsPhoneChangeActiveDelivery() {
        // given
        givenUser();
        when(requestQueryService.hasActiveDelivery(USER_ID)).thenReturn(true);
        SendChangePhoneOtpRequest request = new SendChangePhoneOtpRequest("0987654321");

        // when, then
        assertErrorCode(
                () -> service.sendChangePhoneOtp(USER_ID, request), UserErrorCode.USER_PHONE_CHANGE_ACTIVE_DELIVERY);
        verify(otpService, never()).issue(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    // ==================== changePhone (FR-07, BR-U10) ====================

    @Test
    void changePhone_whenValid_verifiesOtpAndUpdatesPhoneAndRecordsTimestamp() {
        // given
        User user = givenUser();
        user.setPendingPhone("0987654321");
        when(requestQueryService.hasActiveDelivery(USER_ID)).thenReturn(false);
        when(userRepository.existsByPhoneAndStatus("0987654321", UserStatus.ACTIVE))
                .thenReturn(false);

        ChangePhoneRequest request = new ChangePhoneRequest("+84987654321", "123456");

        // when
        MeResponse response = service.changePhone(USER_ID, request);

        // then
        verify(otpService).verify(user, "123456", OtpPurpose.CHANGE_PHONE);
        assertThat(user.getPhone()).isEqualTo("0987654321");
        assertThat(user.getPendingPhone()).isNull();
        assertThat(user.getPhoneChangedAt()).isNotNull();
        assertThat(response.phone()).isEqualTo("0987654321");
    }

    @Test
    void changePhone_whenOtpServiceThrows_propagatesExceptionWithoutUpdatingPhone() {
        // given
        User user = givenUser();
        when(requestQueryService.hasActiveDelivery(USER_ID)).thenReturn(false);
        doThrow(new BusinessException(AuthErrorCode.AUTH_OTP_INVALID))
                .when(otpService)
                .verify(user, "999999", OtpPurpose.CHANGE_PHONE);

        ChangePhoneRequest request = new ChangePhoneRequest("0987654321", "999999");

        // when, then
        assertThatThrownBy(() -> service.changePhone(USER_ID, request))
                .isInstanceOfSatisfying(BusinessException.class, ex -> {
                    assertThat(ex.getErrorCode()).isEqualTo(AuthErrorCode.AUTH_OTP_INVALID);
                });
        assertThat(user.getPhone()).isEqualTo(PHONE);
        assertThat(user.getPhoneChangedAt()).isNull();
    }

    @Test
    void changePhone_whenSameAsCurrentPhone_throwsPhoneSameAsCurrent() {
        // given
        givenUser();
        when(requestQueryService.hasActiveDelivery(USER_ID)).thenReturn(false);
        ChangePhoneRequest request = new ChangePhoneRequest("0901000002", "123456");

        // when, then
        assertErrorCode(() -> service.changePhone(USER_ID, request), UserErrorCode.USER_PHONE_SAME_AS_CURRENT);
        verify(otpService, never())
                .verify(
                        org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.any());
    }

    @Test
    void changePhone_whenPhoneAlreadyUsedByActiveUser_throwsPhoneAlreadyUsed() {
        // given
        User user = givenUser();
        when(requestQueryService.hasActiveDelivery(USER_ID)).thenReturn(false);
        when(userRepository.existsByPhoneAndStatus("0987654321", UserStatus.ACTIVE))
                .thenReturn(true);

        ChangePhoneRequest request = new ChangePhoneRequest("0987654321", "123456");

        // when, then
        assertErrorCode(() -> service.changePhone(USER_ID, request), UserErrorCode.USER_PHONE_ALREADY_USED);
    }

    @Test
    void changePhone_whenCooldownNotPassed_throwsPhoneChangeCooldown() {
        // given
        User user = givenUser();
        user.setPhoneChangedAt(Instant.now().minus(5, ChronoUnit.DAYS));
        when(configService.getInt(ConfigKey.PHONE_CHANGE_INTERVAL_DAYS)).thenReturn(30);

        ChangePhoneRequest request = new ChangePhoneRequest("0987654321", "123456");

        // when, then
        assertErrorCode(() -> service.changePhone(USER_ID, request), UserErrorCode.USER_PHONE_CHANGE_COOLDOWN);
    }

    @Test
    void changePhone_whenHasActiveDelivery_throwsPhoneChangeActiveDelivery() {
        // given
        givenUser();
        when(requestQueryService.hasActiveDelivery(USER_ID)).thenReturn(true);
        ChangePhoneRequest request = new ChangePhoneRequest("0987654321", "123456");

        // when, then
        assertErrorCode(() -> service.changePhone(USER_ID, request), UserErrorCode.USER_PHONE_CHANGE_ACTIVE_DELIVERY);
    }

    private void assertErrorCode(ThrowingCallable call, UserErrorCode expected) {
        assertThatThrownBy(call)
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(expected));
    }

    @Test
    void searchAccounts_whenLockedAdminWithKeyword_buildsExpectedFilters() {
        // given: PotentialStubbingProblem sẽ báo lỗi nếu service truyền sai tham số lọc
        when(userRepository.searchForAdmin(eq("%an%"), isNull(), eq(true), eq(true), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        // when
        PageResponse<AdminUserSummary> result =
                service.searchAccounts("  An ", AdminAccountState.LOCKED, AdminAccountRole.ADMIN, 0, 20);

        // then
        assertThat(result.items()).isEmpty();
    }

    @Test
    void searchAccounts_whenNoFilters_passesNullForEveryFilter() {
        // given
        when(userRepository.searchForAdmin(isNull(), isNull(), isNull(), isNull(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        // when: từ khoá toàn khoảng trắng coi như không có từ khoá
        PageResponse<AdminUserSummary> result = service.searchAccounts("   ", null, null, 0, 20);

        // then
        assertThat(result.items()).isEmpty();
    }

    @Test
    void searchAccounts_mapsDerivedStateAndRoleForEachAccount() {
        // given
        User lockedUser = new User("a@x.test", "hash", "Bị khoá", "0900000001", false, UserStatus.ACTIVE);
        lockedUser.deactivate();
        User bannedAndLocked = new User("b@x.test", "hash", "Bị cấm", "0900000002", false, UserStatus.BANNED);
        bannedAndLocked.deactivate();
        User pendingAdmin = new User("c@x.test", "hash", "Chờ", "0900000003", true, UserStatus.PENDING_VERIFICATION);
        when(userRepository.searchForAdmin(any(), any(), any(), any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(lockedUser, bannedAndLocked, pendingAdmin)));

        // when
        List<AdminUserSummary> items =
                service.searchAccounts(null, null, null, 0, 20).items();

        // then: BANNED được ưu tiên hơn LOCKED
        assertThat(items.get(0).status()).isEqualTo(AdminAccountState.LOCKED);
        assertThat(items.get(0).locked()).isTrue();
        assertThat(items.get(0).role()).isEqualTo(AdminAccountRole.USER);
        assertThat(items.get(1).status()).isEqualTo(AdminAccountState.BANNED);
        assertThat(items.get(2).status()).isEqualTo(AdminAccountState.PENDING_VERIFICATION);
        assertThat(items.get(2).role()).isEqualTo(AdminAccountRole.ADMIN);
    }

    private User givenUser() {
        User user = new User(EMAIL, "hash", "Nguyễn Văn An", PHONE, false, UserStatus.ACTIVE);
        ReflectionTestUtils.setField(user, "id", USER_ID); // id do DB sinh, test phải gán tay
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
        return user;
    }
}
