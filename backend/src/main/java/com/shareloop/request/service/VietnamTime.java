package com.shareloop.request.service;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/** Định dạng giờ trong câu thông báo của module request, theo múi giờ Việt Nam, ví dụ "14:30 31/10". */
final class VietnamTime {

    private static final DateTimeFormatter FORMAT =
            DateTimeFormatter.ofPattern("HH:mm dd/MM").withZone(ZoneId.of("Asia/Ho_Chi_Minh"));

    private VietnamTime() {}

    private static final DateTimeFormatter DATE_FORMAT =
            DateTimeFormatter.ofPattern("dd/MM").withZone(ZoneId.of("Asia/Ho_Chi_Minh"));

    /** Chỉ ngày, ví dụ "31/10". */
    static String formatDate(Instant instant) {
        return DATE_FORMAT.format(instant);
    }

    static String format(Instant instant) {
        return FORMAT.format(instant);
    }
}
