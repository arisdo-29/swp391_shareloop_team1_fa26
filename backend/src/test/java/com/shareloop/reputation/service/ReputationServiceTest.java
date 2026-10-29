package com.shareloop.reputation.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.shareloop.audit.dto.ActivityLogDto;
import com.shareloop.audit.service.ActivityLogQueryService;
import com.shareloop.audit.service.ActivityLogService;
import com.shareloop.common.exception.BusinessException;
import com.shareloop.item.service.ItemService;
import com.shareloop.notification.service.NotificationService;
import com.shareloop.reputation.ReputationErrorCode;
import com.shareloop.reputation.dto.GraceStarResponse;
import com.shareloop.reputation.dto.MyReputationResponse;
import com.shareloop.reputation.dto.RepeatedPair;
import com.shareloop.reputation.dto.ReputationSummary;
import com.shareloop.reputation.dto.RequestParticipantProjection;
import com.shareloop.reputation.entity.Rank;
import com.shareloop.reputation.entity.UserReputation;
import com.shareloop.reputation.repository.UserReputationRepository;
import com.shareloop.setting.ConfigKey;
import com.shareloop.setting.service.ConfigService;
import com.shareloop.user.UserErrorCode;
import com.shareloop.user.event.UserLockCleanup;
import com.shareloop.user.service.UserService;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ReputationServiceTest {

    // 18:00 UTC ngày 21/10 là 01:00 ngày 22/10 giờ Việt Nam: UTC và giờ VN lệch ngày để kiểm reset theo giờ VN.
    private static final Instant NOW = Instant.parse("2026-10-21T18:00:00Z");
    private static final LocalDate TODAY_VN = LocalDate.of(2026, 10, 22);
    private static final Long ITEM_ID = 77L;

    @Mock
    private ConfigService configService;

    @Mock
    private UserReputationRepository userReputationRepository;

    @Mock
    private ActivityLogService activityLogService;

    @Mock
    private ActivityLogQueryService activityLogQueryService;

    @Mock
    private ItemService itemService;

    @Mock
    private NotificationService notificationService;

    @Mock
    private UserService userService;

    private ReputationService service;

    @BeforeEach
    void setUp() {
        // Service đọc đúng ConfigKey (key viết thường mà trang Admin sửa)
        when(configService.getInt(ConfigKey.REPUTATION_SILVER_MIN)).thenReturn(100);
        when(configService.getInt(ConfigKey.REPUTATION_GOLD_MIN)).thenReturn(300);
        when(configService.getInt(ConfigKey.REPUTATION_DIAMOND_MIN)).thenReturn(600);

        when(configService.getInt(ConfigKey.REPUTATION_GIVE_DONOR)).thenReturn(10);
        when(configService.getInt(ConfigKey.REPUTATION_GIVE_RECEIVER)).thenReturn(2);
        when(configService.getInt(ConfigKey.REPUTATION_SWAP_EACH)).thenReturn(6);

        when(configService.getInt(ConfigKey.DAILY_POINTS_CAP)).thenReturn(30);
        when(configService.getInt(ConfigKey.REPUTATION_PAIR_WINDOW_DAYS)).thenReturn(90);
        when(configService.getInt(ConfigKey.REPUTATION_PAIR_NO_POINTS_FROM)).thenReturn(3);
        when(configService.getInt(ConfigKey.REPUTATION_CLEAN_STREAK_FOR_STAR)).thenReturn(10);

        service = new ReputationService(
                configService,
                userReputationRepository,
                activityLogService,
                activityLogQueryService,
                itemService,
                notificationService,
                userService,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Nested
    @DisplayName("1. Tính hạng theo ngưỡng (getRank)")
    class RankCalculationTests {

        @ParameterizedTest
        @CsvSource({
            "0,BRONZE",
            "99,BRONZE",
            "100,SILVER",
            "299,SILVER",
            "300,GOLD",
            "599,GOLD",
            "600,DIAMOND",
            "10000,DIAMOND"
        })
        void getRank_theoNguong(int points, Rank expected) {
            assertEquals(expected, service.getRank(points));
        }

        @Test
        void getRankReturnsBronzeByDefaultWhenConfigServiceIsNull() {
            ReputationService serviceWithoutConfig = new ReputationService();
            assertThat(serviceWithoutConfig.getRank(500)).isEqualTo(Rank.BRONZE);
        }

        @Test
        void getRankReturnsBronzeWhenUserIdIsNull() {
            assertThat(service.getRank((Long) null)).isEqualTo(Rank.BRONZE);
        }

        @ParameterizedTest
        @ValueSource(longs = {0L, -1L, 999999L})
        void getRankReturnsBronzeForNonExistentUserId(long userId) {
            when(userReputationRepository.findById(userId)).thenReturn(Optional.empty());
            assertThat(service.getRank(userId)).isEqualTo(Rank.BRONZE);
        }

        @Test
        void getRankByUserIdCalculatesFromTotalPoints() {
            UserReputation silverUser = new UserReputation(10L, (short) 5, 250, Rank.BRONZE);
            UserReputation diamondUser = new UserReputation(20L, (short) 5, 800, Rank.GOLD);
            when(userReputationRepository.findById(10L)).thenReturn(Optional.of(silverUser));
            when(userReputationRepository.findById(20L)).thenReturn(Optional.of(diamondUser));

            assertThat(service.getRank(10L)).isEqualTo(Rank.SILVER);
            assertThat(service.getRank(20L)).isEqualTo(Rank.DIAMOND);
        }
    }

    @Nested
    @DisplayName("2. Tóm tắt uy tín (getReputationSummary)")
    class ReputationSummaryTests {

        @Test
        void getReputationSummary_whenUserIdIsNull_returnsDefaultSummary() {
            ReputationSummary summary = service.getReputationSummary(null);

            assertThat(summary.userId()).isNull();
            assertThat(summary.trustStars()).isEqualTo(5);
            assertThat(summary.rank()).isEqualTo(Rank.BRONZE);
            assertThat(summary.asDescribedRate()).isNull();
        }

        @Test
        void getReputationSummary_withoutRepo_returnsDefaultSummary() {
            ReputationService serviceWithoutRepo = new ReputationService(configService);
            ReputationSummary summary = serviceWithoutRepo.getReputationSummary(123L);

            assertThat(summary.userId()).isEqualTo(123L);
            assertThat(summary.trustStars()).isEqualTo(5);
            assertThat(summary.rank()).isEqualTo(Rank.BRONZE);
            assertThat(summary.asDescribedRate()).isNull();
        }

        @Test
        void getReputationSummary_whenUserFoundInRepo_returnsReputationDataWithCalculatedRank() {
            UserReputation userRep = new UserReputation(123L, (short) 4, 350, Rank.BRONZE);
            when(userReputationRepository.findById(123L)).thenReturn(Optional.of(userRep));
            when(userReputationRepository.countRatedCompletedAsDonor(123L)).thenReturn(4L);
            when(userReputationRepository.calculateAsDescribedRate(123L)).thenReturn(96.5);

            ReputationSummary summary = service.getReputationSummary(123L);

            assertThat(summary.userId()).isEqualTo(123L);
            assertThat(summary.trustStars()).isEqualTo(4);
            assertThat(summary.rank()).isEqualTo(Rank.GOLD);
            // Cùng thang 0–1 với /me/reputation và docs/api/request.md
            assertThat(summary.asDescribedRate()).isEqualTo(0.965);
        }

        @ParameterizedTest
        @ValueSource(longs = {0L, 1L, 2L})
        @DisplayName("Dưới 3 giao dịch đã đánh giá thì chưa hiện tỉ lệ đúng mô tả (SRS 6.12)")
        void getReputationSummary_whenFewerThanThreeRated_returnsNullRate(long rated) {
            when(userReputationRepository.findById(123L))
                    .thenReturn(Optional.of(new UserReputation(123L, (short) 5, 0, Rank.BRONZE)));
            when(userReputationRepository.countRatedCompletedAsDonor(123L)).thenReturn(rated);
            when(userReputationRepository.calculateAsDescribedRate(123L)).thenReturn(50.0);

            assertThat(service.getReputationSummary(123L).asDescribedRate()).isNull();
        }

        @Test
        @DisplayName("Đủ đúng 3 giao dịch đã đánh giá thì hiện tỉ lệ")
        void getReputationSummary_whenExactlyThreeRated_returnsRate() {
            when(userReputationRepository.findById(123L))
                    .thenReturn(Optional.of(new UserReputation(123L, (short) 5, 0, Rank.BRONZE)));
            when(userReputationRepository.countRatedCompletedAsDonor(123L)).thenReturn(3L);
            when(userReputationRepository.calculateAsDescribedRate(123L)).thenReturn(100.0);

            assertThat(service.getReputationSummary(123L).asDescribedRate()).isEqualTo(1.0);
        }

        @Test
        @DisplayName("Admin đổi ngưỡng hạng (key viết thường) thì hạng đổi theo")
        void rankFollowsLowercaseConfigKey() {
            when(configService.getInt(ConfigKey.REPUTATION_GOLD_MIN)).thenReturn(400);
            assertThat(service.getRank(350)).isEqualTo(Rank.SILVER);

            when(configService.getInt(ConfigKey.REPUTATION_GOLD_MIN)).thenReturn(200);
            assertThat(service.getRank(350)).isEqualTo(Rank.GOLD);
        }

        @Test
        void getReputationSummary_whenUserNotFoundInRepo_returnsDefaultSummary() {
            when(userReputationRepository.findById(999L)).thenReturn(Optional.empty());

            ReputationSummary summary = service.getReputationSummary(999L);

            assertThat(summary.userId()).isEqualTo(999L);
            assertThat(summary.trustStars()).isEqualTo(5);
            assertThat(summary.rank()).isEqualTo(Rank.BRONZE);
            assertThat(summary.asDescribedRate()).isNull();
        }
    }

    @Nested
    @DisplayName("3. Hoàn tất giao dịch cộng điểm (onCompleted - FR-97, FR-100, Issue #114)")
    class OnCompletedTests {

        @Test
        void onCompleted_whenRequestTypeGive_addsDonor10AndReceiver2() {
            long requestId = 101L;
            long donorId = 1L;
            long receiverId = 2L;

            RequestParticipantProjection participant = createParticipant(donorId, receiverId, "GIVE");
            when(userReputationRepository.isReputationAppliedForRequest(requestId))
                    .thenReturn(false);
            when(userReputationRepository.findRequestParticipant(requestId)).thenReturn(participant);

            UserReputation donor = new UserReputation(donorId, (short) 5, 20, Rank.BRONZE);
            UserReputation receiver = new UserReputation(receiverId, (short) 5, 10, Rank.BRONZE);

            when(userReputationRepository.findByIdForUpdate(donorId)).thenReturn(Optional.of(donor));
            when(userReputationRepository.findByIdForUpdate(receiverId)).thenReturn(Optional.of(receiver));

            service.onCompleted(requestId);

            // Kiểm tra điểm cộng
            assertThat(donor.getTotalPoints()).isEqualTo(30); // 20 + 10
            assertThat(receiver.getTotalPoints()).isEqualTo(12); // 10 + 2

            verify(userReputationRepository).save(donor);
            verify(userReputationRepository).save(receiver);

            // Kiểm tra ghi activity log cho cả hai bên
            verify(activityLogService)
                    .log(
                            eq("POINTS"),
                            eq(donorId),
                            eq("REQUEST"),
                            eq(requestId),
                            any(java.util.Map.class),
                            anyString());
            verify(activityLogService)
                    .log(
                            eq("POINTS"),
                            eq(receiverId),
                            eq("REQUEST"),
                            eq(requestId),
                            any(java.util.Map.class),
                            anyString());
        }

        @Test
        void onCompleted_whenRequestTypeSwap_adds6PointsToBothSides() {
            long requestId = 202L;
            long donorId = 1L;
            long receiverId = 2L;

            RequestParticipantProjection participant = createParticipant(donorId, receiverId, "SWAP");
            when(userReputationRepository.isReputationAppliedForRequest(requestId))
                    .thenReturn(false);
            when(userReputationRepository.findRequestParticipant(requestId)).thenReturn(participant);

            UserReputation donor = new UserReputation(donorId, (short) 5, 40, Rank.BRONZE);
            UserReputation receiver = new UserReputation(receiverId, (short) 5, 50, Rank.SILVER);

            when(userReputationRepository.findByIdForUpdate(donorId)).thenReturn(Optional.of(donor));
            when(userReputationRepository.findByIdForUpdate(receiverId)).thenReturn(Optional.of(receiver));

            service.onCompleted(requestId);

            // Mỗi bên +6
            assertThat(donor.getTotalPoints()).isEqualTo(46);
            assertThat(receiver.getTotalPoints()).isEqualTo(56);

            verify(userReputationRepository).save(donor);
            verify(userReputationRepository).save(receiver);
        }

        @Test
        void onCompleted_whenPointsExceedThreshold_rankUpgradesImmediately() {
            long requestId = 303L;
            long donorId = 1L;
            long receiverId = 2L;

            RequestParticipantProjection participant = createParticipant(donorId, receiverId, "GIVE");
            when(userReputationRepository.isReputationAppliedForRequest(requestId))
                    .thenReturn(false);
            when(userReputationRepository.findRequestParticipant(requestId)).thenReturn(participant);

            // donor đang 95 điểm (BRONZE, ngưỡng SILVER là 100). Sau khi cộng 10 điểm -> 105 điểm (lên SILVER ngay)
            UserReputation donor = new UserReputation(donorId, (short) 5, 95, Rank.BRONZE);
            UserReputation receiver = new UserReputation(receiverId, (short) 5, 0, Rank.BRONZE);

            when(userReputationRepository.findByIdForUpdate(donorId)).thenReturn(Optional.of(donor));
            when(userReputationRepository.findByIdForUpdate(receiverId)).thenReturn(Optional.of(receiver));

            service.onCompleted(requestId);

            assertThat(donor.getTotalPoints()).isEqualTo(105);
            assertThat(donor.getRankLevel()).isEqualTo(Rank.SILVER);
        }

        @Test
        void onCompleted_calledTwice_idempotent_pointsOnlyAppliedOnce() {
            long requestId = 404L;

            // Lần 1: chưa cộng
            when(userReputationRepository.isReputationAppliedForRequest(requestId))
                    .thenReturn(true);

            service.onCompleted(requestId);

            // Không tìm participant và không save vì đã cộng rồi
            verify(userReputationRepository, never()).findRequestParticipant(requestId);
            verify(userReputationRepository, never()).save(any());
        }

        @Test
        void onCompleted_whenConfigServiceValuesChange_pointsFollowConfig() {
            long requestId = 505L;
            long donorId = 10L;
            long receiverId = 20L;

            // Admin sửa key viết thường qua trang cấu hình -> điểm cộng đổi theo
            when(configService.getInt(ConfigKey.REPUTATION_GIVE_DONOR)).thenReturn(15);
            when(configService.getInt(ConfigKey.REPUTATION_GIVE_RECEIVER)).thenReturn(5);

            RequestParticipantProjection participant = createParticipant(donorId, receiverId, "GIVE");
            when(userReputationRepository.isReputationAppliedForRequest(requestId))
                    .thenReturn(false);
            when(userReputationRepository.findRequestParticipant(requestId)).thenReturn(participant);

            UserReputation donor = new UserReputation(donorId, (short) 5, 0, Rank.BRONZE);
            UserReputation receiver = new UserReputation(receiverId, (short) 5, 0, Rank.BRONZE);

            when(userReputationRepository.findByIdForUpdate(donorId)).thenReturn(Optional.of(donor));
            when(userReputationRepository.findByIdForUpdate(receiverId)).thenReturn(Optional.of(receiver));

            service.onCompleted(requestId);

            assertThat(donor.getTotalPoints()).isEqualTo(15);
            assertThat(receiver.getTotalPoints()).isEqualTo(5);
        }

        @Test
        void onCompleted_whenRequestNotFound_doesNotThrow() {
            when(userReputationRepository.isReputationAppliedForRequest(999L)).thenReturn(false);
            when(userReputationRepository.findRequestParticipant(999L)).thenReturn(null);

            service.onCompleted(999L);

            verify(userReputationRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("4. Trừ sao và vô hiệu quyền đăng bài (deductStar - FR-91, FR-92, BR-U05)")
    class DeductStarTests {

        @Test
        void deductStar_remainingStars_stillCanPost() {
            long userId = 1L;
            UserReputation user = new UserReputation(userId, (short) 5, 100, Rank.SILVER);
            when(userReputationRepository.findByIdForUpdate(userId)).thenReturn(Optional.of(user));
            when(userReputationRepository.findById(userId)).thenReturn(Optional.of(user));

            service.deductStar(userId, 2, "Hủy giao dịch không báo trước");

            assertThat(user.getTrustStars()).isEqualTo(3);
            assertThat(service.canPost(userId)).isTrue();
            assertThat(service.canRequest(userId)).isTrue();

            verify(userReputationRepository).save(user);
            verify(activityLogService)
                    .log(eq("STARS"), any(), eq("USER"), eq(userId), any(java.util.Map.class), anyString());
            // UT-03: báo số sao còn lại trước khi bị cấm
            verify(notificationService)
                    .notify(
                            userId,
                            "TRUST_STARS_DEDUCTED",
                            "Bạn bị trừ sao uy tín",
                            "Bạn bị trừ 2 sao. Bạn còn 3 sao trước khi tài khoản bị cấm.",
                            "/profile?tab=reputation");
            // Vẫn còn 3 sao: không cấm, không ẩn bài, không gửi thông báo hết sao
            verify(userService, never()).ban(anyLong());
            verify(itemService, never()).hideAllOfUser(anyLong());
            verify(notificationService, never())
                    .notify(anyLong(), eq("TRUST_STARS_DEPLETED"), anyString(), anyString(), anyString());
        }

        @Test
        void deductStar_dropsToZero_bansAccountAndNotifiesExactlyOnce() {
            long userId = 2L;
            UserReputation user = new UserReputation(userId, (short) 1, 100, Rank.SILVER);
            when(userReputationRepository.findByIdForUpdate(userId)).thenReturn(Optional.of(user));
            when(userReputationRepository.findById(userId)).thenReturn(Optional.of(user));
            UserLockCleanup cleanup = new UserLockCleanup();
            cleanup.addCancelledRequests(2);
            cleanup.addReleasedCredit(15);
            cleanup.addHiddenItemIds(List.of(40L));
            when(userService.ban(userId)).thenReturn(cleanup);

            service.deductStar(userId, 1, "Vi phạm quy định nhiều lần");

            assertThat(user.getTrustStars()).isEqualTo(0);
            assertThat(service.canPost(userId)).isFalse();
            assertThat(service.canRequest(userId)).isFalse();

            // D-09: cấm qua UserService (dọn dẹp như khoá) và ghi USER_BAN kèm kết quả dọn dẹp, người thực hiện null
            verify(userService, times(1)).ban(userId);
            verify(activityLogService)
                    .log(
                            eq("USER_BAN"),
                            isNull(),
                            eq("USER"),
                            eq(userId),
                            eq(Map.<String, Object>of(
                                    "cancelledRequests", 2, "releasedCredit", 15L, "hiddenItemIds", List.of(40L))),
                            anyString());
            verify(itemService, times(1)).hideAllOfUser(userId);
            // Thông báo trỏ về tab uy tín trong hồ sơ (FE không có /me/reputation), không còn nhắc sao ân hạn
            verify(notificationService, times(1))
                    .notify(
                            eq(userId),
                            eq("TRUST_STARS_DEPLETED"),
                            anyString(),
                            argThat(body -> body.contains("bị cấm") && !body.contains("ân hạn")),
                            eq("/profile?tab=reputation"));
            verify(notificationService, never())
                    .notify(anyLong(), eq("TRUST_STARS_DEDUCTED"), anyString(), anyString(), anyString());
        }

        @Test
        void deductStar_dropsToZeroWhenAlreadyBanned_doesNotLogBanAgain() {
            long userId = 5L;
            UserReputation user = new UserReputation(userId, (short) 1, 100, Rank.SILVER);
            when(userReputationRepository.findByIdForUpdate(userId)).thenReturn(Optional.of(user));
            when(userService.ban(userId)).thenReturn(null);

            service.deductStar(userId, 1, "Tái phạm sau sao ân hạn");

            verify(activityLogService, never())
                    .log(eq("USER_BAN"), any(), anyString(), anyLong(), any(Map.class), anyString());
        }

        @Test
        void deductStar_alreadyZero_doesNotBecomeNegative_doesNotNotifyAgain() {
            long userId = 3L;
            UserReputation user = new UserReputation(userId, (short) 0, 50, Rank.SILVER);
            when(userReputationRepository.findByIdForUpdate(userId)).thenReturn(Optional.of(user));
            when(userReputationRepository.findById(userId)).thenReturn(Optional.of(user));

            service.deductStar(userId, 1, "Vi phạm tiếp");

            assertThat(user.getTrustStars()).isEqualTo(0); // Không âm
            assertThat(service.canPost(userId)).isFalse();

            // Không cấm lại, không gọi hideAllOfUser hay gửi thông báo nào nữa
            verify(userService, never()).ban(anyLong());
            verify(itemService, never()).hideAllOfUser(anyLong());
            verify(notificationService, never()).notify(anyLong(), anyString(), anyString(), anyString(), anyString());
        }

        @Test
        void deductStar_whenAmountExceedsCurrentStars_capsAtZero() {
            long userId = 4L;
            UserReputation user = new UserReputation(userId, (short) 2, 50, Rank.SILVER);
            when(userReputationRepository.findByIdForUpdate(userId)).thenReturn(Optional.of(user));

            service.deductStar(userId, 5, "Vi phạm nghiêm trọng");

            assertThat(user.getTrustStars()).isEqualTo(0); // 2 - 5 -> 0, không âm
            verify(userService, times(1)).ban(userId);
            verify(itemService, times(1)).hideAllOfUser(userId);
        }
    }

    @Nested
    @DisplayName("Tests cho getMyReputation (FR-101, Issue #128)")
    class GetMyReputationTests {

        @Test
        @DisplayName("Lấy thông tin uy tín cá nhân thành công có đầy đủ điểm, hạng, sao, tỉ lệ và lịch sử")
        void getMyReputation_success() {
            Long userId = 1L;
            UserReputation user = new UserReputation(userId, (short) 4, 320, Rank.GOLD);
            when(userReputationRepository.findById(userId)).thenReturn(Optional.of(user));
            when(userReputationRepository.countRatedCompletedAsDonor(userId)).thenReturn(25L);
            when(userReputationRepository.calculateAsDescribedRate(userId)).thenReturn(92.0);

            ActivityLogDto logDto = new ActivityLogDto(
                    101L,
                    userId,
                    "Nguyễn Văn A",
                    "user1@test.com",
                    "POINTS",
                    "Cộng điểm",
                    "REQUEST",
                    50L,
                    "Giao dịch món “Nồi cơm điện”",
                    Map.of("points", 10, "totalPoints", 320, "rank", "GOLD", "itemId", 77L),
                    "Cộng 10 điểm hoàn tất giao dịch món “Nồi cơm điện” (vai trò cho/tặng)",
                    "Cộng 10 điểm",
                    Instant.parse("2026-10-18T10:00:00Z"));
            when(activityLogQueryService.search(eq(userId), eq("POINTS"), any(), any(), eq(0), eq(20)))
                    .thenReturn(new PageImpl<>(List.of(logDto), PageRequest.of(0, 20), 1));

            MyReputationResponse res = service.getMyReputation(userId, 0, 20);

            assertThat(res.totalPoints()).isEqualTo(320);
            assertThat(res.rank()).isEqualTo(Rank.GOLD);
            assertThat(res.trustStars()).isEqualTo(4);
            assertThat(res.accuracyRate()).isEqualTo(0.92);
            assertThat(res.history().getContent()).hasSize(1);
            assertThat(res.history().getContent().get(0).delta()).isEqualTo(10);
            assertThat(res.history().getContent().get(0).reason())
                    .isEqualTo("Cộng 10 điểm hoàn tất giao dịch món “Nồi cơm điện” (vai trò cho/tặng)");
        }

        @Test
        @DisplayName("Người dùng chưa có giao dịch nào trả về accuracyRate = null và lịch sử rỗng")
        void getMyReputation_noTransactions_returnsNullAccuracyRate() {
            Long userId = 2L;
            UserReputation user = new UserReputation(userId, (short) 5, 0, Rank.BRONZE);
            when(userReputationRepository.findById(userId)).thenReturn(Optional.of(user));
            when(userReputationRepository.calculateAsDescribedRate(userId)).thenReturn(null);
            when(activityLogQueryService.search(eq(userId), eq("POINTS"), any(), any(), eq(0), eq(20)))
                    .thenReturn(Page.empty());

            MyReputationResponse res = service.getMyReputation(userId, 0, 20);

            assertThat(res.totalPoints()).isEqualTo(0);
            assertThat(res.rank()).isEqualTo(Rank.BRONZE);
            assertThat(res.trustStars()).isEqualTo(5);
            assertNull(res.accuracyRate());
            assertThat(res.history().getContent()).isEmpty();
        }

        @Test
        @DisplayName("Không tìm thấy người dùng ném BusinessException USER_NOT_FOUND")
        void getMyReputation_userNotFound_throwsException() {
            Long userId = 999L;
            when(userReputationRepository.findById(userId)).thenReturn(Optional.empty());

            BusinessException ex = assertThrows(BusinessException.class, () -> service.getMyReputation(userId, 0, 20));
            assertThat(ex.getErrorCode()).isEqualTo(UserErrorCode.USER_NOT_FOUND);
        }

        @Test
        @DisplayName("userId là null ném IllegalArgumentException")
        void getMyReputation_nullUserId_throwsException() {
            assertThrows(IllegalArgumentException.class, () -> service.getMyReputation(null, 0, 20));
        }
    }

    @Nested
    @DisplayName("5. Chống cày điểm, trần điểm mỗi ngày và hồi sao (FR-94, FR-98, FR-99, Issue #150)")
    class AntiFarmingTests {

        private static final long REQUEST_ID = 601L;
        private static final long DONOR_ID = 1L;
        private static final long RECEIVER_ID = 2L;

        private UserReputation donor;
        private UserReputation receiver;

        @BeforeEach
        void givenCompletedGiveRequest() {
            donor = new UserReputation(DONOR_ID, (short) 5, 20, Rank.BRONZE);
            receiver = new UserReputation(RECEIVER_ID, (short) 5, 10, Rank.BRONZE);
            when(userReputationRepository.isReputationAppliedForRequest(REQUEST_ID))
                    .thenReturn(false);
            when(userReputationRepository.findRequestParticipant(REQUEST_ID))
                    .thenReturn(createParticipant(DONOR_ID, RECEIVER_ID, "GIVE"));
            when(userReputationRepository.findByIdForUpdate(DONOR_ID)).thenReturn(Optional.of(donor));
            when(userReputationRepository.findByIdForUpdate(RECEIVER_ID)).thenReturn(Optional.of(receiver));
        }

        @Test
        @DisplayName("Giao dịch thứ 3 giữa cùng cặp trong 90 ngày: cộng 0 điểm, vẫn ghi POINTS kèm lý do")
        void onCompleted_thirdTransactionBetweenSamePair_grantsNoPoints() {
            when(userReputationRepository.countCompletedBetween(
                            DONOR_ID, RECEIVER_ID, NOW.minus(Duration.ofDays(90)), REQUEST_ID))
                    .thenReturn(2L);

            service.onCompleted(REQUEST_ID);

            assertThat(donor.getTotalPoints()).isEqualTo(20);
            assertThat(receiver.getTotalPoints()).isEqualTo(10);
            // Không cộng điểm nhưng vẫn là giao dịch sạch
            assertThat(donor.getCleanStreak()).isEqualTo(1);
            assertThat(receiver.getCleanStreak()).isEqualTo(1);

            @SuppressWarnings("unchecked")
            ArgumentCaptor<Map<String, Object>> data = ArgumentCaptor.forClass(Map.class);
            ArgumentCaptor<String> reason = ArgumentCaptor.forClass(String.class);
            verify(activityLogService)
                    .log(eq("POINTS"), eq(DONOR_ID), eq("REQUEST"), eq(REQUEST_ID), data.capture(), reason.capture());
            assertThat(data.getValue())
                    .containsEntry("points", 0)
                    .containsEntry("basePoints", 10)
                    .containsEntry("pairTransactionNo", 3L)
                    .containsEntry("itemId", ITEM_ID); // để nhật ký Admin hiện tên món
            // Lý do gọi giao dịch bằng tên món, không có "#id"
            assertThat(reason.getValue())
                    .isEqualTo("Không cộng điểm giao dịch món “Nồi cơm điện” (vai trò cho/tặng): giao dịch thứ 3"
                            + " giữa cùng hai người trong 90 ngày")
                    .doesNotContain("#");
            verify(activityLogService)
                    .log(eq("POINTS"), eq(RECEIVER_ID), eq("REQUEST"), eq(REQUEST_ID), any(Map.class), anyString());
        }

        @Test
        @DisplayName("Giao dịch thứ 2 giữa cùng cặp vẫn được cộng điểm đủ")
        void onCompleted_secondTransactionBetweenSamePair_grantsFullPoints() {
            when(userReputationRepository.countCompletedBetween(
                            DONOR_ID, RECEIVER_ID, NOW.minus(Duration.ofDays(90)), REQUEST_ID))
                    .thenReturn(1L);

            service.onCompleted(REQUEST_ID);

            assertThat(donor.getTotalPoints()).isEqualTo(30);
            assertThat(receiver.getTotalPoints()).isEqualTo(12);
        }

        @Test
        @DisplayName("Chạm trần 30 điểm mỗi ngày: chỉ cộng phần còn lại và ghi lý do")
        void onCompleted_dailyCapAlmostReached_grantsOnlyRemainingPoints() {
            donor.claimDailyPoints(25, 30, TODAY_VN);

            service.onCompleted(REQUEST_ID);

            assertThat(donor.getTotalPoints()).isEqualTo(25); // 20 + 5, không phải 20 + 10
            assertThat(donor.getPointsToday()).isEqualTo(30);
            ArgumentCaptor<String> reason = ArgumentCaptor.forClass(String.class);
            verify(activityLogService)
                    .log(eq("POINTS"), eq(DONOR_ID), eq("REQUEST"), eq(REQUEST_ID), any(Map.class), reason.capture());
            assertThat(reason.getValue())
                    .isEqualTo("Cộng 5/10 điểm hoàn tất giao dịch món “Nồi cơm điện” (vai trò cho/tặng): chạm trần 30"
                            + " điểm trong ngày");
        }

        @Test
        @DisplayName("Đã đạt trần trong ngày: cộng 0 điểm")
        void onCompleted_dailyCapReached_grantsNoPoints() {
            donor.claimDailyPoints(30, 30, TODAY_VN);

            service.onCompleted(REQUEST_ID);

            assertThat(donor.getTotalPoints()).isEqualTo(20);
            assertThat(donor.getPointsToday()).isEqualTo(30);
        }

        @Test
        @DisplayName("Sang ngày mới theo giờ VN (UTC vẫn là hôm trước): points_today đếm lại từ 0")
        void onCompleted_newDayInVietnam_resetsPointsToday() {
            // Đã đạt trần ngày 21/10; NOW là 01:00 ngày 22/10 giờ VN nhưng vẫn là ngày 21/10 theo UTC
            donor.claimDailyPoints(30, 30, TODAY_VN.minusDays(1));

            service.onCompleted(REQUEST_ID);

            assertThat(donor.getTotalPoints()).isEqualTo(30);
            assertThat(donor.getPointsToday()).isEqualTo(10);
            assertThat(donor.getPointsDate()).isEqualTo(TODAY_VN);
        }

        @Test
        @DisplayName("Đủ 10 giao dịch sạch liên tiếp khi chưa đủ 5 sao: hồi 1 sao, chuỗi về 0, ghi STAR_RESTORED")
        void onCompleted_tenthCleanTransaction_restoresOneStar() {
            donor.setTrustStars((short) 3);
            donor.setCleanStreak(9);

            service.onCompleted(REQUEST_ID);

            assertThat(donor.getTrustStars()).isEqualTo(4);
            assertThat(donor.getCleanStreak()).isZero();
            verify(activityLogService)
                    .log(
                            eq("STAR_RESTORED"),
                            isNull(),
                            eq("USER"),
                            eq(DONOR_ID),
                            eq(Map.of("before", 3, "after", 4, "cleanStreak", 10)),
                            eq("Hồi 1 sao sau 10 giao dịch sạch liên tiếp"));
        }

        @Test
        @DisplayName("Đủ 10 giao dịch sạch nhưng đang 5 sao: không hồi thêm")
        void onCompleted_streakReachedAtFiveStars_doesNotRestore() {
            donor.setCleanStreak(9);

            service.onCompleted(REQUEST_ID);

            assertThat(donor.getTrustStars()).isEqualTo(5);
            assertThat(donor.getCleanStreak()).isEqualTo(10);
            verify(activityLogService, never())
                    .log(eq("STAR_RESTORED"), any(), anyString(), anyLong(), any(Map.class), anyString());
        }

        @Test
        @DisplayName("Bị trừ sao thì mất chuỗi giao dịch sạch")
        void deductStar_resetsCleanStreak() {
            donor.setCleanStreak(7);

            service.deductStar(DONOR_ID, 1, "Vi phạm đã xác minh");

            assertThat(donor.getTrustStars()).isEqualTo(4);
            assertThat(donor.getCleanStreak()).isZero();
        }
    }

    @Nested
    @DisplayName("6. Cấp sao ân hạn (grantGraceStar - FR-95, Issue #150)")
    class GraceStarTests {

        private static final long ADMIN_ID = 99L;
        private static final long USER_ID = 7L;

        @Test
        @DisplayName("Người dùng 0 sao chưa từng được cấp: lên 1 sao, đánh dấu đã dùng, ghi GRACE_STAR")
        void grantGraceStar_userAtZeroStars_grantsOneStar() {
            UserReputation user = new UserReputation(USER_ID, (short) 0, 40, Rank.BRONZE);
            when(userReputationRepository.findByIdForUpdate(USER_ID)).thenReturn(Optional.of(user));

            GraceStarResponse response = service.grantGraceStar(ADMIN_ID, USER_ID, "Đã giải trình");

            assertThat(response).isEqualTo(new GraceStarResponse(USER_ID, 1, true));
            assertThat(user.getTrustStars()).isEqualTo(1);
            assertThat(user.isGraceStarUsed()).isTrue();
            verify(userReputationRepository).save(user);
            verify(activityLogService)
                    .log(
                            "GRACE_STAR",
                            ADMIN_ID,
                            "USER",
                            USER_ID,
                            Map.<String, Object>of("before", 0, "after", 1),
                            "Đã giải trình");
        }

        @Test
        @DisplayName("Về 0 sao lần hai rồi xin cấp lại: 409 REPUTATION_GRACE_STAR_ALREADY_USED")
        void grantGraceStar_secondTime_throwsAlreadyUsed() {
            UserReputation user = new UserReputation(USER_ID, (short) 0, 40, Rank.BRONZE);
            when(userReputationRepository.findByIdForUpdate(USER_ID)).thenReturn(Optional.of(user));
            service.grantGraceStar(ADMIN_ID, USER_ID, "Lần đầu");
            service.deductStar(USER_ID, 1, "Tái phạm");

            BusinessException ex =
                    assertThrows(BusinessException.class, () -> service.grantGraceStar(ADMIN_ID, USER_ID, "Lần hai"));

            assertThat(ex.getErrorCode()).isEqualTo(ReputationErrorCode.REPUTATION_GRACE_STAR_ALREADY_USED);
            assertThat(ex.getErrorCode().status().value()).isEqualTo(409);
            assertThat(user.getTrustStars()).isZero();
        }

        @Test
        @DisplayName("Người dùng vẫn còn sao: 409 REPUTATION_GRACE_STAR_NOT_ELIGIBLE, không ghi log")
        void grantGraceStar_userStillHasStars_throwsNotEligible() {
            UserReputation user = new UserReputation(USER_ID, (short) 2, 40, Rank.BRONZE);
            when(userReputationRepository.findByIdForUpdate(USER_ID)).thenReturn(Optional.of(user));

            BusinessException ex = assertThrows(
                    BusinessException.class, () -> service.grantGraceStar(ADMIN_ID, USER_ID, "Xin thêm sao"));

            assertThat(ex.getErrorCode()).isEqualTo(ReputationErrorCode.REPUTATION_GRACE_STAR_NOT_ELIGIBLE);
            assertThat(user.isGraceStarUsed()).isFalse();
            verify(userReputationRepository, never()).save(any());
            verify(activityLogService, never())
                    .log(anyString(), any(), anyString(), anyLong(), any(Map.class), anyString());
        }

        @Test
        @DisplayName("Không tìm thấy người dùng: 404 USER_NOT_FOUND")
        void grantGraceStar_userNotFound_throwsUserNotFound() {
            when(userReputationRepository.findByIdForUpdate(USER_ID)).thenReturn(Optional.empty());

            BusinessException ex = assertThrows(
                    BusinessException.class, () -> service.grantGraceStar(ADMIN_ID, USER_ID, "Đã giải trình"));

            assertThat(ex.getErrorCode()).isEqualTo(UserErrorCode.USER_NOT_FOUND);
        }
    }

    @Nested
    @DisplayName("7. Cặp giao dịch lặp cho cảnh báo Admin (findRepeatedPairs - FR-113, Issue #148)")
    class RepeatedPairsTests {

        @Test
        @DisplayName("Đổi từng dòng của native query thành RepeatedPair, đọc được thời gian dạng OffsetDateTime")
        void findRepeatedPairs_mapsRowsToRepeatedPairs() {
            Instant since = NOW.minus(Duration.ofDays(90));
            Object[] row = {3L, 7L, 6L, OffsetDateTime.parse("2026-10-20T15:00:00+07:00")};
            when(userReputationRepository.findRepeatedPairs(since, 5L)).thenReturn(List.<Object[]>of(row));

            assertThat(service.findRepeatedPairs(since, 5))
                    .containsExactly(new RepeatedPair(3L, 7L, 6, Instant.parse("2026-10-20T08:00:00Z")));
        }
    }

    private RequestParticipantProjection createParticipant(Long donorId, Long receiverId, String type) {
        return new RequestParticipantProjection() {
            @Override
            public Long getDonorId() {
                return donorId;
            }

            @Override
            public Long getReceiverId() {
                return receiverId;
            }

            @Override
            public String getType() {
                return type;
            }

            @Override
            public String getStatus() {
                return "COMPLETED";
            }

            @Override
            public Long getItemId() {
                return ITEM_ID;
            }

            @Override
            public String getItemTitle() {
                return "Nồi cơm điện";
            }
        };
    }
}
