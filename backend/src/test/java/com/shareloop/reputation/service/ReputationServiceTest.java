package com.shareloop.reputation.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

import com.shareloop.reputation.dto.ReputationSummary;
import com.shareloop.reputation.entity.Rank;
import com.shareloop.reputation.entity.UserReputation;
import com.shareloop.reputation.repository.UserReputationRepository;
import com.shareloop.setting.service.ConfigService;
import java.math.BigDecimal;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ReputationServiceTest {

    @Mock
    private ConfigService configService;

    @Mock
    private UserReputationRepository userReputationRepository;

    private ReputationService service;

    @BeforeEach
    void setUp() {
        when(configService.getDecimal("REPUTATION_SILVER_MIN")).thenReturn(new BigDecimal("100"));
        when(configService.getDecimal("REPUTATION_GOLD_MIN")).thenReturn(new BigDecimal("300"));
        when(configService.getDecimal("REPUTATION_DIAMOND_MIN")).thenReturn(new BigDecimal("600"));

        service = new ReputationService(configService, userReputationRepository);
    }

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

    @Test
    void onCompletedDoesNotThrow() {
        service.onCompleted(100L);
        service.onCompleted(Long.valueOf(200L));
    }

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
