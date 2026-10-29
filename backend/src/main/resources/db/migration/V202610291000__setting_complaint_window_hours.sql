-- U08 (chốt C4, 28/10): khiếu nại sau hoàn tất trong 24 giờ, bên bị khiếu nại giải trình trong 48 giờ kể từ lúc
-- nhận thông báo. Hai số cố định: ConfigKey đánh dấu Admin không xem, không sửa được (adminEditable = false).
INSERT INTO website_attributes (attr_group, attr_key, attr_value)
VALUES
  ('CONFIG', 'complaint_window_hours', '24'),
  ('CONFIG', 'response_window_hours', '48')
ON CONFLICT (attr_group, attr_key) WHERE NOT is_deleted DO NOTHING;

-- Khoá cũ tính theo ngày (V202610051200, V202610282000) và khoá viết hoa cũ (V202610181400) không còn đọc ở đâu.
-- Xoá mềm để không còn hai nơi khai báo cùng một hạn.
UPDATE website_attributes
SET is_deleted = TRUE
WHERE attr_group = 'CONFIG'
  AND attr_key IN ('complaint_window_days', 'response_window_days', 'DISPUTE_WINDOW_DAYS', 'DISPUTE_RESPONSE_DAYS')
  AND is_deleted = FALSE;
