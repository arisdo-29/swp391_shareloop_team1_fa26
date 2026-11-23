package com.shareloop.setting.controller;

import com.shareloop.setting.ConfigKey;
import com.shareloop.setting.service.ConfigService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Các con số nghiệp vụ người dùng cần biết (trang Giới thiệu, câu chữ thông báo), không cần đăng nhập (G1). Chỉ trả danh sách khoá
 * chọn lọc, không trả khoá nội bộ như phí, AI hay từ khoá cấm.
 */
@Tag(name = "public-config-controller", description = "Cấu hình công khai cho người dùng")
@RestController
@RequestMapping("/api/v1/configs")
public class PublicConfigController {

    // Danh sách trắng: thêm khoá nào ở đây là công khai khoá đó.
    static final List<ConfigKey> PUBLIC_KEYS = List.of(
            ConfigKey.MAX_ACTIVE_GIVE_ITEMS,
            ConfigKey.MAX_PENDING_REQUESTS,
            ConfigKey.MAX_ACTIVE_TRANSACTIONS,
            ConfigKey.REQUEST_PENDING_DAYS,
            ConfigKey.REQUEST_RESEND_COOLDOWN_HOURS,
            ConfigKey.CHAT_OPEN_DAYS,
            ConfigKey.AUTO_CONFIRM_DAYS,
            ConfigKey.AUTO_CONFIRM_DAYS_SHIPPING,
            ConfigKey.COMPLAINT_WINDOW_HOURS,
            ConfigKey.RESPONSE_WINDOW_HOURS,
            ConfigKey.CANCEL_REQUEST_RESPONSE_HOURS,
            ConfigKey.REPORT_HIDE_THRESHOLD,
            ConfigKey.REPUTATION_CLEAN_STREAK_FOR_STAR,
            ConfigKey.STAR_RESTORE_MIN_DISTINCT_PARTNERS,
            ConfigKey.REPUTATION_PAIR_NO_POINTS_FROM,
            ConfigKey.REPUTATION_PAIR_WINDOW_DAYS,
            ConfigKey.REPUTATION_GIVE_DONOR,
            ConfigKey.REPUTATION_GIVE_RECEIVER,
            ConfigKey.REPUTATION_SWAP_EACH,
            ConfigKey.DAILY_POINTS_CAP,
            ConfigKey.PHONE_CHANGE_INTERVAL_DAYS,
            ConfigKey.AI_FREE_PER_DAY,
            ConfigKey.AI_SEARCH_FEE,
            ConfigKey.BOOST_COOLDOWN_DAYS,
            ConfigKey.BOOST_MAX_PER_USER,
            ConfigKey.BOOST_MAX_ACTIVE,
            ConfigKey.BOOST_MAX_PER_PAGE,
            ConfigKey.BOOST_ROTATE_SECONDS,
            ConfigKey.POST_DISPLAY_DAYS,
            ConfigKey.POST_DISPLAY_DAYS_GOLD);

    private final ConfigService configService;

    public PublicConfigController(ConfigService configService) {
        this.configService = configService;
    }

    @Operation(
            summary = "Các con số nghiệp vụ công khai",
            description = "Không cần đăng nhập. Trả map khoá cấu hình sang số, chỉ gồm danh sách khoá chọn lọc.")
    @GetMapping("/public")
    public Map<String, Integer> publicConfigs() {
        Map<String, Integer> values = new LinkedHashMap<>();
        for (ConfigKey key : PUBLIC_KEYS) {
            values.put(key.key(), configService.getInt(key));
        }
        return values;
    }
}
