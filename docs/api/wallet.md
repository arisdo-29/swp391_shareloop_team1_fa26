# API module wallet

Quy ước chung theo Lộ trình mục 3.4: tiền tố `/api/v1`, thời gian ISO-8601 UTC, thành công trả thẳng DTO (không bọc),
lỗi theo định dạng thống nhất bên dưới.

Người sở hữu: BE1. File này gồm endpoint đọc ví và lịch sử ví. Nạp Credit (QR minh họa/admin duyệt, MOCK, VNPAY) xem
`docs/api/payment.md`. Đổi số dư chỉ xảy ra bên trong `CreditService` (xem mục cuối).

## GET /api/v1/wallet

Ví Credit của người đang đăng nhập (F04). Id người dùng lấy từ token (claim `sub`), không nhận từ client.

- Xác thực: **bắt buộc** Bearer Token (JWT).
- Thành công: `200`, một `WalletResponse`.
- Lỗi: `401 UNAUTHORIZED` khi thiếu hoặc sai token; `404 WALLET_NOT_FOUND` khi tài khoản trong token không còn
  tồn tại (đã xoá mềm).

```json
{ "creditBalance": 20, "heldCredit": 5, "available": 15, "hasToppedUp": true }
```

## GET /api/v1/wallet/transactions

Lịch sử ví của người đang đăng nhập (FR-16), mới nhất trước, có phân trang. Chỉ gồm các dòng `credit_ledger` (giao
dịch làm đổi số dư thật: nạp, phí đăng/sửa/gia hạn/đẩy bài, phí AI, Admin điều chỉnh). Giữ chỗ và nhả giữ không làm
đổi số dư nên không có ở đây; số đang giữ xem ở `GET /wallet`.

- Xác thực: **bắt buộc** Bearer Token. Id người dùng lấy từ token.
- Tham số query (đều tuỳ chọn):

| Tham số | Kiểu                | Mặc định | Ý nghĩa                                                     |
|---------|---------------------|----------|-------------------------------------------------------------|
| `from`  | ISO-8601 UTC        | không lọc | Mốc bắt đầu, **gồm** (`createdAt >= from`)                 |
| `to`    | ISO-8601 UTC        | không lọc | Mốc kết thúc, **không gồm** (`createdAt < to`)             |
| `page`  | number ≥ 0          | `0`      | Số trang                                                    |
| `size`  | number 1..100       | `20`     | Số dòng mỗi trang                                           |

  Lọc theo tuần/tháng/quý: FE tự tính `from`, `to` (ví dụ tháng 10/2026 giờ Việt Nam:
  `from=2026-09-30T17:00:00Z&to=2026-10-31T17:00:00Z`).
- Thành công: `200`, một `PageResponse<WalletTransactionResponse>`; sắp theo `createdAt` giảm dần, rồi `id` giảm dần.
- Lỗi: `401 UNAUTHORIZED`; `400 VALIDATION_FAILED` khi `page`/`size` ngoài khoảng hoặc `from`/`to` sai định dạng;
  `422 WALLET_INVALID_DATE_RANGE` khi `from` không trước `to`.

```json
{
  "items": [
    { "id": 12, "type": "TOP_UP", "amount": 20, "balanceAfter": 40, "itemId": null,
      "paymentOrderId": 7, "note": null, "createdAt": "2026-10-14T03:15:00Z" },
    { "id": 9, "type": "POST_FEE", "amount": -5, "balanceAfter": 20, "itemId": 31,
      "paymentOrderId": null, "note": null, "createdAt": "2026-10-10T08:00:00Z" }
  ],
  "page": 0,
  "size": 20,
  "totalElements": 2
}
```

## WalletResponse

| Trường          | Kiểu    | Ý nghĩa                                                              |
|-----------------|---------|----------------------------------------------------------------------|
| `creditBalance` | number  | Tổng Credit đang có                                                  |
| `heldCredit`    | number  | Credit đang bị giữ chờ duyệt bài (chưa mất)                          |
| `available`     | number  | Credit dùng được = `creditBalance - heldCredit`                      |
| `hasToppedUp`   | boolean | Đã từng nạp thành công; điều kiện mở hạn mức AI (C18)                |

## WalletTransactionResponse

| Trường           | Kiểu           | Ý nghĩa                                                                  |
|------------------|----------------|--------------------------------------------------------------------------|
| `id`             | number         | Id dòng sổ cái                                                           |
| `type`           | string         | `TOP_UP`, `POST_FEE`, `EDIT_FEE`, `RENEW_FEE`, `BOOST_FEE`, `AI_SEARCH_FEE`, `ADMIN_ADJUST` |
| `amount`         | number         | Dương là cộng, âm là trừ                                                 |
| `balanceAfter`   | number         | Số dư `creditBalance` ngay sau giao dịch                                 |
| `itemId`         | number \| null | Bài liên quan (các loại phí bài đăng)                                    |
| `paymentOrderId` | number \| null | Đơn nạp liên quan (`TOP_UP`)                                             |
| `note`           | string \| null | Ghi chú (`ADMIN_ADJUST`)                                                 |
| `createdAt`      | string         | Thời điểm giao dịch, ISO-8601 UTC                                        |

