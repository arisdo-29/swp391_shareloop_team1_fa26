package com.shareloop.item.dto;

/**
 * Hạn mức đăng bài hiện tại của người dùng, cho trang Đăng tin.
 *
 * @param give số bài Cho tặng đang hiển thị hoặc chờ duyệt và hạn mức (-1 là không giới hạn)
 * @param swap số bài Trao đổi đang hiển thị hoặc chờ duyệt và ngưỡng phí thường theo hạng
 * @param rank hạng uy tín hiện tại (BRONZE, SILVER, GOLD, DIAMOND)
 */
public record PostingQuotaResponse(Quota give, Quota swap, String rank, int nextPostFee) {

    /**
     * @param active số bài đang chiếm hạn mức
     * @param limit hạn mức tối đa, -1 là không giới hạn
     */
    public record Quota(long active, int limit) {}
}
