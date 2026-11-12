/** Nhãn đơn vị trình bày theo ConfigKey của backend, không quy đổi hoặc định dạng giá trị. */
const configUnits: Record<string, string> = {
  vnd_per_credit: 'VND / Credit',
  topup_min_credits: 'Credit',
  post_fee: 'Credit',
  post_fee_over_quota: 'Credit',
  edit_fee: 'Credit',
  renew_fee: 'Credit',
  boost_fee: 'Credit',
  ai_search_fee: 'Credit',
  payment_order_expire_minutes: 'Phút',
  post_display_days: 'Ngày',
  post_display_days_gold: 'Ngày',
  max_active_items_bronze: 'Bài đăng',
  max_active_items_silver: 'Bài đăng',
  max_active_items_gold: 'Bài đăng',
  max_active_items_diamond: 'Bài đăng',
  max_active_give_items: 'Bài đăng',
  boost_days: 'Ngày',
  boost_max_days: 'Ngày',
  boost_cooldown_days: 'Ngày',
  boost_max_per_user: 'Bài đăng',
  boost_max_active: 'Bài đăng',
  boost_max_per_page: 'Bài đăng',
  boost_rotate_seconds: 'Giây',
  boost_ending_notice_hours: 'Giờ',
  ai_free_per_day: 'Lượt / ngày',
  ai_trial_uses: 'Lượt',
  ai_search_timeout_seconds: 'Giây',
  ai_describe_per_day: 'Lượt / ngày',
  ai_describe_timeout_seconds: 'Giây',
  chat_violation_alert: 'Tin nhắn',
  review_newcomer_max_approved: 'Bài đăng',
  item_photos_min: 'Ảnh',
  item_photos_max: 'Ảnh',
  image_max_mb: 'MB',
  report_hide_threshold: 'Báo cáo',
  otp_ttl_minutes: 'Phút',
  otp_max_failed: 'Lần',
  otp_resend_cooldown_seconds: 'Giây',
  pending_account_ttl_hours: 'Giờ',
  accounts_per_ip_24h: 'Tài khoản / 24 giờ',
  phone_change_interval_days: 'Ngày',
  max_pending_requests: 'Yêu cầu',
  request_pending_days: 'Ngày',
  chat_open_days: 'Ngày',
  auto_confirm_days: 'Ngày',
  stuck_request_hours: 'Giờ',
  complaint_window_hours: 'Giờ',
  response_window_hours: 'Giờ',
  daily_points_cap: 'Điểm uy tín / ngày',
  reputation_silver_min: 'Điểm uy tín',
  reputation_gold_min: 'Điểm uy tín',
  reputation_diamond_min: 'Điểm uy tín',
  reputation_give_donor: 'Điểm uy tín',
  reputation_give_receiver: 'Điểm uy tín',
  reputation_swap_each: 'Điểm uy tín',
  reputation_clean_streak_for_star: 'Giao dịch',
  reputation_pair_no_points_from: 'Lần thứ',
  reputation_pair_window_days: 'Ngày',
  reputation_pair_alert_threshold: 'Giao dịch',
  ip_alert_window_days: 'Ngày',
  ip_alert_min_accounts: 'Tài khoản',
};

export function ConfigUnit({ configKey }: { configKey: string }) {
  return configUnits[configKey] ? (
    <span className="settings-unit">{configUnits[configKey]}</span>
  ) : null;
}

export function SettingsSectionHeading({
  title,
  description,
}: {
  title: string;
  description: string;
}) {
  return (
    <header className="settings-section-heading">
      <h2>{title}</h2>
      <p>{description}</p>
    </header>
  );
}
