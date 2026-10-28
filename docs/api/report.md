# Report API Contract

Module báo cáo vi phạm (`report`) quản lý việc tiếp nhận khiếu nại và báo cáo từ người dùng nhằm duy trì môi trường an toàn cho nền tảng ShareLoop.

## Admin đọc khiếu nại (#118)

### GET /api/v1/admin/reports

Yêu cầu Bearer JWT có `ROLE_ADMIN`. Query: `page` mặc định `0`, tối thiểu `0`;
`size` mặc định `20`, trong khoảng `1–100`; `status` và `targetType` không bắt buộc,
nhận đúng enum bên dưới. Hai bộ lọc kết hợp bằng AND. Thứ tự cố định:
`createdAt DESC, id DESC`. Không trả bản ghi đã xoá mềm.

Ví dụ: `GET /api/v1/admin/reports?page=0&size=20&status=OPEN&targetType=REQUEST`.

```json
{
  "items": [{
    "id": 71, "reporterId": 10, "targetType": "REQUEST",
    "itemId": 105, "targetUserId": 42, "requestId": 88,
    "reason": "NOT_AS_DESCRIBED", "description": "Đồ khác mô tả",
    "stage": "BEFORE", "status": "OPEN", "createdAt": "2026-10-20T03:00:00Z",
    "reporterName": "Người gửi", "targetName": "Áo khoác gió nam"
  }],
  "page": 0, "size": 20, "totalElements": 1, "totalPages": 1
}
```

Trang rỗng trả `items: []`; không có bản ghi thì `totalPages: 0`.

`reporterName` là họ tên người gửi. `targetName` là tên đối tượng bị báo cáo để giao diện không hiện id: với hồ sơ `USER` là họ tên người bị báo cáo, với hồ sơ `ITEM` là tiêu đề bài đăng, với hồ sơ `REQUEST` là tiêu đề bài đăng của giao dịch. Cả hai là `null` khi không tra được (tài khoản hoặc bài đã bị xoá, giao dịch không còn). Hai trường này chỉ có trong API của Admin, còn `POST /reports` để `null`.

### GET /api/v1/admin/reports/{reportId}

Yêu cầu Admin; `reportId` là số nguyên dương. Response:

```json
{
  "report": {
    "id": 71, "reporterId": 10, "targetType": "REQUEST",
    "itemId": 105, "targetUserId": 42, "requestId": 88,
    "reason": "NOT_AS_DESCRIBED", "description": "Đồ khác mô tả",
    "stage": "BEFORE", "status": "OPEN", "createdAt": "2026-10-20T03:00:00Z",
    "reporterName": "Người gửi", "targetName": "Áo khoác gió nam"
  },
  "reporter": {"id": 10, "fullName": "Người gửi"},
  "reportedUser": {"id": 42, "fullName": "Người bị báo cáo"},
  "relatedItemId": 105,
  "response": "Nội dung giải trình", "respondedAt": "2026-10-20T04:00:00Z",
  "responseDueAt": "2026-10-23T03:00:00Z",
  "evidence": [{"id": 12, "url": "/api/v1/admin/reports/71/evidence/12", "available": true}],
  "responseEvidence": [{"id": 15, "url": null, "available": false}],
  "resolvedBy": null, "resolution": null, "starsDeducted": 0, "resolvedAt": null,
  "relatedItemTitle": "Áo khoác gió nam", "resolvedByName": null
}
```

`relatedItemTitle` là tiêu đề bài đăng liên quan (`null` khi không có hoặc bài đã bị xoá). `resolvedByName` là họ tên Admin đã kết luận (`null` khi chưa kết luận).

Không trả email, số điện thoại hoặc storage key. Tên có thể `null` nếu người dùng không
còn khả dụng; `reportedUser` có thể `null` nếu không xác định được bên bị báo cáo.
Hồ sơ vẫn đọc được khi đối tượng liên quan đã mất. `relatedItemId` lấy thêm từ giao dịch
nếu hồ sơ cũ chưa lưu ID bài. Trường giải trình/kết luận chưa có trả `null`.

