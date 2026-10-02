package com.shareloop.request.dto;

/**
 * DTO gửi yêu cầu xin đồ hoặc đề nghị trao đổi đồ (UC-20).
 *
 * @param offeredItemId id bài đăng đem đổi (bắt buộc khi bài gốc là SWAP, null khi là GIVE)
 */
public record CreateRequest(Long offeredItemId) {}
