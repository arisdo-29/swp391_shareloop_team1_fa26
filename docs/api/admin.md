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
| `actorName` | string | Tên người thực hiện. `actorId = null` → `"Hệ thống"`. Không tìm thấy user (đã xoá mềm) → `"Tài khoản đã xoá"` |
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
| `USER` | Họ tên người dùng, không tìm thấy (đã xoá mềm) → `"Tài khoản đã xoá"` |
| `CONFIG` | Nhãn tiếng Việt của khoá trong `data.key` (vd `reputation_gold_min` → `"Điểm tối thiểu hạng Vàng"`), khoá lạ thì trả lại chính khoá |
| `REPORT` | `"Báo cáo về “<tên bài>”"` theo `data.itemId`, không có bài (báo cáo người dùng, log cũ) → `"Báo cáo"` |
| `REQUEST` | `"Giao dịch món “<tên bài>”"` theo `data.itemId`, log cũ không có → `"Giao dịch"` |
| Loại khác | `null` |

Không trả `#<id>` trong `targetTitle`, `actorName`. Log `REPORT_DISMISSED`, `REPORT_UPHELD`, `POINTS` lưu thêm `data.itemId`.

**Quy tắc `detail`:** `REVIEW_APPROVE` → `"Duyệt bài"`; `CONFIG_UPDATE` → `"<nhãn tiếng Việt của key>: <oldValue> → <newValue>"`; `AI_USAGE` và mã cũ `AiUsage` → vai trò tìm đồ/kiểm duyệt, `outcome` và thời lượng từ `data` (không hiển thị câu nhập hay API key); còn lại → `reason`.

### Loại hành động (ActivityType)

