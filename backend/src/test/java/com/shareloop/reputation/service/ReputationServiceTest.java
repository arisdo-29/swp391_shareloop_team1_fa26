package com.shareloop.reputation.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.shareloop.reputation.dto.ReputationSummary;
import com.shareloop.reputation.entity.Rank;
import com.shareloop.reputation.entity.UserReputation;
import com.shareloop.reputation.repository.UserReputationRepository;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ReputationServiceTest {

    private final ReputationService service = new ReputationService();
    private final UserReputationRepository userReputationRepository = mock(UserReputationRepository.class);
    private final ReputationService serviceWithRepo = new ReputationService(userReputationRepository);

    @Test
    void getRankReturnsBronzeByDefault() {
        assertThat(service.getRank(1L)).isEqualTo(Rank.BRONZE);
    }

    @ParameterizedTest
    @ValueSource(longs = {0L, -1L, 999999L})
    void getRankReturnsBronzeForAnyUserId(long userId) {
        assertThat(service.getRank(userId)).isEqualTo(Rank.BRONZE);
    }

    @Test
    void getRankReturnsBronzeWhenUserIdIsNull() {
        assertThat(service.getRank(null)).isEqualTo(Rank.BRONZE);
    }

    @Test
    void onCompletedDoesNotThrow() {
        service.onCompleted(100L);
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
        ReputationSummary summary = service.getReputationSummary(123L);

        assertThat(summary.userId()).isEqualTo(123L);
        assertThat(summary.trustStars()).isEqualTo(5);
        assertThat(summary.rank()).isEqualTo(Rank.BRONZE);
        assertThat(summary.asDescribedRate()).isNull();
    }

    @Test
    void getReputationSummary_whenUserFoundInRepo_returnsReputationData() {
        UserReputation userRep = new UserReputation(123L, (short) 4, 150, Rank.SILVER);
        when(userReputationRepository.findById(123L)).thenReturn(Optional.of(userRep));
        when(userReputationRepository.calculateAsDescribedRate(123L)).thenReturn(96.5);

        ReputationSummary summary = serviceWithRepo.getReputationSummary(123L);

        assertThat(summary.userId()).isEqualTo(123L);
        assertThat(summary.trustStars()).isEqualTo(4);
        assertThat(summary.rank()).isEqualTo(Rank.SILVER);
        assertThat(summary.asDescribedRate()).isEqualTo(96.5);
    }

    @Test
    void getReputationSummary_whenUserNotFoundInRepo_returnsDefaultSummary() {
        when(userReputationRepository.findById(999L)).thenReturn(Optional.empty());

        ReputationSummary summary = serviceWithRepo.getReputationSummary(999L);

        assertThat(summary.userId()).isEqualTo(999L);
        assertThat(summary.trustStars()).isEqualTo(5);
        assertThat(summary.rank()).isEqualTo(Rank.BRONZE);
        assertThat(summary.asDescribedRate()).isNull();
    }
}
