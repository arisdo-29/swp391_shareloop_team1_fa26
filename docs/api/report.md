# Report API Contract

Module báo cáo vi phạm (`report`) quản lý việc tiếp nhận khiếu nại và báo cáo từ người dùng nhằm duy trì môi trường an toàn cho nền tảng ShareLoop.

---

## 1. POST /api/v1/reports

Báo cáo một bài đăng hoặc một người dùng vi phạm quy tắc cộng đồng.

- **Xác thực:** Bắt buộc Bearer Token (JWT). ID người báo cáo được trích xuất từ phiên đăng nhập hiện tại (`@CurrentUserId`).
- **Quy tắc nghiệp vụ:**
  - Không thể tự báo cáo chính mình hoặc bài đăng của chính mình (`400 REPORT_CANNOT_REPORT_SELF`).
  - Mỗi người dùng chỉ được báo cáo một đối tượng tối đa 1 lần (`409 REPORT_ALREADY_REPORTED`).
  - Đối tượng báo cáo phải tồn tại trong hệ thống (`404 REPORT_TARGET_NOT_FOUND`).
  - Cơ chế tự động bảo vệ (BR-A03): Nếu một bài đăng (`ITEM`) nhận đủ **3 báo cáo** vi phạm, hệ thống tự động ẩn bài (`itemService.hide(itemId)`).

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
  - Cửa sổ khiếu nại: Khiếu nại phải được gửi trong vòng 7 ngày kể từ thời điểm giao dịch hoàn thành (`completedAt`). Quá 7 ngày sẽ bị từ chối (`422 DISPUTE_WINDOW_EXPIRED`). Số ngày được cấu hình qua `DISPUTE_WINDOW_DAYS` (mặc định 7 ngày).
  - Mỗi người chỉ được khiếu nại giao dịch này một lần (`409 REPORT_ALREADY_REPORTED`).
  - Người khiếu nại có thể đính kèm danh sách ID media làm bằng chứng. Các media file phải tồn tại và thuộc quyền sở hữu của người gửi (`403 MEDIA_NOT_OWNER`, `404 MEDIA_NOT_FOUND`).
  - Sau khi mở khiếu nại thành công, hệ thống mở cửa sổ 3 ngày cho bên bị khiếu nại gửi giải trình (`responseDueAt = now + 3 days`), cấu hình qua `DISPUTE_RESPONSE_DAYS` (mặc định 3 ngày). Trạng thái bản ghi là `AWAITING_RESPONSE`.
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
    - `DISPUTE_WINDOW_EXPIRED`: Đã quá 7 ngày kể từ lúc giao dịch hoàn tất.

---

## 3. POST /api/v1/disputes/{disputeId}/response

Bên bị khiếu nại gửi văn bản giải trình kèm bằng chứng đối chất (FR-90, BR-T08, Issue #112).

- **Xác thực:** Bắt buộc Bearer Token (JWT). ID người giải trình được trích xuất từ phiên đăng nhập hiện tại (`@CurrentUserId`).
- **Quy tắc nghiệp vụ:**
  - Bản ghi khiếu nại phải tồn tại trong hệ thống (`404 DISPUTE_NOT_FOUND`).
  - Chỉ bên bị khiếu nại (`respondent`) mới có quyền gửi giải trình (`403 DISPUTE_NOT_RESPONDENT`).
  - Khiếu nại chưa bị đóng hoặc đã được Admin kết luận (`409 DISPUTE_ALREADY_CONCLUDED`).
  - Bên bị khiếu nại chỉ được gửi giải trình đúng 1 lần (`409 DISPUTE_ALREADY_RESPONDED`).
  - Giải trình phải được gửi trong vòng 3 ngày kể từ lúc khiếu nại được mở (`now <= responseDueAt`). Quá 3 ngày sẽ bị từ chối (`422 DISPUTE_RESPONSE_WINDOW_EXPIRED`).
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
