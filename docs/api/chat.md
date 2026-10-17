# API module chat

Quy ước chung theo Lộ trình mục 3.4: tiền tố `/api/v1`, thời gian ISO-8601 UTC, thành công trả thẳng DTO (không bọc),
lỗi theo định dạng thống nhất bên dưới.

Người sở hữu: BE3 (@dwargon73-sketch). FR-68, FR-69, FR-70, FR-72, FR-75; BR-C01, BR-C02, BR-C03, BR-C05, BR-C07.

---

## 1. POST /api/v1/requests/{requestId}/messages

Gửi tin nhắn mới (văn bản, ảnh hoặc chia sẻ thẻ bài đăng) trong khung chat của yêu cầu giao dịch (FR-68, FR-70, UC-24, UC-25).

- **Xác thực:** Bắt buộc Bearer Token (JWT). Chỉ 2 bên của yêu cầu giao dịch (chủ bài đăng hoặc người nhận được chọn).
- **Điều kiện:** Yêu cầu giao dịch phải ở trạng thái từ `RESERVED` trở lên (BR-C03).
- **Path variable:**
  - `requestId`: ID của yêu cầu giao dịch.
- **Request body:**

```json
{
  "content": "Chào bạn, mình hẹn gặp ở cổng trường nhé!",
  "mediaId": null,
  "refItemId": null
}
```

*Ghi chú:*
- Gửi tin nhắn văn bản: truyền `content`, `mediaId = null`, `refItemId = null`.
- Gửi ảnh: truyền `mediaId` (nhận được từ API tải ảnh `POST /api/v1/media`), `content` là ghi chú/caption tuỳ chọn.
  - **Quy tắc gửi ảnh (FR-75, BR-C05):** Ảnh chỉ được phép gửi sau khi hai bên đã xác nhận lịch hẹn (từ trạng thái `LOGISTICS_CONFIRMED` trở lên). Gửi trước thời điểm này sẽ bị chặn với lỗi `CHAT_IMAGE_BEFORE_LOGISTICS_FORBIDDEN`.
- Gửi thẻ bài đăng (FR-70, BR-C07): truyền `refItemId` (id bài đăng muốn chia sẻ), `content` là lời nhắn kèm theo (tuỳ chọn).
  - **Quy tắc chia sẻ thẻ bài (BR-C07):** Chỉ chia sẻ được thẻ của bài đăng ở trạng thái đã được duyệt (`APPROVED`). Nếu chia sẻ bài chưa duyệt hoặc không tồn tại sẽ bị từ chối với lỗi tương ứng.

- **Thành công:** `201 Created`, trả về `MessageResponse`.

```json
{
  "id": 1,
  "requestId": 100,
  "senderId": 10,
  "kind": "ITEM_CARD",
  "content": "Mình muốn đổi món này, bạn xem thử nhé",
  "mediaId": null,
  "refItemId": 45,
  "itemCard": {
    "id": 45,
    "title": "Bàn học gỗ",
    "offerType": "SWAP",
    "status": "APPROVED",
    "imageUrl": "/api/v1/media/12",
    "donorId": 10
  },
  "offerStatus": null,
  "moderation": "PASS",
  "blockedReason": null,
  "createdAt": "2026-10-14T14:00:00Z"
}
```

- **Lỗi:**
  - `400 BAD_REQUEST` (`CHAT_MESSAGE_EMPTY`): Cả nội dung tin nhắn, ảnh và mã thẻ bài đều rỗng.
  - `400 BAD_REQUEST` (`CHAT_ITEM_NOT_APPROVED`): Chia sẻ thẻ của bài đăng chưa được duyệt (`APPROVED`) (FR-70, BR-C07).
  - `400 BAD_REQUEST` (`CHAT_MESSAGE_BLOCKED`): Tin nhắn bị chặn do chứa thông tin liên hệ (số điện thoại viết bằng số hoặc bằng chữ, email, liên kết) (FR-72, FR-74; BR-C01).
  - `401 UNAUTHORIZED`: Chưa đăng nhập.
  - `403 FORBIDDEN` (`CHAT_FORBIDDEN`): Người dùng không thuộc hai bên của giao dịch.
  - `404 NOT_FOUND` (`CHAT_REQUEST_NOT_FOUND`): Yêu cầu giao dịch không tồn tại.
  - `404 NOT_FOUND` (`CHAT_ITEM_NOT_FOUND`): Không tìm thấy bài đăng để chia sẻ thẻ.
  - `409 CONFLICT` (`CHAT_REQUEST_NOT_RESERVED`): Yêu cầu chưa ở trạng thái `RESERVED`.
  - `409 CONFLICT` (`CHAT_IMAGE_BEFORE_LOGISTICS_FORBIDDEN`): Gửi ảnh trước khi hai bên xác nhận lịch hẹn (`LOGISTICS_CONFIRMED`) (FR-75, BR-C05).

---

## 2. GET /api/v1/requests/{requestId}/messages

