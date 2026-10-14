# API module chat

Quy ước chung theo Lộ trình mục 3.4: tiền tố `/api/v1`, thời gian ISO-8601 UTC, thành công trả thẳng DTO (không bọc),
lỗi theo định dạng thống nhất bên dưới.

Người sở hữu: BE3 (@dwargon73-sketch). FR-68, FR-72; BR-C01, BR-C03.

---

## 1. POST /api/v1/requests/{requestId}/messages

Gửi tin nhắn mới trong khung chat của yêu cầu giao dịch (FR-68, UC-24).

- **Xác thực:** Bắt buộc Bearer Token (JWT). Chỉ 2 bên của yêu cầu giao dịch (chủ bài đăng hoặc người nhận được chọn).
- **Điều kiện:** Yêu cầu giao dịch phải ở trạng thái từ `RESERVED` trở lên (BR-C03).
- **Path variable:**
  - `requestId`: ID của yêu cầu giao dịch.
- **Request body:**

```json
{
  "content": "Chào bạn, mình hẹn gặp ở cổng trường nhé!"
}
```

- **Thành công:** `201 Created`, trả về `MessageResponse`.

```json
{
  "id": 1,
  "requestId": 100,
  "senderId": 10,
  "kind": "TEXT",
  "content": "Chào bạn, mình hẹn gặp ở cổng trường nhé!",
  "mediaId": null,
  "refItemId": null,
  "offerStatus": null,
  "moderation": "PASS",
  "blockedReason": null,
  "createdAt": "2026-10-14T14:00:00Z"
}
```

- **Lỗi:**
  - `400 BAD_REQUEST` (`CHAT_MESSAGE_EMPTY`): Nội dung tin nhắn rỗng.
  - `401 UNAUTHORIZED`: Chưa đăng nhập.
  - `403 FORBIDDEN` (`CHAT_FORBIDDEN`): Người dùng không thuộc hai bên của giao dịch.
  - `404 NOT_FOUND` (`CHAT_REQUEST_NOT_FOUND`): Yêu cầu giao dịch không tồn tại.
  - `409 CONFLICT` (`CHAT_REQUEST_NOT_RESERVED`): Yêu cầu chưa ở trạng thái `RESERVED`.

---

## 2. GET /api/v1/requests/{requestId}/messages

Lấy danh sách tin nhắn trong khung chat (hỗ trợ hỏi tin mới theo chu kỳ - polling) (FR-68, UC-24).

- **Xác thực:** Bắt buộc Bearer Token (JWT). Chỉ 2 bên của yêu cầu giao dịch.
- **Điều kiện:** Yêu cầu giao dịch phải ở trạng thái từ `RESERVED` trở lên.
- **Path variable:**
  - `requestId`: ID của yêu cầu giao dịch.
- **Query parameters:**
  - `afterId` *(tuỳ chọn)*: ID của tin nhắn mốc cuối cùng client đã nhận. Nếu truyền, server chỉ trả về các tin nhắn có `id > afterId` (dùng cho polling).
- **Thành công:** `200 OK`, mảng `MessageResponse` sắp xếp theo thời gian tăng dần (`id ASC`).

```json
[
  {
    "id": 1,
    "requestId": 100,
    "senderId": 10,
    "kind": "TEXT",
    "content": "Chào bạn, mình hẹn gặp ở cổng trường nhé!",
    "mediaId": null,
    "refItemId": null,
    "offerStatus": null,
    "moderation": "PASS",
    "blockedReason": null,
    "createdAt": "2026-10-14T14:00:00Z"
  },
  {
    "id": 2,
    "requestId": 100,
    "senderId": 20,
    "kind": "TEXT",
    "content": "Ok bạn, 5h chiều nay nhé.",
    "mediaId": null,
    "refItemId": null,
    "offerStatus": null,
    "moderation": "PASS",
    "blockedReason": null,
    "createdAt": "2026-10-14T14:02:00Z"
  }
]
```

- **Lỗi:**
  - `401 UNAUTHORIZED`: Chưa đăng nhập.
  - `403 FORBIDDEN` (`CHAT_FORBIDDEN`): Người dùng không thuộc hai bên của giao dịch.
  - `404 NOT_FOUND` (`CHAT_REQUEST_NOT_FOUND`): Yêu cầu giao dịch không tồn tại.
  - `409 CONFLICT` (`CHAT_REQUEST_NOT_RESERVED`): Yêu cầu chưa ở trạng thái `RESERVED`.
