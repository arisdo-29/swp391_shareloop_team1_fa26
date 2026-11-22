package com.shareloop.report.service;

import com.shareloop.request.service.RequestService;
import com.shareloop.setting.ConfigKey;
import com.shareloop.setting.service.ConfigService;
import com.shareloop.user.service.UserService;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Quyết định một người báo cáo có "đủ tin cậy" hay không (F2). Báo cáo của người đủ tin cậy mới tính vào ngưỡng tự ẩn bài
 * (report_hide_threshold); báo cáo của tài khoản mới vẫn vào hàng chờ Admin nhưng không tự ẩn được bài.
 *
 * <p>Đủ tin cậy khi tuổi tài khoản từ report_trusted_min_account_days ngày, hoặc đã có ít nhất report_trusted_min_completed
 * giao dịch hoàn tất.
 */
@Service
@Transactional(readOnly = true)
public class ReporterTrustService {

    private final UserService userService;
    private final RequestService requestService;
    private final ConfigService configService;
    private final Clock clock;

    public ReporterTrustService(
            UserService userService, RequestService requestService, ConfigService configService, Clock clock) {
        this.userService = userService;
        this.requestService = requestService;
        this.configService = configService;
        this.clock = clock;
    }

    /** Trong số các người báo cáo cho trước, những ai đủ tin cậy. */
    public Set<Long> findTrusted(Collection<Long> reporterIds) {
        Set<Long> trusted = new HashSet<>();
        if (reporterIds == null || reporterIds.isEmpty()) {
            return trusted;
        }
        Map<Long, Instant> createdAt = userService.findCreatedAt(reporterIds);
        Instant oldEnough =
                clock.instant().minus(Duration.ofDays(configService.getInt(ConfigKey.REPORT_TRUSTED_MIN_ACCOUNT_DAYS)));
        int minCompleted = configService.getInt(ConfigKey.REPORT_TRUSTED_MIN_COMPLETED);
        for (Long reporterId : new HashSet<>(reporterIds)) {
            Instant created = createdAt.get(reporterId);
            boolean oldAccount = created != null && !created.isAfter(oldEnough);
            // Chỉ đếm giao dịch khi tài khoản còn mới, vì tài khoản đủ tuổi đã đủ tin cậy
            if (oldAccount || requestService.countCompletedTransactions(reporterId) >= minCompleted) {
                trusted.add(reporterId);
            }
        }
        return trusted;
    }
}
