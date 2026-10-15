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

## 5. POST /api/v1/requests/{id}/select

Chủ bài đăng chọn đúng một người nhận cho bài đăng (F10 bước 2, FR-66, BR-D01).

- **Xác thực:** Bắt buộc JWT (phải là chủ bài đăng `donorId`).
- **Path variable:** `id` (id của request được chọn).
- **Hành vi nghiệp vụ:**
  - Request được chọn chuyển sang trạng thái `RESERVED` (`reservedAt` được ghi nhận).
  - Tất cả các Request khác gửi tới cùng bài đăng đang ở trạng thái `PENDING` sẽ tự động chuyển sang `REJECTED`.
  - Bài đăng chuyển sang trạng thái `RESERVED` qua `ItemService.reserve(itemId)`.
  - Người nhận được chọn nhận thông báo `REQUEST_ACCEPTED`.
  - Các người nhận bị từ chối nhận thông báo `REQUEST_REJECTED`.
- **Thành công:** `200 OK`, trả về `RequestResponse` của yêu cầu được chọn (`status = RESERVED`).
- **Lỗi:**
  - `403 REQUEST_FORBIDDEN`: Không phải chủ bài đăng.
  - `404 REQUEST_NOT_FOUND`: Không tìm thấy yêu cầu.
  - `404 REQUEST_ITEM_NOT_FOUND`: Không tìm thấy bài đăng liên quan.
  - `409 REQUEST_INVALID_STATE_TRANSITION`: Yêu cầu không còn ở trạng thái `PENDING` để chọn.

---

## 6. GET /api/v1/requests/{id}

Xem chi tiết thông tin của một yêu cầu giao dịch (F10, UC-26).

- **Xác thực:** Bắt buộc JWT (phải là chủ bài đăng `donorId` hoặc người nhận `receiverId`).
- **Path variable:** `id` (id của request).
- **Thành công:** `200 OK`, trả về `RequestResponse`.
- **Lỗi:**
  - `403 REQUEST_FORBIDDEN`: Không phải thành viên tham gia giao dịch này.
  - `404 REQUEST_NOT_FOUND`: Không tìm thấy yêu cầu.

---

## 7. POST /api/v1/requests/{id}/schedule

Đề xuất hoặc cập nhật lịch hẹn giao nhận (FR-79, FR-80, BR-T01, BR-T02, UC-26).

- **Xác thực:** Bắt buộc JWT (chủ bài đăng hoặc người nhận).
- **Path variable:** `id` (id của request).
- **Request body:**

```json
{
  "deliveryMethod": "IN_PERSON", // "IN_PERSON" hoặc "SHIPPING"
  "meetingPlace": "Cổng trường ĐH FPT",
  "meetingTime": "2026-10-20T10:00:00Z",
  "agreeToWaiver": true // Tuỳ chọn, bắt buộc khi xác nhận Shipping
}
```

- **Hành vi nghiệp vụ:**
  - Request nếu đang `RESERVED` sẽ tự động chuyển sang `AWAITING_LOGISTICS`.
  - Nếu Request đã ở `LOGISTICS_CONFIRMED` trở lên thì bị khoá không cho sửa lịch (FR-83, BR-T05).
  - Thời gian hẹn phải ở tương lai.
  - Bên gửi đề xuất tự động được ghi nhận đồng ý lịch mới (`donorScheduleOkAt` hoặc `receiverScheduleOkAt`), bên còn lại bị reset để xác nhận lại.
  - Gửi thông báo `SCHEDULE_UPDATED` cho bên còn lại.
- **Thành công:** `200 OK`, trả về `RequestResponse`.
- **Lỗi:**
  - `400 REQUEST_SCHEDULE_PAST_TIME`: Thời gian hẹn ở quá khứ.
  - `403 REQUEST_FORBIDDEN`: Không phải thành viên tham gia giao dịch.
  - `404 REQUEST_NOT_FOUND`: Không tìm thấy yêu cầu.
  - `409 REQUEST_SCHEDULE_LOCKED`: Lịch hẹn đã chốt, không thể chỉnh sửa.

