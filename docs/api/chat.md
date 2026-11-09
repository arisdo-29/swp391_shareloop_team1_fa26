# API module chat

Quy ước chung: tiền tố `/api/v1`, thời gian ISO-8601 UTC, thành công trả thẳng DTO (không bọc),
lỗi theo định dạng thống nhất bên dưới.

Người sở hữu: BE3 (@dwargon73-sketch). Luật liên quan: CH-01 đến CH-07, GN-05.

## Danh sách cuộc trò chuyện

Không có endpoint riêng: mỗi cuộc trò chuyện gắn với một Request (`requestId`). Màn Tin nhắn dựng danh sách từ
`GET /api/v1/me/requests` (yêu cầu mình gửi) và `GET /api/v1/items/{itemId}/requests` (yêu cầu tới bài của mình),
xem `docs/api/request.md`. Cả hai trả **đủ mọi cuộc, không phân trang**, nên lọc Tất cả / Chưa đọc / Đang giao dịch /
Đã đóng và tìm theo tên món hoặc người làm ở frontend, backend không có tham số `filter`, `q`.

Mỗi `RequestResponse` đã có đủ dữ liệu cho một dòng:

| Cần cho dòng | Trường |
|---|---|
| Đối tác | `donorId`, `donorName`, `donorAvatarUrl` hoặc `receiverId`, `receiverName`, `receiverAvatarUrl` (tuỳ vai người xem) |
| Món đồ | `itemId`, `itemTitle`, `itemImage`, `type` (`GIVE` / `SWAP`) |
| Trạng thái | `status` |
| Tin cuối | `lastMessageAt` |

"Chưa đọc": backend **chưa** lưu mốc đã đọc (bảng `messages` không có cột đã đọc). Frontend so `lastMessageAt` với mốc
đã xem lưu ở trình duyệt, nên trạng thái chưa đọc không đồng bộ giữa các thiết bị.

---

## 1. POST /api/v1/requests/{requestId}/messages

Gửi tin nhắn mới (văn bản, ảnh hoặc chia sẻ thẻ bài đăng) trong khung chat của yêu cầu giao dịch (CH-01, CH-06, UC-24, UC-25).

- **Xác thực:** Bắt buộc Bearer Token (JWT). Chỉ 2 bên của yêu cầu giao dịch (chủ bài đăng hoặc người nhận được chọn).
- **Điều kiện:** Yêu cầu giao dịch phải ở trạng thái từ `RESERVED` trở lên (CH-01).
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
  - **Quy tắc gửi ảnh (CH-07):** Ảnh chỉ được phép gửi sau khi hai bên đã xác nhận lịch hẹn (từ trạng thái `LOGISTICS_CONFIRMED` trở lên). Gửi trước thời điểm này sẽ bị chặn với lỗi `CHAT_IMAGE_BEFORE_LOGISTICS_FORBIDDEN`.
  - **Người nhận trong giao dịch Cho tặng không gửi ảnh:** Khi `Request.type = GIVE` và người gửi là người nhận (`receiverId`), tin nhắn có `mediaId` bị chặn với `403 CHAT_IMAGE_GIVE_RECEIVER_FORBIDDEN`. Chủ bài vẫn gửi ảnh được sau khi chốt lịch. Tin chữ và thẻ bài (`refItemId`) không bị ảnh hưởng. Giao dịch Trao đổi (`SWAP`) giữ nguyên như cũ cho cả hai phía.
- Gửi thẻ bài đăng (CH-06): truyền `refItemId` (id bài đăng muốn chia sẻ), `content` là lời nhắn kèm theo (tuỳ chọn).
  - **Quy tắc chia sẻ thẻ bài (CH-06):** Chỉ chia sẻ được thẻ của bài đăng ở trạng thái đã được duyệt (`APPROVED`). Nếu chia sẻ bài chưa duyệt hoặc không tồn tại sẽ bị từ chối với lỗi tương ứng.
