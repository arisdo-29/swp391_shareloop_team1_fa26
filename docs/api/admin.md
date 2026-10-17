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
| `type` | string | Không | - | Lọc theo loại hành động (vd: `APPROVE_ITEM`, `USER_LOCK`) |
| `from` | yyyy-MM-dd | Không | - | Từ ngày (tính từ 00:00:00 theo múi giờ Asia/Ho_Chi_Minh) |
| `to` | yyyy-MM-dd | Không | - | Đến ngày (tính hết 23:59:59 của ngày `to` theo múi giờ Asia/Ho_Chi_Minh) |
| `page` | number | Không | 0 | Chỉ số trang (bắt đầu từ 0) |
| `size` | number | Không | 20 | Số lượng bản ghi mỗi trang (tối đa 100) |

- **Thành công:** `200 OK`, trả về `Page<ActivityLogDto>`.

```json
{
  "content": [
    {
      "id": 101,
      "actorId": 1,
      "type": "APPROVE_ITEM",
      "targetType": "ITEM",
      "targetId": 42,
      "data": {
        "status": "APPROVED"
      },
      "reason": "Nội dung hợp lệ",
      "detail": "Nội dung hợp lệ",
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
