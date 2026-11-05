-- Luật mua gói đẩy mới: một đợt đẩy liên tục tối đa boost_max_days ngày tính từ lúc BẮT ĐẦU đợt (không còn tính từ
-- hiện tại), hết đợt nghỉ boost_cooldown_days ngày, mỗi người tối đa boost_max_per_user bài và toàn hệ thống tối đa
-- boost_max_active bài đang đẩy cùng lúc.

-- Mốc bắt đầu đợt đẩy hiện tại; mua thêm khi đang đẩy thì giữ nguyên mốc này.
ALTER TABLE items ADD COLUMN boost_started_at TIMESTAMPTZ;

-- Bài đang đẩy lấy mốc lần mua gần nhất làm mốc bắt đầu đợt. Không cắt ngắn đợt đang chạy: luật 6 ngày chỉ chặn lần
-- mua sau.
UPDATE items
SET boost_started_at = COALESCE(last_boosted_at, updated_at)
WHERE boosted_until > CURRENT_TIMESTAMP;

INSERT INTO website_attributes (attr_group, attr_key, attr_value, is_active, is_deleted)
VALUES
  ('CONFIG', 'boost_cooldown_days', '3', TRUE, FALSE),
  ('CONFIG', 'boost_max_per_user', '3', TRUE, FALSE),
  ('CONFIG', 'boost_max_active', '30', TRUE, FALSE),
  ('CONFIG', 'boost_rotate_seconds', '180', TRUE, FALSE)
ON CONFLICT DO NOTHING;

-- Đổi nghĩa: tối đa số ngày đẩy liên tục trong một đợt (trước đây 14 ngày tính từ hiện tại).
UPDATE website_attributes
SET attr_value = '6'
WHERE attr_group = 'CONFIG'
  AND attr_key = 'boost_max_days'
  AND is_deleted = FALSE;

-- Bật lại boost_max_per_page để trang danh sách xoay vòng bài đẩy. V202610291054 đã đặt is_active = FALSE và
-- V202611021000 đã xoá mềm (is_deleted = TRUE), nên làm ngược cả hai. Chỉ khôi phục một dòng (id lớn nhất) và chỉ khi
-- chưa có dòng nào đang dùng, vì chỉ mục ux_attr không cho hai dòng chưa xoá trùng khoá.
UPDATE website_attributes
SET is_deleted = FALSE,
    is_active = TRUE,
    attr_value = '3'
WHERE id = (
    SELECT MAX(id) FROM website_attributes
    WHERE attr_group = 'CONFIG' AND attr_key = 'boost_max_per_page' AND is_deleted = TRUE
  )
  AND NOT EXISTS (
    SELECT 1 FROM website_attributes
    WHERE attr_group = 'CONFIG' AND attr_key = 'boost_max_per_page' AND is_deleted = FALSE
  );

-- Phòng khi DB chưa từng có dòng boost_max_per_page (đã có dòng đang dùng thì bỏ qua).
INSERT INTO website_attributes (attr_group, attr_key, attr_value, is_active, is_deleted)
VALUES
  ('CONFIG', 'boost_max_per_page', '3', TRUE, FALSE)
ON CONFLICT DO NOTHING;
