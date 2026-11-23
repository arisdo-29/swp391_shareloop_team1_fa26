package com.shareloop.request.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * DTO báo đã gửi hàng cho chiều của mình (GN-12). Không tra mã vận đơn tự động, chỉ lưu để hai bên đối chiếu.
 *
 * @param trackingCode mã vận đơn, 3 đến 50 ký tự
 * @param mediaIds id ảnh biên nhận đã tải lên (không bắt buộc), lưu riêng tư như ảnh giao nhận
 */
public record ShipmentRequest(
        @NotBlank(message = "Mã vận đơn không được để trống")
        @Size(min = 3, max = 50, message = "Mã vận đơn phải từ 3 đến 50 ký tự")
        String trackingCode,

        List<Long> mediaIds) {}