Migration `V202610181400__report_dispute_fields_and_configs.sql` trên develop đã có ba cột
`evidence`, `response_evidence`, `response_due_at`. Migration tương thích của #118
`V202610200300__report_evidence_and_response_deadline.sql` dùng `IF NOT EXISTS`, không tạo trùng cột.
Hai cột bằng chứng lưu
JSON array media ID dạng text (ví dụ `[12, 15]`); API luôn trả danh sách object đã chuẩn hoá.
Hồ sơ cũ không có dữ liệu trả mảng rỗng và hạn `null`, không suy đoán thời hạn lịch sử.
Media bị xoá/ẩn hoặc không còn gắn trả `available: false, url: null`. Tệp mất trong storage
có thể chỉ phát hiện khi đọc ảnh; FE hiển thị fallback riêng, vẫn giữ hồ sơ.

### GET /api/v1/admin/reports/{reportId}/evidence/{mediaId}

Chỉ Admin và chỉ media ID gắn trong một trong hai danh sách bằng chứng của đúng hồ sơ.
Trả bytes ảnh với `Content-Type` media gốc và `Cache-Control: no-store`.
FE tải qua HTTP client có Bearer JWT rồi dùng blob URL cho ảnh; không gắn URL riêng tư
thẳng vào `img` vì trình duyệt không tự thêm Authorization. Thu hồi blob URL khi đóng hồ sơ.
Không đổi quyền đọc endpoint public: bằng chứng đã gắn có visibility `PRIVATE`.

Mã lỗi các API đọc:

| HTTP | Code | Trường hợp |
| --- | --- | --- |
| 400 | `VALIDATION_FAILED` | Sai enum/kiểu query, page âm, size ngoài 1–100 hoặc ID không dương |
| 401 | `UNAUTHORIZED` | Chưa đăng nhập/phiên hết hạn |
| 403 | `FORBIDDEN` | Không có quyền Admin |
| 404 | `REPORT_NOT_FOUND` | Hồ sơ không tồn tại hoặc đã xoá mềm |
| 404 | `MEDIA_NOT_FOUND` | Media không thuộc hồ sơ, bị xoá/ẩn hoặc không còn gắn |
| 404 | `MEDIA_FILE_MISSING` | Bản ghi còn nhưng tệp không còn trong storage |
| 500 | `MEDIA_STORAGE_ERROR` | Lỗi đọc storage |

### Ghi bằng chứng và giải trình để tạo hồ sơ thật

`POST /api/v1/reports` nhận thêm `evidenceMediaIds?: number[]` (ID nguyên dương,
không null/trùng, bỏ qua hoặc mảng rỗng được phép). Upload ảnh bằng API media hiện có
trước khi tạo báo cáo. Chỉ media `UPLOADED`, còn hoạt động và thuộc người gửi mới được gắn;
sau khi gắn chuyển `ATTACHED`, `PRIVATE`. Lỗi media rollback cả việc tạo báo cáo.

Khi báo cáo `REQUEST`, cả `POST /reports` và `POST /requests/{requestId}/disputes` dùng
cùng luồng kiểm tra giao dịch `COMPLETED`, người tham gia, cửa sổ khiếu nại và chống gửi lặp.
Hệ thống lưu người bị báo cáo, ID bài và `responseDueAt`, chuyển `AWAITING_RESPONSE`.
Hạn khiếu nại và hạn giải trình đọc thẳng `ConfigKey.COMPLAINT_WINDOW_DAYS` (`complaint_window_days`, mặc định 7)
và `ConfigKey.RESPONSE_WINDOW_DAYS` (`response_window_days`, mặc định 3), đúng key trang Admin sửa.
Khoá viết hoa cũ `DISPUTE_WINDOW_DAYS`/`DISPUTE_RESPONSE_DAYS` không còn được đọc.
Thông báo `DISPUTE_OPENED` và `ITEM_AUTO_HIDDEN` gọi bằng tên bài (vd `Giao dịch “Nồi cơm điện” bị khiếu nại`), không dùng `#id`.
Ghi chú mặc định khi Admin kết luận mà không nhập ghi chú: `Bác bỏ báo cáo về “<tên bài>”`,
`Chấp thuận báo cáo về “<tên bài>”, xử phạt trừ 1 sao uy tín` (báo cáo người dùng không có bài thì bỏ phần “về …”).
Cả hai luồng dùng Clock được inject. Báo cáo `ITEM` lưu thêm ID chủ bài để giữ danh tính đối tượng.

