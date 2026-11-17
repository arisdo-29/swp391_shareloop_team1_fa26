-- Dữ liệu test cho ca B14 (trang Hoạt động: thẻ "Việc cần bạn xử lý" và các tab).
-- KHÔNG chạy tự động: file này nằm ngoài db/migration nên Flyway không đọc. Chạy tay trên DB test/staging
-- (Neon SQL Editor hoặc psql) SAU KHI đã sửa hai email ở bước 0.
--
-- Tạo cho tài khoản test (TEST_EMAIL), mọi bài có tiền tố [B14] trong tên:
--   1. "Bài có người xin": bài Cho tặng APPROVED của bạn, có 1 yêu cầu PENDING từ PARTNER_EMAIL
--      → thẻ "Chờ bạn chọn người" = 1 bài, 1 người đang xin.
--   2. "Giao dịch chờ chốt lịch": bài Cho tặng của bạn đã chọn PARTNER_EMAIL (bài RESERVED, yêu cầu RESERVED, chưa có lịch)
--      → thẻ "Giao dịch chờ chốt lịch" = 1 giao dịch; vào Tin nhắn thấy cuộc trò chuyện để hẹn lịch.
--   3. "Yêu cầu đã gửi": bài Cho tặng APPROVED của PARTNER_EMAIL, bạn đã gửi 1 yêu cầu PENDING
--      → thẻ "Yêu cầu bạn đã gửi" = 1 yêu cầu, 0 đã được chọn, 1 đang chờ chủ bài.
--   4. "Bài bị ẩn": bài Cho tặng APPROVED của bạn đang bị ẩn (is_active = false, như khi đủ ngưỡng báo cáo)
--      → tab Bài của tôi hiện bài với trạng thái tạm ẩn, nút Gia hạn / Đẩy bài bị khoá.
--
-- Cả hai tài khoản phải có sẵn, ACTIVE, không phải Admin. Chạy lại nhiều lần được: lần sau xoá mềm dữ liệu [B14] cũ
-- của TEST_EMAIL rồi tạo mới. Không đụng ví, Credit hay dữ liệu khác. Giao dịch số 2 sẽ bị RequestJob tự huỷ nếu quá
-- chat_open_days ngày chưa chốt lịch (tính từ reserved_at = lúc chạy script).

BEGIN;

-- 0. SỬA HAI EMAIL NÀY trước khi chạy.
CREATE TEMP TABLE b14_params ON COMMIT DROP AS
SELECT 'tai-khoan-test@example.com'::text AS test_email,      -- tài khoản dùng để test
       'tai-khoan-doi-tac@example.com'::text AS partner_email; -- tài khoản thứ hai (người xin / chủ bài kia)

-- 1. Kiểm hai tài khoản có thật, đang hoạt động, không phải Admin; sai thì dừng và rollback cả script.
DO $$
DECLARE
  missing text;
BEGIN
  SELECT string_agg(e.email, ', ') INTO missing
  FROM (SELECT test_email AS email FROM b14_params UNION ALL SELECT partner_email FROM b14_params) e
  WHERE NOT EXISTS (
    SELECT 1 FROM users u
    WHERE lower(u.email) = lower(e.email) AND u.status = 'ACTIVE' AND NOT u.is_admin AND NOT u.is_deleted
  );
  IF missing IS NOT NULL THEN
    RAISE EXCEPTION 'B14 seed: không có tài khoản ACTIVE (không phải Admin) với email: %', missing;
  END IF;
  IF (SELECT lower(test_email) = lower(partner_email) FROM b14_params) THEN
    RAISE EXCEPTION 'B14 seed: hai email phải khác nhau';
  END IF;
END $$;