---

## 8. POST /api/v1/requests/{id}/confirm-logistics

Xác nhận lịch hẹn giao nhận và cam kết trách nhiệm (FR-80, FR-81, BR-T03, BR-T05, UC-26).

- **Xác thực:** Bắt buộc JWT (chủ bài đăng hoặc người nhận).
- **Path variable:** `id` (id của request).
- **Request body:**

```json
{
  "agreeToWaiver": true // Bắt buộc true nếu deliveryMethod là SHIPPING
}
```

- **Hành vi nghiệp vụ:**
  - Ghi nhận bên gọi đã đồng ý lịch hẹn.
  - Với hình thức `SHIPPING`, bắt buộc phải đồng ý cam kết trách nhiệm (BR-T03, BR-T04: hệ thống không can thiệp hay bồi thường hàng hoá và phí vận chuyển).
  - Khi cả hai bên đều đã đồng ý lịch (và cam kết trách nhiệm nếu là `SHIPPING`), Request tự động chuyển sang `LOGISTICS_CONFIRMED` và ghi nhận `contactRevealedAt`.
  - Gửi thông báo `LOGISTICS_CONFIRMED` cho đối phương.
- **Thành công:** `200 OK`, trả về `RequestResponse`.
- **Lỗi:**
  - `400 REQUEST_SCHEDULE_NOT_SET`: Chưa có lịch hẹn để xác nhận.
  - `400 REQUEST_WAIVER_REQUIRED`: Chưa đồng ý cam kết trách nhiệm khi chọn giao hàng (Shipping).
  - `403 REQUEST_FORBIDDEN`: Không phải thành viên tham gia giao dịch.
  - `404 REQUEST_NOT_FOUND`: Không tìm thấy yêu cầu.
  - `409 REQUEST_INVALID_STATE_TRANSITION`: Trạng thái yêu cầu không phải AWAITING_LOGISTICS.

---

## 9. GET /api/v1/requests/{id}/contact

Lấy thông tin liên lạc (họ tên, email, số điện thoại) của đối tác khi giao dịch đã chốt lịch hẹn (FR-82, BR-T11, BR-T12, UC-26, UC-62).

- **Xác thực:** Bắt buộc JWT (chủ bài đăng hoặc người nhận).
- **Path variable:** `id` (id của request).
- **Hành vi nghiệp vụ:**
  - Chỉ thành viên trong giao dịch mới có quyền xem thông tin của đối phương.
  - Chỉ trả về khi Request đã chốt lịch hẹn (tức `contactRevealedAt != null`, từ `LOGISTICS_CONFIRMED` trở lên).
  - Lấy thông tin qua `UserService.findContact(partnerId)`.
  - Một khi đã chốt lịch và trao thông tin, thông tin này không bị ẩn lại (BR-T12).
- **Thành công:** `200 OK`, trả về `RequestContactResponse`:

```json
{
  "userId": 10,
  "fullName": "Nguyễn Văn B",
  "email": "nguyenvanb@example.com",
  "phone": "0912345678"
}
```

- **Lỗi:**
  - `403 REQUEST_FORBIDDEN`: Không phải thành viên tham gia giao dịch này.
  - `404 REQUEST_NOT_FOUND`: Không tìm thấy yêu cầu.
  - `409 REQUEST_CONTACT_NOT_REVEALED`: Chưa chốt lịch hẹn (chưa đạt `LOGISTICS_CONFIRMED`).

---

## 10. POST /api/v1/requests/{id}/confirm-handover

Xác nhận đã giao / đã nhận đồ và câu hỏi "đúng mô tả?" (FR-84, FR-85, BR-D02, BR-R05, UC-27).

- **Xác thực:** Bắt buộc JWT (chủ bài đăng `donorId` hoặc người nhận `receiverId`).
- **Path variable:** `id` (id của request).
- **Request body:**

