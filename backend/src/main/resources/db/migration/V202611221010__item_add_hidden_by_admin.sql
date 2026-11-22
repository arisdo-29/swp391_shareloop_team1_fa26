-- Bài do Admin ẩn thì chỉ nút "Hiện lại" của Admin mới hiện lại được. Các đường tự động (báo cáo bị bác bỏ, bài được duyệt lại sau khi sửa,
-- mở khoá tài khoản) bỏ qua bài có cờ này. Bài đang ẩn từ trước không có cờ nên giữ hành vi cũ.
ALTER TABLE items ADD COLUMN hidden_by_admin BOOLEAN NOT NULL DEFAULT FALSE;
