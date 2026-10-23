package com.shareloop.request.dto;

/**
 * Tên món đồ và tên người liên quan của một yêu cầu, để giao diện hiện tên thay vì id. Mọi trường có thể null khi
 * không tra được (bài đã xoá, tài khoản đã xoá).
 *
 * @param itemTitle tiêu đề bài đăng được yêu cầu
 * @param itemImage đường dẫn ảnh đầu tiên của bài đăng, dạng /api/v1/media/{id}
 * @param donorId id chủ bài đăng
 * @param donorName họ tên chủ bài đăng
 * @param offeredItemTitle tiêu đề món người xin đề nghị đổi (chỉ yêu cầu Trao đổi)
 */
public record RequestItemInfo(
        String itemTitle, String itemImage, Long donorId, String donorName, String offeredItemTitle) {

    public static final RequestItemInfo EMPTY = new RequestItemInfo(null, null, null, null, null);
}
