package com.shareloop.setting;

import java.math.BigDecimal;

/**
 * Các khoá cấu hình nghiệp vụ và giá trị mặc định theo SRS v10 (ERD mục 7.10). Mỗi khoá có nhóm, tên và mô tả tiếng
 * Việt để trang Admin hiển thị cho người không biết tên khoá kỹ thuật.
 */
public enum ConfigKey {
    VND_PER_CREDIT(
            "vnd_per_credit",
            "1000",
            ConfigGroup.FEES,
            "Tỉ lệ quy đổi",
            "Số đồng ứng với 1 Credit khi nạp tiền. Mặc định 1.000đ là 1 Credit."),
    TOPUP_MIN_CREDITS(
            "topup_min_credits",
            "10",
            ConfigGroup.FEES,
            "Nạp tối thiểu",
            "Số Credit tối thiểu cho mỗi lần nạp. Số tiền nạp phải chia hết cho tỉ lệ quy đổi."),
    POST_FEE(
            "post_fee",
            "5",
            ConfigGroup.FEES,
            "Phí đăng bài",
            "Số Credit được giữ khi đăng bài và chỉ bị trừ khi Admin duyệt. Bài bị từ chối thì được hoàn lại."),
    POST_DISPLAY_DAYS(
            "post_display_days",
            "30",
            ConfigGroup.POSTING,
            "Số ngày hiển thị bài",
            "Số ngày bài được hiển thị sau khi duyệt hoặc gia hạn."),
    POST_DISPLAY_DAYS_GOLD(
            "post_display_days_gold",
            "45",
            ConfigGroup.REPUTATION,
            "Số ngày hiển thị bài từ hạng Vàng",
            "Số ngày bài được hiển thị sau khi duyệt hoặc gia hạn, áp dụng cho người dùng từ hạng Vàng."),
    MAX_ACTIVE_ITEMS_BRONZE(
            "max_active_items_bronze",
            "3",
            ConfigGroup.REPUTATION,
            "Số bài tối đa hạng Đồng",
            "Số bài đang hiển thị hoặc chờ duyệt tối đa của hạng Đồng. Nhập -1 nếu không giới hạn."),
    MAX_ACTIVE_ITEMS_SILVER(
            "max_active_items_silver",
            "5",
            ConfigGroup.REPUTATION,
            "Số bài tối đa hạng Bạc",
            "Số bài đang hiển thị hoặc chờ duyệt tối đa của hạng Bạc. Nhập -1 nếu không giới hạn."),
    MAX_ACTIVE_ITEMS_GOLD(
            "max_active_items_gold",
            "10",
            ConfigGroup.REPUTATION,
            "Số bài tối đa hạng Vàng",
            "Số bài đang hiển thị hoặc chờ duyệt tối đa của hạng Vàng. Nhập -1 nếu không giới hạn."),
    MAX_ACTIVE_ITEMS_DIAMOND(
            "max_active_items_diamond",
            "-1",
            ConfigGroup.REPUTATION,
            "Số bài tối đa hạng Kim cương",
            "Số bài đang hiển thị hoặc chờ duyệt tối đa của hạng Kim cương. Nhập -1 nếu không giới hạn."),
    EDIT_FEE(
            "edit_fee",
            "5",
            ConfigGroup.FEES,
            "Phí sửa bài",
            "Số Credit cho mỗi lần sửa bài đã được duyệt, tính từ lần sửa thứ hai. Lần sửa đầu sau khi duyệt miễn phí."),
    RENEW_FEE(
            "renew_fee",
            "5",
            ConfigGroup.FEES,
            "Phí gia hạn",
            "Số Credit bị trừ ngay khi gia hạn thêm một kỳ hiển thị cho bài."),
    RENEW_EXPIRED_WINDOW_DAYS(
            "renew_expired_window_days",
            "7",
            ConfigGroup.POSTING,
            "Hạn gia hạn bài quá hạn",
            "Số ngày sau khi bài hết hạn mà vẫn còn gia hạn được."),
    BOOST_FEE("boost_fee", "5", ConfigGroup.FEES, "Phí đẩy bài", "Số Credit cho mỗi gói đẩy bài, bị trừ ngay khi đẩy."),
    BOOST_DAYS(
            "boost_days",
            "3",
            ConfigGroup.POSTING,
            "Số ngày mỗi gói đẩy bài",
            "Số ngày bài được đẩy lên đầu cho mỗi gói."),
    BOOST_MAX_DAYS(
            "boost_max_days",
            "14",
            ConfigGroup.POSTING,
            "Số ngày đẩy tối đa",
            "Tổng thời gian đẩy bài cộng dồn tối đa, tính từ thời điểm hiện tại."),
    BOOST_MAX_PER_PAGE(
            "boost_max_per_page",
            "3",
            ConfigGroup.POSTING,
            "Số bài đẩy tối đa mỗi trang",
            "Số bài đang được đẩy hiển thị ở đầu mỗi trang kết quả."),
    AI_SEARCH_FEE(
            "ai_search_fee",
            "2",
            ConfigGroup.FEES,
            "Phí tìm bằng AI",
            "Số Credit cho mỗi lượt AI trợ lý sau khi hết lượt miễn phí."),
    AI_FREE_PER_DAY(
            "ai_free_per_day",
            "5",
            ConfigGroup.AI,
            "Lượt AI miễn phí mỗi ngày",
            "Số lượt AI trợ lý miễn phí mỗi ngày cho tài khoản đã từng nạp Credit."),
    AI_TRIAL_USES(
            "ai_trial_uses",
            "1",
            ConfigGroup.AI,
            "Lượt AI dùng thử",
            "Số lượt AI trợ lý dùng thử cho tài khoản chưa từng nạp Credit."),
    AI_SEARCH_TIMEOUT_SECONDS(
            "ai_search_timeout_seconds",
            "15",
            ConfigGroup.AI,
            "Thời gian chờ AI tìm kiếm",
            "Số giây chờ AI trợ lý trả lời. Quá thời gian thì hoàn lượt và chuyển sang tìm kiếm thường."),
    CHAT_AI_THRESHOLD(
            "chat_ai_threshold",
            "0.7",
            ConfigGroup.AI,
            "Ngưỡng chặn tin nhắn của AI",
            "Độ tin cậy tối thiểu, từ 0 đến 1, để AI chặn một tin nhắn.",
            ValueType.DECIMAL,
            BigDecimal.ZERO,
            BigDecimal.ONE),
    CHAT_AI_TIMEOUT_SECONDS(
            "chat_ai_timeout_seconds",
            "5",
            ConfigGroup.AI,
            "Thời gian chờ AI kiểm duyệt chat",
            "Số giây chờ AI kiểm duyệt tin nhắn. Nếu AI lỗi thì tin nhắn bị chặn nhưng không tính là vi phạm."),
    ITEM_AI_SCREENING_ENABLED(
            "item_ai_screening_enabled",
            "false",
            ConfigGroup.AI,
            "Bật AI sàng lọc bài đăng",
            "Bật thì bài mới hoặc vừa sửa được AI gắn cờ trước khi vào hàng chờ duyệt. SRS v11 tắt tầng này.",
            ValueType.BOOLEAN,
            null,
            null),
    ITEM_AI_TIMEOUT_SECONDS(
            "item_ai_timeout_seconds",
            "15",
            ConfigGroup.AI,
            "Thời gian chờ AI sàng lọc bài",
            "Số giây chờ AI sàng lọc bài đăng mới."),
    ITEM_AI_RETRIES(
            "item_ai_retries",
            "1",
            ConfigGroup.AI,
            "Số lần thử lại AI sàng lọc bài",
            "Số lần thử lại khi AI sàng lọc bài đăng bị lỗi."),
    CHAT_VIOLATION_ALERT(
            "chat_violation_alert",
            "3",
            ConfigGroup.MODERATION,
            "Ngưỡng cảnh báo vi phạm chat",
            "Số tin nhắn vi phạm trong một giao dịch thì cảnh báo Admin."),
    ITEM_PHOTOS_MIN(
            "item_photos_min",
            "3",
            ConfigGroup.POSTING,
            "Số ảnh tối thiểu mỗi bài",
            "Số ảnh ít nhất người dùng phải tải lên cho mỗi bài đăng."),
    ITEM_PHOTOS_MAX(
            "item_photos_max",
            "8",
            ConfigGroup.POSTING,
            "Số ảnh tối đa mỗi bài",
            "Số ảnh nhiều nhất được tải lên cho mỗi bài đăng."),
    IMAGE_MAX_MB(
            "image_max_mb",
            "5",
            ConfigGroup.POSTING,
            "Dung lượng ảnh tối đa",
            "Dung lượng tối đa của mỗi ảnh, tính bằng MB."),
    MEDIA_ORPHAN_HOURS(
            "media_orphan_hours",
            "24",
            ConfigGroup.POSTING,
            "Thời gian giữ ảnh chưa dùng",
            "Số giờ giữ ảnh đã tải lên nhưng chưa gắn vào bài nào. Quá mốc này ảnh sẽ bị dọn."),
    REVIEW_SLA_HOURS(
            "review_sla_hours",
            "24",
            ConfigGroup.MODERATION,
            "Hạn duyệt bài",
            "Số giờ tối đa một bài được chờ duyệt. Quá mốc này hệ thống cảnh báo Admin."),
    REPORT_HIDE_THRESHOLD(
            "report_hide_threshold",
            "3",
            ConfigGroup.MODERATION,
            "Ngưỡng tự ẩn bài bị báo cáo",
            "Số báo cáo đang mở khiến một bài tự động bị ẩn."),
    OTP_TTL_MINUTES(
            "otp_ttl_minutes", "5", ConfigGroup.ACCOUNT, "Hạn mã OTP", "Số phút mã OTP gửi qua email còn hiệu lực."),
    OTP_MAX_FAILED(
            "otp_max_failed",
            "5",
            ConfigGroup.ACCOUNT,
            "Số lần nhập sai OTP tối đa",
            "Số lần nhập sai mã OTP tối đa trước khi mã bị vô hiệu."),
    OTP_RESEND_COOLDOWN_SECONDS(
            "otp_resend_cooldown_seconds",
            "60",
            ConfigGroup.ACCOUNT,
            "Khoảng cách gửi lại OTP",
            "Số giây tối thiểu giữa hai lần gửi lại mã OTP."),
    PENDING_ACCOUNT_TTL_HOURS(
            "pending_account_ttl_hours",
            "24",
            ConfigGroup.ACCOUNT,
            "Hạn tài khoản chưa xác thực",
            "Số giờ giữ tài khoản chưa xác thực OTP. Quá mốc này tài khoản bị xoá."),
    ACCOUNTS_PER_IP_24H(
            "accounts_per_ip_24h",
            "3",
            ConfigGroup.ACCOUNT,
            "Số tài khoản mới mỗi IP",
            "Số tài khoản tối đa được đăng ký từ một địa chỉ IP trong 24 giờ. Vượt mức này thì chờ Admin xem xét."),
    PHONE_CHANGE_INTERVAL_DAYS(
            "phone_change_interval_days",
            "30",
            ConfigGroup.ACCOUNT,
            "Khoảng cách đổi số điện thoại",
            "Số ngày tối thiểu giữa hai lần đổi số điện thoại."),
    MAX_PENDING_REQUESTS(
            "max_pending_requests",
            "5",
            ConfigGroup.TRANSACTION,
            "Số yêu cầu chờ tối đa",
            "Số yêu cầu đang chờ phản hồi mà một người được gửi cùng lúc."),
    REQUEST_PENDING_DAYS(
            "request_pending_days",
            "3",
            ConfigGroup.TRANSACTION,
            "Hạn yêu cầu chờ phản hồi",
            "Số ngày một yêu cầu được chờ phản hồi. Quá hạn thì yêu cầu tự huỷ."),
    CHAT_OPEN_DAYS(
            "chat_open_days",
            "10",
            ConfigGroup.TRANSACTION,
            "Thời gian mở chat",
            "Số ngày kể từ khi chọn người nhận. Chưa chốt lịch trong thời gian này thì giao dịch bị huỷ và chat đóng."),
    AUTO_CONFIRM_DAYS(
            "auto_confirm_days",
            "3",
            ConfigGroup.TRANSACTION,
            "Hạn tự động hoàn tất",
            "Một bên đã xác nhận mà bên kia im lặng quá số ngày này thì giao dịch tự hoàn tất."),
    COMPLAINT_WINDOW_DAYS(
            "complaint_window_days",
            "7",
            ConfigGroup.TRANSACTION,
            "Hạn khiếu nại",
            "Số ngày được khiếu nại sau khi giao dịch hoàn tất."),
    RESPONSE_WINDOW_DAYS(
            "response_window_days",
            "3",
            ConfigGroup.TRANSACTION,
            "Hạn giải trình",
            "Số ngày bên bị khiếu nại được giải trình."),
    DAILY_POINTS_CAP(
            "daily_points_cap",
            "30",
            ConfigGroup.REPUTATION,
            "Trần điểm mỗi ngày",
            "Số điểm uy tín tối đa một người được cộng trong một ngày."),
    PAYMENT_ORDER_EXPIRE_MINUTES(
            "payment_order_expire_minutes",
            "30",
            ConfigGroup.FEES,
            "Hạn đơn nạp",
            "Số phút một đơn nạp được chờ thanh toán. Quá hạn thì đơn chuyển sang hết hạn."),
    REPUTATION_SILVER_MIN(
            "reputation_silver_min",
            "50",
            ConfigGroup.REPUTATION,
            "Điểm tối thiểu hạng Bạc",
            "Tổng điểm cần có để lên hạng Bạc. Phải nhỏ hơn điểm tối thiểu hạng Vàng."),
    REPUTATION_GOLD_MIN(
            "reputation_gold_min",
            "150",
            ConfigGroup.REPUTATION,
            "Điểm tối thiểu hạng Vàng",
            "Tổng điểm cần có để lên hạng Vàng. Phải lớn hơn hạng Bạc và nhỏ hơn hạng Kim cương."),
    REPUTATION_DIAMOND_MIN(
            "reputation_diamond_min",
            "400",
            ConfigGroup.REPUTATION,
            "Điểm tối thiểu hạng Kim cương",
            "Tổng điểm cần có để lên hạng Kim cương. Phải lớn hơn điểm tối thiểu hạng Vàng."),
    REPUTATION_GIVE_DONOR(
            "reputation_give_donor",
            "10",
            ConfigGroup.REPUTATION,
            "Điểm người cho",
            "Số điểm người cho nhận được khi một giao dịch Cho hoàn tất."),
    REPUTATION_GIVE_RECEIVER(
            "reputation_give_receiver",
            "2",
            ConfigGroup.REPUTATION,
            "Điểm người nhận",
            "Số điểm người nhận được khi một giao dịch Cho hoàn tất."),
    REPUTATION_SWAP_EACH(
            "reputation_swap_each",
            "6",
            ConfigGroup.REPUTATION,
            "Điểm mỗi bên khi trao đổi",
            "Số điểm mỗi bên nhận được khi một giao dịch Trao đổi hoàn tất."),
    REPUTATION_CLEAN_STREAK_FOR_STAR(
            "reputation_clean_streak_for_star",
            "10",
            ConfigGroup.REPUTATION,
            "Số giao dịch sạch để hồi sao",
            "Số giao dịch hoàn tất liên tiếp không vi phạm để được hồi 1 sao, khi người dùng đang dưới 5 sao."),
    REPUTATION_PAIR_NO_POINTS_FROM(
            "reputation_pair_no_points_from",
            "3",
            ConfigGroup.REPUTATION,
            "Giao dịch lặp cặp hết điểm từ lần thứ",
            "Từ giao dịch thứ mấy giữa cùng hai người, trong thời gian tính lặp cặp, thì cả hai không được cộng điểm."),
    REPUTATION_PAIR_WINDOW_DAYS(
            "reputation_pair_window_days",
            "90",
            ConfigGroup.REPUTATION,
            "Số ngày tính lặp cặp",
            "Số ngày gần nhất dùng để đếm các giao dịch giữa cùng hai người."),
    REPUTATION_PAIR_ALERT_THRESHOLD(
            "reputation_pair_alert_threshold",
            "5",
            ConfigGroup.REPUTATION,
            "Ngưỡng cảnh báo lặp cặp",
            "Số giao dịch giữa cùng hai người vượt mức này thì cảnh báo Admin.");

