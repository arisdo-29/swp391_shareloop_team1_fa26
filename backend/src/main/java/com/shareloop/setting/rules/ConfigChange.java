package com.shareloop.setting.rules;

import com.shareloop.setting.ConfigKey;

/**
 * Một khoá bị đổi giá trị. causedByKey và note chỉ có khi khoá được đồng bộ theo khoá khác, còn khoá do Admin tự gửi
 * lên thì cả hai đều null.
 */
public record ConfigChange(ConfigKey key, String oldValue, String newValue, ConfigKey causedByKey, String note) {}
