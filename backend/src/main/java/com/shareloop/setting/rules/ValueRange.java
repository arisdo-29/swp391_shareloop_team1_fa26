package com.shareloop.setting.rules;

import com.shareloop.setting.ConfigKey;
import java.math.BigDecimal;

/** Khoảng giá trị cho phép của một khoá. min hoặc max bằng null nghĩa là không giới hạn phía đó. */
public record ValueRange(BigDecimal min, BigDecimal max) {

    /** Khoảng mặc định: đọc từ minValue và maxValue khai báo trong ConfigKey. */
    public static ValueRange of(ConfigKey key) {
        return new ValueRange(key.minValue(), key.maxValue());
    }

    public boolean contains(BigDecimal value) {
        boolean belowMin = min != null && value.compareTo(min) < 0;
        boolean aboveMax = max != null && value.compareTo(max) > 0;
        return !belowMin && !aboveMax;
    }
}
