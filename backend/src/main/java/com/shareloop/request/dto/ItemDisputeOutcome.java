package com.shareloop.request.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

/**
 * Cách xử lý một món của giao dịch khi Admin chấp thuận khiếu nại và giao dịch bị huỷ (GN-14).
 *
 * @param itemId món của giao dịch (món chính hoặc món đổi)
 * @param choice REOPEN mở lại bài, HIDE ẩn bài, TRANSFERRED món đã đổi chủ (ẩn bài, hai bên tự hoàn trả)
 */
@Schema(description = "Cách xử lý một món của giao dịch bị huỷ sau khi chấp thuận khiếu nại")
public record ItemDisputeOutcome(
        @NotNull(message = "Thiếu món cần xử lý") Long itemId,

        @NotNull(message = "Vui lòng chọn cách xử lý cho món")
        Choice choice) {

    /** Ba lựa chọn cho mỗi món: Mở lại, Ẩn, Đã đổi chủ. */
    public enum Choice {
        REOPEN,
        HIDE,
        TRANSFERRED
    }
}
