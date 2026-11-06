-- Số giờ báo trước khi bài hết đẩy (thông báo BOOST_ENDING_SOON), để Admin chỉnh được thay vì viết cứng trong code.
INSERT INTO website_attributes (attr_group, attr_key, attr_value, is_active, is_deleted)
VALUES
  ('CONFIG', 'boost_ending_notice_hours', '24', TRUE, FALSE)
ON CONFLICT DO NOTHING;