CREATE TEMP TABLE b14_ctx ON COMMIT DROP AS
SELECT
  (SELECT id FROM users WHERE lower(email) = lower(p.test_email) AND NOT is_deleted) AS test_id,
  (SELECT id FROM users WHERE lower(email) = lower(p.partner_email) AND NOT is_deleted) AS partner_id,
  -- Danh mục thường (không hạn chế, không phải Điện tử để khỏi phải khai Trạng thái hoạt động).
  (SELECT id FROM item_categories
   WHERE is_active AND NOT is_deleted AND NOT is_restricted AND name <> 'Điện tử và phụ kiện'
   ORDER BY sort_order, id LIMIT 1) AS category_id,
  (SELECT id FROM areas WHERE level = 2 AND is_active AND NOT is_deleted ORDER BY id LIMIT 1) AS area_id
FROM b14_params p;

-- 2. Xoá mềm dữ liệu [B14] của lần chạy trước (yêu cầu trước, rồi bài).
UPDATE requests SET status = 'CANCELLED', cancel_reason = '[Hệ thống] Dọn dữ liệu test B14', is_deleted = TRUE
WHERE NOT is_deleted
  AND item_id IN (
    SELECT i.id FROM items i, b14_ctx c
    WHERE i.title LIKE '[B14]%' AND i.donor_id IN (c.test_id, c.partner_id) AND NOT i.is_deleted);
UPDATE items SET is_deleted = TRUE, is_active = FALSE
WHERE title LIKE '[B14]%' AND NOT is_deleted
  AND donor_id IN (SELECT test_id FROM b14_ctx UNION SELECT partner_id FROM b14_ctx);

-- 3. Bốn bài Cho tặng (miễn phí đăng nên fee_state = NONE), đã duyệt, còn hạn hiển thị 30 ngày.
INSERT INTO items (donor_id, category_id, area_id, offer_type, title, description, condition, status, is_active,
                   approved_at, expire_at)
SELECT v.donor_id, c.category_id, c.area_id, 'GIVE', v.title, v.description, 'GOOD', v.status, v.is_active,
       now() - INTERVAL '1 day', now() + INTERVAL '30 days'
FROM b14_ctx c
CROSS JOIN LATERAL (VALUES
  (c.test_id, '[B14] Bài có người xin', 'Bài test B14: có một người đang xin, chờ bạn chọn.', 'APPROVED', TRUE),
  (c.test_id, '[B14] Giao dịch chờ chốt lịch', 'Bài test B14: đã chọn người nhận, chưa chốt lịch.', 'RESERVED', TRUE),
  (c.partner_id, '[B14] Bài của người khác (bạn đã xin)', 'Bài test B14: bạn đã gửi yêu cầu xin.', 'APPROVED', TRUE),
  (c.test_id, '[B14] Bài bị ẩn', 'Bài test B14: đang bị ẩn chờ Admin xem xét.', 'APPROVED', FALSE)
) AS v(donor_id, title, description, status, is_active);

-- 4. Ba yêu cầu.
INSERT INTO requests (item_id, receiver_id, type, status, reserved_at)
SELECT i.id, v.receiver_id, 'GIVE', v.status, v.reserved_at
FROM b14_ctx c
CROSS JOIN LATERAL (VALUES
  ('[B14] Bài có người xin', c.test_id, c.partner_id, 'PENDING', NULL::timestamptz),
  ('[B14] Giao dịch chờ chốt lịch', c.test_id, c.partner_id, 'RESERVED', now()),
  ('[B14] Bài của người khác (bạn đã xin)', c.partner_id, c.test_id, 'PENDING', NULL::timestamptz)
) AS v(title, donor_id, receiver_id, status, reserved_at)
JOIN items i ON i.title = v.title AND i.donor_id = v.donor_id AND NOT i.is_deleted;

-- 5. Xem lại dữ liệu vừa tạo trước khi COMMIT.
SELECT i.id AS item_id, i.title, i.status AS item_status, i.is_active, r.id AS request_id, r.status AS request_status,
       ru.email AS receiver_email
FROM items i
LEFT JOIN requests r ON r.item_id = i.id AND NOT r.is_deleted
LEFT JOIN users ru ON ru.id = r.receiver_id
WHERE i.title LIKE '[B14]%' AND NOT i.is_deleted
  AND i.donor_id IN (SELECT test_id FROM b14_ctx UNION SELECT partner_id FROM b14_ctx)
ORDER BY i.id;

COMMIT;
