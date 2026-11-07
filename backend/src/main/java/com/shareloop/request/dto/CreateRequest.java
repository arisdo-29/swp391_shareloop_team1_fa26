package com.shareloop.request.dto;

/**
 * DTO gửi yêu cầu xin đồ hoặc đề nghị trao đổi đồ (UC-20).
 *
 * @param offeredItemId id bài đăng đem đổi; tuỳ chọn với cả bài GIVE và bài SWAP (CD-01): kèm món thì là đề nghị
 *     trao đổi, để null thì là xin cho không
 */
public record CreateRequest(Long offeredItemId) {}
