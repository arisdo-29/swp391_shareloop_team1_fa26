-- Ngưỡng giờ để Admin lọc giao dịch bị kẹt: quá giờ hẹn hơn 48 giờ mà chưa bên nào xác nhận bàn giao.
INSERT INTO website_attributes (attr_group, attr_key, attr_value)
VALUES ('CONFIG', 'stuck_request_hours', '48')
ON CONFLICT (attr_group, attr_key) WHERE NOT is_deleted DO NOTHING;