```json
{
  "asDescribed": true,
  "notAsDescribedReason": "Món đồ bị vỡ một góc không giống trong ảnh" // Bắt buộc nếu người nhận chọn asDescribed = false
}
```

- **Hành vi nghiệp vụ:**
  - Chỉ áp dụng khi Request đang ở trạng thái `LOGISTICS_CONFIRMED` hoặc `AWAITING_HANDOVER`.
  - Bên cho xác nhận -> ghi nhận `donorHandoverAt`.
  - Bên nhận xác nhận -> bắt buộc trả lời `asDescribed` (nếu chọn `false` bắt buộc có lý do); ghi nhận `receiverHandoverAt` và `asDescribed`.
  - Một bên xác nhận: Request chuyển sang trạng thái `AWAITING_HANDOVER` (BR-D02).
  - Cả hai bên đều đã xác nhận:
    - Request chuyển sang trạng thái `COMPLETED` và ghi nhận `completedAt`.
    - Bài đăng chuyển sang trạng thái `TRADED` qua `ItemService.markTraded(itemId)` (nếu là SWAP thì cả 2 bài đều chuyển sang TRADED).
    - Gửi sự kiện tới `ReputationService.onCompleted(requestId)` để xử lý uy tín.
    - Gửi thông báo `TRANSACTION_COMPLETED` cho cả hai bên.
- **Thành công:** `200 OK`, trả về `RequestResponse`.
- **Lỗi:**
  - `400 REQUEST_AS_DESCRIBED_REQUIRED`: Bên nhận chưa trả lời câu hỏi món đồ có đúng mô tả không.
  - `400 REQUEST_NOT_AS_DESCRIBED_REASON_REQUIRED`: Thiếu lý do khi chọn không đúng mô tả.
  - `403 REQUEST_FORBIDDEN`: Không phải thành viên tham gia giao dịch.
  - `404 REQUEST_NOT_FOUND`: Không tìm thấy yêu cầu.
  - `409 REQUEST_INVALID_STATE_TRANSITION`: Yêu cầu chưa chốt lịch hoặc đã hoàn thành.
  - `409 REQUEST_HANDOVER_ALREADY_CONFIRMED`: Bên gọi đã xác nhận giao nhận trước đó rồi.

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
| `status` | string | Trạng thái (`PENDING`, `RESERVED`, `AWAITING_LOGISTICS`, `LOGISTICS_CONFIRMED`, `AWAITING_HANDOVER`, `COMPLETED`, ...) |
| `deliveryMethod` | string? | `IN_PERSON` hoặc `SHIPPING` |
| `meetingPlace` | string? | Địa điểm hẹn giao nhận |
| `meetingTime` | string? | Thời gian hẹn ISO-8601 UTC |
| `donorScheduleOkAt` | string? | Thời điểm chủ bài xác nhận lịch hẹn ISO-8601 UTC |
| `receiverScheduleOkAt` | string? | Thời điểm người nhận xác nhận lịch hẹn ISO-8601 UTC |
| `donorWaiverAt` | string? | Thời điểm chủ bài xác nhận cam kết trách nhiệm ISO-8601 UTC |
| `receiverWaiverAt` | string? | Thời điểm người nhận xác nhận cam kết trách nhiệm ISO-8601 UTC |
| `contactRevealedAt` | string? | Thời điểm mở thông tin liên lạc ISO-8601 UTC |
| `donorHandoverAt` | string? | Thời điểm người giao xác nhận đã giao ISO-8601 UTC |
| `receiverHandoverAt` | string? | Thời điểm người nhận xác nhận đã nhận ISO-8601 UTC |
| `asDescribed` | boolean? | Người nhận đánh giá món đồ đúng mô tả hay không |
| `completedAt` | string? | Thời điểm hoàn tất giao dịch ISO-8601 UTC |
| `createdAt` | string | Thời điểm tạo ISO-8601 UTC |

### ScheduleRequest

