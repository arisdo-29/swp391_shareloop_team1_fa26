# API module item

Quy ước chung theo Lộ trình mục 3.4: tiền tố `/api/v1`, thời gian ISO-8601 UTC, thành công trả thẳng DTO (không bọc),
lỗi theo định dạng thống nhất bên dưới.

Bản demo gấp (chưa tích hợp ảnh, phí đăng bài, kiểm duyệt AI).

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
  "brand": "NXB Giáo Dục"
}
```

*Lưu ý nghiệp vụ:*
- Nếu `offerType` là `SWAP`, bắt buộc trường `desiredItem` không được rỗng.
- Nếu `condition` là `DEFECTIVE`, bắt buộc trường `defectNote` không được rỗng.
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
  "status": "PENDING_REVIEW",
  "createdAt": "2026-10-05T14:00:00Z",
  "updatedAt": "2026-10-05T14:00:00Z"
}
```

- **Lỗi:**
  - `400 VALIDATION_FAILED`: Dữ liệu đầu vào không hợp lệ (thiếu trường bắt buộc, vượt độ dài).
  - `400 ITEM_SWAP_DESIRED_ITEM_REQUIRED`: Đăng bài SWAP nhưng thiếu món mong muốn.
  - `400 ITEM_DEFECT_NOTE_REQUIRED`: Đồ DEFECTIVE nhưng thiếu mô tả lỗi.
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
| `VALIDATION_FAILED` | 400 | Sai định dạng hoặc thiếu trường bắt buộc |
| `UNAUTHORIZED` | 401 | Thiếu token hoặc token không hợp lệ |
