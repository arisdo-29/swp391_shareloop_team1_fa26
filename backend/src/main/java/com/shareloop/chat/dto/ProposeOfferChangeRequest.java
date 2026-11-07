package com.shareloop.chat.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Người nhận đề nghị đưa một món của mình vào đổi trong giao dịch Cho đã chọn người (Cho → Trao đổi).
 *
 * @param offeredItemId id món người nhận đem đổi (bắt buộc)
 * @param content lời nhắn kèm theo (tuỳ chọn), qua cùng bộ lọc liên hệ và từ khoá cấm như tin văn bản
 */
public record ProposeOfferChangeRequest(
        @NotNull(message = "Vui lòng chọn món muốn đưa vào trao đổi")
        Long offeredItemId,

        @Size(max = 500, message = "Lời nhắn không được vượt quá 500 ký tự")
        String content) {}
