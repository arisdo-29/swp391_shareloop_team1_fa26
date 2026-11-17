-- Cấu hình giới hạn báo cáo mới (BC-01).
-- report_max_items_per_owner: số bài tối đa của cùng một chủ bài mà một người được báo cáo trong khoảng thời gian cửa sổ (mặc định: 3).
-- report_window_days: số ngày cửa sổ giới hạn báo cáo bài và thời gian chặn báo cáo lại cùng một người dùng (mặc định: 30).
INSERT INTO website_attributes (attr_group, attr_key, attr_value, is_active, is_deleted)
VALUES
  ('CONFIG', 'report_max_items_per_owner', '3', TRUE, FALSE),
  ('CONFIG', 'report_window_days', '30', TRUE, FALSE)
ON CONFLICT (attr_group, attr_key) WHERE NOT is_deleted DO NOTHING;

