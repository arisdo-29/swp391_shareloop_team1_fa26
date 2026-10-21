# API module admin

Quy ước chung theo Lộ trình mục 3.4: tiền tố `/api/v1`, thời gian ISO-8601 UTC, thành công trả thẳng DTO (không bọc), lỗi theo định dạng thống nhất của hệ thống.

---

## 1. GET /api/v1/admin/activity-logs

Xem nhật ký hành động trong hệ thống (FR-115, Issue #117). Chỉ dành cho quản trị viên (Admin). Sắp xếp mới nhất lên trước (`createdAt DESC`), hỗ trợ phân trang và lọc đa tiêu chí.

- **Xác thực:** Bắt buộc Bearer Token (JWT) có quyền Admin (`hasRole('ADMIN')`).
- **Query Parameters:**

| Tham số | Kiểu | Bắt buộc | Mặc định | Mô tả |
|---|---|---|---|---|
| `actorId` | number | Không | - | Lọc theo ID người thực hiện |
| `type` | string | Không | - | Lọc theo loại hành động, một trong các mã ở bảng [Loại hành động](#loại-hành-động-activitytype) (vd: `REVIEW_APPROVE`, `USER_LOCK`) |
| `from` | yyyy-MM-dd | Không | - | Từ ngày (tính từ 00:00:00 theo múi giờ Asia/Ho_Chi_Minh) |
| `to` | yyyy-MM-dd | Không | - | Đến ngày (tính hết 23:59:59 của ngày `to` theo múi giờ Asia/Ho_Chi_Minh) |
| `page` | number | Không | 0 | Chỉ số trang (bắt đầu từ 0) |
| `size` | number | Không | 20 | Số lượng bản ghi mỗi trang (tối đa 100) |

- **Thành công:** `200 OK`, trả về `Page<ActivityLogDto>`. Tên người thực hiện và tên đối tượng đã được backend tra sẵn, FE không cần gọi thêm API.

```json
{
  "content": [
    {
      "id": 101,
      "actorId": 1,
      "actorName": "Nguyễn Văn A",
      "actorEmail": "admin@fpt.edu.vn",
      "type": "CONFIG_UPDATE",
      "typeLabel": "Sửa cấu hình",
      "targetType": "CONFIG",
      "targetId": null,
      "targetTitle": "POST_FEE",
      "data": {
        "key": "POST_FEE",
        "oldValue": "5",
        "newValue": "10"
      },
      "reason": "Điều chỉnh phí đăng bài",
      "detail": "POST_FEE: 5 → 10",
      "createdAt": "2026-10-17T10:00:00Z"
    }
  ],
  "page": {
    "size": 20,
    "number": 0,
    "totalElements": 1,
    "totalPages": 1
  }
}
```

- **Mã lỗi:**

| Mã | Ý nghĩa | Mô tả |
|----|---------|-------|
| `200` | Thành công | Trả về danh sách nhật ký hành động |
| `400` | Bad Request | `INVALID_DATE_RANGE`: Từ ngày (`from`) sau đến ngày (`to`) |
| `401` | Unauthorized | Chưa đăng nhập hoặc token không hợp lệ |
| `403` | Forbidden | Người dùng không có vai trò Admin |

### Các trường của ActivityLogDto

| Trường | Kiểu | Mô tả |
|---|---|---|
| `id` | number | ID bản ghi nhật ký |
| `actorId` | number \| null | ID người thực hiện; `null` khi hệ thống tự làm (vd: trừ sao tự động) |
| `actorName` | string | Tên người thực hiện. `actorId = null` → `"Hệ thống"`. Không tìm thấy user (đã xoá mềm) → `"Người dùng #<id>"` |
| `actorEmail` | string \| null | Email người thực hiện; `null` khi là hệ thống hoặc không tìm thấy |
| `type` | string | Mã loại hành động (xem bảng dưới) |
| `typeLabel` | string | Nhãn tiếng Việt của `type`. Mã cũ không còn trong bảng thì trả lại chính mã đó |
| `targetType` | string \| null | Loại đối tượng: `ITEM`, `USER`, `CONFIG`, `REPORT`, `REQUEST`, `LEXICON`, `CHECKLIST`, `CATEGORY`, `CATEGORY_ATTRIBUTE`, `AREA` |
| `targetId` | number \| null | ID đối tượng |
| `targetTitle` | string \| null | Tên hiển thị của đối tượng, xem quy tắc dưới |
| `data` | object \| null | Dữ liệu bổ sung dạng key-value, khác nhau theo `type` |
| `reason` | string \| null | Lý do Admin nhập |
| `detail` | string \| null | Câu tóm tắt để hiển thị, xem quy tắc dưới |
| `createdAt` | string (ISO-8601) | Thời điểm thực hiện |

**Quy tắc `targetTitle`:**

| `targetType` | `targetTitle` |
|---|---|
| `ITEM` | Tên bài đăng (kể cả bài đã xoá mềm) |
| `USER` | Họ tên người dùng; không tìm thấy → `"Người dùng #<id>"` |
| `CONFIG` | Giá trị `data.key` |
| `REPORT` | `"Báo cáo #<id>"` |
| `REQUEST` | `"Yêu cầu #<id>"` |
| Loại khác | `null` |

**Quy tắc `detail`:** `REVIEW_APPROVE` → `"Duyệt bài"`; `CONFIG_UPDATE` → `"<key>: <oldValue> → <newValue>"`; còn lại → `reason`.

### Loại hành động (ActivityType)

| `type` | Nhãn | `targetType` | `data` |
|---|---|---|---|
| `REVIEW_APPROVE` | Duyệt bài | `ITEM` | `checks` (câu hỏi checklist và kết quả) |
| `REVIEW_REJECT` | Từ chối bài | `ITEM` | - (lý do ở `reason`) |
| `CONFIG_UPDATE` | Sửa cấu hình | `CONFIG` | `key`, `oldValue`, `newValue` |
| `USER_LOCK` | Khoá tài khoản | `USER` | - |
| `USER_UNLOCK` | Mở khoá tài khoản | `USER` | - |
| `REPORT_DISMISSED` | Bác bỏ báo cáo | `REPORT` | `decision`, `reportId`, `targetType` |
| `REPORT_UPHELD` | Chấp nhận báo cáo | `REPORT` | `decision`, `reportId`, `targetType`, `violatorId`, `starsDeducted`, `seriousViolation` |
| `POINTS` | Cộng điểm | `REQUEST` | `points`, `totalPoints`, `rank`, `role` |
| `STARS` | Trừ sao | `USER` | `amount`, `before`, `after` |
| `AI_USAGE` | Gọi AI kiểm duyệt | `ITEM` | `attempt`, `outcome`, `durationMs`, `model` |
| `LEXICON_CREATE` | Thêm từ khoá cấm | `LEXICON` | `term`, `severity`, `active` |
| `LEXICON_UPDATE` | Sửa từ khoá cấm | `LEXICON` | `term`, `severity`, `active` |
| `LEXICON_TOGGLE` | Bật/tắt từ khoá cấm | `LEXICON` | `term`, `severity`, `active` |
| `CHECKLIST_CREATE` | Thêm câu hỏi kiểm duyệt | `CHECKLIST` | `question`, `active` |
| `CHECKLIST_UPDATE` | Sửa câu hỏi kiểm duyệt | `CHECKLIST` | `question`, `active` |
| `CHECKLIST_TOGGLE` | Bật/tắt câu hỏi kiểm duyệt | `CHECKLIST` | `question`, `active` |
| `CHECKLIST_REORDER` | Sắp xếp câu hỏi kiểm duyệt | `CHECKLIST` (`targetId = null`) | `itemIds` (thứ tự mới) |
| `CATEGORY_CREATE` | Thêm danh mục | `CATEGORY` | `name`, `active` |
| `CATEGORY_UPDATE` | Sửa danh mục | `CATEGORY` | `name`, `active` |
| `CATEGORY_TOGGLE` | Bật/tắt danh mục | `CATEGORY` | `name`, `active` |
| `CATEGORY_ATTRIBUTE_CREATE` | Thêm thuộc tính danh mục | `CATEGORY_ATTRIBUTE` | `categoryId`, `name`, `active` |
| `CATEGORY_ATTRIBUTE_UPDATE` | Sửa thuộc tính danh mục | `CATEGORY_ATTRIBUTE` | `categoryId`, `name`, `active` |
| `CATEGORY_ATTRIBUTE_TOGGLE` | Bật/tắt thuộc tính danh mục | `CATEGORY_ATTRIBUTE` | `categoryId`, `name`, `active` |
| `AREA_CREATE` | Thêm khu vực | `AREA` | `name`, `active` |
| `AREA_UPDATE` | Sửa khu vực | `AREA` | `name`, `active` |
| `AREA_TOGGLE` | Bật/tắt khu vực | `AREA` | `name`, `active` |

> Bản ghi cũ có `type = 'AiUsage'` được migration `V202610191500__audit_rename_ai_usage` đổi thành `AI_USAGE`.

---

## 1.1. GET /api/v1/admin/activity-logs/types

Danh sách loại hành động kèm nhãn tiếng Việt, dùng để dựng bộ lọc `type` trên FE. Thứ tự theo bảng [Loại hành động](#loại-hành-động-activitytype).

- **Xác thực:** Bắt buộc Bearer Token (JWT) có quyền Admin (`hasRole('ADMIN')`).
- **Thành công:** `200 OK`

```json
[
  { "type": "REVIEW_APPROVE", "label": "Duyệt bài" },
  { "type": "REVIEW_REJECT", "label": "Từ chối bài" },
  { "type": "CONFIG_UPDATE", "label": "Sửa cấu hình" }
]
```

- **Mã lỗi:** `401` chưa đăng nhập, `403` không phải Admin.

---

## 2. GET /api/v1/admin/finance/report

Báo cáo tài chính ba sổ và đối chiếu TopUp (FR-109, FR-110, BR-P09, Issue #115). Chỉ dành cho Admin. Số liệu lấy trực tiếp qua `CreditService` (tiền mặt thu, Credit cộng vào, Credit trừ ra, đối chiếu với đơn nạp TopUp).

- **Xác thực:** Bắt buộc Bearer Token (JWT) có quyền Admin (`hasRole('ADMIN')`).
- **Query Parameters:**

| Tham số | Kiểu | Bắt buộc | Mặc định | Mô tả |
|---|---|---|---|---|
| `from` | yyyy-MM-dd | Không | - | Từ ngày (tính từ 00:00:00 theo múi giờ Asia/Ho_Chi_Minh) |
| `to` | yyyy-MM-dd | Không | - | Đến ngày (tính hết 23:59:59 của ngày `to` theo múi giờ Asia/Ho_Chi_Minh) |

- **Thành công:** `200 OK`, trả về `FinanceSummaryResponse`.

```json
{
  "from": "2026-09-30T17:00:00Z",
  "to": "2026-10-31T17:00:00Z",
  "cashOrderCount": 3,
  "cashAmountVnd": 100000,
  "creditInTotal": 107,
  "creditInByType": [
    { "type": "ADMIN_ADJUST", "amount": 7, "entryCount": 1 },
    { "type": "TOP_UP", "amount": 100, "entryCount": 3 }
  ],
  "creditOutTotal": 22,
  "creditOutByType": [
    { "type": "AI_SEARCH_FEE", "amount": 2, "entryCount": 2 },
    { "type": "EDIT_FEE", "amount": 5, "entryCount": 1 },
    { "type": "POST_FEE", "amount": 10, "entryCount": 2 },
    { "type": "RENEW_FEE", "amount": 5, "entryCount": 1 }
  ],
  "reconciliation": {
    "cashCredits": 100,
    "topUpCredits": 100,
    "difference": 0,
    "matched": true
  }
}
```

- **Mã lỗi:**

| Mã | Ý nghĩa | Mô tả |
|----|---------|-------|
| `200` | Thành công | Trả về báo cáo tài chính |
| `400` | Bad Request | `INVALID_DATE_RANGE`: Từ ngày (`from`) sau đến ngày (`to`) |
| `401` | Unauthorized | Chưa đăng nhập hoặc token không hợp lệ |
| `403` | Forbidden | Người dùng không có vai trò Admin |

---

## 2.1. GET /api/v1/admin/finance/report.csv

Xuất báo cáo tài chính ra file CSV (FR-111, Issue #149). Cùng số liệu và cùng cách hiểu `from`/`to` với [mục 2](#2-get-apiv1adminfinancereport); chỉ khác định dạng trả về. Chỉ dành cho Admin.

- **Xác thực:** Bắt buộc Bearer Token (JWT) có quyền Admin (`hasRole('ADMIN')`).
- **Query Parameters:** `from`, `to` giống [mục 2](#2-get-apiv1adminfinancereport).
- **Thành công:** `200 OK`, body là file CSV.

| Header | Giá trị |
|---|---|
| `Content-Type` | `text/csv;charset=UTF-8` |
| `Content-Disposition` | `attachment; filename="finance-report_<from>_<to>.csv"`; thiếu `from` ghi `start`, thiếu `to` ghi `now` (vd: `finance-report_start_now.csv`) |

**Định dạng file:**

- Mã hoá UTF-8 **có BOM** (3 byte đầu `EF BB BF`) để Excel hiển thị đúng tiếng Việt; phân cách bằng dấu phẩy, xuống dòng CRLF.
- File chia thành các phần cách nhau một dòng trống. Mỗi phần gồm dòng tiêu đề tiếng Việt, dòng header trùng tên trường của `FinanceSummaryResponse`, rồi các dòng dữ liệu.
- `Từ ngày` / `Đến ngày` là ngày Admin truyền vào (theo giờ Việt Nam); không truyền thì ghi `không giới hạn`.
- Phần Credit liệt kê từng `type` có dữ liệu (sắp theo tên type), số luôn dương; dòng `Tổng` có `amount` = `creditInTotal` / `creditOutTotal` và `entryCount` = tổng `entryCount` các dòng phía trên.

Ví dụ với cùng dữ liệu của mục 2 (`from=2026-10-01&to=2026-10-31`):

```csv
Báo cáo tài chính ShareLoop
Từ ngày,2026-10-01
Đến ngày,2026-10-31

Tiền mặt thu
cashOrderCount,cashAmountVnd
3,100000

Credit cộng vào
type,amount,entryCount
ADMIN_ADJUST,7,1
TOP_UP,100,3
Tổng,107,4

Credit trừ ra
type,amount,entryCount
AI_SEARCH_FEE,2,2
EDIT_FEE,5,1
POST_FEE,10,2
RENEW_FEE,5,1
Tổng,22,6

Đối chiếu TopUp
cashCredits,topUpCredits,difference,matched
100,100,0,true
```

- **Mã lỗi:** trả JSON theo định dạng lỗi chung (không phải CSV), không có `Content-Disposition`.

| Mã | Ý nghĩa | Mô tả |
|----|---------|-------|
| `200` | Thành công | Trả về file CSV |
| `400` | Bad Request | `INVALID_DATE_RANGE`: Từ ngày (`from`) sau đến ngày (`to`); `VALIDATION_FAILED`: ngày sai định dạng |
| `401` | Unauthorized | Chưa đăng nhập hoặc token không hợp lệ |
| `403` | Forbidden | Người dùng không có vai trò Admin |

> FE gọi khác origin (vd: `VITE_API_BASE_URL` trỏ sang Render) không đọc được header `Content-Disposition` vì CORS mới expose `X-Trace-Id`; FE tự đặt tên file khi lưu blob.

---

## 3. GET /api/v1/admin/finance/users/{userId}/history

Tra cứu lịch sử tài chính và biến động số dư Credit của một người dùng (FR-112, Issue #115). Chỉ dành cho Admin. Phân trang và sắp xếp mới nhất lên trước (`createdAt DESC, id DESC`).

- **Xác thực:** Bắt buộc Bearer Token (JWT) có quyền Admin (`hasRole('ADMIN')`).
- **Path Variable:** `userId` (ID người dùng cần tra cứu).
- **Query Parameters:**

| Tham số | Kiểu | Bắt buộc | Mặc định | Mô tả |
|---|---|---|---|---|
| `from` | yyyy-MM-dd | Không | - | Từ ngày (tính từ 00:00:00 theo múi giờ Asia/Ho_Chi_Minh) |
| `to` | yyyy-MM-dd | Không | - | Đến ngày (tính hết 23:59:59 của ngày `to` theo múi giờ Asia/Ho_Chi_Minh) |
| `page` | number ≥ 0 | Không | 0 | Chỉ số trang |
| `size` | number 1..100 | Không | 20 | Số dòng mỗi trang |

- **Thành công:** `200 OK`, trả về `PageResponse<WalletTransactionResponse>`.

```json
{
  "items": [
    {
      "id": 12,
      "type": "TOP_UP",
      "amount": 20,
      "balanceAfter": 40,
      "itemId": null,
      "paymentOrderId": 7,
      "note": null,
      "createdAt": "2026-10-14T03:15:00Z"
    },
    {
      "id": 9,
      "type": "POST_FEE",
      "amount": -5,
      "balanceAfter": 20,
      "itemId": 31,
      "paymentOrderId": null,
      "note": null,
      "createdAt": "2026-10-10T08:00:00Z"
    }
  ],
  "page": 0,
  "size": 20,
  "totalElements": 2
}
```

- **Mã lỗi:**

| Mã | Ý nghĩa | Mô tả |
|----|---------|-------|
| `200` | Thành công | Trả về danh sách giao dịch |
| `400` | Bad Request | `INVALID_DATE_RANGE` hoặc `VALIDATION_FAILED` khi `page`/`size` sai |
| `401` | Unauthorized | Chưa đăng nhập hoặc token không hợp lệ |
| `403` | Forbidden | Người dùng không có vai trò Admin |
| `404` | Not Found | `WALLET_NOT_FOUND`: Người dùng không tồn tại ví trong hệ thống |

---

## 4. POST /api/v1/admin/users/{userId}/lock

Khoá tài khoản người dùng và ghi nhật ký hoạt động (FR-107, Issue #116). Chỉ dành cho quản trị viên (Admin). Bắt buộc cung cấp lý do khoá.

- **Xác thực:** Bắt buộc Bearer Token (JWT) có quyền Admin (`hasRole('ADMIN')`).
- **Path Variable:** `userId` (ID người dùng cần khoá).
- **Request Body:**

```json
{
  "reason": "Nhiều báo cáo lừa đảo đã được xác minh"
}
```

- **Thành công:** `200 OK` (không có body).
- **Mã lỗi:**

| Mã | Ý nghĩa | Mô tả |
|----|---------|-------|
| `200` | Thành công | Khoá tài khoản thành công và đã ghi nhật ký |
| `400` | Bad Request | `VALIDATION_FAILED` (thiếu lý do hoặc vượt quá 500 ký tự) hoặc `USER_CANNOT_LOCK_SELF` (Admin tự khoá chính mình) |
| `401` | Unauthorized | Chưa đăng nhập hoặc token không hợp lệ |
| `403` | Forbidden | Người dùng không có vai trò Admin |
| `404` | Not Found | `USER_NOT_FOUND`: Người dùng không tồn tại |
| `409` | Conflict | `USER_ALREADY_LOCKED`: Tài khoản đã bị khoá từ trước |

---

## 5. POST /api/v1/admin/users/{userId}/unlock

Mở khoá tài khoản người dùng và ghi nhật ký hoạt động (FR-107, Issue #116). Chỉ dành cho quản trị viên (Admin). Bắt buộc cung cấp lý do mở khoá.

- **Xác thực:** Bắt buộc Bearer Token (JWT) có quyền Admin (`hasRole('ADMIN')`).
- **Path Variable:** `userId` (ID người dùng cần mở khoá).
- **Request Body:**

```json
{
  "reason": "Người dùng đã giải trình hợp lệ và hoàn tất cam kết"
}
```

- **Thành công:** `200 OK` (không có body).
- **Mã lỗi:**

| Mã | Ý nghĩa | Mô tả |
|----|---------|-------|
| `200` | Thành công | Mở khoá tài khoản thành công và đã ghi nhật ký |
| `400` | Bad Request | `VALIDATION_FAILED`: Thiếu lý do hoặc lý do vượt quá 500 ký tự |
| `401` | Unauthorized | Chưa đăng nhập hoặc token không hợp lệ |
| `403` | Forbidden | Người dùng không có vai trò Admin |
| `404` | Not Found | `USER_NOT_FOUND`: Người dùng không tồn tại |
| `409` | Conflict | `USER_NOT_LOCKED`: Tài khoản đang hoạt động bình thường, không cần mở khoá |
