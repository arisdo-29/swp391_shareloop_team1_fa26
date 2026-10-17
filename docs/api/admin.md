# API module admin

Quy ước chung theo Lộ trình mục 3.4: tiền tố `/api/v1`, thời gian ISO-8601 UTC, thành công trả thẳng DTO (không bọc), lỗi theo định dạng thống nhất của hệ thống.

---

## 1. GET /api/v1/admin/activity-logs

Xem nhật ký hành động trong hệ thống (FR-115, Issue #117). Chỉ dành cho quản trị viên (Admin). Sắp xếp mới nhất lên trước (`createdAt DESC`), hỗ trợ phân trang và lọc đa tiêu chí.

- **Xác thực:** Bắt buộc Bearer Token (JWT) có quyền Admin (`hasRole('ADMIN')`).
- **Query Parameters:**

| Tham số | Kiểu | Bắt buộc | Mặc định | Mô tả |
|---|---|---|---|---|
| `actorId` | number | Không | - | Lọc theo ID người thực hiện |
| `type` | string | Không | - | Lọc theo loại hành động (vd: `APPROVE_ITEM`, `USER_LOCK`) |
| `from` | yyyy-MM-dd | Không | - | Từ ngày (tính từ 00:00:00 theo múi giờ Asia/Ho_Chi_Minh) |
| `to` | yyyy-MM-dd | Không | - | Đến ngày (tính hết 23:59:59 của ngày `to` theo múi giờ Asia/Ho_Chi_Minh) |
| `page` | number | Không | 0 | Chỉ số trang (bắt đầu từ 0) |
| `size` | number | Không | 20 | Số lượng bản ghi mỗi trang (tối đa 100) |

- **Thành công:** `200 OK`, trả về `Page<ActivityLogDto>`.

```json
{
  "content": [
    {
      "id": 101,
      "actorId": 1,
      "type": "APPROVE_ITEM",
      "targetType": "ITEM",
      "targetId": 42,
      "data": {
        "status": "APPROVED"
      },
      "reason": "Nội dung hợp lệ",
      "detail": "Nội dung hợp lệ",
      "createdAt": "2026-10-17T10:00:00Z"
    }
  ],
  "page": {
    "size": 20,
    "number": 0,
    "totalElements": 1,
    "totalPages": 1
  }
}
```

- **Mã lỗi:**

| Mã | Ý nghĩa | Mô tả |
|----|---------|-------|
| `200` | Thành công | Trả về danh sách nhật ký hành động |
| `400` | Bad Request | `INVALID_DATE_RANGE`: Từ ngày (`from`) sau đến ngày (`to`) |
| `401` | Unauthorized | Chưa đăng nhập hoặc token không hợp lệ |
| `403` | Forbidden | Người dùng không có vai trò Admin |

---

## 2. POST /api/v1/admin/users/{userId}/lock

Khoá tài khoản người dùng và ghi nhật ký hoạt động (FR-107, Issue #116). Chỉ dành cho quản trị viên (Admin). Bắt buộc cung cấp lý do khoá.

- **Xác thực:** Bắt buộc Bearer Token (JWT) có quyền Admin (`hasRole('ADMIN')`).
- **Path Variable:** `userId` (ID người dùng cần khoá).
- **Request Body:**

```json
{
  "reason": "Nhiều báo cáo lừa đảo đã được xác minh"
}
```

- **Thành công:** `200 OK` (không có body).
- **Mã lỗi:**

| Mã | Ý nghĩa | Mô tả |
|----|---------|-------|
| `200` | Thành công | Khoá tài khoản thành công và đã ghi nhật ký |
| `400` | Bad Request | `VALIDATION_FAILED` (thiếu lý do hoặc vượt quá 500 ký tự) hoặc `USER_CANNOT_LOCK_SELF` (Admin tự khoá chính mình) |
| `401` | Unauthorized | Chưa đăng nhập hoặc token không hợp lệ |
| `403` | Forbidden | Người dùng không có vai trò Admin |
| `404` | Not Found | `USER_NOT_FOUND`: Người dùng không tồn tại |
| `409` | Conflict | `USER_ALREADY_LOCKED`: Tài khoản đã bị khoá từ trước |

---

## 3. POST /api/v1/admin/users/{userId}/unlock

Mở khoá tài khoản người dùng và ghi nhật ký hoạt động (FR-107, Issue #116). Chỉ dành cho quản trị viên (Admin). Bắt buộc cung cấp lý do mở khoá.

- **Xác thực:** Bắt buộc Bearer Token (JWT) có quyền Admin (`hasRole('ADMIN')`).
- **Path Variable:** `userId` (ID người dùng cần mở khoá).
- **Request Body:**

```json
{
  "reason": "Người dùng đã giải trình hợp lệ và hoàn tất cam kết"
}
```

- **Thành công:** `200 OK` (không có body).
- **Mã lỗi:**

| Mã | Ý nghĩa | Mô tả |
|----|---------|-------|
| `200` | Thành công | Mở khoá tài khoản thành công và đã ghi nhật ký |
| `400` | Bad Request | `VALIDATION_FAILED`: Thiếu lý do hoặc lý do vượt quá 500 ký tự |
| `401` | Unauthorized | Chưa đăng nhập hoặc token không hợp lệ |
| `403` | Forbidden | Người dùng không có vai trò Admin |
| `404` | Not Found | `USER_NOT_FOUND`: Người dùng không tồn tại |
| `409` | Conflict | `USER_NOT_LOCKED`: Tài khoản đang hoạt động bình thường, không cần mở khoá |
