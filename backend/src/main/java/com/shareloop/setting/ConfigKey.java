package com.shareloop.setting;

/** Các khoá cấu hình nghiệp vụ và giá trị mặc định theo SRS v10. */
public enum ConfigKey {
    VND_PER_CREDIT("vnd_per_credit", "1000"),
    TOPUP_MIN_CREDITS("topup_min_credits", "10"),
    POST_FEE("post_fee", "5"),
    POST_DISPLAY_DAYS("post_display_days", "30"),
    POST_DISPLAY_DAYS_GOLD("post_display_days_gold", "45"),
    MAX_ACTIVE_ITEMS_BRONZE("max_active_items_bronze", "3"),
    MAX_ACTIVE_ITEMS_SILVER("max_active_items_silver", "5"),
    MAX_ACTIVE_ITEMS_GOLD("max_active_items_gold", "10"),
    MAX_ACTIVE_ITEMS_DIAMOND("max_active_items_diamond", "-1"),
    EDIT_FEE("edit_fee", "5"),
    RENEW_FEE("renew_fee", "5"),
    RENEW_EXPIRED_WINDOW_DAYS("renew_expired_window_days", "7"),
    BOOST_FEE("boost_fee", "5"),
    BOOST_DAYS("boost_days", "3"),
    BOOST_MAX_DAYS("boost_max_days", "14"),
    BOOST_MAX_PER_PAGE("boost_max_per_page", "3"),
    AI_SEARCH_FEE("ai_search_fee", "2"),
    AI_FREE_PER_DAY("ai_free_per_day", "5"),
    AI_TRIAL_USES("ai_trial_uses", "1"),
    AI_SEARCH_TIMEOUT_SECONDS("ai_search_timeout_seconds", "15"),
    CHAT_AI_THRESHOLD("chat_ai_threshold", "0.7"),
    CHAT_AI_TIMEOUT_SECONDS("chat_ai_timeout_seconds", "5"),
    ITEM_AI_TIMEOUT_SECONDS("item_ai_timeout_seconds", "20"),
    ITEM_AI_RETRIES("item_ai_retries", "1"),
    CHAT_VIOLATION_ALERT("chat_violation_alert", "3"),
    ITEM_PHOTOS_MIN("item_photos_min", "3"),
    ITEM_PHOTOS_MAX("item_photos_max", "8"),
    IMAGE_MAX_MB("image_max_mb", "5"),
    MEDIA_ORPHAN_HOURS("media_orphan_hours", "24"),
    REVIEW_SLA_HOURS("review_sla_hours", "24"),
    REPORT_HIDE_THRESHOLD("report_hide_threshold", "3"),
    OTP_TTL_MINUTES("otp_ttl_minutes", "5"),
    OTP_MAX_FAILS("otp_max_fails", "5"),
    OTP_RESEND_SECONDS("otp_resend_seconds", "60"),
    UNVERIFIED_ACCOUNT_HOURS("unverified_account_hours", "24"),
    ACCOUNTS_PER_IP_24H("accounts_per_ip_24h", "3"),
    PHONE_CHANGE_INTERVAL_DAYS("phone_change_interval_days", "30"),
    MAX_PENDING_REQUESTS("max_pending_requests", "5"),
    REQUEST_PENDING_DAYS("request_pending_days", "3"),
    CHAT_OPEN_DAYS("chat_open_days", "10"),
    AUTO_CONFIRM_DAYS("auto_confirm_days", "3"),
    COMPLAINT_WINDOW_DAYS("complaint_window_days", "7"),
    RESPONSE_WINDOW_DAYS("response_window_days", "3"),
    DAILY_POINTS_CAP("daily_points_cap", "30"),
    PAYMENT_ORDER_EXPIRE_MINUTES("payment_order_expire_minutes", "30");

    private final String key;
    private final String defaultValue;

    ConfigKey(String key, String defaultValue) {
        this.key = key;
        this.defaultValue = defaultValue;
    }

    public String key() {
        return key;
    }

    public String defaultValue() {
        return defaultValue;
    }
}
