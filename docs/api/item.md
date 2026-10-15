# API module item

## Service cửa ngõ nội bộ

- `ItemService.reserve(Long itemId)` đã có thân; `release(long itemId)` chờ #74.
- `markTraded(long itemId)` đã có thân; `hide(long itemId)`, `unhide(long itemId)`, `remove(long itemId)`, `hideAllOfUser(long userId)` chờ #91.
- Các hàm chờ triển khai ném `UnsupportedOperationException` để tránh báo thành công giả; chưa có endpoint HTTP tương ứng trong #38.

Quy ước chung theo Lộ trình mục 3.4: tiền tố `/api/v1`, thời gian ISO-8601 UTC, thành công trả thẳng DTO (không bọc),
lỗi theo định dạng thống nhất bên dưới.

Bản demo gấp (đã hỗ trợ gắn ảnh upload; chưa tích hợp phí đăng bài và kiểm duyệt AI).

---

## 1. POST /api/v1/items

Đăng bài viết mới cho tặng (GIVE) hoặc trao đổi (SWAP) (UC-20, F07 bước 1).

- **Xác thực:** Bắt buộc Bearer Token (JWT).
- **Request body:**

```json
{
  "categoryId": 1,
  "areaId": 1,
  "offerType": "GIVE",
  "title": "Giáo trình Toán rời rạc",
  "description": "Sách còn mới 95%, tặng cho bạn nào cần ôn thi",
  "desiredItem": null,
  "condition": "LIKE_NEW",
  "defectNote": null,
  "brand": "NXB Giáo Dục",
  "images": ["/api/v1/media/42", "/api/v1/media/43", "/api/v1/media/44"]
}
```

*Lưu ý nghiệp vụ:*
- Nếu `offerType` là `SWAP`, bắt buộc trường `desiredItem` không được rỗng.
- Nếu `condition` là `DEFECTIVE`, bắt buộc trường `defectNote` không được rỗng.
- `images` bắt buộc có 3–8 URL ảnh đã upload của chính người đăng (giới hạn từ `ConfigKey.ITEM_PHOTOS_MIN/MAX`); thứ tự gửi là thứ tự hiển thị.
- Bài đăng mới tạo có trạng thái mặc định là `PENDING_REVIEW`.

- **Thành công:** `201 Created`, trả về `ItemResponse`.

```json
{
  "id": 1,
  "donorId": 2,
  "categoryId": 1,
  "areaId": 1,
  "offerType": "GIVE",
  "title": "Giáo trình Toán rời rạc",
  "description": "Sách còn mới 95%, tặng cho bạn nào cần ôn thi",
  "desiredItem": null,
  "condition": "LIKE_NEW",
  "defectNote": null,
  "brand": "NXB Giáo Dục",
  "images": ["/api/v1/media/42", "/api/v1/media/43", "/api/v1/media/44"],
  "status": "PENDING_REVIEW",
  "createdAt": "2026-10-05T14:00:00Z",
  "updatedAt": "2026-10-05T14:00:00Z"
}
```

- **Lỗi:**
  - `400 VALIDATION_FAILED`: Dữ liệu đầu vào không hợp lệ (thiếu trường bắt buộc, vượt độ dài).
  - `400 ITEM_SWAP_DESIRED_ITEM_REQUIRED`: Đăng bài SWAP nhưng thiếu món mong muốn.
  - `400 ITEM_DEFECT_NOTE_REQUIRED`: Đồ DEFECTIVE nhưng thiếu mô tả lỗi.
  - `400 ITEM_INVALID_IMAGE`: `images[]` chứa URL không phải URL trả về từ `POST /api/v1/media`.
  - `422 ITEM_INVALID_IMAGE_COUNT` (`field: images`): Có ít hơn 3 hoặc nhiều hơn 8 ảnh.
  - `403 MEDIA_NOT_OWNER`: Ảnh không thuộc người đăng bài.
  - `404 MEDIA_NOT_FOUND`: Không tìm thấy ảnh đã upload.
  - `409 MEDIA_NOT_UPLOADED`: Ảnh đã gắn vào bài khác hoặc không còn dùng được.
  - `401 UNAUTHORIZED`: Chưa đăng nhập.

---

## 2. GET /api/v1/items/{id}

Xem chi tiết một bài đăng theo ID.

- **Xác thực:** Không bắt buộc (công khai đối với bài đã duyệt).
- **Quy tắc hiển thị:**
  - Bài ở trạng thái `APPROVED`: Bất kỳ ai (khách vãng lai hay thành viên khác) đều xem được.
  - Bài ở các trạng thái khác (`PENDING_REVIEW`, `REJECTED`, `RESERVED`, `TRADED`, `EXPIRED`): Chỉ chính **chủ bài đăng** (`donorId`) mới xem được.
  - Trường hợp bài không tồn tại, đã xoá mềm, hoặc người xem không phải chủ bài: Trả về `404 ITEM_NOT_FOUND`.
