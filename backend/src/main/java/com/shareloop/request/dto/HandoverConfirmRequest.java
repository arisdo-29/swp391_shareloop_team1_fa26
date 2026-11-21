package com.shareloop.request.dto;

import java.util.List;

/**
 * DTO xác nhận hoàn tất giao nhận (GN-06, GN-08, UC-27, GN-10).
 *
 * @param mediaIds danh sách ID ảnh/video minh chứng khi giao nhận (tuỳ chọn, GN-10)
 */
public record HandoverConfirmRequest(List<Long> mediaIds) {}
