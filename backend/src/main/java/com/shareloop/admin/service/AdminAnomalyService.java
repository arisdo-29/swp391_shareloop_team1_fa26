package com.shareloop.admin.service;

import com.shareloop.admin.dto.AnomalyReportResponse;
import com.shareloop.admin.dto.AnomalyUser;
import com.shareloop.admin.dto.RepeatedPairAnomaly;
import com.shareloop.reputation.dto.RepeatedPair;
import com.shareloop.reputation.service.ReputationService;
import com.shareloop.setting.ConfigKey;
import com.shareloop.setting.service.ConfigService;
import com.shareloop.user.dto.UserBrief;
import com.shareloop.user.service.UserService;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Cảnh báo bất thường cho Admin (FR-113, UC-52, Issue #148). Tính trực tiếp mỗi lần gọi, không lưu bảng (ERD 17 bảng
 * đã chốt).
 *
 * <p>Hiện có cặp giao dịch lặp (BR-U08). Cảnh báo nhiều tài khoản cùng IP chưa làm: cần thêm method vào UserService
 * (module user), đang chờ BE1 đồng ý.
 */
@Service
public class AdminAnomalyService {

    private final ReputationService reputationService;
    private final UserService userService;
    private final ConfigService configService;
    private final Clock clock;

    public AdminAnomalyService(
            ReputationService reputationService, UserService userService, ConfigService configService, Clock clock) {
        this.reputationService = reputationService;
        this.userService = userService;
        this.configService = configService;
        this.clock = clock;
    }

    /**
     * Cặp người dùng có hơn reputation_pair_alert_threshold giao dịch COMPLETED với nhau trong
     * reputation_pair_window_days ngày gần nhất, kèm họ tên và email để Admin đối chiếu.
     */
    @Transactional(readOnly = true)
    public AnomalyReportResponse getAnomalies() {
        Instant now = clock.instant();
        int windowDays = configService.getInt(ConfigKey.REPUTATION_PAIR_WINDOW_DAYS);
        int threshold = configService.getInt(ConfigKey.REPUTATION_PAIR_ALERT_THRESHOLD);

        List<RepeatedPair> pairs =
                reputationService.findRepeatedPairs(now.minus(Duration.ofDays(windowDays)), threshold);
        Map<Long, UserBrief> briefs = pairs.isEmpty()
                ? Map.of()
                : userService.findBriefs(pairs.stream()
                        .flatMap(pair -> Stream.of(pair.userAId(), pair.userBId()))
                        .distinct()
                        .toList());

        List<RepeatedPairAnomaly> repeatedPairs = pairs.stream()
                .map(pair -> new RepeatedPairAnomaly(
                        toAnomalyUser(pair.userAId(), briefs),
                        toAnomalyUser(pair.userBId(), briefs),
                        pair.completedCount(),
                        pair.lastCompletedAt()))
                .toList();
        return new AnomalyReportResponse(now, windowDays, threshold, repeatedPairs);
    }

    // Người dùng đã xoá mềm không có trong map; hiển thị giống trang nhật ký của Admin.
    private static AnomalyUser toAnomalyUser(Long userId, Map<Long, UserBrief> briefs) {
        UserBrief brief = briefs.get(userId);
        return brief != null
                ? new AnomalyUser(userId, brief.fullName(), brief.email())
                : new AnomalyUser(userId, "Người dùng #" + userId, null);
    }
}
