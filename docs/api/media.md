# API module media

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

Lỗi: `422 MEDIA_INVALID_FILE`, `422 MEDIA_FILE_TOO_LARGE`, `401 UNAUTHORIZED`,
`500 MEDIA_STORAGE_ERROR`.

## MediaService.attach(mediaIds, userId)

Service nội bộ cho module item. Kiểm tất cả id không trùng, tồn tại, thuộc người gọi và
đang `UPLOADED`, sau đó chuyển tất cả sang `ATTACHED` trong transaction người gọi.
Thiếu tệp: `MEDIA_NOT_FOUND`; không phải chủ: `MEDIA_NOT_OWNER`; đã gắn:
`MEDIA_NOT_UPLOADED`. Module item chịu trách nhiệm lưu `item_media_mappings` cùng transaction.
