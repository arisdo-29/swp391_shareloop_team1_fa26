-- Dữ liệu demo, chỉ chạy qua DemoDataSeeder (profile demo); KHÔNG nằm trong db/migration.
-- Mọi câu lệnh đều idempotent: chạy lại không tạo trùng. Bốn tài khoản demo đã được DemoDataSeeder tạo trước đó.

-- Khu vực cho người dùng demo (chỉ điền khi còn trống)
UPDATE users SET area_id = (SELECT id FROM areas WHERE name = 'Quận 1')
WHERE email LIKE '%.demo@shareloop.local' AND area_id IS NULL;

-- Credit: mỗi người dùng thường 200 Credit qua một đơn nạp Thành công của cổng giả lập (MOCK), để báo cáo tài chính
-- khớp: tiền mặt thu = Credit nạp × 1.000 đ (VI-08, VI-09). Không dùng dòng ADMIN_ADJUST (hệ thống không có cộng tay).
UPDATE users SET credit_balance = 200
WHERE email IN ('an.demo@shareloop.local', 'binh.demo@shareloop.local', 'chi.demo@shareloop.local')
  AND NOT EXISTS (SELECT 1 FROM credit_ledger l WHERE l.user_id = users.id AND l.note = 'Demo seed');

INSERT INTO payment_orders
  (user_id, order_code, amount_vnd, credits, vnd_per_credit, provider, provider_txn_id, status,
   signature_verified, paid_at)
SELECT u.id, 'DEMO-SEED-' || u.id, 200000, 200, 1000, 'MOCK', 'DEMO-SEED-' || u.id, 'SUCCESS', TRUE,
       COALESCE((SELECT min(l.created_at) FROM credit_ledger l WHERE l.user_id = u.id AND l.note = 'Demo seed'), now())
FROM users u
WHERE u.email IN ('an.demo@shareloop.local', 'binh.demo@shareloop.local', 'chi.demo@shareloop.local')
  AND NOT u.is_deleted
  AND NOT EXISTS (SELECT 1 FROM payment_orders o WHERE o.order_code = 'DEMO-SEED-' || u.id);

INSERT INTO credit_ledger (user_id, amount, type, balance_after, payment_order_id, note)
SELECT u.id, 200, 'TOP_UP', 200, o.id, 'Demo seed'
FROM users u
JOIN payment_orders o ON o.order_code = 'DEMO-SEED-' || u.id
WHERE u.email IN ('an.demo@shareloop.local', 'binh.demo@shareloop.local', 'chi.demo@shareloop.local')
  AND NOT EXISTS (SELECT 1 FROM credit_ledger l WHERE l.user_id = u.id AND l.note = 'Demo seed');

-- Dữ liệu seed cũ (trước 16/11) ghi ADMIN_ADJUST: đổi thành dòng Nạp gắn với đơn nạp demo ở trên.
UPDATE credit_ledger l
SET type = 'TOP_UP', payment_order_id = o.id
FROM payment_orders o
WHERE o.order_code = 'DEMO-SEED-' || l.user_id
  AND l.note = 'Demo seed'
  AND l.type = 'ADMIN_ADJUST'
  AND NOT EXISTS (SELECT 1 FROM credit_ledger x WHERE x.payment_order_id = o.id);

-- Đồng bộ cả các đơn demo đã seed trước đây với mốc thời gian của dòng sổ được liên kết.
UPDATE payment_orders o
SET paid_at = l.created_at
FROM credit_ledger l
WHERE l.payment_order_id = o.id AND l.user_id = o.user_id
  AND l.type = 'TOP_UP' AND l.note = 'Demo seed'
  AND o.order_code = 'DEMO-SEED-' || l.user_id AND o.provider = 'MOCK' AND o.status = 'SUCCESS'
  AND o.paid_at IS DISTINCT FROM l.created_at;

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
   donor_schedule_ok_at, receiver_schedule_ok_at, reserved_at, contact_revealed_at)
SELECT i.id, r.id, 'GIVE', 'LOGISTICS_CONFIRMED', 'IN_PERSON', 'Cổng chính Đại học FPT',
       now() + INTERVAL '2 days', now(), now(), now(), now()
FROM items i
JOIN users d ON d.id = i.donor_id AND d.email = 'an.demo@shareloop.local'
JOIN users r ON r.email = 'binh.demo@shareloop.local'
WHERE i.title = 'Demo - Bộ ly thủy tinh (đã chốt lịch)'
  AND NOT EXISTS (SELECT 1 FROM requests q WHERE q.item_id = i.id AND q.receiver_id = r.id);

-- Giao dịch demo tạo trước 16/11 thiếu mốc hiện liên lạc: đã chốt lịch thì phải hiện liên lạc (GN-04).
UPDATE requests q
SET contact_revealed_at = COALESCE(q.receiver_schedule_ok_at, now())
FROM items i
WHERE q.item_id = i.id
  AND i.title = 'Demo - Bộ ly thủy tinh (đã chốt lịch)'
  AND q.status = 'LOGISTICS_CONFIRMED'
  AND q.contact_revealed_at IS NULL;
