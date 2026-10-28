-- Script seed dữ liệu demo cho cảnh báo bất thường: cặp người dùng giao dịch lặp (FR-113, BR-U08, Q04)
-- Ngưỡng cảnh báo mặc định: hơn 5 giao dịch COMPLETED trong vòng 90 ngày (ConfigKey: reputation_pair_alert_threshold = 5, reputation_pair_window_days = 90).
-- Script này tạo 6 giao dịch COMPLETED giữa Nguyễn Văn An (an@shareloop.local) và Trần Thị Bình (binh@shareloop.local)
-- trong vòng 30 ngày qua (3 lần An -> Bình, 3 lần Bình -> An).

-- 1. Đảm bảo người dùng mẫu tồn tại
INSERT INTO users (email, password_hash, full_name, phone, is_admin, status, credit_balance)
VALUES
  ('an@shareloop.local', '$2a$10$4Hvp0UWQn/YzJwPc.No2aeXjgNxex0B8YyZgg51Ypgpaf50e29ioS', 'Nguyễn Văn An', '0901000002', FALSE, 'ACTIVE', 100),
  ('binh@shareloop.local', '$2a$10$4Hvp0UWQn/YzJwPc.No2aeXjgNxex0B8YyZgg51Ypgpaf50e29ioS', 'Trần Thị Bình', '0901000003', FALSE, 'ACTIVE', 100)
ON CONFLICT (phone) WHERE status = 'ACTIVE' AND NOT is_deleted DO NOTHING;

-- 2. Dọn sạch dữ liệu demo lặp cũ nếu có (đảm bảo tính idempotent khi chạy lại nhiều lần)
DELETE FROM requests WHERE item_id IN (SELECT id FROM items WHERE title LIKE '%Demo lặp%');
DELETE FROM items WHERE title LIKE '%Demo lặp%';

-- 3. Tạo 6 món đồ mẫu trạng thái TRADED giữa An và Bình
-- 3 bài của An:
INSERT INTO items (donor_id, category_id, area_id, offer_type, title, description, condition, status, approved_at, expire_at)
VALUES
  ((SELECT id FROM users WHERE email = 'an@shareloop.local'),
   (SELECT id FROM item_categories ORDER BY id LIMIT 1),
   (SELECT id FROM areas WHERE level = 2 ORDER BY id LIMIT 1),
   'GIVE', 'Sách lập trình Python cơ bản (Demo lặp 1)', 'Sách còn mới, chia sẻ cho người cần.', 'LIKE_NEW', 'TRADED', now() - INTERVAL '16 days', now() + INTERVAL '14 days'),

  ((SELECT id FROM users WHERE email = 'an@shareloop.local'),
   (SELECT id FROM item_categories ORDER BY id LIMIT 1),
   (SELECT id FROM areas WHERE level = 2 ORDER BY id LIMIT 1),
   'GIVE', 'Giáo trình Cấu trúc dữ liệu & Giải thuật (Demo lặp 3)', 'Giáo trình đại học còn đầy đủ trang.', 'GOOD', 'TRADED', now() - INTERVAL '11 days', now() + INTERVAL '19 days'),

  ((SELECT id FROM users WHERE email = 'an@shareloop.local'),
   (SELECT id FROM item_categories ORDER BY id LIMIT 1),
   (SELECT id FROM areas WHERE level = 2 ORDER BY id LIMIT 1),
   'GIVE', 'Sổ tay ghi chép kẻ ngang A5 (Demo lặp 5)', 'Sổ tay bìa da chưa sử dụng.', 'NEW', 'TRADED', now() - INTERVAL '5 days', now() + INTERVAL '25 days');

-- 3 bài của Bình:
INSERT INTO items (donor_id, category_id, area_id, offer_type, title, description, condition, status, approved_at, expire_at)
VALUES
  ((SELECT id FROM users WHERE email = 'binh@shareloop.local'),
   (SELECT id FROM item_categories ORDER BY id LIMIT 1),
   (SELECT id FROM areas WHERE level = 2 ORDER BY id LIMIT 1),
   'GIVE', 'Bình giữ nhiệt inox 500ml (Demo lặp 2)', 'Bình giữ nhiệt tốt không dùng đến.', 'GOOD', 'TRADED', now() - INTERVAL '13 days', now() + INTERVAL '17 days'),

  ((SELECT id FROM users WHERE email = 'binh@shareloop.local'),
   (SELECT id FROM item_categories ORDER BY id LIMIT 1),
   (SELECT id FROM areas WHERE level = 2 ORDER BY id LIMIT 1),
   'GIVE', 'Hộp cơm giữ nhiệt văn phòng (Demo lặp 4)', 'Hộp cơm 2 tầng, sạch sẽ.', 'LIKE_NEW', 'TRADED', now() - INTERVAL '8 days', now() + INTERVAL '22 days'),

  ((SELECT id FROM users WHERE email = 'binh@shareloop.local'),
   (SELECT id FROM item_categories ORDER BY id LIMIT 1),
   (SELECT id FROM areas WHERE level = 2 ORDER BY id LIMIT 1),
   'GIVE', 'Bộ muỗng đũa inox du lịch (Demo lặp 6)', 'Bộ đồ ăn cá nhân tiện mang đi làm.', 'NEW', 'TRADED', now() - INTERVAL '2 days', now() + INTERVAL '28 days');

-- 4. Tạo 6 giao dịch COMPLETED tương ứng
INSERT INTO requests (item_id, receiver_id, type, status, completed_at)
VALUES
  ((SELECT id FROM items WHERE title = 'Sách lập trình Python cơ bản (Demo lặp 1)'),
   (SELECT id FROM users WHERE email = 'binh@shareloop.local'),
   'GIVE', 'COMPLETED', now() - INTERVAL '15 days'),

  ((SELECT id FROM items WHERE title = 'Bình giữ nhiệt inox 500ml (Demo lặp 2)'),
   (SELECT id FROM users WHERE email = 'an@shareloop.local'),
   'GIVE', 'COMPLETED', now() - INTERVAL '12 days'),

  ((SELECT id FROM items WHERE title = 'Giáo trình Cấu trúc dữ liệu & Giải thuật (Demo lặp 3)'),
   (SELECT id FROM users WHERE email = 'binh@shareloop.local'),
   'GIVE', 'COMPLETED', now() - INTERVAL '10 days'),

  ((SELECT id FROM items WHERE title = 'Hộp cơm giữ nhiệt văn phòng (Demo lặp 4)'),
   (SELECT id FROM users WHERE email = 'an@shareloop.local'),
   'GIVE', 'COMPLETED', now() - INTERVAL '7 days'),

  ((SELECT id FROM items WHERE title = 'Sổ tay ghi chép kẻ ngang A5 (Demo lặp 5)'),
   (SELECT id FROM users WHERE email = 'binh@shareloop.local'),
   'GIVE', 'COMPLETED', now() - INTERVAL '4 days'),

  ((SELECT id FROM items WHERE title = 'Bộ muỗng đũa inox du lịch (Demo lặp 6)'),
   (SELECT id FROM users WHERE email = 'an@shareloop.local'),
   'GIVE', 'COMPLETED', now() - INTERVAL '1 days');
