# API module report

Quy ước chung theo Lộ trình mục 3.4: tiền tố `/api/v1`, thời gian ISO-8601 UTC, thành công trả thẳng DTO (không bọc), lỗi theo định dạng thống nhất của hệ thống.

Người sở hữu: BE4 (@kopslngbtram2110).

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

