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

-- Ba giao dịch demo cho luật huỷ có xác nhận, dời lịch và giao hàng hai chiều (đợt 3).
-- Bài đang nằm trong giao dịch: RESERVED.
INSERT INTO items
  (donor_id, category_id, area_id, offer_type, title, description, desired_item,
   condition, status, approved_at, expire_at)
SELECT u.id, c.id, a.id, v.offer_type, v.title, v.description, v.desired_item,
       v.condition, 'RESERVED', now(), now() + INTERVAL '30 days'
FROM (VALUES
  ('an.demo@shareloop.local', 'Đồ gia dụng, nhà bếp', 'Quận 1', 'GIVE',
   'Demo - Máy xay sinh tố (đang có đề nghị huỷ)', 'Máy xay còn tốt, chủ bài vừa đề nghị huỷ vì bận đột xuất.',
   NULL, 'GOOD'),
  ('binh.demo@shareloop.local', 'Đồ gia dụng, nhà bếp', 'Quận 3', 'GIVE',
   'Demo - Đèn bàn (đã dời lịch một lần)', 'Đèn bàn còn sáng tốt, hai bên đã đồng ý dời lịch một lần.',
   NULL, 'GOOD'),
  ('an.demo@shareloop.local', 'Thể thao, dã ngoại', 'Quận 5', 'SWAP',
   'Demo - Máy ảnh cũ (trao đổi qua giao hàng)', 'Máy ảnh còn chụp tốt, đổi lấy mũ bảo hiểm.',
   'Mũ bảo hiểm', 'GOOD'),
  ('chi.demo@shareloop.local', 'Thể thao, dã ngoại', 'Quận 5', 'SWAP',
   'Demo - Mũ bảo hiểm (đổi qua giao hàng)', 'Mũ bảo hiểm còn mới, đổi lấy máy ảnh.',
   'Máy ảnh', 'LIKE_NEW')
) AS v(email, category, area, offer_type, title, description, desired_item, condition)
JOIN users u ON u.email = v.email AND NOT u.is_deleted
JOIN item_categories c ON c.name = v.category
JOIN areas a ON a.name = v.area
WHERE NOT EXISTS (SELECT 1 FROM items i WHERE i.donor_id = u.id AND i.title = v.title);

-- Gặp trực tiếp, chủ bài An đề nghị huỷ vì bận đột xuất, Chi chưa phản hồi (hạn phản hồi do cấu hình).
INSERT INTO requests
  (item_id, receiver_id, type, status, delivery_method, meeting_place, meeting_time,
   donor_schedule_ok_at, receiver_schedule_ok_at, donor_waiver_at, receiver_waiver_at,
   reserved_at, contact_revealed_at, cancel_requested_by, cancel_requested_at, cancel_request_code)
SELECT i.id, r.id, 'GIVE', 'LOGISTICS_CONFIRMED', 'IN_PERSON', 'Cổng chính Đại học FPT',
       now() + INTERVAL '1 day', now(), now(), now(), now(), now(), now(), d.id, now() - INTERVAL '1 hour', 'BUSY'
FROM items i
JOIN users d ON d.id = i.donor_id AND d.email = 'an.demo@shareloop.local'
JOIN users r ON r.email = 'chi.demo@shareloop.local'
WHERE i.title = 'Demo - Máy xay sinh tố (đang có đề nghị huỷ)'
  AND NOT EXISTS (SELECT 1 FROM requests q WHERE q.item_id = i.id AND q.receiver_id = r.id);

-- Gặp trực tiếp đã dời lịch một lần (reschedule_count = 1).
INSERT INTO requests
  (item_id, receiver_id, type, status, delivery_method, meeting_place, meeting_time,
   donor_schedule_ok_at, receiver_schedule_ok_at, donor_waiver_at, receiver_waiver_at,
   reserved_at, contact_revealed_at, reschedule_count)
SELECT i.id, r.id, 'GIVE', 'LOGISTICS_CONFIRMED', 'IN_PERSON', 'Thư viện trường',
       now() + INTERVAL '3 days', now(), now(), now(), now(), now(), now(), 1
FROM items i
JOIN users d ON d.id = i.donor_id AND d.email = 'binh.demo@shareloop.local'
JOIN users r ON r.email = 'an.demo@shareloop.local'
WHERE i.title = 'Demo - Đèn bàn (đã dời lịch một lần)'
  AND NOT EXISTS (SELECT 1 FROM requests q WHERE q.item_id = i.id AND q.receiver_id = r.id);

-- Trao đổi qua Giao hàng: An đã gửi chiều 1, Chi đã nhận chiều 1, chiều 2 (Chi gửi mũ cho An) chưa gửi.
INSERT INTO requests
  (item_id, receiver_id, offered_item_id, type, status, delivery_method, meeting_time,
   donor_schedule_ok_at, receiver_schedule_ok_at, donor_waiver_at, receiver_waiver_at,
   reserved_at, contact_revealed_at, receiver_address, donor_address,
   donor_shipped_at, receiver_handover_at)
SELECT i.id, r.id, o.id, 'SWAP', 'AWAITING_HANDOVER', 'SHIPPING', now() - INTERVAL '3 days',
       now() - INTERVAL '6 days', now() - INTERVAL '6 days', now() - INTERVAL '6 days', now() - INTERVAL '6 days',
       now() - INTERVAL '6 days', now() - INTERVAL '6 days', '12 Lê Lợi, Quận 1', '34 Trần Phú, Quận 5',
       now() - INTERVAL '2 days', now() - INTERVAL '1 day'
FROM items i
JOIN users d ON d.id = i.donor_id AND d.email = 'an.demo@shareloop.local'
JOIN users r ON r.email = 'chi.demo@shareloop.local'
JOIN items o ON o.donor_id = r.id AND o.title = 'Demo - Mũ bảo hiểm (đổi qua giao hàng)'
WHERE i.title = 'Demo - Máy ảnh cũ (trao đổi qua giao hàng)'
  AND NOT EXISTS (SELECT 1 FROM requests q WHERE q.item_id = i.id AND q.receiver_id = r.id);
