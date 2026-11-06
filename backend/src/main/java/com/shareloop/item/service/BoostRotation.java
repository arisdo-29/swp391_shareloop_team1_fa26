package com.shareloop.item.service;

import java.util.ArrayList;
import java.util.List;

/**
 * Chọn bài đẩy theo vòng xoay: các bài đang đẩy xếp thành một vòng tròn cố định (id tăng dần), mỗi lượt (round) cửa sổ
 * nhìn trượt đi {@code perRound} vị trí, và mỗi người xem (viewerKey) bắt đầu ở một chỗ khác nhau. Nhờ vậy các bài được
 * chia lượt đều, không phụ thuộc ai mua trước hay sau. Hàm thuần: không đọc DB, không đọc giờ, nên test thẳng được.
 */
public final class BoostRotation {

    private BoostRotation() {}

    /**
     * Lấy {@code count} id liên tiếp trên vòng, bắt đầu từ vị trí {@code (round * perRound + viewerKey) mod n}, hết vòng
     * thì quay lại đầu. Vòng rỗng hoặc count ≤ 0 trả danh sách rỗng; count ≥ n trả đủ n id theo thứ tự đã xoay.
     *
     * @param ring id bài đang đẩy, đã sắp theo id tăng dần
     * @param round lượt hiện tại (số giây epoch chia boost_rotate_seconds)
     * @param viewerKey số không âm riêng của người xem
     * @param perRound số vị trí cửa sổ trượt đi sau mỗi lượt
     * @param count số id cần lấy
     */
    public static List<Long> pick(List<Long> ring, long round, long viewerKey, int perRound, int count) {
        int ringSize = ring.size();
        List<Long> picked = new ArrayList<>();
        if (ringSize == 0 || count <= 0) {
            return picked;
        }
        int takeCount = Math.min(count, ringSize);
        int start = startIndex(ringSize, round, viewerKey, perRound);
        for (int offset = 0; offset < takeCount; offset++) {
            picked.add(ring.get((start + offset) % ringSize));
        }
        return picked;
    }

    // Lấy dư từng phần trước khi cộng để không tràn số khi viewerKey hay round rất lớn; floorMod luôn trả số không âm.
    private static int startIndex(int ringSize, long round, long viewerKey, int perRound) {
        long roundPart = Math.floorMod(round, ringSize) * (long) Math.floorMod(perRound, ringSize);
        long viewerPart = Math.floorMod(viewerKey, ringSize);
        return (int) Math.floorMod(roundPart + viewerPart, (long) ringSize);
    }
}