`POST /api/v1/reports/{reportId}/response` yêu cầu Bearer JWT của bên bị báo cáo:

```json
{"response": "Nội dung giải trình", "evidenceMediaIds": [15]}
```

`response` bắt buộc, không toàn khoảng trắng, tối đa 2000 ký tự; `evidenceMediaIds`
giống quy tắc bên gửi. Thành công `204 No Content`, lưu nội dung đã trim, ảnh và
`respondedAt`, chuyển hồ sơ về `OPEN`. Khoá dòng cùng cơ chế kết luận, không nhận
giải trình lần hai, sau hạn, hoặc khi hồ sơ không còn `AWAITING_RESPONSE`.
Endpoint `POST /disputes/{disputeId}/response` hiện có vẫn nhận `{note, evidenceMediaIds}`,
trả `200` và giữ các mã lỗi `DISPUTE_*` riêng. Cả hai endpoint đều gắn bằng chứng riêng tư
qua cùng gateway và chuyển `AWAITING_RESPONSE` về `OPEN` sau khi giải trình, đúng mục 5 (Sơ đồ trạng thái) trong Luồng hoạt động & ERD.

### Report tự mở khi báo "chưa nhận được" / "chưa giao được" đồ

Không có endpoint mới. Khi một bên gọi `POST /api/v1/requests/{id}/handover-issue`, module request chuyển Request sang
`DISPUTED` rồi phát event `HandoverIssueReportedEvent`. Listener trong module report (`@EventListener`, chạy **cùng
transaction** với bước chuyển `DISPUTED`, lỗi thì cả hai rollback) gọi
`ReportService.openHandoverIssueReport(reporterId, requestId, note, evidenceMediaIds)`:

- Tạo đúng 1 report: `targetType = REQUEST`, `reason = NO_SHOW`, `stage = AFTER`, `status = AWAITING_RESPONSE`,
  `description` là ghi chú của người báo, `evidence` lấy từ `evidenceMediaIds` qua `ReportEvidenceService.attach`,
  `targetUserId` là bên còn lại, `responseDueAt` theo `DISPUTE_RESPONSE_DAYS`. Không thêm bảng hay cột.
- Chống trùng: người báo đã có report `REQUEST` cho giao dịch này thì bỏ qua, không lỗi.
- Gửi thông báo `DISPUTE_OPENED` cho bên còn lại, dẫn tới `/me/disputes/{reportId}` để giải trình.
- Chủ bài, người nhận và Admin xử lý như khiếu nại thường (`POST /disputes/{disputeId}/response`, `.../conclude`).

### Report tự mở khi bên nhận chọn "Không đúng mô tả" (FR-85, BR-R05)

Không có endpoint mới. Khi bên nhận gọi `POST /api/v1/requests/{id}/confirm-handover` với `asDescribed = false`,
module request phát event `NotAsDescribedReportedEvent`. Listener trong module report
(`@TransactionalEventListener(AFTER_COMMIT)`, chạy trong transaction mới) gọi
`ReportService.openNotAsDescribedReport(receiverId, requestId, reason)`:

- Tạo đúng 1 report: `targetType = REQUEST`, `reason = NOT_AS_DESCRIBED`, `stage = AFTER`, `status = AWAITING_RESPONSE`,
  `description` là lý do bên nhận nhập, `targetUserId` là chủ bài, `responseDueAt` theo `DISPUTE_RESPONSE_DAYS`.
  Không thêm bảng hay cột.
