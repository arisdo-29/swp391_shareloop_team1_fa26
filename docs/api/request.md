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
  - `403 REQUEST_REPUTATION_BLOCKED`: Tài khoản có 0 sao uy tín, bị vô hiệu hoá quyền gửi yêu cầu giao dịch (BR-U05, FR-92).
  - `404 REQUEST_ITEM_NOT_FOUND`: Không tìm thấy bài đăng hoặc bài đã bị xoá/gỡ.
  - `409 REQUEST_ITEM_NOT_AVAILABLE`: Bài đăng không ở trạng thái sẵn sàng nhận yêu cầu (không phải APPROVED).
  - `409 REQUEST_ALREADY_EXISTS`: Đã có yêu cầu đang hoạt động cho bài này (BR-R02).

---

## 2. GET /api/v1/items/{itemId}/requests

Chủ bài xem danh sách các yêu cầu gửi tới bài đăng của mình (F10 bước 2, FR-65, UC-22).

- **Xác thực:** Bắt buộc JWT (phải là chủ bài đăng).
- **Path variable:** `itemId` (id bài đăng).
- **Thành công:** `200 OK`, mảng `RequestResponse` (sắp xếp theo thời gian tạo mới nhất lên trước). Mỗi phần tử đính kèm thông tin uy tín của người gửi yêu cầu: `receiverTrustStars`, `receiverRank`, `receiverAsDescribedRate` (đồng thời hỗ trợ alias `trustStars`, `rank`, `asDescribedRate`).
- **Ví dụ Response:**

