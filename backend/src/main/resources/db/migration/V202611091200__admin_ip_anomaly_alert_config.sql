-- Cấu hình cảnh báo nhiều tài khoản cùng địa chỉ IP trên màn Giám sát bất thường (FR-113).
-- Mặc định: gom các IP có từ 3 tài khoản trở lên đăng ký trong 30 ngày gần nhất.
INSERT INTO website_attributes (attr_group, attr_key, attr_value, is_active, is_deleted)
VALUES
  ('CONFIG', 'ip_alert_window_days', '30', TRUE, FALSE),
  ('CONFIG', 'ip_alert_min_accounts', '3', TRUE, FALSE)
ON CONFLICT (attr_group, attr_key) WHERE NOT is_deleted DO NOTHING;

