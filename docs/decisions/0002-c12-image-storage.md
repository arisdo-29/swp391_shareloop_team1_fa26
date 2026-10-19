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

## Vận hành

### Checklist sau khi bật R2

1. Trên Render đặt `STORAGE_TYPE=s3` và đủ các biến `S3_*`, deploy lại; ghi lại thời điểm deploy
   xong (dùng làm `cutoff` cho script dọn).
2. Đăng nhập staging, tải một ảnh mới (đăng bài hoặc đổi avatar). Mở `GET /api/v1/media/{id}`
   của ảnh đó: phải trả `200` đúng ảnh.
3. Kiểm trên Cloudflare dashboard: bucket `shareloop-media` có tệp `{uuid}.{đuôi}` vừa tải.
4. Restart dịch vụ trên Render (Manual Deploy hoặc Restart), chờ dịch vụ lên lại.
5. Mở lại đúng URL ảnh ở bước 2: vẫn phải trả `200`. Nếu ra `404 MEDIA_FILE_MISSING` thì backend
   vẫn đang dùng Local; xem lại `STORAGE_TYPE` và log khởi động.
6. Xem log Render: không có dòng `Cannot read media from storage` (lỗi kết nối R2, trả
   `500 MEDIA_STORAGE_ERROR`).

### Dọn ảnh đã mất trước khi chuyển

Ảnh tải lên trước khi bật R2 trả `404 MEDIA_FILE_MISSING` (log có dòng `Media file missing in
storage` kèm `mediaId`, `storageKey`). Dùng script chạy tay `docs/ops/cleanup-lost-media.sql`
(không phải migration):

1. Sửa dòng `\set cutoff` thành thời điểm ở bước 1 checklist, ví dụ `'2026-10-20 09:00:00+07'`.
2. Chạy `psql "$DATABASE_URL" -f docs/ops/cleanup-lost-media.sql`. Script mặc định kết thúc bằng
   `ROLLBACK`, nên lần chạy đầu chỉ để xem kết quả bước a (danh sách media, bài đăng và avatar
   liên quan) và số dòng bước b, c sẽ sửa.
3. Kiểm danh sách: chỉ gồm ảnh tạo trước cutoff, không có ảnh mới trên R2.
4. Đổi `ROLLBACK` cuối script thành `COMMIT` rồi chạy lại. Script gỡ avatar trỏ tới các ảnh đó
   và xoá mềm bản ghi `media_files` (`is_deleted = true`, `is_active = false`).
5. Bài đăng có ảnh bị dọn vẫn còn nhưng thiếu ảnh; báo người đăng tải lại hoặc xoá bài thử.