```json
[
  {
    "id": 101,
    "itemId": 45,
    "receiverId": 12,
    "receiverName": "Nguyễn Văn A",
    "receiverTrustStars": 5,
    "receiverRank": "BRONZE",
    "receiverAsDescribedRate": 1.0,
    "trustStars": 5,
    "rank": "BRONZE",
    "asDescribedRate": 1.0,
    "offeredItemId": null,
    "type": "GIVE",
    "status": "PENDING",
    "deliveryMethod": null,
    "meetingPlace": null,
    "meetingTime": null,
    "createdAt": "2026-10-02T14:00:00Z"
  }
]
```

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
  - Bài đăng chuyển sang trạng thái `RESERVED` qua `ItemService.reserve(itemId)`. Với yêu cầu `SWAP`, món mang đổi
    (`offeredItemId`) cũng chuyển `RESERVED` trong cùng transaction; nếu món đó không còn `APPROVED` thì cả lần chọn bị
    hoàn tác và trả `409 ITEM_INVALID_STATUS_TRANSITION`.
  - Khi giao dịch bị huỷ (người dùng, job hết hạn), cả hai món được nhả về `APPROVED`.
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
  "notAsDescribedReason": "Món đồ bị vỡ một góc không giống trong ảnh", // Bắt buộc nếu người nhận chọn asDescribed = false
  "mediaIds": [1, 2] // Tuỳ chọn: danh sách ID ảnh/video minh chứng khi giao nhận (FR-86, BR-T09)
}
```

- **Hành vi nghiệp vụ:**
  - Chỉ áp dụng khi Request đang ở trạng thái `LOGISTICS_CONFIRMED` hoặc `AWAITING_HANDOVER`.
  - Nếu có truyền `mediaIds`: gọi `MediaService.attach(mediaIds, currentUserId)` và lưu vào `request_media_mappings` với stage `HANDOVER` (FR-86, BR-T09).
  - Bên cho xác nhận -> ghi nhận `donorHandoverAt`.
  - Bên nhận xác nhận -> bắt buộc trả lời `asDescribed` (nếu chọn `false` bắt buộc có lý do); ghi nhận `receiverHandoverAt` và `asDescribed`.
  - Bên nhận chọn `asDescribed = false`: sau khi giao dịch commit, `RequestService` phát event `NotAsDescribedReportedEvent` (`requestId`, `receiverId`, `reason`) và module report tự mở một report `NOT_AS_DESCRIBED`, mô tả là `notAsDescribedReason` (FR-85, BR-R05, xem [report.md](report.md)). Request không gọi thẳng `ReportService` để tránh vòng phụ thuộc. Lỗi mở report không làm mất câu trả lời của bên nhận, chỉ được ghi log.
  - Giao dịch đã có report thì job tự hoàn tất sau `auto_confirm_days` ngày bỏ qua, không tự `COMPLETED` (FR-87, BR-T06). Giao dịch ở lại `AWAITING_HANDOVER` cho đến khi bên còn lại tự xác nhận.
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

## 11. POST /api/v1/requests/{id}/media

Đính kèm ảnh hoặc video minh chứng vào yêu cầu giao dịch (FR-86, BR-T09, UC-28).

- **Xác thực:** Bắt buộc JWT (chủ bài đăng `donorId` hoặc người nhận `receiverId`).
- **Path variable:** `id` (id của request).
- **Request body:**

```json
{
  "mediaIds": [1, 2],
  "stage": "HANDOVER" // Tuỳ chọn: 'HANDOVER' hoặc 'DISPUTE' (mặc định 'HANDOVER')
}
```

- **Hành vi nghiệp vụ:**
  - Chỉ thành viên trong giao dịch mới có quyền đính kèm minh chứng.
  - Request phải ở giai đoạn giao nhận trở đi (`LOGISTICS_CONFIRMED`, `AWAITING_HANDOVER`, `COMPLETED`, hoặc `DISPUTED`).
  - Gọi `MediaService.attach(mediaIds, currentUserId)` để chuyển trạng thái media sang `ATTACHED`.
  - Lưu vào bảng `request_media_mappings`.
- **Thành công:** `200 OK`, trả về danh sách `RequestMediaResponse`.
- **Lỗi:**
  - `400 REQUEST_MEDIA_REQUIRED`: Danh sách `mediaIds` rỗng.
  - `400 REQUEST_MEDIA_STAGE_INVALID`: Giai đoạn không hợp lệ (chỉ chấp nhận `HANDOVER` hoặc `DISPUTE`).
  - `403 REQUEST_FORBIDDEN`: Người gọi không phải thành viên tham gia giao dịch.
  - `404 REQUEST_NOT_FOUND`: Không tìm thấy yêu cầu.
  - `409 REQUEST_INVALID_STATE_TRANSITION`: Yêu cầu chưa đến giai đoạn giao nhận (chưa đạt `LOGISTICS_CONFIRMED`).

---

## 12. GET /api/v1/requests/{id}/media

Lấy danh sách ảnh hoặc video minh chứng của yêu cầu giao dịch (FR-86, BR-T09).

- **Xác thực:** Bắt buộc JWT (chỉ chủ bài đăng hoặc người nhận).
- **Path variable:** `id` (id của request).
- **Hành vi nghiệp vụ:**
  - **Chỉ hai bên tham gia giao dịch** mới có quyền xem minh chứng (BR-T09). Người dùng khác bị chặn `403 Forbidden`.
  - Trả về danh sách minh chứng đã đính kèm theo thứ tự thời gian tạo tăng dần.
- **Thành công:** `200 OK`, trả về danh sách `RequestMediaResponse`:

```json
[
  {
    "mediaId": 1,
    "url": "/api/v1/media/1",
    "stage": "HANDOVER",
    "uploadedBy": 10,
    "createdAt": "2026-10-18T10:00:00Z"
  }
]
```

- **Lỗi:**
  - `403 REQUEST_FORBIDDEN`: Người gọi không phải thành viên tham gia giao dịch (BR-T09).
  - `404 REQUEST_NOT_FOUND`: Không tìm thấy yêu cầu.

---

## 13. POST /api/v1/requests/{id}/cancel-transaction

Chủ bài hoặc người nhận huỷ giao dịch sau khi chủ bài đã chọn người nhận.

- **Xác thực:** Bắt buộc JWT (phải là chủ bài `donorId` hoặc người nhận `receiverId` của giao dịch).
- **Path variable:** `id` (id của request).
- **Body:** `CancelTransactionRequest` (`reason` bắt buộc, 5 đến 500 ký tự).
- **Trạng thái được huỷ:** `RESERVED`, `AWAITING_LOGISTICS`, `LOGISTICS_CONFIRMED`, `AWAITING_HANDOVER`.
  `PENDING` huỷ qua `POST /requests/{id}/cancel` (mục 4). `COMPLETED`, `DISPUTED`, `CANCELLED`, `REJECTED` trả `409`.
- **Hành vi nghiệp vụ:**
  - Request chuyển sang `CANCELLED`. `cancelReason` lưu `"[Chủ bài] "` hoặc `"[Người nhận] "` cộng lý do đã cắt khoảng trắng
    hai đầu (tối đa 600 ký tự). Không có cột riêng cho người huỷ.
  - Bài chính và món mang đổi (nếu `SWAP`) được nhả về `APPROVED`, bài mở lại cho người khác. Không tính lại phí đăng
    vì bài không qua duyệt lần nữa.
  - Bên còn lại nhận thông báo `TRANSACTION_CANCELLED` kèm lý do.
  - Ghi activity log `REQUEST_CANCEL` (`targetType = REQUEST`, `reason` là `cancelReason`).
- **Thành công:** `200 OK`, trả về `RequestResponse` với `status = CANCELLED`.
- **Lỗi:**
  - `400 VALIDATION_FAILED`: `reason` trống hoặc ngoài 5 đến 500 ký tự.
  - `403 REQUEST_FORBIDDEN`: Không phải chủ bài hoặc người nhận.
  - `404 REQUEST_NOT_FOUND`: Không tìm thấy yêu cầu.
  - `409 REQUEST_INVALID_STATE_TRANSITION`: Trạng thái hiện tại không cho huỷ.

---

## 14. POST /api/v1/requests/{id}/handover-issue

Báo "Tôi chưa nhận được đồ" (người nhận) hoặc "Tôi chưa giao được" (chủ bài) khi bên kia đã xác nhận bàn giao còn mình
thì chưa.

- **Xác thực:** Bắt buộc JWT (chủ bài hoặc người nhận của giao dịch).
- **Path variable:** `id` (id của request).
- **Body:** `HandoverIssueRequest` (`note` bắt buộc 5 đến 1000 ký tự, `evidenceMediaIds` tuỳ chọn).
- **Điều kiện:** Request đang `AWAITING_HANDOVER`, bên kia **đã** xác nhận bàn giao và người gọi **chưa**. Mọi trường hợp
  khác (kể cả `LOGISTICS_CONFIRMED`, vì lúc đó chưa bên nào xác nhận) trả `409`.
- **Hành vi nghiệp vụ:**
  - Request chuyển `AWAITING_HANDOVER` sang `DISPUTED`.
  - Mở khiếu nại `NO_SHOW` cho giao dịch qua module report, xem `report.md` mục "Report tự mở khi báo chưa nhận được".
    Bên còn lại nhận thông báo `DISPUTE_OPENED`.
  - Job tự xác nhận sau `AUTO_CONFIRM_DAYS` ngày chỉ xử lý `AWAITING_HANDOVER` nên bỏ qua giao dịch `DISPUTED`.
- **Thành công:** `200 OK`, trả về `RequestResponse` với `status = DISPUTED`.
- **Lỗi:**
  - `400 VALIDATION_FAILED`: `note` trống hoặc ngoài 5 đến 1000 ký tự.
  - `403 REQUEST_FORBIDDEN`: Không phải chủ bài hoặc người nhận.
  - `404 REQUEST_NOT_FOUND`: Không tìm thấy yêu cầu.
  - `409 REQUEST_INVALID_STATE_TRANSITION`: Sai trạng thái hoặc sai điều kiện xác nhận bàn giao.

---

## DTO Models

### RequestMediaResponse

| Trường | Kiểu | Ý nghĩa |
|---|---|---|
| `mediaId` | number | ID tệp media |
| `url` | string | Đường dẫn xem tệp (`/api/v1/media/{id}`) |
| `stage` | string | Giai đoạn đính kèm (`HANDOVER` hoặc `DISPUTE`) |
| `uploadedBy` | number | ID người tải lên |
| `createdAt` | string | Thời điểm đính kèm ISO-8601 UTC |

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
| `cancelReason` | string? | Lý do huỷ, có tiền tố người huỷ `[Chủ bài] `, `[Người nhận] ` hoặc `[Hệ thống] `. Có khi `status = CANCELLED` |
| `createdAt` | string | Thời điểm tạo ISO-8601 UTC |
| `receiverName` | string? | Họ tên người gửi yêu cầu (có trong danh sách theo bài, danh sách của tôi và chi tiết). Có thể `null`. Không kèm email/số điện thoại (BR-T11) |
| `itemTitle` | string? | Tiêu đề bài đăng được yêu cầu, có ở mọi API trả `RequestResponse` (kể cả khi bài đã `RESERVED` trở lên, lúc `GET /items/{id}` không còn cho người xin xem). `null` khi bài đã bị xoá |
| `itemImage` | string? | Ảnh đầu tiên của bài đăng, dạng `/api/v1/media/{id}`. `null` khi bài không có ảnh |
| `donorId` | number? | Id chủ bài đăng |
| `donorName` | string? | Họ tên chủ bài đăng. `null` khi tài khoản đã bị xoá. Không kèm email/số điện thoại (BR-T11) |
| `offeredItemTitle` | string? | Tiêu đề món người xin đề nghị đổi (chỉ yêu cầu `SWAP`) |
| `receiverTrustStars` | number? | Số sao uy tín của người gửi yêu cầu (TrustStars: 1-5). Alias: `trustStars` (FR-65) |
| `receiverRank` | string? | Hạng uy tín của người gửi yêu cầu (`BRONZE`, `SILVER`, `GOLD`, `DIAMOND`). Alias: `rank` (FR-65) |
| `receiverAsDescribedRate` | number? | Tỉ lệ giao dịch đúng mô tả của người gửi yêu cầu, thang 0.00 - 1.00 (vd `0.92` là 92%). `null` khi có dưới 3 giao dịch hoàn tất đã được đánh giá "đúng mô tả?" (SRS 6.12). Alias: `asDescribedRate` (FR-65) |

### ScheduleRequest

| Trường | Kiểu | Bắt buộc | Ràng buộc |
|---|---|---|---|
| `deliveryMethod` | string | Có | `IN_PERSON` hoặc `SHIPPING` |
| `meetingPlace` | string | Có | Tối đa 255 ký tự |
| `meetingTime` | string | Có | Thời gian hẹn ISO-8601 UTC ở tương lai |
| `agreeToWaiver` | boolean | Không | Bắt buộc true nếu là SHIPPING |

### CancelTransactionRequest

| Trường | Kiểu | Bắt buộc | Ràng buộc |
|---|---|---|---|
| `reason` | string | Có | 5 đến 500 ký tự (sau khi cắt khoảng trắng hai đầu nên dùng `maxLength = 500` ở client) |

### HandoverIssueRequest

| Trường | Kiểu | Bắt buộc | Ràng buộc |
|---|---|---|---|
| `note` | string | Có | 5 đến 1000 ký tự |
| `evidenceMediaIds` | number[] | Không | Id ảnh/video minh chứng đã tải lên |

### ConfirmLogisticsRequest

| Trường | Kiểu | Bắt buộc | Ràng buộc |
|---|---|---|---|
| `agreeToWaiver` | boolean | Không | Bắt buộc true nếu là SHIPPING |

### HandoverConfirmRequest

| Trường | Kiểu | Bắt buộc | Ràng buộc |
|---|---|---|---|
| `asDescribed` | boolean | Bên nhận: Có | Bắt buộc với bên nhận |
| `notAsDescribedReason` | string | Không | Bắt buộc nếu `asDescribed = false` |
| `mediaIds` | number[] | Không | Danh sách ID ảnh/video minh chứng khi giao nhận (FR-86, BR-T09) |

### AttachMediaRequest

| Trường | Kiểu | Bắt buộc | Ràng buộc |
|---|---|---|---|
| `mediaIds` | number[] | Có | Danh sách ID tệp media đã tải lên (không được rỗng) |
| `stage` | string | Không | Giai đoạn ('HANDOVER' hoặc 'DISPUTE', mặc định 'HANDOVER') |

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
| `REQUEST_MEDIA_REQUIRED` | 400 | Danh sách ảnh/video minh chứng không được rỗng (FR-86) |
| `REQUEST_MEDIA_STAGE_INVALID` | 400 | Giai đoạn đính kèm minh chứng không hợp lệ |
| `REQUEST_ITEM_NOT_AVAILABLE` | 409 | Bài đăng không ở trạng thái sẵn sàng (chưa duyệt, đã trao đổi...) |
| `REQUEST_ALREADY_EXISTS` | 409 | Đã có một yêu cầu đang hoạt động cho bài này (BR-R02) |
| `REQUEST_SCHEDULE_LOCKED` | 409 | Lịch hẹn đã chốt và bị khoá (FR-83, BR-T05) |
| `REQUEST_CONTACT_NOT_REVEALED` | 409 | Chưa chốt lịch hẹn để xem thông tin liên lạc (FR-82, BR-T11) |
| `REQUEST_HANDOVER_ALREADY_CONFIRMED` | 409 | Bạn đã xác nhận giao nhận cho yêu cầu này rồi |
| `REQUEST_INVALID_STATE_TRANSITION` | 409 | Chuyển đổi trạng thái không hợp lệ theo sơ đồ |
| `REQUEST_FORBIDDEN` | 403 | Không có quyền thao tác trên yêu cầu này (hoặc người ngoài xem minh chứng) |

