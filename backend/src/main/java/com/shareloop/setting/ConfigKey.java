package com.shareloop.setting;

/** Cấu hình nghiệp vụ được lưu trong website_attributes với attr_group = CONFIG. */
public enum ConfigKey {
    OTP_TTL_MINUTES("otp_ttl_minutes", 5),
    OTP_MAX_FAILED("otp_max_failed", 5),
    OTP_RESEND_COOLDOWN_SECONDS("otp_resend_cooldown_seconds", 60),
    PENDING_ACCOUNT_TTL_HOURS("pending_account_ttl_hours", 24);

    private final String attrKey;
    private final int defaultValue;

    ConfigKey(String attrKey, int defaultValue) {
        this.attrKey = attrKey;
        this.defaultValue = defaultValue;
    }

    public String getAttrKey() {
        return attrKey;
    }

    public int getDefaultValue() {
        return defaultValue;
    }
}
