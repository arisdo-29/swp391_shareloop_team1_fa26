package com.shareloop.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.shareloop.catalog.service.AreaService;
import com.shareloop.common.exception.BusinessException;
import com.shareloop.reputation.dto.ReputationSummary;
import com.shareloop.reputation.entity.Rank;
import com.shareloop.reputation.service.ReputationService;
import com.shareloop.request.service.RequestQueryService;
import com.shareloop.user.UserErrorCode;
import com.shareloop.user.dto.PublicProfileResponse;
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
import org.springframework.test.util.ReflectionTestUtils;

/** Unit test UserProfileService: Mockito, không khởi động Spring, không đụng DB. */
@ExtendWith(MockitoExtension.class)
class UserProfileServiceTest {

    private static final long USER_ID = 7L;

    @Mock
    UserRepository userRepository;

    @Mock
    AreaService areaService;

    @Mock
    ReputationService reputationService;

    @Mock
    RequestQueryService requestQueryService;

    UserProfileService service;

    @BeforeEach
    void createService() {
        service = new UserProfileService(
                userRepository, new UserMapper(), areaService, reputationService, requestQueryService);
    }

    @Test
    void getPublicProfile_whenActiveUser_returnsNumbersFromReputationAndRequest() {
        // given
        User user = givenUser("Nguyễn Văn An", UserStatus.ACTIVE);
        user.updateProfile("Nguyễn Văn An", 12L, 30L);
        when(areaService.findName(12L)).thenReturn(Optional.of("Quận 3"));
        when(reputationService.getReputationSummary(USER_ID))
                .thenReturn(new ReputationSummary(USER_ID, 4, Rank.GOLD, 0.92));
        when(requestQueryService.countCompletedByUser(USER_ID)).thenReturn(6L);

        // when
        PublicProfileResponse profile = service.getPublicProfile(USER_ID);

        // then
        assertThat(profile.id()).isEqualTo(USER_ID);
        assertThat(profile.fullName()).isEqualTo("Nguyễn Văn An");
        assertThat(profile.avatarUrl()).isEqualTo("/api/v1/media/30");
        assertThat(profile.avatarInitials()).isEqualTo("NA");
        assertThat(profile.district()).isEqualTo("Quận 3");
        assertThat(profile.rank()).isEqualTo(Rank.GOLD);
        assertThat(profile.trustStars()).isEqualTo(4);
        assertThat(profile.asDescribedRate()).isEqualTo(0.92);
        assertThat(profile.completedTransactions()).isEqualTo(6L);
    }

    @Test
    void getPublicProfile_whenNoAreaAndNoAvatar_returnsNullDistrictAndAvatar() {
        // given
        givenUser("An", UserStatus.ACTIVE);
        when(reputationService.getReputationSummary(USER_ID))
                .thenReturn(new ReputationSummary(USER_ID, 5, Rank.BRONZE, null));

        // when
        PublicProfileResponse profile = service.getPublicProfile(USER_ID);

        // then
        assertThat(profile.district()).isNull();
        assertThat(profile.avatarUrl()).isNull();
        assertThat(profile.avatarInitials()).isEqualTo("A");
        assertThat(profile.asDescribedRate()).isNull();
    }

    @Test
    void getPublicProfile_whenUserMissing_throwsUserNotFound() {
        // given
        when(userRepository.findById(USER_ID)).thenReturn(Optional.empty());

        // when, then
        assertThatThrownBy(() -> service.getPublicProfile(USER_ID))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(UserErrorCode.USER_NOT_FOUND);
    }

    @Test
    void getPublicProfile_whenUserLocked_throwsUserNotFound() {
        // given
        User user = givenUser("Người Bị Khoá", UserStatus.ACTIVE);
        user.deactivate();

        // when, then
        assertThatThrownBy(() -> service.getPublicProfile(USER_ID))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(UserErrorCode.USER_NOT_FOUND);
    }

    @Test
    void getPublicProfile_whenUserBanned_throwsUserNotFound() {
        // given
        givenUser("Người Bị Cấm", UserStatus.BANNED);

        // when, then
        assertThatThrownBy(() -> service.getPublicProfile(USER_ID))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(UserErrorCode.USER_NOT_FOUND);
    }

    @Test
    void getPublicProfile_whenUserNotVerified_throwsUserNotFound() {
        // given
        givenUser("Chưa Xác Thực", UserStatus.PENDING_VERIFICATION);

        // when, then
        assertThatThrownBy(() -> service.getPublicProfile(USER_ID))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(UserErrorCode.USER_NOT_FOUND);
    }

    @Test
    void getPublicProfile_whenUserIsAdmin_throwsUserNotFound() {
        // given
        User admin = new User("admin@shareloop.local", "hash", "Quản Trị", "0901000003", true, UserStatus.ACTIVE);
        ReflectionTestUtils.setField(admin, "id", USER_ID);
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(admin));

        // when, then
        assertThatThrownBy(() -> service.getPublicProfile(USER_ID))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(UserErrorCode.USER_NOT_FOUND);
    }

    private User givenUser(String fullName, UserStatus status) {
        User user = new User("an@shareloop.local", "hash", fullName, "0901000002", false, status);
        ReflectionTestUtils.setField(user, "id", USER_ID);
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
        return user;
    }
}
