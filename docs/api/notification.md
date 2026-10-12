# API module notification

Quy ước chung theo Lộ trình mục 3.4: tiền tố `/api/v1`, thời gian ISO-8601 UTC, thành công trả thẳng DTO (không bọc),
lỗi theo định dạng thống nhất bên dưới.

Người sở hữu: BE3 (@dwargon73-sketch).

---

## 1. GET /api/v1/notifications

Xem danh sách thông báo của tôi (phân trang, sắp xếp mới nhất lên trước) (FR-118, UC-20).

- **Xác thực:** Bắt buộc Bearer Token (JWT).
- **Query parameters:**
  - `page`: số trang (mặc định 0).
  - `size`: số lượng bản ghi mỗi trang (mặc định 20, tối đa 100).
- **Thành công:** `200 OK`, mảng `NotificationResponse`.

```json
[
  {
    "id": 1,
    "type": "NEW_REQUEST",
    "title": "Có yêu cầu mới",
    "body": "Trần Thị Bình vừa gửi yêu cầu xin món đồ Giáo trình Toán rời rạc của bạn.",
    "link": "/requests/101",
    "isRead": false,
    "createdAt": "2026-10-12T14:00:00Z"
  }
]
```

- **Lỗi:**
  - `401 UNAUTHORIZED`: Chưa đăng nhập.

---

## 2. POST /api/v1/notifications/{id}/read

Đánh dấu một thông báo đã đọc.

- **Xác thực:** Bắt buộc Bearer Token (JWT) - người gửi phải là chủ sở hữu của thông báo.
- **Path variable:** `id` (ID của thông báo).
- **Thành công:** `200 OK`, trả về `NotificationResponse` với `isRead: true`.
- **Lỗi:**
  - `401 UNAUTHORIZED`: Chưa đăng nhập.
  - `404 NOTIFICATION_NOT_FOUND`: Thông báo không tồn tại hoặc không thuộc quyền sở hữu của người dùng.

---

## 3. GET /api/v1/notifications/unread-count

Lấy số lượng thông báo chưa đọc của tôi (dùng cho icon quả chuông trên Header).

- **Xác thực:** Bắt buộc Bearer Token (JWT).
- **Thành công:** `200 OK`, trả về `UnreadCountResponse`.

```json
{
  "unreadCount": 3
}
```

- **Lỗi:**
  - `401 UNAUTHORIZED`: Chưa đăng nhập.

---

## DTO Models

### NotificationResponse

| Trường | Kiểu | Ý nghĩa |
|---|---|---|
| `id` | number | ID của thông báo |
| `type` | string | Loại thông báo (`POST_APPROVED`, `NEW_REQUEST`, `SELECTED`, `LOGISTICS_CONFIRMED`, ...) |
| `title` | string | Tiêu đề thông báo |
| `body` | string | Nội dung chi tiết |
| `link` | string? | Đường dẫn chuyển hướng trong frontend |
| `isRead` | boolean | Trạng thái đã đọc hay chưa |
| `createdAt` | string | Thời điểm tạo ISO-8601 UTC |

### UnreadCountResponse

| Trường | Kiểu | Ý nghĩa |
|---|---|---|
| `unreadCount` | number | Số lượng thông báo chưa đọc |

---

## Mã lỗi

| Code | HTTP | Khi nào |
|---|---|---|
| `NOTIFICATION_NOT_FOUND` | 404 | Không tìm thấy thông báo hoặc không thuộc về người dùng hiện tại |
| `NOTIFICATION_SEND_FAILED` | 500 | Gửi thông báo hoặc email thất bại |
