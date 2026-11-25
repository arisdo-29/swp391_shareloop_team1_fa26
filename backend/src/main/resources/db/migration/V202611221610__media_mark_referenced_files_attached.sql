-- readPublic chỉ trả ảnh đã gắn (D2). Ảnh cũ còn đang được bài đăng, ảnh đại diện, tin nhắn hoặc giao nhận dùng nhưng
-- chưa từng được đánh dấu ATTACHED thì đánh dấu ngay để không bị chặn nhầm hay bị job dọn ảnh xoá nhầm.
UPDATE media_files SET status = 'ATTACHED'
WHERE status = 'UPLOADED'
  AND (id IN (SELECT media_id FROM item_media_mappings)
    OR id IN (SELECT avatar_media_id FROM users WHERE avatar_media_id IS NOT NULL)
    OR id IN (SELECT media_id FROM messages WHERE media_id IS NOT NULL)
    OR id IN (SELECT media_id FROM request_media_mappings));
