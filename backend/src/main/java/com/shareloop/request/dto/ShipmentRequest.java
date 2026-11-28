package com.shareloop.request.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

/**
 * DTO báo đã gửi hàng cho chiều của mình (GN-12).
 *
 * @param mediaIds id ảnh biên nhận đã tải lên, bắt buộc với Trao đổi qua giao hàng, lưu riêng tư
 */
@JsonIgnoreProperties("trackingCode")
public record ShipmentRequest(List<Long> mediaIds) {}
