package com.shareloop.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.shareloop.catalog.service.AreaService;
import com.shareloop.common.exception.BusinessException;
import com.shareloop.media.MediaErrorCode;
import com.shareloop.media.service.MediaService;
import com.shareloop.user.UserErrorCode;
import com.shareloop.user.dto.ContactInfo;
import com.shareloop.user.dto.MeResponse;
import com.shareloop.user.dto.UpdateProfileRequest;
import com.shareloop.user.entity.User;
import com.shareloop.user.entity.UserStatus;
import com.shareloop.user.mapper.UserMapper;
import com.shareloop.user.repository.UserRepository;
import java.util.List;
import java.util.Optional;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
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

    UserService service;

    @BeforeEach
    void createService() {
        service = new UserService(userRepository, new UserMapper(), areaService, mediaService);
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

    private void assertErrorCode(ThrowingCallable call, UserErrorCode expected) {
        assertThatThrownBy(call)
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(expected));
    }

    private User givenUser() {
        User user = new User(EMAIL, "hash", "Nguyễn Văn An", PHONE, false, UserStatus.ACTIVE);
        ReflectionTestUtils.setField(user, "id", USER_ID); // id do DB sinh, test phải gán tay
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
        return user;
    }
}
