-- Chỉ profile dev: 4 bài đã duyệt của tài khoản mẫu An.
-- Bài bị lỗi là một bài GIVE: DEFECTIVE là condition, không phải offer_type.
INSERT INTO items
  (donor_id, category_id, area_id, offer_type, title, description, desired_item,
   condition, defect_note, status, approved_at, expire_at)
VALUES
  (
    (SELECT id FROM users WHERE email = 'an@shareloop.local'),
    (SELECT id FROM item_categories WHERE name = 'Sách, truyện, giáo trình, văn phòng phẩm'),
    (SELECT id FROM areas WHERE name = 'Quận 1'),
    'GIVE', 'Giáo trình Java cơ bản', 'Giáo trình còn nguyên trang, có ghi chú ở vài trang.', NULL,
    'GOOD', NULL, 'APPROVED', now(), now() + INTERVAL '30 days'
  ),
  (
    (SELECT id FROM users WHERE email = 'an@shareloop.local'),
    (SELECT id FROM item_categories WHERE name = 'Đồ gia dụng, nhà bếp'),
    (SELECT id FROM areas WHERE name = 'Quận 3'),
    'GIVE', 'Bộ ly thủy tinh', 'Bộ bốn ly còn sử dụng tốt.', NULL,
    'LIKE_NEW', NULL, 'APPROVED', now(), now() + INTERVAL '30 days'
  ),
  (
    (SELECT id FROM users WHERE email = 'an@shareloop.local'),
    (SELECT id FROM item_categories WHERE name = 'Thể thao, dã ngoại'),
    (SELECT id FROM areas WHERE name = 'Quận 5'),
    'SWAP', 'Vợt cầu lông', 'Vợt còn dùng tốt, muốn đổi lấy sách học ngoại ngữ.',
    'Sách học ngoại ngữ', 'GOOD', NULL, 'APPROVED', now(), now() + INTERVAL '30 days'
  ),
  (
    (SELECT id FROM users WHERE email = 'an@shareloop.local'),
    (SELECT id FROM item_categories WHERE name = 'Điện tử và phụ kiện'),
    (SELECT id FROM areas WHERE name = 'Quận Bình Thạnh'),
    'GIVE', 'Đèn bàn cần sửa công tắc', 'Đèn vẫn sáng nhưng công tắc hoạt động không ổn định.', NULL,
    'DEFECTIVE', 'Công tắc đôi khi không bật được, cần kiểm tra hoặc thay mới.',
    'APPROVED', now(), now() + INTERVAL '30 days'
  );
