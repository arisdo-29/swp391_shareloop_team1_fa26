package com.shareloop.reputation.entity;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

/** F4: chỉ hồi sao khi chuỗi giao dịch sạch có đủ số đối tác khác nhau. */
class UserReputationCleanStreakTest {

    private static final int STREAK_FOR_STAR = 10;
    private static final int MIN_PARTNERS = 5;

    private UserReputation userWithStars(int stars) {
        UserReputation user = new UserReputation();
        user.setTrustStars((short) stars);
        return user;
    }

    @Test
    void recordCleanTransaction_whenTenTransactionsWithSamePartner_doesNotRestoreStar() {
        // given
        UserReputation user = userWithStars(4);

        // when
        boolean restored = false;
        for (int i = 0; i < 10; i++) {
            restored = user.recordCleanTransaction(STREAK_FOR_STAR, MIN_PARTNERS, 7L);
        }

        // then: chuỗi đủ 10 nhưng chỉ có 1 đối tác nên chưa hồi sao
        assertThat(restored).isFalse();
        assertThat(user.getTrustStars()).isEqualTo(4);
        assertThat(user.getCleanStreakPartners()).containsExactly(7L);
    }

    @Test
    void recordCleanTransaction_whenTenTransactionsWithFiveDifferentPartners_restoresStarAndClearsPartners() {
        // given
        UserReputation user = userWithStars(4);

        // when
        boolean restored = false;
        for (int i = 0; i < 10; i++) {
            restored = user.recordCleanTransaction(STREAK_FOR_STAR, MIN_PARTNERS, 1L + (i % 5));
        }

        // then
        assertThat(restored).isTrue();
        assertThat(user.getTrustStars()).isEqualTo(5);
        assertThat(user.getCleanStreak()).isZero();
        assertThat(user.getCleanStreakPartners()).isEmpty();
    }

    @Test
    void resetCleanStreak_whenStarDeducted_clearsPartners() {
        // given
        UserReputation user = userWithStars(4);
        user.recordCleanTransaction(STREAK_FOR_STAR, MIN_PARTNERS, 3L);
        ReflectionTestUtils.setField(user, "cleanStreak", 4);

        // when
        user.resetCleanStreak();

        // then
        assertThat(user.getCleanStreak()).isZero();
        assertThat(user.getCleanStreakPartners()).isEmpty();
    }
}
