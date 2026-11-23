# API module media

Service cửa ngõ nội bộ: `MediaService.deactivate(long mediaId)` và `remove(long mediaId)` hiện là chữ ký tạm, ném `UnsupportedOperationException`; chưa có endpoint HTTP tương ứng trong #38.

## POST /api/v1/media

Cần JWT. Gửi `multipart/form-data` với part `file`. Chấp nhận JPG, PNG, WebP hợp lệ;
giới hạn kích thước lấy từ `ConfigKey.IMAGE_MAX_MB` (mặc định 5 MB). Server xác định
MIME từ chữ ký tệp, không tin tên hoặc MIME do client gửi.

Thành công `201`:

```json
{ "mediaId": 42, "url": "/api/v1/media/42" }
```

Tệp được lưu Local với khóa ngẫu nhiên; bản ghi `media_files` có trạng thái `UPLOADED`.
`GET /api/v1/media/{id}` công khai chỉ đọc ảnh `PUBLIC` đang dùng, trả đúng MIME và bytes.

Response thành công của `GET /api/v1/media/{id}` có header `Cache-Control: max-age=604800, public`
(7 ngày, không `immutable` vì Admin có thể ẩn ảnh); response lỗi không được cache 7 ngày.

Lỗi: `422 MEDIA_INVALID_FILE`, `422 MEDIA_FILE_TOO_LARGE`, `401 UNAUTHORIZED`,
`500 MEDIA_STORAGE_ERROR`.

Lỗi của `GET /api/v1/media/{id}`: `404 MEDIA_NOT_FOUND` (không có bản ghi, ảnh bị ẩn hoặc
không `PUBLIC`), `404 MEDIA_FILE_MISSING` (bản ghi còn nhưng tệp không còn trong storage, ví dụ
ảnh tải lên Render trước khi chuyển sang R2; FE nên báo người dùng tải lại ảnh),
`500 MEDIA_STORAGE_ERROR` (storage lỗi khi đọc).

## MediaService.attach(mediaIds, userId)

Service nội bộ cho module item. Kiểm tất cả id không trùng, tồn tại, thuộc người gọi và
đang `UPLOADED`, sau đó chuyển tất cả sang `ATTACHED` trong transaction người gọi.
Thiếu tệp: `MEDIA_NOT_FOUND`; không phải chủ: `MEDIA_NOT_OWNER`; đã gắn:
`MEDIA_NOT_UPLOADED`. Module item chịu trách nhiệm lưu `item_media_mappings` cùng transaction.

## Ảnh riêng tư và dọn ảnh (D2, rà soát 22/11)

- `GET /api/v1/media/{id}` (công khai) chỉ trả ảnh `PUBLIC` đã gắn vào bài đăng hoặc là ảnh đại diện. Ảnh mới tải lên chưa gắn, và ảnh `PRIVATE` (chat, giao nhận, minh chứng báo cáo), trả `404 MEDIA_NOT_FOUND`.
- `GET /api/v1/media/{id}/preview` (cần đăng nhập): người tải lên xem lại ảnh của mình khi chưa gắn. Người khác `404`.
- `GET /api/v1/requests/{requestId}/media/{mediaId}` (cần đăng nhập): ảnh chat và ảnh giao nhận, chỉ hai bên của giao dịch và Admin xem được và ảnh phải thuộc đúng giao dịch. Không phải người được xem hoặc ảnh không thuộc giao dịch thì `404`, không trả `403`, để không lộ ảnh có tồn tại. `RequestMediaResponse.url` và `AdminChatMessage.mediaUrl` đã đổi sang đường dẫn này.
- Ảnh chat và ảnh giao nhận được gắn ở dạng riêng tư (`MediaService.attachPrivate`). Migration `V202611221600` chuyển ảnh cũ sang riêng tư và `V202611221610` đánh dấu `ATTACHED` cho ảnh cũ còn đang được dùng.
- Mỗi người tải lên tối đa `media_upload_per_hour` ảnh mỗi giờ (mặc định 40), quá thì `429 MEDIA_UPLOAD_TOO_FAST` ("Bạn tải ảnh quá nhanh. Vui lòng thử lại sau ít phút").
- Job `MediaOrphanCleanupJob` (mỗi giờ, cũng chạy qua `/internal/jobs/run`) xoá đối tượng trên kho lưu trữ rồi đánh dấu xoá các ảnh chưa gắn quá `media_orphan_cleanup_hours` giờ (mặc định 24).
