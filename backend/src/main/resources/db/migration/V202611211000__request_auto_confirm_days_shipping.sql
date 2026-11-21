-- Hạn tự hoàn tất riêng cho hình thức Giao hàng (GN-09): cần thời gian vận chuyển nên dài hơn gặp trực tiếp.
-- auto_confirm_days_shipping: một bên đã xác nhận mà bên kia im lặng quá số ngày này thì giao dịch tự hoàn tất (mặc định: 7).
INSERT INTO website_attributes (attr_group, attr_key, attr_value, is_active, is_deleted)
VALUES
  ('CONFIG', 'auto_confirm_days_shipping', '7', TRUE, FALSE)
ON CONFLICT (attr_group, attr_key) WHERE NOT is_deleted DO NOTHING;