- Trước khi chốt lịch, nội dung chứa thông tin liên hệ (số điện thoại, mạng xã hội, link ngoài) hoặc từ khoá cấm sẽ bị chặn ngay và ghi nhận vi phạm (`CHAT_MESSAGE_BLOCKED`). Theo Business Rule v12 (D-17, CH-04), hệ thống chỉ dùng bộ lọc regex và từ khoá tĩnh tầng 1, không dùng AI kiểm tra tin nhắn.

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
  - `400 BAD_REQUEST` (`CHAT_ITEM_NOT_APPROVED`): Chia sẻ thẻ của bài đăng chưa được duyệt (`APPROVED`) (CH-06).
  - `400 BAD_REQUEST` (`CHAT_MESSAGE_BLOCKED`): Tin nhắn bị chặn do chứa thông tin liên hệ (trước `LOGISTICS_CONFIRMED`) hoặc chứa từ khoá cấm mức `BLOCK` (CH-02, CH-03, CH-05). Nếu một giao dịch có từ 3 lần vi phạm trở lên sẽ tự động gửi cảnh báo tới Admin (CH-05).
  - `401 UNAUTHORIZED`: Chưa đăng nhập.
  - `403 FORBIDDEN` (`CHAT_FORBIDDEN`): Người dùng không thuộc hai bên của giao dịch.
  - `404 NOT_FOUND` (`CHAT_REQUEST_NOT_FOUND`): Yêu cầu giao dịch không tồn tại.
  - `404 NOT_FOUND` (`CHAT_ITEM_NOT_FOUND`): Không tìm thấy bài đăng để chia sẻ thẻ.
  - `409 CONFLICT` (`CHAT_REQUEST_NOT_RESERVED`): Yêu cầu chưa ở trạng thái `RESERVED`.
  - `409 CONFLICT` (`CHAT_IMAGE_BEFORE_LOGISTICS_FORBIDDEN`): Gửi ảnh trước khi hai bên xác nhận lịch hẹn (`LOGISTICS_CONFIRMED`) (CH-07).
  - `403 FORBIDDEN` (`CHAT_IMAGE_GIVE_RECEIVER_FORBIDDEN`): Người nhận trong giao dịch Cho tặng gửi tin có ảnh.

---

## 2. GET /api/v1/requests/{requestId}/messages

Lấy danh sách tin nhắn trong khung chat (hỗ trợ hỏi tin mới theo chu kỳ - polling) (CH-01, UC-24). Các tin nhắn có `refItemId` sẽ được backend tự động ghép thông tin thẻ bài hiện tại qua trường `itemCard`. Chỉ trả về các tin nhắn hợp lệ (`moderation = PASS`), các tin nhắn bị chặn được lưu để kiểm duyệt và cảnh báo Admin, không trả về trong khung chat.

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

Lấy danh sách thẻ sản phẩm ghim ở đầu khung chat (CH-06).
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

## Đề nghị đưa món vào đổi (Cho → Trao đổi trong chat)

Giao dịch Cho đã chọn người (chat đã mở), người nhận muốn đưa một món của mình vào đổi. Chủ bài nhận đề nghị ngay
trong chat; đồng ý thì trả phí đăng Trao đổi (`post_fee`) và giao dịch chuyển thành Trao đổi. Quy tắc chuyển đổi chung
xem `docs/api/request.md`, mục "Chuyển đổi hình thức Cho ↔ Trao đổi".

Đề nghị là một tin nhắn `kind = OFFER_CHANGE`, `refItemId` = món đề nghị, `offerStatus` đi theo vòng:

| Từ | Sang | Khi nào |
|---|---|---|
| (mới) | `PENDING` | Người nhận gửi đề nghị |
| `PENDING` | `ACCEPTED` | Chủ bài đồng ý (giao dịch chuyển thành Trao đổi) |
| `PENDING` | `DECLINED` | Chủ bài từ chối; người nhận gửi đề nghị mới (đề nghị cũ tự huỷ); giao dịch rời giai đoạn thương lượng |

- Mỗi giao dịch có **tối đa một** đề nghị `PENDING`.
- **Tự huỷ:** khi giao dịch sang `LOGISTICS_CONFIRMED` hoặc bị `CANCELLED` (người dùng huỷ, Admin huỷ, khoá tài khoản,
  job quá hạn chưa chốt lịch), mọi đề nghị `PENDING` thành `DECLINED` và chat có thêm một tin `SYSTEM` ngắn. Không gửi
  thông báo cho trường hợp này.
- Frontend tìm đề nghị đang chờ trong `GET /messages`: tin `kind = OFFER_CHANGE` và `offerStatus = PENDING`. Phí hiển
  thị cho chủ bài đọc từ `RequestResponse.conversionFee`, không viết cứng.

## 4. POST /api/v1/requests/{requestId}/offer-changes

Người nhận đề nghị đưa một món của mình vào đổi.

- **Xác thực:** Bắt buộc JWT. **Chỉ người nhận** (`receiverId`) của giao dịch.
- **Điều kiện:**
  - Giao dịch `type = GIVE` và `status` là `RESERVED` hoặc `AWAITING_LOGISTICS`.
  - Món đề nghị: của chính người nhận, `APPROVED`, `is_active`, chưa xoá, khác `itemId` của giao dịch (cùng điều kiện
    CD-01 khi gửi yêu cầu).
  - `content` (nếu có) qua cùng bộ lọc thông tin liên hệ và từ khoá cấm như tin văn bản.
