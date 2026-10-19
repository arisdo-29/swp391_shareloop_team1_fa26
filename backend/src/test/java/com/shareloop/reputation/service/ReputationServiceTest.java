package com.shareloop.reputation.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
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
import com.shareloop.reputation.dto.MyReputationResponse;
import com.shareloop.reputation.dto.ReputationSummary;
import com.shareloop.reputation.dto.RequestParticipantProjection;
import com.shareloop.reputation.entity.Rank;
import com.shareloop.reputation.entity.UserReputation;
import com.shareloop.reputation.repository.UserReputationRepository;
import com.shareloop.setting.service.ConfigService;
import com.shareloop.user.UserErrorCode;
import java.math.BigDecimal;
import java.time.Instant;
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

    private ReputationService service;

    @BeforeEach
    void setUp() {
        when(configService.getDecimal("REPUTATION_SILVER_MIN")).thenReturn(new BigDecimal("100"));
        when(configService.getDecimal("REPUTATION_GOLD_MIN")).thenReturn(new BigDecimal("300"));
        when(configService.getDecimal("REPUTATION_DIAMOND_MIN")).thenReturn(new BigDecimal("600"));

        when(configService.getDecimal("REPUTATION_GIVE_DONOR")).thenReturn(new BigDecimal("10"));
        when(configService.getDecimal("REPUTATION_GIVE_RECEIVER")).thenReturn(new BigDecimal("2"));
        when(configService.getDecimal("REPUTATION_SWAP_EACH")).thenReturn(new BigDecimal("6"));

        service = new ReputationService(
                configService,
                userReputationRepository,
                activityLogService,
                activityLogQueryService,
                itemService,
                notificationService);
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
            when(userReputationRepository.calculateAsDescribedRate(123L)).thenReturn(96.5);

            ReputationSummary summary = service.getReputationSummary(123L);

            assertThat(summary.userId()).isEqualTo(123L);
            assertThat(summary.trustStars()).isEqualTo(4);
            assertThat(summary.rank()).isEqualTo(Rank.GOLD);
            assertThat(summary.asDescribedRate()).isEqualTo(96.5);
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

            when(configService.getDecimal("REPUTATION_GIVE_DONOR")).thenReturn(new BigDecimal("15"));
            when(configService.getDecimal("REPUTATION_GIVE_RECEIVER")).thenReturn(new BigDecimal("5"));

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
            // Vẫn còn 3 sao, không vô hiệu hóa bài hay gửi thông báo hết sao
            verify(itemService, never()).hideAllOfUser(anyLong());
            verify(notificationService, never())
                    .notify(anyLong(), eq("TRUST_STARS_DEPLETED"), anyString(), anyString(), anyString());
        }

        @Test
        void deductStar_dropsToZero_disablesPostingAndNotifiesExactlyOnce() {
            long userId = 2L;
            UserReputation user = new UserReputation(userId, (short) 1, 100, Rank.SILVER);
            when(userReputationRepository.findByIdForUpdate(userId)).thenReturn(Optional.of(user));
            when(userReputationRepository.findById(userId)).thenReturn(Optional.of(user));

            service.deductStar(userId, 1, "Vi phạm quy định nhiều lần");

            assertThat(user.getTrustStars()).isEqualTo(0);
            assertThat(service.canPost(userId)).isFalse();
            assertThat(service.canRequest(userId)).isFalse();

            // Ẩn toàn bộ bài đăng và gửi thông báo
            verify(itemService, times(1)).hideAllOfUser(userId);
            verify(notificationService, times(1))
                    .notify(eq(userId), eq("TRUST_STARS_DEPLETED"), anyString(), anyString(), anyString());
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

            // Không gọi hideAllOfUser hay notification lần thứ hai
            verify(itemService, never()).hideAllOfUser(anyLong());
            verify(notificationService, never())
                    .notify(anyLong(), eq("TRUST_STARS_DEPLETED"), anyString(), anyString(), anyString());
        }

        @Test
        void deductStar_whenAmountExceedsCurrentStars_capsAtZero() {
            long userId = 4L;
            UserReputation user = new UserReputation(userId, (short) 2, 50, Rank.SILVER);
            when(userReputationRepository.findByIdForUpdate(userId)).thenReturn(Optional.of(user));

            service.deductStar(userId, 5, "Vi phạm nghiêm trọng");

            assertThat(user.getTrustStars()).isEqualTo(0); // 2 - 5 -> 0, không âm
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
                    "Yêu cầu #50",
                    Map.of("points", 10, "totalPoints", 320, "rank", "GOLD"),
                    "Cộng 10 điểm hoàn tất giao dịch #50",
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
            assertThat(res.history().getContent().get(0).reason()).isEqualTo("Cộng 10 điểm hoàn tất giao dịch #50");
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
        };
    }
}
