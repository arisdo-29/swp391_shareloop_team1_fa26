package com.shareloop.request.service;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * Hạn khiếu nại sau khi giao dịch hoàn tất (U08). Số giờ do nơi gọi đọc từ ConfigService
 * (ConfigKey.COMPLAINT_WINDOW_HOURS), không viết cứng ở đây.
 */
final class ComplaintWindow {

    private static final DateTimeFormatter DEADLINE_FORMAT =
            DateTimeFormatter.ofPattern("HH:mm dd/MM").withZone(ZoneId.of("Asia/Ho_Chi_Minh"));

    private ComplaintWindow() {}

    /** Thời điểm hết hạn khiếu nại, null khi giao dịch chưa hoàn tất. */
    static Instant deadline(Instant completedAt, int hours) {
        return completedAt == null ? null : completedAt.plus(Duration.ofHours(hours));
    }

    /** "Bạn có 24 giờ, đến 14:30 31/10, để khiếu nại nếu có vấn đề." Giờ theo múi giờ Việt Nam. */
    static String sentence(int hours, Instant deadline) {
        return "Bạn có " + hours + " giờ, đến " + DEADLINE_FORMAT.format(deadline) + ", để khiếu nại nếu có vấn đề.";
    }
}
