# K02 – Kiểm thử vòng xoay banner trang chủ

1. Trên staging, chọn ít nhất một bài test còn hiển thị, đã duyệt, có ảnh và thuộc tài khoản test đang hoạt động. Nên chọn nhiều bài có ảnh khác nhau. Điền các ID vào [script chuẩn bị dữ liệu](../../scripts/seed_k02_banner_rotation.sql) và chạy trên DB staging. Script trả ID của 7 bài `[K02] Banner 01–07`. Bài tự hết hạn sau 30 phút.
2. Mở trang chủ, giữ cùng phiên trình duyệt. Trong tab Network, tìm `GET /api/v1/items/featured?limit=5` và ghi lại ID của 5 bài cùng `nextRotateAt`. Chờ qua mốc này khoảng một giây để trình duyệt tự gọi lại. Kiểm tra danh sách ID và thumbnail đổi sang nhóm mới, bấm một thumbnail rồi mở chi tiết để kiểm tra đúng bài. Hiệu ứng kính mờ vẫn dùng ảnh của bài đang chọn.
3. Mặc định backend đổi nhóm sau mỗi **180 giây**. Có thể tạm đổi `boost_rotate_seconds` thành **60** trong Cấu hình để kiểm thử nhanh hơn. Tải lại trang một lần sau khi đổi cấu hình để lấy lịch mới, rồi quan sát các lần đổi nhóm tự động. Trả về **180** sau test.
4. Kiểm tra trường hợp API featured trả `items: []`: dùng staging test tại thời điểm không có bài đẩy hoặc dùng dữ liệu phản hồi trống ở local. Trang chủ vẫn hiện phần giới thiệu và ô tìm đồ. Thumbnail, ảnh nền từ bài và hiệu ứng lau kính chỉ xuất hiện khi nhóm có ảnh.

Nếu cần dọn bài K02 sớm, chạy câu lệnh sau trên chính DB đã seed. Chỉ các bài thử nghiệm được đánh dấu bởi script bị ẩn.

```sql
UPDATE items
SET is_active = FALSE, is_deleted = TRUE
WHERE NOT is_deleted
  AND title ~ '^\[K02\] Banner 0[1-7]$'
  AND description = 'Dữ liệu kiểm thử vòng xoay banner K02.';
```