## Mã lỗi

| code                               | HTTP | Khi nào                                                           | Lộ ra qua API       |
|------------------------------------|------|-------------------------------------------------------------------|---------------------|
| `WALLET_NOT_FOUND`                 | 404  | Không có tài khoản với id đã cho                                  | `GET /wallet`       |
| `WALLET_INSUFFICIENT_BALANCE`      | 402  | Credit khả dụng không đủ để giữ hoặc trừ                          | qua API của module gọi |
| `WALLET_INVALID_AMOUNT`            | 422  | Số Credit không dương                                             | qua API của module gọi |
| `WALLET_HELD_INSUFFICIENT`         | 409  | Số đang giữ nhỏ hơn số cần nhả hoặc trừ                           | qua API của module gọi |
| `WALLET_INVALID_DATE_RANGE`        | 422  | `from` không trước `to`                                           | `GET /wallet/transactions`; báo cáo tài chính của admin |
| `WALLET_TOPUP_AMOUNT_INVALID`      | 422  | Số tiền nạp dưới mức tối thiểu hoặc không phải bội số             | xem `payment.md`    |
| `WALLET_PAYMENT_ORDER_NOT_FOUND`   | 404  | Không có đơn nạp hoặc đơn của người khác                          | xem `payment.md`    |
| `WALLET_PAYMENT_ORDER_NOT_PAYABLE` | 409  | Đơn nạp `FAILED` hoặc `EXPIRED`                                   | xem `payment.md`    |

Định dạng lỗi (`field` và `details` chỉ có khi liên quan):

```json
{ "code": "WALLET_NOT_FOUND", "message": "Không tìm thấy ví của người dùng.", "traceId": "a1b2c3d4" }
```

## Hàm nội bộ `CreditService` (cho module khác, không phải endpoint)

Mọi hàm chạy trong transaction của người gọi và khoá dòng `users` của chủ ví. Số tiền do người gọi lấy từ
ConfigService, `CreditService` không tự quyết mức phí.

| Hàm                                          | Đổi gì                                         | Ghi `credit_ledger` | `type` hợp lệ                         |
|----------------------------------------------|------------------------------------------------|---------------------|---------------------------------------|
| `hold(userId, amount, type, itemId)`         | `held_credit` tăng                             | Không               | `POST_FEE`, `EDIT_FEE`                |
| `release(userId, amount, type, itemId)`      | `held_credit` giảm                             | Không               | `POST_FEE`, `EDIT_FEE`                |
| `chargeHeld(userId, amount, type, itemId)`   | `held_credit` và `credit_balance` cùng giảm    | Có, amount âm       | `POST_FEE`, `EDIT_FEE`                |
| `spend(userId, amount, type, itemId)`        | `credit_balance` giảm (chỉ từ phần khả dụng)   | Có, amount âm       | `RENEW_FEE`, `BOOST_FEE`, `AI_SEARCH_FEE` |
| `chargeAiSearch(userId)` → `long` ledgerId   | `credit_balance` giảm `ai_search_fee` (tự đọc ConfigService, chỉ từ phần khả dụng) | Có, amount âm | `AI_SEARCH_FEE` |
| `refundAiSearch(userId, ledgerId)`           | `credit_balance` tăng lại đúng số đã trừ ở dòng `ledgerId` | Có, amount dương, note `Hoàn phí AI #<ledgerId>` | `AI_SEARCH_FEE` |
| `lockWalletRow(userId)`                      | Không đổi, chỉ khoá dòng `users` tới hết transaction của người gọi | Không | -                       |
| `getWallet(userId)`                          | Không đổi, đọc không khoá                      | Không               | -                                     |
| `topUp(userId, paymentOrderId)`              | Đơn cổng thanh toán PENDING → SUCCESS, `credit_balance` tăng, `has_topped_up = true`; SUCCESS không đổi; từ chối MANUAL | Có, amount dương, kèm `payment_order_id` | `TOP_UP` |
| `topUpManual(userId, paymentOrderId)` | Chỉ đơn MANUAL đã mark-paid; cộng snapshot Credit khi admin duyệt, SUCCESS không cộng lại | Có, amount dương, kèm `payment_order_id` | `TOP_UP` |
| `getTransactions(userId, from, to, page, size)` | Không đổi, đọc không khoá                   | Không               | -                                     |
| `getFinanceSummary(from, to)`                | Không đổi, đọc không khoá; tổng hợp toàn hệ thống | Không          | -                                     |

`topUp` khoá dòng `users` rồi mới khoá dòng `payment_orders` (thứ tự cố định để tránh deadlock) và trả `WalletResponse`.
Người gọi: `PaymentService` (MOCK), VNPay IPN qua `topUpFromGateway`, `ManualTopUpService` qua `topUpManual`.
Luồng MANUAL khóa ví rồi đơn, duyệt và ghi ActivityLogService trong cùng transaction; mark-paid/từ chối
không đổi số dư. Snapshot Credit được giữ dù cấu hình tỷ lệ thay đổi. MANUAL không đánh dấu xác minh chữ ký.

