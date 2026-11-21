package com.shareloop.setting.rules;

import com.shareloop.setting.ConfigKey;
import com.shareloop.setting.SettingErrorCode;
import java.util.List;

/**
 * Các luật kiểm tra chặn lưu. Mỗi luật chỉ báo lỗi khi một khoá của nó vừa bị đổi, để dữ liệu cũ đang lệch không chặn
 * những thay đổi không liên quan. Giá trị không phải số thì bỏ qua, validateValue sẽ báo lỗi kiểu.
 */
public final class ErrorRules {

    // -1 nghĩa là không giới hạn; khi so sánh coi như vô cực.
    private static final int UNLIMITED = -1;

    private ErrorRules() {}

    public static List<ConfigRule> all() {
        return List.of(
                ErrorRules::photoRange,
                ErrorRules::boostDaysChain,
                ErrorRules::boostMaxDaysMultiple,
                ErrorRules::boostSlots,
                ErrorRules::activeItemsOrder,
                ErrorRules::rankThresholdsOrder);
    }

    private static void photoRange(ConfigSnapshot before, ConfigSnapshot after, RuleResult result) {
        if (!after.changedFrom(before, ConfigKey.ITEM_PHOTOS_MIN, ConfigKey.ITEM_PHOTOS_MAX)) {
            return;
        }
        Integer min = after.getInt(ConfigKey.ITEM_PHOTOS_MIN);
        Integer max = after.getInt(ConfigKey.ITEM_PHOTOS_MAX);
        if (min == null || max == null || min <= max) {
            return;
        }
        result.addError(violation(
                "%s không được lớn hơn %s."
                        .formatted(ConfigKey.ITEM_PHOTOS_MIN.label(), ConfigKey.ITEM_PHOTOS_MAX.label()),
                ConfigKey.ITEM_PHOTOS_MIN,
                ConfigKey.ITEM_PHOTOS_MAX));
    }

    // Một gói không dài hơn cả đợt, và cả đợt không dài hơn thời gian bài được hiển thị (đợt đẩy không vượt expire_at).
    private static void boostDaysChain(ConfigSnapshot before, ConfigSnapshot after, RuleResult result) {
        if (!after.changedFrom(before, ConfigKey.BOOST_DAYS, ConfigKey.BOOST_MAX_DAYS, ConfigKey.POST_DISPLAY_DAYS)) {
            return;
        }
        Integer boostDays = after.getInt(ConfigKey.BOOST_DAYS);
        Integer maxDays = after.getInt(ConfigKey.BOOST_MAX_DAYS);
        Integer displayDays = after.getInt(ConfigKey.POST_DISPLAY_DAYS);
        if (boostDays == null || maxDays == null || displayDays == null) {
            return;
        }
        if (boostDays > maxDays) {
            result.addError(violation(
                    "%s (%d) không được lớn hơn %s (%d)."
                            .formatted(
                                    ConfigKey.BOOST_DAYS.label(), boostDays, ConfigKey.BOOST_MAX_DAYS.label(), maxDays),
                    ConfigKey.BOOST_DAYS,
                    ConfigKey.BOOST_MAX_DAYS));
        }
        if (maxDays > displayDays) {
            result.addError(violation(
                    "%s (%d) không được lớn hơn %s (%d), vì đợt đẩy không thể dài hơn thời gian bài được hiển thị."
                            .formatted(
                                    ConfigKey.BOOST_MAX_DAYS.label(),
                                    maxDays,
                                    ConfigKey.POST_DISPLAY_DAYS.label(),
                                    displayDays),
                    ConfigKey.BOOST_MAX_DAYS,
                    ConfigKey.POST_DISPLAY_DAYS));
        }
    }

    // BoostService chặn chứ không cắt khi gói cuối vượt mốc, nên số ngày tối đa lệch bội số sẽ có gói không bao giờ mua
    // được.
    private static void boostMaxDaysMultiple(ConfigSnapshot before, ConfigSnapshot after, RuleResult result) {
        if (!after.changedFrom(before, ConfigKey.BOOST_DAYS, ConfigKey.BOOST_MAX_DAYS)) {
            return;
        }
        Integer boostDays = after.getInt(ConfigKey.BOOST_DAYS);
        Integer maxDays = after.getInt(ConfigKey.BOOST_MAX_DAYS);
        if (boostDays == null || maxDays == null || boostDays <= 0 || maxDays % boostDays == 0) {
            return;
        }
        String message = "Số ngày tối đa một đợt đẩy phải là bội số của số ngày mỗi gói (%d).".formatted(boostDays)
                + multipleSuggestion(after, boostDays, maxDays);
        result.addError(violation(message, ConfigKey.BOOST_DAYS, ConfigKey.BOOST_MAX_DAYS));
    }

