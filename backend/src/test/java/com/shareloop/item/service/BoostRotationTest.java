package com.shareloop.item.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

/** Test hàm thuần BoostRotation.pick: không cần Spring, không cần DB. */
class BoostRotationTest {

    private static final List<Long> TEN_IDS = List.of(1L, 2L, 3L, 4L, 5L, 6L, 7L, 8L, 9L, 10L);

    @Test
    void pick_whenRingEmpty_returnsEmpty() {
        // given
        List<Long> ring = List.of();

        // when
        List<Long> picked = BoostRotation.pick(ring, 7, 3, 3, 5);

        // then
        assertThat(picked).isEmpty();
    }

    @Test
    void pick_whenCountExceedsRingSize_returnsWholeRingInRotatedOrder() {
        // given: vòng 3 bài, xin 5 bài, bắt đầu ở vị trí (0 * 2 + 1) mod 3 = 1
        List<Long> ring = List.of(10L, 20L, 30L);

        // when
        List<Long> picked = BoostRotation.pick(ring, 0, 1, 2, 5);

        // then: đủ 3 bài, mỗi bài một lần, bắt đầu từ bài thứ hai
        assertThat(picked).containsExactly(20L, 30L, 10L);
    }

    @Test
    void pick_whenWindowPassesEndOfRing_wrapsToStart() {
        // given: vòng 5 bài, bắt đầu ở vị trí (1 * 3 + 0) mod 5 = 3
        List<Long> ring = List.of(1L, 2L, 3L, 4L, 5L);

        // when
        List<Long> picked = BoostRotation.pick(ring, 1, 0, 3, 3);

        // then: lấy 4, 5 rồi quay lại 1
        assertThat(picked).containsExactly(4L, 5L, 1L);
    }

    @Test
    void pick_whenViewerKeyDiffers_returnsDifferentGroup() {
        // given: cùng lượt, hai người xem khác nhau

        // when
        List<Long> firstViewer = BoostRotation.pick(TEN_IDS, 0, 0, 3, 3);
        List<Long> secondViewer = BoostRotation.pick(TEN_IDS, 0, 2, 3, 3);

        // then: người thứ hai bắt đầu lệch 2 vị trí
        assertThat(firstViewer).containsExactly(1L, 2L, 3L);
        assertThat(secondViewer).containsExactly(3L, 4L, 5L);
    }

    @Test
    void pick_whenRoundIncreasesByOne_windowMovesByPerRound() {
        // given: lượt 4 bắt đầu ở (4 * 3) mod 10 = 2

        // when
        List<Long> roundFour = BoostRotation.pick(TEN_IDS, 4, 0, 3, 3);
        List<Long> roundFive = BoostRotation.pick(TEN_IDS, 5, 0, 3, 3);

        // then: sang lượt 5 cửa sổ trượt đúng 3 vị trí, ngay sau nhóm của lượt 4
        assertThat(roundFour).containsExactly(3L, 4L, 5L);
        assertThat(roundFive).containsExactly(6L, 7L, 8L);
    }

    @Test
    void pick_whenViewerKeyAndRoundHuge_doesNotOverflow() {
        // given: số rất lớn, nếu cộng thẳng sẽ tràn long và ra vị trí âm

        // when
        List<Long> picked = BoostRotation.pick(TEN_IDS, Long.MAX_VALUE, Long.MAX_VALUE, 3, 3);

        // then: vẫn ra 3 bài hợp lệ của vòng
        assertThat(picked).hasSize(3).isSubsetOf(TEN_IDS);
    }
}
