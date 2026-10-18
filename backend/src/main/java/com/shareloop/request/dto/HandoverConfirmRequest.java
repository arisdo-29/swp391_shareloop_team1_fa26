package com.shareloop.request.dto;

import java.util.List;

/**
 * DTO yêu cầu xác nhận đã giao / đã nhận đồ (FR-84, FR-85, BR-D02, BR-R05, UC-27, FR-86, BR-T09).
 *
 * @param asDescribed người nhận đánh giá món đồ có đúng như mô tả không (bắt buộc khi là bên nhận)
 * @param notAsDescribedReason lý do không đúng mô tả nếu asDescribed là false
 * @param mediaIds danh sách ID ảnh/video minh chứng khi giao nhận (tuỳ chọn, FR-86, BR-T09)
 */
public record HandoverConfirmRequest(Boolean asDescribed, String notAsDescribedReason, List<Long> mediaIds) {

    public HandoverConfirmRequest(Boolean asDescribed, String notAsDescribedReason) {
        this(asDescribed, notAsDescribedReason, null);
    }
}
