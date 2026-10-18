# 0002 — C12: nơi lưu ảnh khi deploy

- Trạng thái: đã chốt.
- Bối cảnh: backend staging chạy trên Render gói free. Ổ đĩa của gói này không bền: tệp tải lên
  bị xoá mỗi khi deploy lại, khởi động lại hoặc dịch vụ ngủ sau 15 phút không có truy cập, và gói
  free không gắn được persistent disk. Vì vậy `STORAGE_TYPE=local` làm mất ảnh trên staging.
  Lộ trình Backend chương 8 đã so sánh các phương án và đề xuất Cloudflare R2.
- Quyết định:
  - Staging và buổi demo dùng Cloudflare R2 qua `S3StorageService` (`STORAGE_TYPE=s3`).
    R2 miễn phí 10 GB-tháng, không tính phí băng thông tải xuống; quy mô demo dưới 0,5 GB.
  - Dùng một bucket private `shareloop-media`. Ảnh vẫn đi qua `GET /api/v1/media/{id}` như bản
    Local: backend đọc từ bucket rồi trả về, nên không cần mở bucket công khai và FE không đổi gì.
  - `STORAGE_TYPE=local` giữ làm dự phòng (máy cá nhân, hoặc khi R2 gặp sự cố trong buổi demo).
  - Dự phòng nếu không kích hoạt được R2: storage tương thích S3 khác (Backblaze B2, MinIO) chỉ
    cần đổi biến môi trường `S3_*`, mã nguồn không đổi.
- Chưa làm trong lần này: tách bucket public/private, URL có hạn cho chứng từ và minh chứng,
  nén ảnh và xoá EXIF (Lộ trình 8.3). Làm khi có tính năng cần ảnh private.
- Di chuyển ảnh cũ: khoá ảnh ở cả hai bản đều là `{uuid}.{đuôi}`, nên chép nguyên tên tệp trong
  thư mục `uploads/` lên bucket là ảnh cũ xem lại được. Ảnh trên staging trước khi chuyển đã mất
  theo ổ đĩa Render; các bản ghi `media_files` đó cần tải lại ảnh hoặc xoá bài thử.
- Biến môi trường: `STORAGE_TYPE`, `S3_ENDPOINT`, `S3_REGION`, `S3_BUCKET`, `S3_ACCESS_KEY`,
  `S3_SECRET_KEY` (xem `backend/.env.example`). Khoá chỉ đặt trên Render, không commit.