| `type` | Nhãn | `targetType` | `data` |
|---|---|---|---|
| `REVIEW_APPROVE` | Duyệt bài | `ITEM` | `checks` (câu hỏi checklist và kết quả) |
| `REVIEW_REJECT` | Từ chối bài | `ITEM` | - (lý do ở `reason`) |
| `CONFIG_UPDATE` | Sửa cấu hình | `CONFIG` | `key`, `oldValue`, `newValue` |
| `USER_LOCK` | Khoá tài khoản | `USER` | `cancelledRequests` (số giao dịch đã huỷ), `releasedCredit` (số Credit đã nhả) |
| `USER_UNLOCK` | Mở khoá tài khoản | `USER` | - |
| `REQUEST_CANCEL` | Huỷ giao dịch | `REQUEST` | - (lý do ở `reason`, có tiền tố người huỷ) |
| `REPORT_DISMISSED` | Bác bỏ báo cáo | `REPORT` | `decision`, `reportId`, `targetType` |
| `REPORT_UPHELD` | Chấp nhận báo cáo | `REPORT` | `decision`, `reportId`, `targetType`, `violatorId`, `starsDeducted`, `seriousViolation` |
| `POINTS` | Cộng điểm | `REQUEST` | `points` (điểm thực cộng, có thể 0), `basePoints`, `totalPoints`, `rank`, `role`, `pairTransactionNo`, `pointsToday` |
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
| `CATEGORY_REORDER` | Sắp xếp danh mục | `CATEGORY` (`targetId = null`) | `categoryIds` (thứ tự mới) |
| `CATEGORY_ATTRIBUTE_CREATE` | Thêm thuộc tính danh mục | `CATEGORY_ATTRIBUTE` | `categoryId`, `name`, `active` |
| `CATEGORY_ATTRIBUTE_UPDATE` | Sửa thuộc tính danh mục | `CATEGORY_ATTRIBUTE` | `categoryId`, `name`, `active` |
| `CATEGORY_ATTRIBUTE_TOGGLE` | Bật/tắt thuộc tính danh mục | `CATEGORY_ATTRIBUTE` | `categoryId`, `name`, `active` |
| `AREA_CREATE` | Thêm khu vực | `AREA` | `name`, `active` |
| `AREA_UPDATE` | Sửa khu vực | `AREA` | `name`, `active` |
| `AREA_TOGGLE` | Bật/tắt khu vực | `AREA` | `name`, `active` |
| `GRACE_STAR` | Cấp sao ân hạn | `USER` | `before`, `after` (lý do Admin nhập ở `reason`) |
| `STAR_RESTORED` | Tự hồi sao | `USER` (`actorId = null`) | `before`, `after`, `cleanStreak` |

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
  },
  "cashByDay": [
    { "date": "2026-10-03", "amountVnd": 20000, "orderCount": 1 },
    { "date": "2026-10-17", "amountVnd": 80000, "orderCount": 2 }
  ]
}
```

`cashByDay` là tiền mặt thực thu theo từng ngày để vẽ biểu đồ. Ngày tính theo giờ Việt Nam (`Asia/Ho_Chi_Minh`) từ thời điểm thanh toán `paid_at`, sắp ngày cũ trước, chỉ gồm ngày có ít nhất một đơn SUCCESS. Giao diện tự điền các ngày trống bằng 0 nếu cần. Tổng `amountVnd` của mảng này bằng `cashAmountVnd`.

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

## 3.1. GET /api/v1/admin/finance/users

Danh sách người dùng thường kèm số liệu Credit cơ bản để Admin tra cứu (FR-112). Không gồm tài khoản Admin. Người dùng mới nhất đứng trước.

- **Xác thực:** Bắt buộc Bearer Token (JWT) có quyền Admin.
- **Query Parameters:**

| Tham số | Kiểu | Bắt buộc | Mặc định | Mô tả |
|---|---|---|---|---|
| `keyword` | string | Không | - | Khớp một phần họ tên, email hoặc số điện thoại, không phân biệt hoa thường. Bỏ trống thì lấy tất cả |
| `page` | int | Không | `0` | Số trang, từ 0 |
| `size` | int | Không | `20` | 1 đến 100 |

- **Thành công:** `200 OK`, `PageResponse<AdminFinanceUserSummary>`.

```json
{
  "items": [
    {
      "userId": 7,
      "fullName": "Nguyễn Văn A",
      "email": "a@example.com",
      "phone": "0901234567",
      "availableCredit": 25,
      "heldCredit": 5,
      "totalCredit": 30,
      "totalToppedUpCredit": 50
    }
  ],
  "page": 0,
  "size": 20,
  "totalElements": 1
}
```

`availableCredit` là Credit dùng được, `heldCredit` là Credit đang giữ chờ duyệt bài, `totalCredit` là tổng Credit đang có (khả dụng cộng đang giữ). `totalToppedUpCredit` là tổng Credit của các đơn nạp SUCCESS từ trước đến nay (mọi cổng thanh toán). Người dùng chưa có ví hoặc chưa nạp lần nào có số liệu bằng 0.

| Mã | Ý nghĩa | Mô tả |
|----|---------|-------|
| `200` | Thành công | Trả về danh sách |
| `400` | Bad Request | `VALIDATION_FAILED` khi `page`/`size` sai |
| `401` | Unauthorized | Chưa đăng nhập hoặc token không hợp lệ |
| `403` | Forbidden | Người dùng không có vai trò Admin |

---

## 3.2. GET /api/v1/admin/finance/users/{userId}/wallet

Họ tên, email, số điện thoại và số liệu Credit của một người dùng, cho màn hình chi tiết của Admin (FR-112). Lịch sử giao dịch của người đó lấy ở [mục 3](#3-get-apiv1adminfinanceusersuseridhistory).

- **Xác thực:** Bắt buộc Bearer Token (JWT) có quyền Admin.
- **Thành công:** `200 OK`, cùng dạng `AdminFinanceUserSummary` như [mục 3.1](#31-get-apiv1adminfinanceusers).

| Mã | Ý nghĩa | Mô tả |
|----|---------|-------|
| `200` | Thành công | Trả về thông tin và số liệu Credit |
| `401` | Unauthorized | Chưa đăng nhập hoặc token không hợp lệ |
| `403` | Forbidden | Người dùng không có vai trò Admin |
| `404` | Not Found | `USER_NOT_FOUND`: Người dùng không tồn tại. `WALLET_NOT_FOUND`: người dùng không có ví |

---

## 3.3. GET /api/v1/admin/users

Danh sách tài khoản cho trang Quản lý tài khoản của Admin (FR-107), có tìm kiếm, lọc và phân trang. Tài khoản mới nhất đứng trước. Dùng cùng với [khoá](#4-post-apiv1adminusersuseridlock) và [mở khoá](#5-post-apiv1adminusersuseridunlock).

- **Xác thực:** Bắt buộc Bearer Token (JWT) có quyền Admin.
- **Query Parameters:**

| Tham số | Kiểu | Bắt buộc | Mặc định | Mô tả |
|---|---|---|---|---|
| `keyword` | string | Không | - | Khớp một phần họ tên, email hoặc số điện thoại, không phân biệt hoa thường |
| `status` | enum | Không | - | `ACTIVE`, `LOCKED`, `PENDING_VERIFICATION` hoặc `BANNED` |
| `role` | enum | Không | - | `ADMIN` hoặc `USER` |
| `page` | int | Không | `0` | Số trang, từ 0 |
| `size` | int | Không | `20` | 1 đến 100 |

`status` là trạng thái hiển thị. Cột `users.status` chỉ có `PENDING_VERIFICATION`, `ACTIVE`, `BANNED`, còn khoá tài khoản là `is_active = false` (ERD 7.11), nên `LOCKED` được suy ra. Khi một tài khoản thuộc nhiều nhóm thì ưu tiên theo thứ tự `BANNED`, `LOCKED`, `PENDING_VERIFICATION`, `ACTIVE`. Lọc `ACTIVE` hoặc `PENDING_VERIFICATION` chỉ lấy tài khoản chưa bị khoá.

- **Thành công:** `200 OK`, `PageResponse<AdminUserSummary>`.

```json
{
  "items": [
    {
      "id": 9,
      "fullName": "Nguyễn Văn A",
      "email": "a@example.com",
      "phone": "0901234567",
      "role": "USER",
      "status": "LOCKED",
      "locked": true,
      "createdAt": "2026-10-14T10:00:00Z"
    }
  ],
  "page": 0,
  "size": 20,
  "totalElements": 1
}
```

| Mã | Ý nghĩa | Mô tả |
|----|---------|-------|
| `200` | Thành công | Trả về danh sách |
| `400` | Bad Request | `VALIDATION_FAILED` khi `page`/`size` sai hoặc `status`/`role` không hợp lệ |
| `401` | Unauthorized | Chưa đăng nhập hoặc token không hợp lệ |
| `403` | Forbidden | Người dùng không có vai trò Admin |

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

- **Dọn dẹp tự động trong cùng transaction** (qua event `UserLockedEvent`, lỗi thì việc khoá cũng rollback):
  - Mọi giao dịch của người bị khoá (vai chủ bài hoặc người nhận) đang `PENDING`, `RESERVED`, `AWAITING_LOGISTICS`,
    `LOGISTICS_CONFIRMED`, `AWAITING_HANDOVER` chuyển `CANCELLED` với `cancelReason = "[Hệ thống] Tài khoản bị khoá"`.
    Bài chính và món mang đổi được nhả về `APPROVED`, bên còn lại nhận thông báo `TRANSACTION_CANCELLED`.
    Giao dịch `DISPUTED` không bị huỷ để Admin phân xử.
  - Các bài `PENDING_REVIEW` của người bị khoá đang giữ phí đăng hoặc phí sửa (`fee_state = HELD`, phí lớn hơn 0) bị
    từ chối như khi Admin từ chối bài (lý do "Tài khoản bị khoá", ghi `REVIEW_REJECT`), phí giữ được nhả về ví qua
    `CreditService.release`.
  - Activity log `USER_LOCK` có `data = { "cancelledRequests": n, "releasedCredit": n }`.
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

---

## 6. POST /api/v1/admin/users/{userId}/grace-star

Cấp 1 sao ân hạn cho người dùng đang có 0 sao (FR-95, BR-U09, UC-51, Issue #150). Mỗi tài khoản chỉ được cấp **một lần** (`grace_star_used` chỉ đổi `false → true`). Bắt buộc có lý do; hành động ghi nhật ký `GRACE_STAR`.

- **Xác thực:** Bắt buộc Bearer Token (JWT) có quyền Admin (`hasRole('ADMIN')`).
- **Path Variable:** `userId` (ID người dùng được cấp).
- **Request Body:**

```json
{
  "reason": "Đã giải trình và cam kết không tái phạm"
}
```

- **Thành công:** `200 OK`, trả về `GraceStarResponse`. Người dùng có lại 1 sao nên đăng bài và gửi yêu cầu được trở lại (BR-U05); bài đã bị ẩn lúc về 0 sao vẫn giữ nguyên trạng thái ẩn.

```json
{
  "userId": 42,
  "trustStars": 1,
  "graceStarUsed": true
}
```

- **Mã lỗi:**

| Mã | Ý nghĩa | Mô tả |
|----|---------|-------|
| `200` | Thành công | Đã cấp 1 sao và ghi nhật ký |
| `400` | Bad Request | `VALIDATION_FAILED`: Thiếu lý do hoặc lý do vượt quá 500 ký tự |
| `401` | Unauthorized | Chưa đăng nhập hoặc token không hợp lệ |
| `403` | Forbidden | Người dùng không có vai trò Admin |
| `404` | Not Found | `USER_NOT_FOUND`: Người dùng không tồn tại |
| `409` | Conflict | `REPUTATION_GRACE_STAR_ALREADY_USED`: Đã được cấp sao ân hạn trước đó (kể cả khi về 0 sao lần hai) |
| `409` | Conflict | `REPUTATION_GRACE_STAR_NOT_ELIGIBLE`: Người dùng vẫn còn sao (chỉ cấp khi đang 0 sao) |

---

## 7. GET /api/v1/admin/anomalies

Cảnh báo bất thường cho Admin (FR-113, UC-52, BR-U08, Issue #148). Kết quả tính trực tiếp mỗi lần gọi, không lưu bảng.

- **Xác thực:** Bắt buộc Bearer Token (JWT) có quyền Admin (`hasRole('ADMIN')`).
- **Cặp giao dịch lặp:** hai người có **hơn** `reputation_pair_alert_threshold` (mặc định `5`) giao dịch `COMPLETED` với nhau trong `reputation_pair_window_days` ngày gần nhất (mặc định `90`), tính cả hai chiều cho/nhận. Giao dịch đã huỷ hoặc hoàn tất ngoài cửa sổ không tính. Cách đếm cặp giống luật không cộng điểm khi giao dịch hoàn tất (BR-U08, [reputation.md mục 1.4](reputation.md#14-void-oncompletedlong-requestid)).
- **Sắp xếp:** nhiều giao dịch nhất lên trước, cùng số thì cặp có giao dịch gần nhất lên trước. `userA` là người có id nhỏ hơn.
- **Chưa làm:** cảnh báo nhiều tài khoản cùng IP, đang chờ BE1 đồng ý thêm method vào `UserService` (module user). Khi làm sẽ thêm một mảng mới vào response (ngưỡng `accounts_per_ip_24h`, không giới hạn thời gian); các trường hiện có giữ nguyên.
- **Thành công:** `200 OK`, trả về `AnomalyReportResponse`. Không có cảnh báo nào thì `repeatedPairs` là mảng rỗng.

```json
{
  "generatedAt": "2026-10-22T03:00:00Z",
  "pairWindowDays": 90,
  "pairThreshold": 5,
  "repeatedPairs": [
    {
      "userA": { "id": 3, "fullName": "Nguyễn Văn An", "email": "an@fpt.edu.vn" },
      "userB": { "id": 7, "fullName": "Trần Thị Bình", "email": "binh@fpt.edu.vn" },
      "completedCount": 6,
      "lastCompletedAt": "2026-10-20T08:00:00Z"
    }
  ]
}
```

| Trường | Kiểu | Mô tả |
|---|---|---|
| `generatedAt` | string (ISO-8601) | Thời điểm tính |
| `pairWindowDays` | number | Số ngày của cửa sổ tính cặp |
| `pairThreshold` | number | Cặp có **hơn** số giao dịch này trong cửa sổ thì bị cảnh báo |
| `repeatedPairs[].userA`, `userB` | object | `id`, `fullName`, `email` của hai người. Người dùng đã xoá mềm: `fullName = "Tài khoản đã xoá"`, `email = null` |
| `repeatedPairs[].completedCount` | number | Số giao dịch `COMPLETED` giữa hai người trong cửa sổ |
| `repeatedPairs[].lastCompletedAt` | string (ISO-8601) | Thời điểm hoàn tất gần nhất của cặp |

- **Mã lỗi:**

| Mã | Ý nghĩa | Mô tả |
|----|---------|-------|
| `200` | Thành công | Trả về danh sách cảnh báo (có thể rỗng) |
| `401` | Unauthorized | Chưa đăng nhập hoặc token không hợp lệ |
| `403` | Forbidden | Người dùng không có vai trò Admin |
