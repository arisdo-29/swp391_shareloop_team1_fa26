-- Dữ liệu demo, chỉ chạy qua DemoDataSeeder (profile demo); KHÔNG nằm trong db/migration.
-- Mọi câu lệnh đều idempotent: chạy lại không tạo trùng. Bốn tài khoản demo đã được DemoDataSeeder tạo trước đó.

-- Khu vực cho người dùng demo (chỉ điền khi còn trống)
UPDATE users SET area_id = (SELECT id FROM areas WHERE name = 'Quận 1')
WHERE email LIKE '%.demo@shareloop.local' AND area_id IS NULL;

-- Credit: mỗi người dùng thường 200 Credit, ghi đúng một dòng sổ cái. Số dư chỉ đổi khi chưa có dòng 'Demo seed'.
UPDATE users SET credit_balance = 200
WHERE email IN ('an.demo@shareloop.local', 'binh.demo@shareloop.local', 'chi.demo@shareloop.local')
  AND NOT EXISTS (SELECT 1 FROM credit_ledger l WHERE l.user_id = users.id AND l.note = 'Demo seed');

INSERT INTO credit_ledger (user_id, amount, type, balance_after, note)
SELECT u.id, 200, 'ADMIN_ADJUST', 200, 'Demo seed'
FROM users u
WHERE u.email IN ('an.demo@shareloop.local', 'binh.demo@shareloop.local', 'chi.demo@shareloop.local')
  AND NOT EXISTS (SELECT 1 FROM credit_ledger l WHERE l.user_id = u.id AND l.note = 'Demo seed');

-- Bài đăng: Cho và Trao đổi đang hiển thị (APPROVED), thêm một bài RESERVED thuộc giao dịch đã chốt lịch.
INSERT INTO items
  (donor_id, category_id, area_id, offer_type, title, description, desired_item,
   condition, status, approved_at, expire_at)
SELECT u.id, c.id, a.id, v.offer_type, v.title, v.description, v.desired_item,
       v.condition, v.status, now(), now() + INTERVAL '30 days'
FROM (VALUES
  ('an.demo@shareloop.local', 'Sách, truyện, giáo trình, văn phòng phẩm', 'Quận 1', 'GIVE',
   'Demo - Giáo trình Java cơ bản', 'Giáo trình còn nguyên trang, có ghi chú ở vài trang.',
   NULL, 'GOOD', 'APPROVED'),
  ('an.demo@shareloop.local', 'Thể thao, dã ngoại', 'Quận 5', 'SWAP',
   'Demo - Vợt cầu lông', 'Vợt còn dùng tốt, muốn đổi lấy sách học ngoại ngữ.',
   'Sách học ngoại ngữ', 'GOOD', 'APPROVED'),
  ('binh.demo@shareloop.local', 'Đồ gia dụng, nhà bếp', 'Quận 3', 'GIVE',
   'Demo - Nồi cơm điện nhỏ', 'Nồi 1 lít, còn dùng tốt, tặng bạn cần.',
   NULL, 'LIKE_NEW', 'APPROVED'),
  ('an.demo@shareloop.local', 'Đồ gia dụng, nhà bếp', 'Quận 3', 'GIVE',
   'Demo - Bộ ly thủy tinh (đã chốt lịch)', 'Bộ bốn ly còn sử dụng tốt, đã hẹn người nhận.',
   NULL, 'LIKE_NEW', 'RESERVED')
) AS v(email, category, area, offer_type, title, description, desired_item, condition, status)
JOIN users u ON u.email = v.email AND NOT u.is_deleted
JOIN item_categories c ON c.name = v.category
JOIN areas a ON a.name = v.area
WHERE NOT EXISTS (SELECT 1 FROM items i WHERE i.donor_id = u.id AND i.title = v.title);

-- Giao dịch đã chốt lịch (LOGISTICS_CONFIRMED): Bình nhận bộ ly của An, hẹn gặp trực tiếp.
INSERT INTO requests
  (item_id, receiver_id, type, status, delivery_method, meeting_place, meeting_time,
   donor_schedule_ok_at, receiver_schedule_ok_at, reserved_at)
SELECT i.id, r.id, 'GIVE', 'LOGISTICS_CONFIRMED', 'IN_PERSON', 'Cổng chính Đại học FPT',
       now() + INTERVAL '2 days', now(), now(), now()
FROM items i
JOIN users d ON d.id = i.donor_id AND d.email = 'an.demo@shareloop.local'
JOIN users r ON r.email = 'binh.demo@shareloop.local'
WHERE i.title = 'Demo - Bộ ly thủy tinh (đã chốt lịch)'
  AND NOT EXISTS (SELECT 1 FROM requests q WHERE q.item_id = i.id AND q.receiver_id = r.id);