Phí AI (#121, người gọi: search): gọi `chargeAiSearch` trước khi gọi AI và giữ `ledgerId` trả về; AI lỗi thì gọi
`refundAiSearch(userId, ledgerId)`. Dòng gốc không có, của người khác hoặc không phải dòng trừ phí AI → `WALLET_AI_FEE_NOT_FOUND`
(404); đã hoàn rồi → `WALLET_AI_FEE_ALREADY_REFUNDED` (409). Hoàn khoá dòng `users` trước khi kiểm nên không hoàn hai lần.

Truyền `type` ngoài danh sách của hàm là lỗi lập trình: ném `IllegalArgumentException`, không phải mã lỗi trên.

### Báo cáo tài chính cho Admin: `getFinanceSummary(from, to)` (FR-109, FR-110, BR-P09)

```java
FinanceSummaryResponse getFinanceSummary(Instant from, Instant to)
```

Báo cáo ba sổ (SRS mục 4.7) của **toàn hệ thống** trong khoảng `[from, to)`. Người gọi: admin (#115) gọi qua
`CreditService`, không inject repository của wallet. Hàm không kiểm quyền; controller của admin tự giới hạn cho Admin.

- `from`, `to` có thể `null` (không giới hạn đầu đó), giống `getTransactions`. `from` không trước `to` thì ném
  `BusinessException(WALLET_INVALID_DATE_RANGE)`.
- Read-only, không khoá dòng; mọi phép cộng chạy bằng `SUM ... GROUP BY` trong DB.

| Trường            | Cách tính                                                                                     |
|-------------------|-----------------------------------------------------------------------------------------------|
| `from`, `to`      | Đúng như người gọi truyền (có thể `null`)                                                     |
| `cashOrderCount`  | Số đơn `payment_orders` có `status = SUCCESS` và `paid_at` trong khoảng                       |
| `cashAmountVnd`   | Tổng `amount_vnd` của các đơn đó (tiền mặt thực thu)                                          |
| `creditInTotal`   | Tổng `amount` của các dòng `credit_ledger` có `amount > 0` và `created_at` trong khoảng       |
| `creditInByType`  | Như trên, tách theo `type` (thực tế: `TOP_UP`, `ADMIN_ADJUST` dương)                          |
| `creditOutTotal`  | Tổng của các dòng có `amount < 0`, **trả số dương** (doanh thu đã thực hiện)                   |
| `creditOutByType` | Như trên, tách theo `type` (`POST_FEE`, `EDIT_FEE`, `RENEW_FEE`, `BOOST_FEE`, `AI_SEARCH_FEE`; `ADMIN_ADJUST` âm nếu có nằm dòng riêng) |
| `reconciliation`  | Dòng đối chiếu BR-P09, xem dưới                                                               |

- Mỗi phần tử `...ByType` là `{ type, amount, entryCount }`; `amount` luôn không âm, `entryCount` là số dòng sổ cái.
  Chỉ có các `type` có dữ liệu trong khoảng, sắp theo tên `type`.
- Giữ chỗ (`hold`) và nhả giữ (`release`) **không** được tính: chúng chỉ đổi `held_credit`, không ghi sổ cái. Phí
  đăng/sửa bài vào "trừ ra" lúc Admin duyệt (`chargeHeld` ghi dòng `POST_FEE`/`EDIT_FEE`).
- Tiền mặt lọc theo `paid_at` (lúc tiền thật về), không theo `created_at` của đơn, để khớp ngày với dòng `TOP_UP`.

**Đối chiếu (`reconciliation`)**: `cashCredits` = tổng `payment_orders.credits` của đơn SUCCESS trong khoảng (tiền mặt
quy ra Credit theo tỉ lệ đã chốt lúc nạp); `topUpCredits` = tổng Credit `TOP_UP` trong khoảng;
`difference = cashCredits - topUpCredits`; `matched = (difference == 0)`. `difference > 0` là có đơn đã thu tiền mà
chưa cộng Credit; `< 0` là cộng Credit không có đơn tương ứng. `paid_at` và `created_at` của dòng `TOP_UP` ghi trong
cùng một transaction nên chỉ chênh rất nhỏ; chỉ có thể lệch giả nếu giao dịch rơi đúng mốc cắt `to`.

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
  "reconciliation": { "cashCredits": 100, "topUpCredits": 100, "difference": 0, "matched": true }
}
```

### Tra cứu lịch sử tài chính của một người (FR-112)

Admin dùng lại hai hàm có sẵn, truyền `userId` của người cần tra (hàm không ràng buộc người gọi phải là chủ ví):

- `getWallet(userId)`: số dư, số đang giữ, khả dụng, đã từng nạp hay chưa.
- `getTransactions(userId, from, to, page, size)`: các dòng sổ cái của người đó, mới nhất trước, có phân trang.

Danh sách yêu cầu MANUAL và thao tác admin duyệt/từ chối: xem `GET /admin/finance/topup-orders` cùng các API
trong `payment.md`. Bộ lọc userId cho danh sách đơn chưa được cung cấp.
