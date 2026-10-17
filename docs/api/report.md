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

## DTO & Enums

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
- `RESOLVED`: Đã giải quyết xong.
- `DISMISSED`: Bác bỏ báo cáo (báo cáo sai sự thật).

