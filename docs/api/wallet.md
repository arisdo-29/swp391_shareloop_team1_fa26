# API module wallet

Quy ước chung theo Lộ trình mục 3.4: tiền tố `/api/v1`, thời gian ISO-8601 UTC, thành công trả thẳng DTO (không bọc),
lỗi theo định dạng thống nhất bên dưới.

Người sở hữu: BE1. File này gồm endpoint đọc ví và lịch sử ví. Nạp Credit (đơn nạp, xác nhận giả lập) xem
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
| `WALLET_INVALID_DATE_RANGE`        | 422  | `from` không trước `to`                                           | `GET /wallet/transactions` |
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
| `lockWalletRow(userId)`                      | Không đổi, chỉ khoá dòng `users` tới hết transaction của người gọi | Không | -                       |
| `getWallet(userId)`                          | Không đổi, đọc không khoá                      | Không               | -                                     |
| `topUp(userId, paymentOrderId)`              | Đơn PENDING → SUCCESS, `credit_balance` tăng, `has_topped_up = true`; đơn đã SUCCESS thì không đổi gì, trả ví hiện tại | Có, amount dương, kèm `payment_order_id` | `TOP_UP` |
| `getTransactions(userId, from, to, page, size)` | Không đổi, đọc không khoá                   | Không               | -                                     |

`topUp` khoá dòng `users` rồi mới khoá dòng `payment_orders` (thứ tự cố định để tránh deadlock) và trả `WalletResponse`.
Người gọi hiện tại: `PaymentService` (xác nhận giả lập); sau này là VNPay IPN.

Truyền `type` ngoài danh sách của hàm là lỗi lập trình: ném `IllegalArgumentException`, không phải mã lỗi trên.
