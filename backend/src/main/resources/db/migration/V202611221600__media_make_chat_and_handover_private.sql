-- Ảnh chat và ảnh giao nhận chỉ hai bên giao dịch và Admin được xem (D2). Chuyển ảnh cũ sang riêng tư. Sau khi chạy,
-- ảnh chỉ đọc được qua GET /api/v1/requests/{id}/media/{mediaId}; kiểm trên staging ảnh cũ vẫn hiện đúng với hai bên.
UPDATE media_files SET visibility = 'PRIVATE'
WHERE id IN (SELECT media_id FROM messages WHERE media_id IS NOT NULL)
   OR id IN (SELECT media_id FROM request_media_mappings);