    // Gợi ý số dưới và số trên gần nhất; bỏ số dưới nếu nhỏ hơn một gói, bỏ số trên nếu vượt số ngày hiển thị bài.
    private static String multipleSuggestion(ConfigSnapshot after, int boostDays, int maxDays) {
        int lower = Math.floorDiv(maxDays, boostDays) * boostDays;
        int upper = lower + boostDays;
        Integer displayDays = after.getInt(ConfigKey.POST_DISPLAY_DAYS);
        boolean lowerAllowed = lower >= boostDays;
        boolean upperAllowed = displayDays == null || upper <= displayDays;
        if (lowerAllowed && upperAllowed) {
            return " Gợi ý: %d hoặc %d".formatted(lower, upper);
        }
        if (lowerAllowed) {
            return " Gợi ý: %d".formatted(lower);
        }
        if (upperAllowed) {
            return " Gợi ý: %d".formatted(upper);
        }
        return "";
    }

    private static void boostSlots(ConfigSnapshot before, ConfigSnapshot after, RuleResult result) {
        checkNotAboveActive(before, after, result, ConfigKey.BOOST_MAX_PER_PAGE);
        checkNotAboveActive(before, after, result, ConfigKey.BOOST_MAX_PER_USER);
    }

    private static void checkNotAboveActive(
            ConfigSnapshot before, ConfigSnapshot after, RuleResult result, ConfigKey limitKey) {
        if (!after.changedFrom(before, limitKey, ConfigKey.BOOST_MAX_ACTIVE)) {
            return;
        }
        Integer limit = after.getInt(limitKey);
        Integer maxActive = after.getInt(ConfigKey.BOOST_MAX_ACTIVE);
        if (limit == null || maxActive == null || limit <= maxActive) {
            return;
        }
        result.addError(violation(
                "%s (%d) không được lớn hơn %s (%d)."
                        .formatted(limitKey.label(), limit, ConfigKey.BOOST_MAX_ACTIVE.label(), maxActive),
                limitKey,
                ConfigKey.BOOST_MAX_ACTIVE));
    }

    // Đồng, Bạc, Vàng, Kim cương phải không giảm dần. -1 (không giới hạn) chỉ hợp lệ khi mọi hạng cao hơn cũng là -1.
    private static void activeItemsOrder(ConfigSnapshot before, ConfigSnapshot after, RuleResult result) {
        List<ConfigKey> tiers = List.of(
                ConfigKey.MAX_ACTIVE_ITEMS_BRONZE,
                ConfigKey.MAX_ACTIVE_ITEMS_SILVER,
                ConfigKey.MAX_ACTIVE_ITEMS_GOLD,
                ConfigKey.MAX_ACTIVE_ITEMS_DIAMOND);
        if (!after.changedFrom(before, tiers.toArray(new ConfigKey[0]))) {
            return;
        }
        for (int index = 0; index < tiers.size() - 1; index++) {
            checkTierPair(after, result, tiers.get(index), tiers.get(index + 1));
        }
    }

    private static void checkTierPair(
            ConfigSnapshot after, RuleResult result, ConfigKey lowerTier, ConfigKey upperTier) {
        Integer lowerValue = after.getInt(lowerTier);
        Integer upperValue = after.getInt(upperTier);
        if (lowerValue == null || upperValue == null || effectiveLimit(lowerValue) <= effectiveLimit(upperValue)) {
            return;
        }
        result.addError(violation(
                "%s không được lớn hơn %s. Giá trị -1 (không giới hạn) chỉ hợp lệ khi mọi hạng cao hơn cũng là -1."
                        .formatted(lowerTier.label(), upperTier.label()),
                lowerTier,
                upperTier));
    }

    private static long effectiveLimit(int value) {
        return value == UNLIMITED ? Long.MAX_VALUE : value;
    }

    // Giữ nguyên mã lỗi và câu chữ cũ của ConfigService.validateRankThresholds (UT-06).
    private static void rankThresholdsOrder(ConfigSnapshot before, ConfigSnapshot after, RuleResult result) {
        if (!after.changedFrom(
                before,
                ConfigKey.REPUTATION_SILVER_MIN,
                ConfigKey.REPUTATION_GOLD_MIN,
                ConfigKey.REPUTATION_DIAMOND_MIN)) {
            return;
        }
        Integer silver = after.getInt(ConfigKey.REPUTATION_SILVER_MIN);
        Integer gold = after.getInt(ConfigKey.REPUTATION_GOLD_MIN);
        Integer diamond = after.getInt(ConfigKey.REPUTATION_DIAMOND_MIN);
        if (silver == null || gold == null || diamond == null || (silver < gold && gold < diamond)) {
            return;
        }
        result.addError(RuleMessage.error(
                SettingErrorCode.SETTING_RANK_THRESHOLD_ORDER.defaultMessage(),
                SettingErrorCode.SETTING_RANK_THRESHOLD_ORDER,
                ConfigKey.REPUTATION_SILVER_MIN,
                ConfigKey.REPUTATION_GOLD_MIN,
                ConfigKey.REPUTATION_DIAMOND_MIN));
    }

    private static RuleMessage violation(String message, ConfigKey... keys) {
        return RuleMessage.error(message, SettingErrorCode.SETTING_CONFIG_RULE_VIOLATION, keys);
    }
}
