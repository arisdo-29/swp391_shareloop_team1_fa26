# API module report

Quy ước chung theo Lộ trình mục 3.4: tiền tố `/api/v1`, thời gian ISO-8601 UTC, thành công trả thẳng DTO (không bọc), lỗi theo định dạng thống nhất của hệ thống.

Người sở hữu: BE4 (@kopslngbtram2110).

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
    "stage": "BEFORE", "status": "OPEN", "createdAt": "2026-10-20T03:00:00Z"
  }],
  "page": 0, "size": 20, "totalElements": 1, "totalPages": 1
}
```

Trang rỗng trả `items: []`; không có bản ghi thì `totalPages: 0`.

### GET /api/v1/admin/reports/{reportId}

Yêu cầu Admin; `reportId` là số nguyên dương. Response:

```json
{
  "report": {
    "id": 71, "reporterId": 10, "targetType": "REQUEST",
    "itemId": 105, "targetUserId": 42, "requestId": 88,
    "reason": "NOT_AS_DESCRIBED", "description": "Đồ khác mô tả",
    "stage": "BEFORE", "status": "OPEN", "createdAt": "2026-10-20T03:00:00Z"
  },
  "reporter": {"id": 10, "fullName": "Người gửi"},
  "reportedUser": {"id": 42, "fullName": "Người bị báo cáo"},
  "relatedItemId": 105,
  "response": "Nội dung giải trình", "respondedAt": "2026-10-20T04:00:00Z",
  "responseDueAt": "2026-10-23T03:00:00Z",
  "evidence": [{"id": 12, "url": "/api/v1/admin/reports/71/evidence/12", "available": true}],
  "responseEvidence": [{"id": 15, "url": null, "available": false}],
  "resolvedBy": null, "resolution": null, "starsDeducted": 0, "resolvedAt": null
}
```

Không trả email, số điện thoại hoặc storage key. Tên có thể `null` nếu người dùng không
còn khả dụng; `reportedUser` có thể `null` nếu không xác định được bên bị báo cáo.
Hồ sơ vẫn đọc được khi đối tượng liên quan đã mất. `relatedItemId` lấy thêm từ giao dịch
nếu hồ sơ cũ chưa lưu ID bài. Trường giải trình/kết luận chưa có trả `null`.

Migration `V202610200300__report_evidence_and_response_deadline.sql` thêm ba cột
`evidence`, `response_evidence`, `response_due_at` vào bảng hiện có. Hai cột bằng chứng lưu
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

Khi báo cáo `REQUEST`, người gửi phải là một trong hai bên giao dịch; hệ thống lưu
người bị báo cáo, ID bài và `responseDueAt` theo `ConfigKey.RESPONSE_WINDOW_DAYS`,
chuyển `AWAITING_RESPONSE`. Báo cáo `ITEM` lưu thêm ID chủ bài để giữ danh tính đối tượng.

`POST /api/v1/reports/{reportId}/response` yêu cầu Bearer JWT của bên bị báo cáo:

```json
{"response": "Nội dung giải trình", "evidenceMediaIds": [15]}
```

`response` bắt buộc, không toàn khoảng trắng, tối đa 2000 ký tự; `evidenceMediaIds`
giống quy tắc bên gửi. Thành công `204 No Content`, lưu nội dung đã trim, ảnh và
`respondedAt`, chuyển hồ sơ về `OPEN`. Khoá dòng cùng cơ chế kết luận, không nhận
giải trình lần hai, sau hạn, hoặc khi hồ sơ không còn `AWAITING_RESPONSE`.

Lỗi ghi bổ sung: `400 VALIDATION_FAILED`; `403 REPORT_NOT_PARTICIPANT`;
`409 REPORT_RESPONSE_NOT_ALLOWED`; `404 REPORT_NOT_FOUND`/`REQUEST_NOT_FOUND`/
`REQUEST_ITEM_NOT_FOUND`; media: `403 MEDIA_NOT_OWNER`, `404 MEDIA_NOT_FOUND`,
`409 MEDIA_NOT_UPLOADED`, `422 MEDIA_INVALID_FILE` (ID trùng).

Admin vẫn dùng `POST .../conclude` với `{decision: "UPHOLD" | "DISMISS", note}`;
ghi chú không bắt buộc, tối đa 500 ký tự. Không thêm hạn chế thời điểm kết luận.
FE xác nhận trước khi gửi, khoá nút khi gửi, tải lại list theo bộ lọc hiện tại và chi tiết
sau thành công; nếu `409 REPORT_ALREADY_CONCLUDED`, báo hồ sơ đã được xử lý và tải lại.
Các request đọc bị huỷ/bỏ qua khi chuyển hồ sơ hoặc bộ lọc.

---

## 1. POST /api/v1/reports

Báo cáo một bài đăng hoặc một người dùng vi phạm quy tắc cộng đồng (FR-88, BR-A03).

- **Xác thực:** Bắt buộc Bearer Token (JWT). ID của người báo cáo được trích xuất tự động từ phiên đăng nhập hiện tại (`@CurrentUserId`).
- **Quy tắc nghiệp vụ:**
  - Người dùng không được tự báo cáo chính mình hoặc bài đăng do chính mình tạo ra (`409 REPORT_CANNOT_REPORT_SELF`).
  - Mỗi người dùng chỉ được báo cáo một đối tượng (bài đăng hoặc người dùng) đúng 1 lần (`409 REPORT_ALREADY_REPORTED`).
  - Đối tượng báo cáo phải tồn tại trong hệ thống (`404 REPORT_TARGET_NOT_FOUND`).
  - **BR-A03 (Tự động ẩn bài khi đạt 3 báo cáo):** Khi số lượng báo cáo hợp lệ đối với một bài đăng đạt ngưỡng từ 3 báo cáo trở lên, hệ thống sẽ tự động kích hoạt ẩn bài đăng (`itemService.hide(itemId)`).
- **Request Body:**

*Báo cáo bài đăng:*

```json
{
  "targetType": "ITEM",
  "itemId": 105,
  "reason": "NOT_AS_DESCRIBED",
  "description": "Món đồ thực tế bị rách nát hoàn toàn, không giống như ảnh mô tả"
}
```

*Hoặc báo cáo người dùng:*

```json
{
  "targetType": "USER",
  "targetUserId": 42,
  "reason": "HARASSMENT",
  "description": "Người dùng nhắn tin xúc phạm và đe doạ"
}
```

- **Thành công:** `201 Created`, trả về `ReportResponse`.

```json
{
  "id": 1,
  "reporterId": 10,
  "targetType": "ITEM",
  "itemId": 105,
  "targetUserId": null,
  "requestId": null,
  "reason": "NOT_AS_DESCRIBED",
  "description": "Món đồ thực tế bị rách nát hoàn toàn, không giống như ảnh mô tả",
  "stage": "BEFORE",
  "status": "OPEN",
  "createdAt": "2026-10-17T16:00:00Z"
}
```

- **Mã lỗi:**
  - `400 BAD_REQUEST`:
    - `REPORT_TARGET_REQUIRED`: Không xác định được ID đối tượng cần báo cáo.
    - `VALIDATION_FAILED`: `targetType`, `reason` hoặc `description` không hợp lệ/bị trống.
  - `401 UNAUTHORIZED`: Chưa đăng nhập.
  - `404 NOT_FOUND`:
    - `REPORT_TARGET_NOT_FOUND`: Bài đăng hoặc người dùng cần báo cáo không tồn tại.
  - `409 CONFLICT`:
    - `REPORT_CANNOT_REPORT_SELF`: Người dùng cố gắng tự báo cáo chính mình hoặc bài đăng của chính mình.
    - `REPORT_ALREADY_REPORTED`: Người dùng đã từng gửi báo cáo cho đối tượng này.

---

## 2. POST /api/v1/admin/reports/{reportId}/conclude

Admin đưa ra kết luận xử lý đối với báo cáo vi phạm (FR-91, FR-93, BR-U04, BR-A07, Issue #113).

- **Xác thực:** Bắt buộc Bearer Token (JWT) có quyền `ROLE_ADMIN`. ID của Admin được trích xuất từ phiên đăng nhập (`@CurrentUserId`).
- **Quy tắc nghiệp vụ:**
  - Báo cáo phải tồn tại trong hệ thống (`404 REPORT_NOT_FOUND`).
  - Báo cáo chưa được kết luận trước đó; nếu đã ở trạng thái `RESOLVED` hoặc `DISMISSED` thì từ chối xử lý (`409 REPORT_ALREADY_CONCLUDED`).
  - **Khi kết luận DISMISS (Bác bỏ / Không có cơ sở):**
    - Trạng thái báo cáo chuyển thành `DISMISSED`.
    - Không trừ sao uy tín, không ẩn bài đăng.
    - Ghi nhận nhật ký hành động Admin (`activity_logs` loại `REPORT_DISMISSED`).
  - **Khi kết luận UPHOLD (Chấp thuận / Khiếu nại có cơ sở):**
    - Trạng thái báo cáo chuyển thành `RESOLVED`.
    - Xác định người vi phạm (`violatorId`):
      - Đối tượng là `ITEM`: Chủ bài đăng (`donorId`).
      - Đối tượng là `USER`: Người dùng bị báo cáo (`targetUserId`).
      - Đối tượng là `REQUEST`: Bên bị khiếu nại trong giao dịch (khác với người gửi báo cáo).
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
- `REQUEST`: Báo cáo yêu cầu nhận đồ (nếu có tranh chấp trong quá trình nhận đồ).

### ReportReason
- `NOT_AS_DESCRIBED`: Đồ không đúng mô tả.
- `COUNTERFEIT`: Hàng giả / hàng nhái.
- `PROHIBITED_ITEM`: Hàng cấm theo quy định pháp luật hoặc nội quy cộng đồng.
- `NO_SHOW`: Không đến điểm hẹn nhận / trao đổi đồ.
- `WRONG_CONTACT_INFO`: Sai thông tin liên hệ / thông tin ảo.
- `HARASSMENT`: Quấy rối, khiếm nhã, đe dọa.
- `OTHER`: Lý do khác.

### ReportStage
- `BEFORE`: Tranh chấp hoặc phát hiện vi phạm trước khi hoàn tất giao dịch.
- `AFTER`: Phát hiện vi phạm sau khi hoàn tất giao dịch.

### ReportStatus
- `OPEN`: Mới tạo, đang chờ ban quản trị xử lý.
- `AWAITING_RESPONSE`: Chờ người bị báo cáo giải trình.
- `RESOLVED`: Đã giải quyết xong (chấp thuận khiếu nại).
- `DISMISSED`: Bác bỏ báo cáo (báo cáo sai sự thật / không có cơ sở).

