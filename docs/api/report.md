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

## DTO & Enums

### TargetType
- `ITEM`: Báo cáo bài đăng cho tặng / trao đổi đồ.
- `USER`: Báo cáo tài khoản người dùng vi phạm.
- `REQUEST`: Báo cáo yêu cầu nhận đồ (khiếu nại tranh chấp giao dịch).

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
- `AFTER`: Phát hiện vi phạm sau khi hoàn tất giao dịch (khiếu nại giao dịch đã COMPLETED).

### ReportStatus
- `OPEN`: Mới tạo, đang chờ xử lý.
- `AWAITING_RESPONSE`: Chờ bên bị khiếu nại gửi giải trình (cửa sổ 3 ngày).
- `RESOLVED`: Đã giải quyết xong.
- `DISMISSED`: Bác bỏ báo cáo / khiếu nại.

