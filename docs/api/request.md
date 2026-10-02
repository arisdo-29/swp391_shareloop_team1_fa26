# API module request

Quy ước chung theo Lộ trình mục 3.4: tiền tố `/api/v1`, thời gian ISO-8601 UTC, thành công trả thẳng DTO (không bọc),
lỗi theo định dạng thống nhất bên dưới.

Người sở hữu: BE3 (@dwargon73-sketch).

---

## 1. POST /api/v1/items/{itemId}/requests

Gửi yêu cầu xin đồ (với bài Give) hoặc đề nghị trao đổi đồ (với bài Swap) (F10 bước 1, UC-20).

- **Xác thực:** Bắt buộc JWT (`Member`).
- **Path variable:** `itemId` (id của bài đăng muốn xin/đổi).
- **Request body:**

```json
{
  "offeredItemId": 12 // Bắt buộc nếu bài đăng là SWAP; để null nếu là GIVE
}
```

- **Thành công:** `201 Created`, trả về `RequestResponse`.

```json
{
  "id": 101,
  "itemId": 45,
  "receiverId": 12,
  "offeredItemId": null,
  "type": "GIVE",
  "status": "PENDING",
  "deliveryMethod": null,
  "meetingPlace": null,
  "meetingTime": null,
  "createdAt": "2026-10-02T14:00:00Z"
}
```

- **Lỗi:**
  - `400 REQUEST_SELF_OFFER_NOT_ALLOWED`: Gửi yêu cầu tới bài của chính mình (BR-R01).
  - `400 REQUEST_LIMIT_EXCEEDED`: Đã có 5 yêu cầu đang ở trạng thái PENDING (BR-U02).
  - `400 REQUEST_SWAP_OFFERED_ITEM_REQUIRED`: Bài đăng là Trao đổi nhưng không đính kèm món đề nghị (BR-S02).
  - `404 REQUEST_ITEM_NOT_FOUND`: Không tìm thấy bài đăng hoặc bài đã bị xoá/gỡ.
  - `409 REQUEST_ITEM_NOT_AVAILABLE`: Bài đăng không ở trạng thái sẵn sàng nhận yêu cầu (không phải APPROVED).
  - `409 REQUEST_ALREADY_EXISTS`: Đã có yêu cầu đang hoạt động cho bài này (BR-R02).

---

## 2. GET /api/v1/items/{itemId}/requests

Chủ bài xem danh sách các yêu cầu gửi tới bài đăng của mình (F10 bước 2, UC-22).

- **Xác thực:** Bắt buộc JWT (phải là chủ bài đăng).
- **Path variable:** `itemId` (id bài đăng).
- **Thành công:** `200 OK`, mảng `RequestResponse` (sắp xếp theo thời gian tạo mới nhất lên trước).
- **Lỗi:**
  - `403 REQUEST_FORBIDDEN`: Không phải chủ bài đăng.
  - `404 REQUEST_ITEM_NOT_FOUND`: Bài đăng không tồn tại.

---

## 3. GET /api/v1/me/requests

Xem danh sách tất cả các yêu cầu do chính mình gửi đi (UC-20).

- **Xác thực:** Bắt buộc JWT.
- **Thành công:** `200 OK`, mảng `RequestResponse`.

---

## 4. POST /api/v1/requests/{id}/cancel

Người gửi tự huỷ yêu cầu khi yêu cầu còn ở trạng thái `PENDING` (F10, BR-R04, UC-21).

- **Xác thực:** Bắt buộc JWT (phải là người tạo yêu cầu `receiverId`).
- **Path variable:** `id` (id của request).
- **Thành công:** `200 OK`, trả về `RequestResponse` với `status = CANCELLED`.
- **Lỗi:**
  - `403 REQUEST_FORBIDDEN`: Không phải người tạo yêu cầu.
  - `404 REQUEST_NOT_FOUND`: Không tìm thấy yêu cầu.
  - `409 REQUEST_INVALID_STATE_TRANSITION`: Yêu cầu không còn ở trạng thái PENDING để huỷ.

---

## DTO Models

### RequestResponse

| Trường | Kiểu | Ý nghĩa |
|---|---|---|
| `id` | number | Id của yêu cầu |
| `itemId` | number | Id bài đăng được xin / đề nghị đổi |
| `receiverId` | number | Id người gửi yêu cầu |
| `offeredItemId` | number? | Id bài đăng đề nghị đem đổi (SWAP) |
| `type` | string | `GIVE` hoặc `SWAP` |
| `status` | string | Trạng thái (`PENDING`, `RESERVED`, `COMPLETED`, ...) |
| `deliveryMethod` | string? | `IN_PERSON` hoặc `SHIPPING` |
| `meetingPlace` | string? | Địa điểm hẹn giao nhận |
| `meetingTime` | string? | Thời gian hẹn ISO-8601 UTC |
| `createdAt` | string | Thời điểm tạo ISO-8601 UTC |

---

## Mã lỗi

| code | HTTP | Khi nào |
|---|---|---|
| `REQUEST_NOT_FOUND` | 404 | Không tìm thấy yêu cầu |
| `REQUEST_ITEM_NOT_FOUND` | 404 | Không tìm thấy bài đăng liên quan |
| `REQUEST_SELF_OFFER_NOT_ALLOWED` | 400 | Tự gửi yêu cầu cho bài của chính mình (BR-R01) |
| `REQUEST_LIMIT_EXCEEDED` | 400 | Vượt quá 5 yêu cầu đang chờ xử lý (BR-U02) |
| `REQUEST_SWAP_OFFERED_ITEM_REQUIRED` | 400 | Thiếu món đề nghị khi gửi yêu cầu đổi đồ (BR-S02) |
| `REQUEST_ITEM_NOT_AVAILABLE` | 409 | Bài đăng không ở trạng thái sẵn sàng (chưa duyệt, đã trao đổi...) |
| `REQUEST_ALREADY_EXISTS` | 409 | Đã có một yêu cầu đang hoạt động cho bài này (BR-R02) |
| `REQUEST_INVALID_STATE_TRANSITION` | 409 | Chuyển đổi trạng thái không hợp lệ theo sơ đồ |
| `REQUEST_FORBIDDEN` | 403 | Không có quyền thao tác trên yêu cầu này |
