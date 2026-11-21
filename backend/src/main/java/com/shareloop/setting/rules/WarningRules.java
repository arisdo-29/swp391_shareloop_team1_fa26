package com.shareloop.setting.rules;

import com.shareloop.setting.ConfigKey;
import java.util.List;

/**
 * Các luật cảnh báo: không chặn lưu nhưng Admin phải xác nhận đã hiểu. Giống luật lỗi, chỉ cảnh báo khi một khoá của
 * luật vừa bị đổi để không nhắc đi nhắc lại một cấu hình cũ.
 */
public final class WarningRules {

    private WarningRules() {}

    public static List<ConfigRule> all() {
        return List.of(
                WarningRules::boostNoticeAsLongAsOnePackage,
                WarningRules::goldDisplayShorterThanBase,
                WarningRules::feeIsZero);
    }

    // Báo trước bằng hoặc dài hơn một gói đẩy thì người dùng nhận thông báo ngay sau khi vừa mua.
    private static void boostNoticeAsLongAsOnePackage(ConfigSnapshot before, ConfigSnapshot after, RuleResult result) {
        if (!after.changedFrom(before, ConfigKey.BOOST_ENDING_NOTICE_HOURS, ConfigKey.BOOST_DAYS)) {
            return;
        }
        Integer noticeHours = after.getInt(ConfigKey.BOOST_ENDING_NOTICE_HOURS);
        Integer boostDays = after.getInt(ConfigKey.BOOST_DAYS);
        if (noticeHours == null || boostDays == null || noticeHours < boostDays * 24L) {
            return;
        }
        result.addWarning(RuleMessage.warning(
                "%s (%d giờ) bằng hoặc dài hơn thời gian một gói đẩy (%d ngày), người dùng sẽ nhận thông báo ngay sau khi vừa mua gói."
                        .formatted(ConfigKey.BOOST_ENDING_NOTICE_HOURS.label(), noticeHours, boostDays),
                ConfigKey.BOOST_ENDING_NOTICE_HOURS,
                ConfigKey.BOOST_DAYS));
    }

    private static void goldDisplayShorterThanBase(ConfigSnapshot before, ConfigSnapshot after, RuleResult result) {
        if (!after.changedFrom(before, ConfigKey.POST_DISPLAY_DAYS, ConfigKey.POST_DISPLAY_DAYS_GOLD)) {
            return;
        }
        Integer baseDays = after.getInt(ConfigKey.POST_DISPLAY_DAYS);
        Integer goldDays = after.getInt(ConfigKey.POST_DISPLAY_DAYS_GOLD);
        if (baseDays == null || goldDays == null || goldDays >= baseDays) {
            return;
        }
        result.addWarning(RuleMessage.warning(
                "%s (%d) ngắn hơn %s (%d), người dùng hạng cao lại được hiển thị bài ít ngày hơn."
                        .formatted(
                                ConfigKey.POST_DISPLAY_DAYS_GOLD.label(),
                                goldDays,
                                ConfigKey.POST_DISPLAY_DAYS.label(),
                                baseDays),
                ConfigKey.POST_DISPLAY_DAYS_GOLD,
                ConfigKey.POST_DISPLAY_DAYS));
    }

    // Mọi khoá có tên kết thúc bằng _fee; phí 0 nghĩa là thao tác đó miễn phí hoàn toàn.
    private static void feeIsZero(ConfigSnapshot before, ConfigSnapshot after, RuleResult result) {
        for (ConfigKey key : ConfigKey.values()) {
            if (!key.key().endsWith("_fee") || !after.changedFrom(before, key)) {
                continue;
            }
            Integer fee = after.getInt(key);
            if (fee != null && fee == 0) {
                result.addWarning(RuleMessage.warning(
                        "%s được đặt bằng 0, thao tác này sẽ miễn phí hoàn toàn.".formatted(key.label()), key));
            }
        }
    }
}
