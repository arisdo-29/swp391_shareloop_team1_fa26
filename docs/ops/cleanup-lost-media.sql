-- Dọn các bản ghi media_files có tệp đã mất khi staging còn dùng STORAGE_TYPE=local trên Render.
-- Script chạy tay bằng psql, KHÔNG phải migration Flyway (không đặt trong db/migration).
--
-- Cách chạy:
--   psql "$DATABASE_URL" -f docs/ops/cleanup-lost-media.sql
-- hoặc truyền mốc thời gian từ dòng lệnh (khi đó xoá dòng \set bên dưới):
--   psql "$DATABASE_URL" -v cutoff='2026-10-20 09:00:00+07' -f docs/ops/cleanup-lost-media.sql
--
-- :cutoff là thời điểm staging chuyển sang R2 (STORAGE_TYPE=s3). Ảnh tạo TRƯỚC mốc này nằm trên
-- ổ đĩa Render cũ và đã mất; ảnh tạo SAU mốc này nằm trên R2 nên không được đụng tới.
-- Giá trị mặc định cố ý không hợp lệ để script dừng nếu quên điền.
\set cutoff 'YYYY-MM-DD HH:MI:SS+07'

\set ON_ERROR_STOP on

BEGIN;

-- Bước a: xem trước. Liệt kê media tạo trước cutoff, kèm bài đăng dùng ảnh (qua item_media_mappings)
-- và người dùng đang lấy ảnh làm avatar. Một media có thể ra nhiều dòng nếu gắn vào nhiều chỗ.
-- Lưu ý: request_media_mappings và messages cũng có thể trỏ tới media; các dòng đó không bị sửa,
-- ảnh chỉ bị ẩn đi (GET /api/v1/media/{id} trả 404 MEDIA_NOT_FOUND).
SELECT media.id          AS media_id,
       media.storage_key,
       media.status,
       media.created_at,
       mapping.item_id,
       avatar_user.id    AS avatar_user_id
FROM media_files media
LEFT JOIN item_media_mappings mapping ON mapping.media_id = media.id
LEFT JOIN users avatar_user ON avatar_user.avatar_media_id = media.id
WHERE media.created_at < :'cutoff'::timestamptz
  AND media.is_deleted = false
ORDER BY media.id;

-- Bước b: gỡ avatar trỏ tới các media đó, để trang hồ sơ hiện avatar mặc định thay vì ảnh lỗi.
-- Phải chạy trước bước c vì điều kiện lọc dựa vào is_deleted = false.
UPDATE users
SET avatar_media_id = NULL
WHERE avatar_media_id IN (
    SELECT id
    FROM media_files
    WHERE created_at < :'cutoff'::timestamptz
      AND is_deleted = false
);

-- Bước c: xoá mềm các media đó (không xoá cứng vì bảng khác còn khoá ngoại tới media_files).
UPDATE media_files
SET is_deleted = true,
    is_active = false
WHERE created_at < :'cutoff'::timestamptz
  AND is_deleted = false;

-- Mặc định huỷ toàn bộ thay đổi. Đổi thành COMMIT khi đã kiểm tra kết quả bước a.
ROLLBACK;
