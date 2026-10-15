package com.shareloop.reputation.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.shareloop.reputation.entity.Rank;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ReputationServiceTest {

    private final ReputationService service = new ReputationService();

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
}
