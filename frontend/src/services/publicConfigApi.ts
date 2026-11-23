import { http } from './http';

// Contract: docs/api/setting.md (GET /api/v1/configs/public). Backend trả các con số nghiệp vụ người dùng cần biết.
export type PublicConfig = Record<string, number>;

// Giá trị dự phòng khi gọi lỗi: khớp giá trị mặc định đã seed, chỉ để trang vẫn đọc được khi chưa có dữ liệu thật.
export const DEFAULT_PUBLIC_CONFIG: PublicConfig = {
  max_active_give_items: 5,
  max_pending_requests: 5,
  max_active_transactions: 5,
  request_pending_days: 3,
  request_resend_cooldown_hours: 24,
  chat_open_days: 10,
  auto_confirm_days: 3,
  auto_confirm_days_shipping: 7,
  complaint_window_hours: 24,
  response_window_hours: 48,
  report_hide_threshold: 3,
  reputation_clean_streak_for_star: 10,
  star_restore_min_distinct_partners: 5,
  reputation_pair_no_points_from: 3,
  reputation_pair_window_days: 90,
  reputation_give_donor: 10,
  reputation_give_receiver: 2,
  reputation_swap_each: 6,
  daily_points_cap: 30,
  phone_change_interval_days: 30,
  ai_free_per_day: 5,
  ai_search_fee: 2,
  boost_cooldown_days: 3,
  boost_max_per_user: 3,
  boost_max_active: 30,
  boost_max_per_page: 3,
  boost_rotate_seconds: 180,
  post_display_days: 30,
  post_display_days_gold: 45,
};

let cached: Promise<PublicConfig> | null = null;

export const publicConfigApi = {
  /** Một lần gọi cho cả phiên; lỗi thì trả giá trị dự phòng và cho phép gọi lại lần sau. */
  load(): Promise<PublicConfig> {
    if (!cached) {
      cached = http
        .get<PublicConfig>('/api/v1/configs/public')
        .then(({ data }) => ({ ...DEFAULT_PUBLIC_CONFIG, ...data }))
        .catch(() => {
          cached = null;
          return DEFAULT_PUBLIC_CONFIG;
        });
    }
    return cached;
  },
};
