-- Mốc lúc chủ bài gửi bản sửa của bài đã từng được duyệt (B1). Khi bản sửa được duyệt, số giờ chờ duyệt
-- được cộng bù vào hạn hiển thị và hạn đẩy, rồi mốc được xoá. Bản sửa bị từ chối cũng xoá mốc.
ALTER TABLE items ADD COLUMN edit_submitted_at TIMESTAMPTZ;