Lấy danh sách tin nhắn trong khung chat (hỗ trợ hỏi tin mới theo chu kỳ - polling) (FR-68, UC-24). Các tin nhắn có `refItemId` sẽ được backend tự động ghép thông tin thẻ bài hiện tại qua trường `itemCard`.

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
    "itemCard": null,
    "offerStatus": null,
    "moderation": "PASS",
    "blockedReason": null,
    "createdAt": "2026-10-14T14:00:00Z"
  },
  {
    "id": 2,
    "requestId": 100,
    "senderId": 20,
    "kind": "ITEM_CARD",
    "content": "Món đề nghị đây bạn nhé",
    "mediaId": null,
    "refItemId": 60,
    "itemCard": {
      "id": 60,
      "title": "Ghế xoay văn phòng",
      "offerType": "SWAP",
      "status": "APPROVED",
      "imageUrl": "/api/v1/media/15",
      "donorId": 20
    },
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

---

## 3. GET /api/v1/requests/{requestId}/pinned-items

Lấy danh sách thẻ sản phẩm ghim ở đầu khung chat (FR-69, BR-C02).
- Nếu yêu cầu là bài Cho Tặng (`GIVE`): ghim 1 thẻ sản phẩm của bài đăng chính.
- Nếu yêu cầu là bài Trao Đổi (`SWAP`): ghim cả 2 thẻ sản phẩm (bài chính của chủ bài và món đề nghị của người nhận).

- **Xác thực:** Bắt buộc Bearer Token (JWT). Chỉ 2 bên của yêu cầu giao dịch.
- **Điều kiện:** Yêu cầu giao dịch phải ở trạng thái từ `RESERVED` trở lên.
- **Path variable:**
  - `requestId`: ID của yêu cầu giao dịch.
- **Thành công:** `200 OK`, mảng `ItemCardResponse`.

```json
[
  {
    "id": 45,
    "title": "Bàn học gỗ",
    "offerType": "SWAP",
    "status": "RESERVED",
    "imageUrl": "/api/v1/media/12",
    "donorId": 10
  },
  {
    "id": 60,
    "title": "Ghế xoay văn phòng",
    "offerType": "SWAP",
    "status": "RESERVED",
    "imageUrl": "/api/v1/media/15",
    "donorId": 20
  }
]
```

- **Lỗi:**
  - `401 UNAUTHORIZED`: Chưa đăng nhập.
  - `403 FORBIDDEN` (`CHAT_FORBIDDEN`): Người dùng không thuộc hai bên của giao dịch.
  - `404 NOT_FOUND` (`CHAT_REQUEST_NOT_FOUND`): Yêu cầu giao dịch không tồn tại.
  - `409 CONFLICT` (`CHAT_REQUEST_NOT_RESERVED`): Yêu cầu chưa ở trạng thái `RESERVED`.

---

## DTO Schemas

### SendMessageRequest

| Trường | Kiểu | Bắt buộc | Ràng buộc / Ý nghĩa |
|---|---|---|---|
| `content` | string? | Không | Nội dung tin nhắn (tối đa 2000 ký tự) |
| `mediaId` | number? | Không | ID ảnh đã tải lên từ MediaService (chỉ từ `LOGISTICS_CONFIRMED`) |
| `refItemId` | number? | Không | ID bài đăng muốn chia sẻ thẻ bài (phải ở trạng thái `APPROVED`) |

### ItemCardResponse / ItemCardDto

| Trường | Kiểu | Ý nghĩa |
|---|---|---|
| `id` | number | ID bài đăng |
| `title` | string | Tiêu đề món đồ |
| `offerType` | string | `GIVE` hoặc `SWAP` |
| `status` | string | Trạng thái bài đăng (`APPROVED`, `RESERVED`, ...) |
| `imageUrl` | string? | Đường dẫn ảnh đầu tiên của món đồ |
| `donorId` | number | ID người đăng món đồ |

### MessageResponse

| Trường | Kiểu | Ý nghĩa |
|---|---|---|
| `id` | number | ID tin nhắn |
| `requestId` | number | ID yêu cầu giao dịch |
| `senderId` | number | ID người gửi tin |
| `kind` | string | `TEXT`, `IMAGE`, `ITEM_CARD`, `OFFER_CHANGE`, `SYSTEM` |
| `content` | string? | Nội dung văn bản |
| `mediaId` | number? | ID ảnh đính kèm (nếu `kind == IMAGE`) |
| `refItemId` | number? | ID bài đăng đính kèm (nếu `kind == ITEM_CARD`) |
| `itemCard` | ItemCardResponse? | Dữ liệu thẻ bài hiện tại được backend ghép tự động |
| `offerStatus` | string? | Trạng thái đề xuất đổi món (`PENDING`, `ACCEPTED`, `DECLINED`) |
| `moderation` | string | `PASS` hoặc `BLOCKED` |
| `blockedReason` | string? | Lý do nếu tin bị chặn |
| `createdAt` | string | Thời điểm tạo ISO-8601 UTC |
