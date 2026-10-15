-- Seed initial reject reason templates for admin item review.
INSERT INTO website_attributes (attr_group, attr_key, attr_value, extra, sort_order)
VALUES
  ('REJECT_REASON', 'fake_or_stock_photos', 'Ảnh không phải ảnh thật của món đồ hoặc lấy từ mạng.', NULL, 10),
  ('REJECT_REASON', 'contact_info_included', 'Bài đăng chứa thông tin liên hệ (số điện thoại, email, link liên kết).', NULL, 20),
  ('REJECT_REASON', 'wrong_category', 'Chọn sai danh mục món đồ.', NULL, 30),
  ('REJECT_REASON', 'prohibited_item', 'Món đồ thuộc danh mục hàng cấm hoặc hạn chế chia sẻ.', NULL, 40),
  ('REJECT_REASON', 'inappropriate_content', 'Nội dung hoặc hình ảnh không phù hợp với tiêu chuẩn cộng đồng.', NULL, 50),
  ('REJECT_REASON', 'missing_defect_description', 'Đồ lỗi nhưng chưa mô tả chi tiết lỗi hoặc thiếu ảnh chụp chỗ lỗi.', NULL, 60)
ON CONFLICT (attr_group, attr_key) WHERE NOT is_deleted DO NOTHING;

