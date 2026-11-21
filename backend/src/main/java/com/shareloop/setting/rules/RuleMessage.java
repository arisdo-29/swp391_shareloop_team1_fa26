package com.shareloop.setting.rules;

import com.shareloop.setting.ConfigKey;
import com.shareloop.setting.SettingErrorCode;
import java.util.List;

/**
 * Một lỗi hoặc cảnh báo. message là tiếng Việt, dùng label của khoá nên không lộ tên khoá kỹ thuật. keys là các khoá
 * liên quan để giao diện tô đúng ô. errorCode chỉ dùng cho lỗi, để giữ mã cũ như SETTING_RANK_THRESHOLD_ORDER.
 */
public record RuleMessage(String message, List<ConfigKey> keys, SettingErrorCode errorCode) {

    public static RuleMessage error(String message, SettingErrorCode errorCode, ConfigKey... keys) {
        return new RuleMessage(message, List.of(keys), errorCode);
    }

    public static RuleMessage warning(String message, ConfigKey... keys) {
        return new RuleMessage(message, List.of(keys), null);
    }
}
