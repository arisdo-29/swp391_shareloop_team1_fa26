package com.shareloop.setting;

import java.math.BigDecimal;
import java.util.Set;

/**
 * Các khoá cấu hình nghiệp vụ và giá trị mặc định theo bảng cấu hình SRS Final Report 09/11, mục 5.2. Mỗi khoá có nhóm, tên và mô tả tiếng
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
    POST_FEE_OVER_QUOTA(
            "post_fee_over_quota",
            "10",
            ConfigGroup.FEES,
            "Phí Trao đổi vượt ngưỡng",
            "Số Credit giữ cho bài Trao đổi khi số bài đang hoạt động đã đạt ngưỡng phí thường của hạng."),
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
            "5",
            ConfigGroup.REPUTATION,
            "Ngưỡng phí thường Trao đổi hạng Đồng",
            "Số bài Trao đổi đang hoạt động được đăng với phí thường của hạng Đồng. Nhập -1 nếu không giới hạn."),
    MAX_ACTIVE_ITEMS_SILVER(
            "max_active_items_silver",
            "8",
            ConfigGroup.REPUTATION,
            "Ngưỡng phí thường Trao đổi hạng Bạc",
            "Số bài Trao đổi đang hoạt động được đăng với phí thường của hạng Bạc. Nhập -1 nếu không giới hạn."),
    MAX_ACTIVE_ITEMS_GOLD(
            "max_active_items_gold",
            "15",
            ConfigGroup.REPUTATION,
            "Ngưỡng phí thường Trao đổi hạng Vàng",
            "Số bài Trao đổi đang hoạt động được đăng với phí thường của hạng Vàng. Nhập -1 nếu không giới hạn."),
    MAX_ACTIVE_ITEMS_DIAMOND(
            "max_active_items_diamond",
            "-1",
            ConfigGroup.REPUTATION,
            "Ngưỡng phí thường Trao đổi hạng Kim cương",
            "Số bài Trao đổi đang hoạt động được đăng với phí thường của hạng Kim cương. Nhập -1 nếu không giới hạn."),
    MAX_ACTIVE_GIVE_ITEMS(
            "max_active_give_items",
            "5",
            ConfigGroup.REPUTATION,
            "Số bài Cho tặng tối đa",
            "Số bài Cho tặng đang hiển thị hoặc chờ duyệt tối đa, áp dụng cho mọi hạng. Nhập -1 nếu không giới hạn."),
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
    BOOST_FEE("boost_fee", "5", ConfigGroup.FEES, "Phí đẩy bài", "Số Credit cho mỗi gói đẩy bài, bị trừ ngay khi đẩy."),
    BOOST_DAYS(
            "boost_days",
            "3",
            ConfigGroup.POSTING,
            "Số ngày mỗi gói đẩy bài",
            "Số ngày bài được đẩy lên đầu cho mỗi gói."),
    BOOST_MAX_DAYS(
            "boost_max_days",
            "6",
            ConfigGroup.POSTING,
            "Số ngày đẩy tối đa",
            "Tối đa số ngày đẩy liên tục trong một đợt, tính từ lúc bắt đầu đợt. Mua thêm gói khi đang đẩy không được"
                    + " vượt mốc này."),
    BOOST_COOLDOWN_DAYS(
            "boost_cooldown_days",
            "3",
            ConfigGroup.POSTING,
            "Số ngày nghỉ giữa hai đợt đẩy",
            "Hết một đợt đẩy, bài phải chờ số ngày này mới được đẩy lại."),
    BOOST_MAX_PER_USER(
            "boost_max_per_user",
            "3",
            ConfigGroup.POSTING,
            "Số bài đang đẩy tối đa mỗi người",
            "Số bài của một người được đẩy cùng lúc. Mua thêm gói cho bài đang đẩy không tính là bài mới."),
    BOOST_MAX_ACTIVE(
            "boost_max_active",
            "30",
            ConfigGroup.POSTING,
            "Số suất đẩy toàn hệ thống",
            "Số bài được đẩy cùng lúc trên toàn hệ thống. Hết suất thì bài mới phải chờ có bài hết đẩy."),
    BOOST_ROTATE_SECONDS(
            "boost_rotate_seconds",
            "180",
            ConfigGroup.POSTING,
            "Chu kỳ xoay vòng bài đẩy",
            "Số giây giữa hai lần đổi nhóm bài đẩy hiển thị ở đầu trang danh sách."),
    BOOST_MAX_PER_PAGE(
            "boost_max_per_page",
            "3",
            ConfigGroup.POSTING,
            "Số bài đẩy mỗi trang",
            "Số bài đẩy tối đa hiển thị ở đầu mỗi trang danh sách."),
    BOOST_ENDING_NOTICE_HOURS(
            "boost_ending_notice_hours",
            "24",
            ConfigGroup.POSTING,
            "Báo trước khi hết đẩy",
            "Số giờ trước lúc bài hết đẩy thì gửi thông báo nhắc chủ bài, kèm mốc còn mua thêm gói được."),
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
    AI_DESCRIBE_PER_DAY(
            "ai_describe_per_day",
            "5",
            ConfigGroup.AI,
            "Lượt gợi ý mô tả mỗi ngày",
            "Số lần tạo gợi ý mô tả miễn phí mỗi ngày cho mỗi thành viên."),
    AI_DESCRIBE_TIMEOUT_SECONDS(
            "ai_describe_timeout_seconds",
            "20",
            ConfigGroup.AI,
            "Thời gian chờ gợi ý mô tả",
            "Số giây tối đa chờ AI tạo gợi ý mô tả từ tên món đồ."),
    CHAT_VIOLATION_ALERT(
            "chat_violation_alert",
            "3",
            ConfigGroup.MODERATION,
            "Ngưỡng cảnh báo vi phạm chat",
            "Số tin nhắn vi phạm trong một giao dịch thì cảnh báo Admin."),
    // KD-06: người đăng có số bài đã từng được duyệt không vượt ngưỡng này được xếp trước trong hàng chờ duyệt.
    REVIEW_NEWCOMER_MAX_APPROVED(
            "review_newcomer_max_approved",
            "3",
            ConfigGroup.MODERATION,
            "Ngưỡng người đăng mới",
            "Người đăng có số bài đã từng được duyệt không vượt quá số này được xem là người mới và xếp trước trong"
                    + " hàng chờ duyệt (trong cùng số sao uy tín)."),
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
    REPORT_HIDE_THRESHOLD(
            "report_hide_threshold",
            "3",
            ConfigGroup.MODERATION,
            "Ngưỡng tự ẩn bài bị báo cáo",
            "Số báo cáo đang mở khiến một bài tự động bị ẩn."),
    // L13: trước đây chỉ đọc bằng getRaw và có cả khoá chữ hoa trùng; nay khai báo ở đây để Admin sửa được.
    REPORT_SERIOUS_REASONS(
            "report_serious_reasons",
            "PROHIBITED_ITEM,COUNTERFEIT",
            ConfigGroup.MODERATION,
            "Lý do vi phạm nghiêm trọng",
            "Các mã lý do báo cáo, cách nhau bởi dấu phẩy. Chấp thuận báo cáo có lý do này thì ẩn toàn bộ bài khác của"
                    + " người vi phạm để rà soát. Mã hợp lệ: NOT_AS_DESCRIBED, COUNTERFEIT, PROHIBITED_ITEM, NO_SHOW,"
                    + " WRONG_CONTACT_INFO, HARASSMENT, OTHER.",
            ValueType.CODE_LIST,
            null,
            null),
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
    STUCK_REQUEST_HOURS(
            "stuck_request_hours",
            "48",
            ConfigGroup.TRANSACTION,
            "Ngưỡng giao dịch bị kẹt",
            "Giao dịch quá giờ hẹn hơn số giờ này mà chưa bên nào xác nhận bàn giao thì Admin thấy ở bộ lọc Bị kẹt."),
    // U08 (chốt C4, 28/10): hai hạn này cố định, Admin không xem hay sửa được (xem adminEditable).
    COMPLAINT_WINDOW_HOURS(
            "complaint_window_hours",
            "24",
            ConfigGroup.TRANSACTION,
            "Hạn khiếu nại",
            "Số giờ được khiếu nại sau khi giao dịch hoàn tất."),
    RESPONSE_WINDOW_HOURS(
            "response_window_hours",
            "48",
            ConfigGroup.TRANSACTION,
            "Hạn giải trình",
            "Số giờ bên bị khiếu nại được giải trình, tính từ lúc nhận thông báo."),
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
            "Số giao dịch giữa cùng hai người vượt mức này thì cảnh báo Admin."),
    IP_ALERT_WINDOW_DAYS(
            "ip_alert_window_days",
            "30",
            ConfigGroup.REPUTATION,
            "Số ngày theo dõi cảnh báo IP",
            "Số ngày gần nhất dùng để phát hiện các tài khoản đăng ký cùng địa chỉ IP."),
    IP_ALERT_MIN_ACCOUNTS(
            "ip_alert_min_accounts",
            "3",
            ConfigGroup.REPUTATION,
            "Ngưỡng tài khoản cảnh báo IP",
            "Số tài khoản đăng ký cùng một địa chỉ IP đạt mức này trở lên thì cảnh báo Admin.");

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

    /**
     * Admin có được xem và sửa khoá này ở trang Cấu hình không. Khoá không sửa được vẫn đọc qua ConfigService để con
     * số nghiệp vụ chỉ khai báo một chỗ, nhưng chỉ đổi được bằng migration.
     */
    public boolean adminEditable() {
        return this != COMPLAINT_WINDOW_HOURS && this != RESPONSE_WINDOW_HOURS;
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

    /**
     * Mã được phép cho khoá kiểu {@link ValueType#CODE_LIST}; rỗng với các kiểu khác. Danh sách lý do báo cáo phải khớp
     * enum ReportReason của module report và CHECK của cột reports.reason (có test kiểm khớp).
     */
    public Set<String> allowedCodes() {
        return this == REPORT_SERIOUS_REASONS ? ReportReasonCodes.ALL : Set.of();
    }

    public enum ValueType {
        INTEGER,
        DECIMAL,
        BOOLEAN,
        /** Danh sách mã cách nhau bởi dấu phẩy, mỗi mã thuộc {@link #allowedCodes()}. */
        CODE_LIST
    }

    // Lớp lồng giữ hằng: enum không đọc được field static của chính nó trong lúc khởi tạo các hằng.
    private static final class ReportReasonCodes {
        static final Set<String> ALL = Set.of(
                "NOT_AS_DESCRIBED",
                "COUNTERFEIT",
                "PROHIBITED_ITEM",
                "NO_SHOW",
                "WRONG_CONTACT_INFO",
                "HARASSMENT",
                "OTHER");
    }
}