- **Request body:** `ProposeOfferChangeRequest`

```json
{
  "offeredItemId": 60,
  "content": "Mình gửi thêm cái ghế này để đổi nhé"
}
```

- **Hành vi:** Đề nghị `PENDING` cũ (nếu có) chuyển `DECLINED`. Chủ bài nhận thông báo `OFFER_CHANGE_PROPOSED`.
- **Thành công:** `201 Created`, trả về `MessageResponse` (có `itemCard` của món đề nghị).

```json
{
  "id": 31,
  "requestId": 100,
  "senderId": 20,
  "kind": "OFFER_CHANGE",
  "content": "Mình gửi thêm cái ghế này để đổi nhé",
  "mediaId": null,
  "refItemId": 60,
  "itemCard": {
    "id": 60,
    "title": "Ghế xoay văn phòng",
    "offerType": "GIVE",
    "status": "APPROVED",
    "imageUrl": "/api/v1/media/15",
    "donorId": 20
  },
  "offerStatus": "PENDING",
  "moderation": "PASS",
  "blockedReason": null,
  "createdAt": "2026-10-14T15:00:00Z"
}
```

- **Lỗi:**
  - `400 VALIDATION_FAILED`: Thiếu `offeredItemId` hoặc `content` dài quá 500 ký tự.
  - `400 CHAT_MESSAGE_BLOCKED`: Lời nhắn chứa thông tin liên hệ hoặc từ khoá cấm.
  - `400 REQUEST_SELF_OFFER_NOT_ALLOWED`: Món đề nghị chính là bài của giao dịch.
  - `403 CHAT_FORBIDDEN`: Người gọi không phải người nhận (kể cả chủ bài và người ngoài).
  - `404 CHAT_REQUEST_NOT_FOUND`: Giao dịch không tồn tại.
  - `404 REQUEST_ITEM_NOT_FOUND`: Món đề nghị không tồn tại hoặc đã xoá.
  - `409 CHAT_REQUEST_NOT_RESERVED`: Giao dịch chưa mở chat (còn `PENDING`, `REJECTED`).
  - `409 REQUEST_OFFER_CHANGE_NOT_ALLOWED`: Giao dịch đã là `SWAP` hoặc không còn `RESERVED` / `AWAITING_LOGISTICS`.
  - `409 REQUEST_ITEM_NOT_AVAILABLE`: Món đề nghị không phải của người nhận, chưa `APPROVED` hoặc đang bị ẩn.

---

## 5. POST /api/v1/requests/{requestId}/offer-changes/{messageId}/accept

Chủ bài đồng ý đề nghị; giao dịch Cho chuyển thành Trao đổi.

- **Xác thực:** Bắt buộc JWT. **Chỉ chủ bài** (`donorId`).
- **Hành vi** (một transaction, theo thứ tự):
  1. Kiểm lại giao dịch (vẫn `GIVE`, vẫn `RESERVED` / `AWAITING_LOGISTICS`) và món đề nghị (vẫn hợp lệ).
  2. Trừ `post_fee` Credit của chủ bài (`credit_ledger` loại `POST_FEE`). Thiếu tiền thì trả `402` và **không đổi gì**,
     đề nghị vẫn `PENDING`.
  3. Bài chính chuyển `offer_type = SWAP`; giao dịch `type = SWAP`, `offeredItemId` = món đề nghị.
  4. Món đề nghị chuyển `RESERVED`. Lỗi ở bước này thì cả transaction hoàn tác, kể cả tiền.
  5. Các yêu cầu `PENDING` khác dùng cùng món đề nghị bị `CANCELLED` (thông báo `REQUEST_CANCELLED`).
  6. Đề nghị thành `ACCEPTED`; chat có tin `SYSTEM` "Chủ bài đã đồng ý trao đổi với [tên món]. Giao dịch chuyển sang
     Trao đổi."; người nhận nhận thông báo `OFFER_CHANGE_ACCEPTED`.
  - Không kiểm hạn mức bài theo hạng (giống chọn người nhận). Sau khi đồng ý, `GET /pinned-items` ghim đủ 2 món; điểm uy
    tín khi hoàn tất tính theo `SWAP` (CD-07).
- **Thành công:** `200 OK`, trả về `RequestResponse` mới (`type = SWAP`, có `offeredItemId`, `itemOfferType = SWAP`,
  `conversionFee = 0`).