- Khác `POST /requests/{requestId}/disputes`: không đòi giao dịch đã `COMPLETED` và không xét cửa sổ khiếu nại
  7 ngày, vì bên nhận có thể trả lời khi giao dịch còn `AWAITING_HANDOVER`.
- Chống trùng: bên nhận đã có report `REQUEST` cho giao dịch này thì bỏ qua, không lỗi.
- Gửi thông báo `DISPUTE_OPENED` cho chủ bài, dẫn tới `/me/disputes/{reportId}` để giải trình.
- Chủ bài và Admin xử lý như khiếu nại thường (`POST /disputes/{disputeId}/response`, `.../conclude`).
- `ReportService.hasReportForRequest(requestId)` cho job tự hoàn tất ở module request bỏ qua giao dịch đã có report (FR-87).

Lỗi ghi bổ sung: `400 VALIDATION_FAILED`; `403 REPORT_NOT_PARTICIPANT` (giải trình qua `/reports`)
hoặc `DISPUTE_NOT_A_PARTY` (tạo báo cáo `REQUEST`);
`409 REPORT_RESPONSE_NOT_ALLOWED`/`DISPUTE_REQUEST_NOT_COMPLETED`;
`422 DISPUTE_WINDOW_EXPIRED`; `404 REPORT_NOT_FOUND`/`REPORT_TARGET_NOT_FOUND`;
media: `403 MEDIA_NOT_OWNER`, `404 MEDIA_NOT_FOUND`,
`409 MEDIA_NOT_UPLOADED`, `422 MEDIA_INVALID_FILE` (ID trùng).

Admin vẫn dùng `POST .../conclude` với `{decision: "UPHOLD" | "DISMISS", note}`;
ghi chú không bắt buộc, tối đa 500 ký tự. Không thêm hạn chế thời điểm kết luận.
FE xác nhận trước khi gửi, khoá nút khi gửi, tải lại list theo bộ lọc hiện tại và chi tiết
sau thành công; nếu `409 REPORT_ALREADY_CONCLUDED`, báo hồ sơ đã được xử lý và tải lại.
Các request đọc bị huỷ/bỏ qua khi chuyển hồ sơ hoặc bộ lọc.

---

## 1. POST /api/v1/reports

Báo cáo một bài đăng hoặc một người dùng vi phạm quy tắc cộng đồng.