    private final String key;
    private final String defaultValue;
    private final ConfigGroup group;
    private final String label;
    private final String description;
    private final ValueType valueType;
    private final BigDecimal minValue;
    private final BigDecimal maxValue;

    ConfigKey(String key, String defaultValue, ConfigGroup group, String label, String description) {
        this(key, defaultValue, group, label, description, ValueType.INTEGER, null, null);
    }

    ConfigKey(
            String key,
            String defaultValue,
            ConfigGroup group,
            String label,
            String description,
            ValueType valueType,
            BigDecimal minValue,
            BigDecimal maxValue) {
        this.key = key;
        this.defaultValue = defaultValue;
        this.group = group;
        this.label = label;
        this.description = description;
        this.valueType = valueType;
        this.minValue = minValue;
        this.maxValue = maxValue;
    }

    public String key() {
        return key;
    }

    public String getAttrKey() {
        return key;
    }

    public String defaultValue() {
        return defaultValue;
    }

    public int getDefaultValue() {
        return Integer.parseInt(defaultValue);
    }

    public ConfigGroup group() {
        return group;
    }

    /** Tên tiếng Việt ngắn để hiển thị thay cho khoá kỹ thuật. */
    public String label() {
        return label;
    }

    /** Mô tả tiếng Việt: giá trị này dùng để làm gì. */
    public String description() {
        return description;
    }

    public ValueType valueType() {
        return valueType;
    }

    public BigDecimal minValue() {
        return minValue;
    }

    public BigDecimal maxValue() {
        return maxValue;
    }

    public enum ValueType {
        INTEGER,
        DECIMAL,
        BOOLEAN
    }
}
