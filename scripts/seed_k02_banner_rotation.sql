-- Chỉ chạy trên DB test hoặc staging. Điền ID các bài thử nghiệm đã duyệt và có ảnh ở bước 1.
-- Tạo 7 bài kiểm thử có ảnh và được đẩy trong 30 phút. Không thay đổi ví, phí hoặc bài nguồn.
BEGIN;

SELECT pg_advisory_xact_lock(hashtext('shareloop-k02-banner-test'));

-- 1. Có thể dùng một bài nguồn. Nên chọn nhiều bài từ tài khoản test để ảnh các nhóm dễ phân biệt.
CREATE TEMP TABLE k02_source_ids (id bigint PRIMARY KEY) ON COMMIT DROP;
-- INSERT INTO k02_source_ids VALUES (<id_bai_thu_nghiem>), (<id_bai_thu_nghiem_khac>);

CREATE TEMP TABLE k02_sources ON COMMIT DROP AS
SELECT i.*, row_number() OVER (ORDER BY i.id) AS source_number
FROM k02_source_ids selected
JOIN items i ON i.id = selected.id
JOIN users u ON u.id = i.donor_id
WHERE i.status = 'APPROVED' AND i.is_active AND NOT i.is_deleted
  AND (i.expire_at IS NULL OR i.expire_at > now())
  AND i.condition <> 'DEFECTIVE'
  AND i.title NOT LIKE '[K02] Banner %'
  AND u.status = 'ACTIVE' AND u.is_active AND NOT u.is_deleted AND NOT u.is_admin
  AND EXISTS (SELECT 1 FROM item_media_mappings m WHERE m.item_id = i.id AND m.role = 'PHOTO');

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM k02_sources)
       OR (SELECT count(*) FROM k02_sources) <> (SELECT count(*) FROM k02_source_ids) THEN
        RAISE EXCEPTION 'K02: cần ID bài test đã duyệt, còn hạn, có ảnh và thuộc tài khoản đang hoạt động';
    END IF;
END
$$;

-- Dọn các bài do lượt chạy trước tạo. Giữ lại các bài nguồn và toàn bộ ảnh gốc.
UPDATE items
SET is_active = FALSE, is_deleted = TRUE
WHERE NOT is_deleted
  AND title ~ '^\[K02\] Banner 0[1-7]$'
  AND description = 'Dữ liệu kiểm thử vòng xoay banner K02.';

CREATE TEMP TABLE k02_slots ON COMMIT DROP AS
SELECT s.id AS source_id, s.donor_id, s.category_id, s.area_id, s.offer_type, s.desired_item, s.condition,
       '[K02] Banner ' || lpad(slot.number::text, 2, '0') AS title
FROM generate_series(1, 7) AS slot(number)
JOIN k02_sources s ON s.source_number = 1 + (slot.number - 1) % (SELECT count(*) FROM k02_sources);

CREATE TEMP TABLE k02_inserted ON COMMIT DROP AS
WITH added AS (
    INSERT INTO items (
        donor_id, category_id, area_id, offer_type, title, description, desired_item,
        condition, status, approved_at, expire_at, boosted_until
    )
    SELECT donor_id, category_id, area_id, offer_type, title, 'Dữ liệu kiểm thử vòng xoay banner K02.',
           desired_item, condition, 'APPROVED', now(), now() + interval '30 minutes', now() + interval '30 minutes'
    FROM k02_slots ORDER BY title
    RETURNING id, title
)
SELECT added.id, added.title, slot.source_id FROM added JOIN k02_slots slot USING (title);

INSERT INTO item_media_mappings (item_id, media_id, role, sort_order)
SELECT inserted.id, media.media_id, media.role, media.sort_order
FROM k02_inserted inserted
JOIN item_media_mappings media ON media.item_id = inserted.source_id AND media.role = 'PHOTO';

INSERT INTO item_attribute_mappings (item_id, attribute_id, value)
SELECT inserted.id, attribute.attribute_id, attribute.value
FROM k02_inserted inserted
JOIN item_attribute_mappings attribute ON attribute.item_id = inserted.source_id;

SELECT id, title, source_id FROM k02_inserted ORDER BY title;

COMMIT;
