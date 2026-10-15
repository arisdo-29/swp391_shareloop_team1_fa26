-- Seed initial moderation lexicon and the configurable admin review checklist.
INSERT INTO website_attributes (attr_group, attr_key, attr_value, extra, sort_order)
VALUES
  ('BANNED_KEYWORD', 'súng', 'súng', '{"category":"WEAPONS","severity":"BLOCK","matchMode":"WORD","exceptions":["súng nước","súng bắn keo","súng phun sơn","súng bắn đinh"],"scope":"POST"}'::jsonb, 10),
  ('BANNED_KEYWORD', 'cần sa', 'cần sa', '{"category":"DRUGS","severity":"BLOCK","matchMode":"PHRASE","exceptions":[],"scope":"BOTH"}'::jsonb, 20),
  ('BANNED_KEYWORD', 'zalo', 'zalo', '{"category":"CONTACT","severity":"REVIEW","matchMode":"WORD","exceptions":[],"scope":"POST"}'::jsonb, 30),
  ('CHECKLIST', 'ownership_transfer', 'Món đồ có cần sang tên, đăng ký, công chứng để đổi chủ không?', NULL, 10),
  ('CHECKLIST', 'cash_exchange', 'Món đồ có quy đổi trực tiếp ra tiền mặt được không?', NULL, 20),
  ('CHECKLIST', 'food_safety', 'Nếu là thực phẩm: còn nguyên tem, nhìn rõ hạn, hạn còn trên 30 ngày?', NULL, 30),
  ('CHECKLIST', 'restricted_goods', 'Có phải thuốc, thực phẩm chức năng, thiết bị y tế, mỹ phẩm đã mở?', NULL, 40),
  ('CHECKLIST', 'real_photos', 'Ảnh là ảnh thật của đúng món đồ, đủ số lượng, không lấy trên mạng?', NULL, 50),
  ('CHECKLIST', 'contact_info', 'Ảnh hoặc mô tả có thông tin liên hệ, kể cả viết ngầm? (xem cờ AI)', NULL, 60),
  ('CHECKLIST', 'condition_match', 'Tình trạng khai báo có khớp với ảnh? Đồ lỗi đã mô tả lỗi?', NULL, 70),
  ('CHECKLIST', 'category_match', 'Danh mục có đúng với món đồ?', NULL, 80),
  ('CHECKLIST', 'desired_item', 'Bài Trao đổi đã có mô tả món mong muốn?', NULL, 90),
  ('CHECKLIST', 'authenticity', 'Có dấu hiệu hàng nhái, hoặc bật nhãn chính hãng mà chứng từ không khớp?', NULL, 100)
ON CONFLICT (attr_group, attr_key) WHERE NOT is_deleted DO NOTHING;