- **Lỗi:**
  - `402 WALLET_INSUFFICIENT_BALANCE`: Chủ bài không đủ Credit khả dụng để trả `post_fee`.
  - `403 CHAT_FORBIDDEN`: Người gọi không phải chủ bài.
  - `404 CHAT_REQUEST_NOT_FOUND`: Giao dịch không tồn tại.
  - `409 CHAT_OFFER_CHANGE_NOT_FOUND`: `messageId` không thuộc giao dịch này hoặc không phải tin `OFFER_CHANGE`.
  - `409 CHAT_OFFER_CHANGE_NOT_PENDING`: Đề nghị đã được trả lời hoặc đã tự huỷ.
  - `409 REQUEST_OFFER_CHANGE_NOT_ALLOWED`: Giao dịch không còn là `GIVE` hoặc đã qua giai đoạn chốt lịch.
  - `404 REQUEST_ITEM_NOT_FOUND`: Món đề nghị đã bị xoá sau khi gửi đề nghị.
  - `409 REQUEST_ITEM_NOT_AVAILABLE` / `409 ITEM_INVALID_STATUS_TRANSITION`: Món đề nghị không còn sẵn sàng (đã bị
    giữ ở giao dịch khác, bị ẩn...).

---

## 6. POST /api/v1/requests/{requestId}/offer-changes/{messageId}/decline

Chủ bài từ chối đề nghị. Không tốn phí, giao dịch vẫn là Cho.

- **Xác thực:** Bắt buộc JWT. **Chỉ chủ bài** (`donorId`).
- **Hành vi:** Đề nghị thành `DECLINED`; chat có tin `SYSTEM` "Chủ bài chưa đồng ý đưa [tên món] vào trao đổi.";
  người nhận nhận thông báo `OFFER_CHANGE_DECLINED`.
- **Thành công:** `200 OK`, trả về `MessageResponse` của đề nghị (`offerStatus = DECLINED`).
- **Lỗi:** `403 CHAT_FORBIDDEN`, `404 CHAT_REQUEST_NOT_FOUND`, `409 CHAT_OFFER_CHANGE_NOT_FOUND`,
  `409 CHAT_OFFER_CHANGE_NOT_PENDING` (cùng ý nghĩa như mục 5).

### Thông báo mới

Theo cách module notification đang dùng (`type` là chuỗi, xem `docs/api/notification.md`); `link` mở thẳng cuộc trò
chuyện của giao dịch.

| `type` | Người nhận | Khi nào | `link` |
|---|---|---|---|
| `OFFER_CHANGE_PROPOSED` | Chủ bài | Người nhận gửi đề nghị đổi món | `/requests/{requestId}` |
| `OFFER_CHANGE_ACCEPTED` | Người nhận | Chủ bài đồng ý, giao dịch chuyển sang Trao đổi | `/requests/{requestId}` |
| `OFFER_CHANGE_DECLINED` | Người nhận | Chủ bài từ chối | `/requests/{requestId}` |

---

## DTO Schemas

### ProposeOfferChangeRequest

| Trường | Kiểu | Bắt buộc | Ràng buộc / Ý nghĩa |
|---|---|---|---|
| `offeredItemId` | number | Có | ID món của người nhận muốn đưa vào đổi |
| `content` | string? | Không | Lời nhắn kèm theo, tối đa 500 ký tự, qua bộ lọc liên hệ và từ khoá cấm |

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
| `senderId` | number? | ID người gửi tin; `null` với tin `SYSTEM` |
| `kind` | string | `TEXT`, `IMAGE`, `ITEM_CARD`, `OFFER_CHANGE`, `SYSTEM` |
| `content` | string? | Nội dung văn bản |
| `mediaId` | number? | ID ảnh đính kèm (nếu `kind == IMAGE`) |
| `refItemId` | number? | ID bài đăng đính kèm (`ITEM_CARD`) hoặc món đề nghị đổi (`OFFER_CHANGE`) |
| `itemCard` | ItemCardResponse? | Dữ liệu thẻ bài hiện tại được backend ghép tự động (cả `ITEM_CARD` và `OFFER_CHANGE`) |
| `offerStatus` | string? | Chỉ có ở `OFFER_CHANGE`: `PENDING`, `ACCEPTED`, `DECLINED`; các loại khác là `null` |
| `moderation` | string | `PASS` hoặc `BLOCKED` |
| `blockedReason` | string? | Lý do nếu tin bị chặn |
| `createdAt` | string | Thời điểm tạo ISO-8601 UTC |