- **Thành công:** `200 OK`, trả về `ItemResponse`.
- **Lỗi:**
  - `404 ITEM_NOT_FOUND`: Không tìm thấy bài đăng hoặc không có quyền xem.

---

## 3. GET /api/v1/me/items

Xem danh sách tất cả các bài đăng do chính mình tạo (sắp xếp mới nhất lên trước).

- **Xác thực:** Bắt buộc Bearer Token (JWT).
- **Thành công:** `200 OK`, mảng `ItemResponse` (mảng rỗng nếu chưa đăng bài nào).
- **Lỗi:**
  - `401 UNAUTHORIZED`: Chưa đăng nhập.

---

## 4. GET /api/v1/items

Danh sách bài `APPROVED` còn hiển thị, mới nhất trước. Không cần đăng nhập.

Tham số tùy chọn: `categoryId`, `areaId` (lọc đúng id), `page` (từ 0, mặc định 0),
`size` (1–100, mặc định 20). Kết quả là `PageResponse<ItemResponse>`:

```json
{ "items": [], "page": 0, "size": 20, "totalElements": 0 }
```

`400 VALIDATION_FAILED` khi tham số phân trang sai.

---

## DTO Models

### CreateItemRequest

| Trường | Kiểu | Bắt buộc | Ràng buộc |
|---|---|---|---|
| `categoryId` | number | Có | Id danh mục |
| `areaId` | number | Có | Id khu vực |
| `offerType` | string | Có | `GIVE` hoặc `SWAP` |
| `title` | string | Có | Tối đa 150 ký tự |
| `description` | string | Có | Không được để trống |
| `desiredItem` | string? | Điều kiện | Bắt buộc nếu `offerType` là `SWAP` |
| `condition` | string | Có | `NEW`, `LIKE_NEW`, `GOOD`, `DEFECTIVE` |
| `defectNote` | string? | Điều kiện | Bắt buộc nếu `condition` là `DEFECTIVE` |
| `brand` | string? | Không | Tối đa 80 ký tự |
| `images` | array[string] | Có | 3–8 URL `/api/v1/media/{id}` nhận từ `POST /api/v1/media`, theo thứ tự hiển thị; chỉ gắn ảnh của chính người đăng |

### ItemResponse

| Trường | Kiểu | Ý nghĩa |
|---|---|---|
| `id` | number | ID bài đăng |
| `donorId` | number | ID người đăng |
| `categoryId` | number | ID danh mục |
| `areaId` | number | ID khu vực |
| `offerType` | string | `GIVE` hoặc `SWAP` |
| `title` | string | Tiêu đề bài đăng |
| `description` | string | Nội dung mô tả |
| `desiredItem` | string? | Món đồ mong muốn đổi (nếu SWAP) |
| `condition` | string | Tình trạng đồ |
| `defectNote` | string? | Mô tả lỗi (nếu DEFECTIVE) |
| `brand` | string? | Nhãn hiệu / Thương hiệu |
| `images` | array[string] | Danh sách URL `/api/v1/media/{id}` của ảnh đã lưu |
| `status` | string | `PENDING_REVIEW`, `APPROVED`, `REJECTED`, `RESERVED`, `TRADED`, `EXPIRED` |
| `createdAt` | string | Thời điểm tạo ISO-8601 UTC |
| `updatedAt` | string | Thời điểm cập nhật ISO-8601 UTC |

---

## Mã lỗi

| code | HTTP | Khi nào |
|---|---|---|
| `ITEM_NOT_FOUND` | 404 | Không tìm thấy bài đăng hoặc bài chưa duyệt mà người xem không phải chủ |
| `ITEM_SWAP_DESIRED_ITEM_REQUIRED` | 400 | Bài đăng SWAP nhưng thiếu món mong muốn |
| `ITEM_DEFECT_NOTE_REQUIRED` | 400 | Bài đăng có tình trạng DEFECTIVE nhưng thiếu mô tả lỗi |
| `ITEM_INVALID_IMAGE` | 400 | URL ảnh không phải URL do API media trả về |
| `ITEM_INVALID_IMAGE_COUNT` | 422 | `images` có ít hơn 3 hoặc nhiều hơn 8 phần tử |
| `VALIDATION_FAILED` | 400 | Sai định dạng hoặc thiếu trường bắt buộc |
| `UNAUTHORIZED` | 401 | Thiếu token hoặc token không hợp lệ |