- **Xác thực:** Bắt buộc Bearer Token (JWT). ID người báo cáo được trích xuất từ phiên đăng nhập hiện tại (`@CurrentUserId`).
- **Quy tắc nghiệp vụ:**
  - Không thể tự báo cáo chính mình hoặc bài đăng của chính mình (`400 REPORT_CANNOT_REPORT_SELF`).
  - Mỗi người dùng chỉ được báo cáo một đối tượng tối đa 1 lần (`409 REPORT_ALREADY_REPORTED`).
  - Đối tượng báo cáo phải tồn tại trong hệ thống (`404 REPORT_TARGET_NOT_FOUND`).
  - Cơ chế tự động bảo vệ (BR-A03, FR-49, Issue #151): Khi một bài đăng có đủ `report_hide_threshold` báo cáo (mặc định **3**, Admin sửa được qua cấu hình) **đang chờ xử lý**, hệ thống tự ẩn bài (`itemService.hide(itemId)`).
    - Chỉ đếm báo cáo `targetType = ITEM` ở trạng thái `OPEN` hoặc `AWAITING_RESPONSE`; báo cáo đã `DISMISSED` hoặc `RESOLVED` không tính.
    - Ẩn bài nghĩa là `is_active = false`, **giữ nguyên `status`** (ERD mục F16). Hàng chờ xử lý là trang khiếu nại của Admin (`GET /api/v1/admin/reports`), nơi các báo cáo `OPEN` đã nằm sẵn.
    - Đúng lúc bài vừa chạm ngưỡng, mọi Admin nhận một thông báo `type = ITEM_AUTO_HIDDEN`, `link = /admin/complaints`. Báo cáo sau đó vào bài đã ẩn không gửi lại thông báo.

- **Request Body:**

```json
{
  "targetType": "ITEM",
  "itemId": 105,
  "reason": "PROHIBITED_ITEM",
  "description": "Sản phẩm có dấu hiệu là hàng cấm lưu hành theo quy định pháp luật."
}
```

*Hoặc báo cáo người dùng:*

```json
{
  "targetType": "USER",
  "targetUserId": 23,
  "reason": "HARASSMENT",
  "description": "Người dùng liên tục nhắn tin xúc phạm sau khi hẹn nhận đồ."
}
```

- **Thành công:** `201 Created`, trả về `ReportResponse`.

```json
{
  "id": 1,
  "reporterId": 42,
  "targetType": "ITEM",
  "itemId": 105,
  "targetUserId": null,
  "requestId": null,
  "reason": "PROHIBITED_ITEM",
  "description": "Sản phẩm có dấu hiệu là hàng cấm lưu hành theo quy định pháp luật.",
  "stage": "BEFORE",
  "status": "OPEN",
  "createdAt": "2026-10-18T10:00:00Z"
}
```

- **Mã lỗi:**
  - `400 BAD_REQUEST`:
    - `REPORT_TARGET_REQUIRED`: Chưa cung cấp đối tượng cần báo cáo.
    - `REPORT_CANNOT_REPORT_SELF`: Tự báo cáo chính mình hoặc bài của mình.
    - `VALIDATION_FAILED`: `targetType` hoặc `reason` không hợp lệ; `description` vượt quá 1000 ký tự.
  - `401 UNAUTHORIZED`: Chưa đăng nhập.
  - `404 NOT_FOUND`:
    - `REPORT_TARGET_NOT_FOUND`: Bài đăng hoặc người dùng không tồn tại.
  - `409 CONFLICT`:
    - `REPORT_ALREADY_REPORTED`: Đã báo cáo đối tượng này trước đó.

---

## 2. POST /api/v1/requests/{requestId}/disputes

Một trong hai bên tham gia giao dịch (người cho hoặc người nhận) gửi khiếu nại sau khi giao dịch đã hoàn tất (FR-89, BR-T08, Issue #112).

- **Xác thực:** Bắt buộc Bearer Token (JWT). ID người khiếu nại được trích xuất từ phiên đăng nhập hiện tại (`@CurrentUserId`).
- **Quy tắc nghiệp vụ:**
  - Yêu cầu giao dịch phải tồn tại trong hệ thống (`404 REPORT_TARGET_NOT_FOUND`).
  - Người khiếu nại bắt buộc phải là một bên trong giao dịch (`donorId` hoặc `receiverId`); người ngoài không có quyền khiếu nại (`403 DISPUTE_NOT_A_PARTY`).
  - Giao dịch bắt buộc phải ở trạng thái đã hoàn thành `COMPLETED` (`409 DISPUTE_REQUEST_NOT_COMPLETED`).
  - Cửa sổ khiếu nại: Khiếu nại phải được gửi trong vòng 24 giờ (1 ngày) kể từ thời điểm giao dịch hoàn thành (`completedAt`). Quá 24 giờ sẽ bị từ chối (`422 DISPUTE_WINDOW_EXPIRED`). Số ngày được cấu hình qua `complaint_window_days` (mặc định 1 ngày).
  - Mỗi người chỉ được khiếu nại giao dịch này một lần (`409 REPORT_ALREADY_REPORTED`).
  - Người khiếu nại có thể đính kèm danh sách ID media làm bằng chứng. Các media file phải tồn tại và thuộc quyền sở hữu của người gửi (`403 MEDIA_NOT_OWNER`, `404 MEDIA_NOT_FOUND`).
  - Sau khi mở khiếu nại thành công, hệ thống mở cửa sổ 48 giờ (2 ngày) cho bên bị khiếu nại gửi giải trình (`responseDueAt = now + 48 hours`), cấu hình qua `response_window_days` (mặc định 2 ngày). Trạng thái bản ghi là `AWAITING_RESPONSE`.
  - Hệ thống gửi thông báo trong ứng dụng cho bên bị khiếu nại (`notificationService.notify`).

- **Request Body:**

```json
{
  "reason": "NOT_AS_DESCRIBED",
  "note": "Áo bị rách ở phần nách áo, không giống như ảnh mô tả",
  "evidenceMediaIds": [12, 15]
}
```

- **Thành công:** `201 Created`, trả về `DisputeResponse`.

```json
{
  "id": 1,
  "responseDueAt": "2026-10-21T13:30:00Z"
}
```

- **Mã lỗi:**
  - `400 BAD_REQUEST`:
    - `VALIDATION_FAILED`: `reason` trống hoặc không hợp lệ; `note` vượt quá 500 ký tự.
    - `MEDIA_INVALID_FILE`: ID media không hợp lệ hoặc bị trùng.
  - `401 UNAUTHORIZED`: Chưa đăng nhập.
  - `403 FORBIDDEN`:
    - `DISPUTE_NOT_A_PARTY`: Người dùng không phải là người cho hoặc người nhận của giao dịch này.
    - `MEDIA_NOT_OWNER`: Media đính kèm không thuộc quyền sở hữu của người gửi.
  - `404 NOT_FOUND`:
    - `REPORT_TARGET_NOT_FOUND`: Không tìm thấy giao dịch.
    - `MEDIA_NOT_FOUND`: Media file không tồn tại.
  - `409 CONFLICT`:
    - `DISPUTE_REQUEST_NOT_COMPLETED`: Giao dịch chưa chuyển sang trạng thái COMPLETED.
    - `REPORT_ALREADY_REPORTED`: Người dùng đã khiếu nại giao dịch này trước đó.
  - `422 UNPROCESSABLE_ENTITY`:
    - `DISPUTE_WINDOW_EXPIRED`: Đã quá 24 giờ kể từ lúc giao dịch hoàn tất.

---

## 3. POST /api/v1/disputes/{disputeId}/response

Bên bị khiếu nại gửi văn bản giải trình kèm bằng chứng đối chất (FR-90, BR-T08, Issue #112).

- **Xác thực:** Bắt buộc Bearer Token (JWT). ID người giải trình được trích xuất từ phiên đăng nhập hiện tại (`@CurrentUserId`).
- **Quy tắc nghiệp vụ:**
  - Bản ghi khiếu nại phải tồn tại trong hệ thống (`404 DISPUTE_NOT_FOUND`).
  - Chỉ bên bị khiếu nại (`respondent`) mới có quyền gửi giải trình (`403 DISPUTE_NOT_RESPONDENT`).
  - Khiếu nại chưa bị đóng hoặc đã được Admin kết luận (`409 DISPUTE_ALREADY_CONCLUDED`).
  - Bên bị khiếu nại chỉ được gửi giải trình đúng 1 lần (`409 DISPUTE_ALREADY_RESPONDED`).
  - Giải trình phải được gửi trong vòng 48 giờ (2 ngày) kể từ lúc khiếu nại được mở (`now <= responseDueAt`). Quá 48 giờ sẽ bị từ chối (`422 DISPUTE_RESPONSE_WINDOW_EXPIRED`).
  - Có thể đính kèm danh sách ID media làm bằng chứng. Các media file phải tồn tại và thuộc quyền sở hữu của người giải trình.

- **Request Body:**

```json
{
  "note": "Áo đã được kiểm tra cẩn thận trước khi giao, không có bất kỳ vết rách nào.",
  "evidenceMediaIds": [18]
}
```

- **Thành công:** `200 OK` (không có body trả về).

- **Mã lỗi:**
  - `400 BAD_REQUEST`:
    - `VALIDATION_FAILED`: `note` trống hoặc vượt quá 1000 ký tự.
  - `401 UNAUTHORIZED`: Chưa đăng nhập.
  - `403 FORBIDDEN`:
    - `DISPUTE_NOT_RESPONDENT`: Người gọi không phải là bên bị khiếu nại.
    - `MEDIA_NOT_OWNER`: Media không thuộc quyền sở hữu của người gửi.
  - `404 NOT_FOUND`:
    - `DISPUTE_NOT_FOUND`: Không tìm thấy bản ghi khiếu nại.
  - `409 CONFLICT`:
    - `DISPUTE_ALREADY_CONCLUDED`: Khiếu nại này đã được Admin kết luận, không thể giải trình thêm.
    - `DISPUTE_ALREADY_RESPONDED`: Bạn đã gửi giải trình cho khiếu nại này rồi.
  - `422 UNPROCESSABLE_ENTITY`:
    - `DISPUTE_RESPONSE_WINDOW_EXPIRED`: Đã quá thời hạn 3 ngày để gửi giải trình.

---

## 4. POST /api/v1/admin/reports/{reportId}/conclude

Admin đưa ra kết luận xử lý đối với báo cáo hoặc khiếu nại vi phạm (FR-91, FR-93, BR-U04, BR-A07, Issue #113).

- **Xác thực:** Bắt buộc Bearer Token (JWT) có quyền `ROLE_ADMIN`. ID của Admin được trích xuất từ phiên đăng nhập (`@CurrentUserId`).
- **Quy tắc nghiệp vụ:**
  - Báo cáo phải tồn tại trong hệ thống (`404 REPORT_NOT_FOUND`).
  - Báo cáo chưa được kết luận trước đó; nếu đã ở trạng thái `RESOLVED` hoặc `DISMISSED` thì từ chối xử lý (`409 REPORT_ALREADY_CONCLUDED`).
  - **Giao dịch đang `DISPUTED`** (khiếu nại `REQUEST` mở từ `POST /requests/{id}/handover-issue`): kết luận xong thì giao dịch được đưa ra khỏi `DISPUTED`. `UPHOLD` -> giao dịch `CANCELLED` (`cancelReason` có tiền tố `[Hệ thống]`), nhả cả hai món về `APPROVED`. `DISMISS` -> giao dịch `COMPLETED` như hoàn tất bình thường (hai món `TRADED`, cộng điểm uy tín). Cả hai bên nhận thông báo kết quả. Khiếu nại mở sau khi giao dịch đã `COMPLETED` (`POST /requests/{requestId}/disputes`) không đổi trạng thái giao dịch, chỉ áp dụng trừ sao như trước.
  - **Khi kết luận DISMISS (Bác bỏ / Không có cơ sở):**
    - Trạng thái báo cáo chuyển thành `DISMISSED`.
    - Không trừ sao uy tín, không ẩn bài đăng.
    - Ghi nhận nhật ký hành động Admin (`activity_logs` loại `REPORT_DISMISSED`).
  - **Khi kết luận UPHOLD (Chấp thuận / Khiếu nại có cơ sở):**
    - Trạng thái báo cáo chuyển thành `RESOLVED`.
    - Xác định người vi phạm (`violatorId`):
      - Đối tượng là `ITEM`: Chủ bài đăng (`donorId`).
      - Đối tượng là `USER`: Người dùng bị báo cáo (`targetUserId`).
      - Đối tượng là `REQUEST`: Bên bị khiếu nại trong giao dịch.
    - Trừ 1 sao uy tín của người vi phạm qua `ReputationService.deductStar(violatorId, 1, note)` (BR-U04).
    - Nếu lý do vi phạm thuộc nhóm nghiêm trọng cấu hình qua `REPORT_SERIOUS_REASONS` (mặc định: `PROHIBITED_ITEM`, `COUNTERFEIT`):
      - Hệ thống tự động ẩn toàn bộ các bài đăng đang hoạt động khác của người vi phạm để rà soát (`itemService.hideAllOfUser(violatorId)`, BR-A07).
    - Nếu đối tượng báo cáo là bài đăng cụ thể (`ITEM`), tự động ẩn bài đăng đó (`itemService.hide(itemId)`).
    - Ghi nhận nhật ký hành động Admin (`activity_logs` loại `REPORT_UPHELD`).
  - Cập nhật thông tin Admin kết luận (`resolved_by`), ghi chú kết luận (`resolution`) và thời điểm kết luận (`resolved_at`).

- **Request Body:**

```json
{
  "decision": "UPHOLD",
  "note": "Xác minh sản phẩm là hàng cấm lưu hành theo quy chế cộng đồng, áp dụng chế tài trừ 1 sao và rà soát các bài đăng khác."
}
```

*Hoặc bác bỏ:*

```json
{
  "decision": "DISMISS",
  "note": "Bằng chứng người báo cáo cung cấp không đủ cơ sở xác định vi phạm."
}
```

- **Thành công:** `200 OK`, trả về `ReportConclusionResponse`.

```json
{
  "id": 1,
  "targetType": "ITEM",
  "itemId": 105,
  "targetUserId": null,
  "requestId": null,
  "reason": "PROHIBITED_ITEM",
  "status": "RESOLVED",
  "resolvedBy": 2,
  "resolution": "Xác minh sản phẩm là hàng cấm lưu hành theo quy chế cộng đồng, áp dụng chế tài trừ 1 sao và rà soát các bài đăng khác.",
  "starsDeducted": 1,
  "resolvedAt": "2026-10-18T13:00:00Z"
}
```

- **Mã lỗi:**
  - `400 BAD_REQUEST`:
    - `VALIDATION_FAILED`: `decision` bị thiếu hoặc không thuộc `UPHOLD`/`DISMISS`; `note` vượt quá 500 ký tự.
  - `401 UNAUTHORIZED`: Chưa đăng nhập.
  - `403 FORBIDDEN`: Người dùng không có vai trò `ROLE_ADMIN`.
  - `404 NOT_FOUND`:
    - `REPORT_NOT_FOUND`: Báo cáo vi phạm với `reportId` tương ứng không tồn tại.
  - `409 CONFLICT`:
    - `REPORT_ALREADY_CONCLUDED`: Báo cáo vi phạm đã được kết luận xử lý trước đó.

---

## DTO & Enums

### ReportDecision
- `UPHOLD`: Chấp thuận khiếu nại (báo cáo có cơ sở, áp dụng chế tài).
- `DISMISS`: Bác bỏ khiếu nại (báo cáo không có cơ sở, đóng khiếu nại).

### TargetType
- `ITEM`: Báo cáo bài đăng cho tặng / trao đổi đồ.
- `USER`: Báo cáo tài khoản người dùng vi phạm.
- `REQUEST`: Báo cáo yêu cầu nhận đồ (khiếu nại tranh chấp giao dịch).

### ReportReason
- `NOT_AS_DESCRIBED`: Đồ không đúng mô tả.
- `NO_SHOW`: Không đến điểm hẹn nhận / trao đổi đồ.
- `COUNTERFEIT`: Hàng giả, hàng nhái.
- `PROHIBITED_ITEM`: Hàng thuộc danh mục cấm cho tặng.
- `HARASSMENT`: Quấy rối, đe dọa, xúc phạm.
- `OTHER`: Lý do khác.

### ReportStage
- `BEFORE`: Tranh chấp hoặc phát hiện vi phạm trước khi hoàn tất giao dịch.
- `AFTER`: Phát hiện vi phạm sau khi hoàn tất giao dịch (khiếu nại giao dịch đã COMPLETED).

### ReportStatus
- `OPEN`: Mới tạo, đang chờ xử lý.
- `AWAITING_RESPONSE`: Chờ bên bị khiếu nại gửi giải trình (cửa sổ 3 ngày).
- `RESOLVED`: Đã giải quyết xong (chấp thuận khiếu nại).
- `DISMISSED`: Bác bỏ báo cáo / khiếu nại.
