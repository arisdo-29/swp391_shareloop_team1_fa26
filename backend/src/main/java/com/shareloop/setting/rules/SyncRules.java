package com.shareloop.setting.rules;

import com.shareloop.setting.ConfigKey;
import java.util.List;

/**
 * Các luật đồng bộ: khi Admin đổi khoá nguồn thì tự tính giá trị mới cho khoá liên quan. Mọi con số cũ lấy từ before
 * (giá trị đang lưu) chứ không từ after, nên chạy lại nhiều vòng vẫn ra cùng kết quả. Muốn thêm luật: viết một method
 * private static rồi thêm vào {@link #all()}.
 */
public final class SyncRules {

    private SyncRules() {}

    public static List<ConfigRule> all() {
        return List.of(SyncRules::boostDays, SyncRules::postFee, SyncRules::postDisplayDays);
    }

    // Giữ nguyên số gói tối đa trong một đợt: 6 ngày với gói 3 ngày là 2 gói, đổi gói thành 4 ngày thì tối đa 8 ngày.
    private static void boostDays(ConfigSnapshot before, ConfigSnapshot after, RuleResult result) {
        if (!after.changedFrom(before, ConfigKey.BOOST_DAYS)) {
            return;
        }
        Integer oldDays = before.getInt(ConfigKey.BOOST_DAYS);
        Integer oldMaxDays = before.getInt(ConfigKey.BOOST_MAX_DAYS);
        Integer newDays = after.getInt(ConfigKey.BOOST_DAYS);
        if (oldDays == null || oldMaxDays == null || newDays == null || oldDays <= 0) {
            return;
        }
        long packages = Math.max(1, Math.round((double) oldMaxDays / oldDays));
        long newMaxDays = packages * newDays;
        result.sync(before, after, ConfigKey.BOOST_MAX_DAYS, String.valueOf(newMaxDays), ConfigKey.BOOST_DAYS);
    }

    private static void postFee(ConfigSnapshot before, ConfigSnapshot after, RuleResult result) {
        if (!after.changedFrom(before, ConfigKey.POST_FEE)) {
            return;
        }
        Integer oldFee = before.getInt(ConfigKey.POST_FEE);
        Integer newFee = after.getInt(ConfigKey.POST_FEE);
        if (oldFee == null || newFee == null) {
            return;
        }
        syncFeeThatEqualedOldPostFee(before, after, result, ConfigKey.EDIT_FEE, oldFee, newFee);
        syncFeeThatEqualedOldPostFee(before, after, result, ConfigKey.RENEW_FEE, oldFee, newFee);
        syncOverQuotaFee(before, after, result, oldFee, newFee);
    }

    // Chỉ kéo theo khi phí đó đang bằng phí đăng cũ: nếu Admin đã cố tình đặt khác thì đó là ý họ, không đụng vào.
    private static void syncFeeThatEqualedOldPostFee(
            ConfigSnapshot before, ConfigSnapshot after, RuleResult result, ConfigKey feeKey, int oldFee, int newFee) {
        Integer currentFee = before.getInt(feeKey);
        if (currentFee == null || currentFee != oldFee) {
            return;
        }
        result.sync(before, after, feeKey, String.valueOf(newFee), ConfigKey.POST_FEE);
    }

    // Giữ tỉ lệ so với phí đăng, làm tròn lên để phí vượt ngưỡng không bao giờ rẻ hơn tỉ lệ cũ.
    private static void syncOverQuotaFee(
            ConfigSnapshot before, ConfigSnapshot after, RuleResult result, int oldFee, int newFee) {
        Integer oldOverQuota = before.getInt(ConfigKey.POST_FEE_OVER_QUOTA);
        if (oldOverQuota == null || oldFee <= 0 || oldOverQuota < 0 || newFee < 0) {
            return;
        }
        long newOverQuota = Math.ceilDiv((long) oldOverQuota * newFee, oldFee);
        result.sync(before, after, ConfigKey.POST_FEE_OVER_QUOTA, String.valueOf(newOverQuota), ConfigKey.POST_FEE);
    }

    // Giữ nguyên khoảng chênh giữa hạng Vàng và mặc định, nhưng không để ngày của hạng Vàng ngắn hơn mặc định.
    private static void postDisplayDays(ConfigSnapshot before, ConfigSnapshot after, RuleResult result) {
        if (!after.changedFrom(before, ConfigKey.POST_DISPLAY_DAYS)) {
            return;
        }
        Integer oldBase = before.getInt(ConfigKey.POST_DISPLAY_DAYS);
        Integer oldGold = before.getInt(ConfigKey.POST_DISPLAY_DAYS_GOLD);
        Integer newBase = after.getInt(ConfigKey.POST_DISPLAY_DAYS);
        if (oldBase == null || oldGold == null || newBase == null) {
            return;
        }
        long newGold = Math.max(newBase, (long) newBase + (oldGold - oldBase));
        result.sync(
                before, after, ConfigKey.POST_DISPLAY_DAYS_GOLD, String.valueOf(newGold), ConfigKey.POST_DISPLAY_DAYS);
    }
}