| Trường | Kiểu | Bắt buộc | Ràng buộc |
|---|---|---|---|
| `deliveryMethod` | string | Có | `IN_PERSON` hoặc `SHIPPING` |
| `meetingPlace` | string | Có | Tối đa 255 ký tự |
| `meetingTime` | string | Có | Thời gian hẹn ISO-8601 UTC ở tương lai |
| `agreeToWaiver` | boolean | Không | Bắt buộc true nếu là SHIPPING |

### ConfirmLogisticsRequest

| Trường | Kiểu | Bắt buộc | Ràng buộc |
|---|---|---|---|
| `agreeToWaiver` | boolean | Không | Bắt buộc true nếu là SHIPPING |

### HandoverConfirmRequest

| Trường | Kiểu | Bắt buộc | Ràng buộc |
|---|---|---|---|
| `asDescribed` | boolean | Bên nhận: Có | Bắt buộc với bên nhận |
| `notAsDescribedReason` | string | Không | Bắt buộc nếu `asDescribed = false` |

### RequestContactResponse

| Trường | Kiểu | Ý nghĩa |
|---|---|---|
| `userId` | number | Id người dùng đối tác |
| `fullName` | string | Họ tên hiển thị của đối tác |
| `email` | string | Email liên lạc của đối tác |
| `phone` | string | Số điện thoại của đối tác |

---

## Mã lỗi

| code | HTTP | Khi nào |
|---|---|---|
| `REQUEST_NOT_FOUND` | 404 | Không tìm thấy yêu cầu |
| `REQUEST_ITEM_NOT_FOUND` | 404 | Không tìm thấy bài đăng liên quan |
| `REQUEST_SELF_OFFER_NOT_ALLOWED` | 400 | Tự gửi yêu cầu cho bài của chính mình (BR-R01) |
| `REQUEST_LIMIT_EXCEEDED` | 400 | Vượt quá 5 yêu cầu đang chờ xử lý (BR-U02) |
| `REQUEST_SWAP_OFFERED_ITEM_REQUIRED` | 400 | Thiếu món đề nghị khi gửi yêu cầu đổi đồ (BR-S02) |
| `REQUEST_SCHEDULE_PAST_TIME` | 400 | Thời gian hẹn ở trong quá khứ |
| `REQUEST_WAIVER_REQUIRED` | 400 | Chưa đồng ý cam kết miễn trừ trách nhiệm khi chọn Ship (BR-T03) |
| `REQUEST_SCHEDULE_NOT_SET` | 400 | Chưa có thông tin lịch hẹn để xác nhận |
| `REQUEST_AS_DESCRIBED_REQUIRED` | 400 | Bên nhận chưa trả lời câu hỏi món đồ có đúng mô tả hay không (BR-R05) |
| `REQUEST_NOT_AS_DESCRIBED_REASON_REQUIRED` | 400 | Thiếu lý do khi bên nhận chọn không đúng mô tả (BR-R05) |
| `REQUEST_ITEM_NOT_AVAILABLE` | 409 | Bài đăng không ở trạng thái sẵn sàng (chưa duyệt, đã trao đổi...) |
| `REQUEST_ALREADY_EXISTS` | 409 | Đã có một yêu cầu đang hoạt động cho bài này (BR-R02) |
| `REQUEST_SCHEDULE_LOCKED` | 409 | Lịch hẹn đã chốt và bị khoá (FR-83, BR-T05) |
| `REQUEST_CONTACT_NOT_REVEALED` | 409 | Chưa chốt lịch hẹn để xem thông tin liên lạc (FR-82, BR-T11) |
| `REQUEST_HANDOVER_ALREADY_CONFIRMED` | 409 | Bạn đã xác nhận giao nhận cho yêu cầu này rồi |
| `REQUEST_INVALID_STATE_TRANSITION` | 409 | Chuyển đổi trạng thái không hợp lệ theo sơ đồ |
| `REQUEST_FORBIDDEN` | 403 | Không có quyền thao tác trên yêu cầu này |
