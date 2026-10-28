package com.shareloop.item.dto;

/**
 * Hạn mức đăng bài hiện tại của người dùng, cho trang Đăng tin.
 *
 * @param give số bài Cho tặng đang hiển thị hoặc chờ duyệt và hạn mức (-1 là không giới hạn)
 * @param swap số bài Trao đổi đang hiển thị hoặc chờ duyệt và hạn mức theo hạng (-1 là không giới hạn)
 * @param rank hạng uy tín hiện tại (BRONZE, SILVER, GOLD, DIAMOND)
 */
public record PostingQuotaResponse(Quota give, Quota swap, String rank) {

    /**
     * @param active số bài đang chiếm hạn mức
     * @param limit hạn mức tối đa, -1 là không giới hạn
     */
    public record Quota(long active, int limit) {}
}
