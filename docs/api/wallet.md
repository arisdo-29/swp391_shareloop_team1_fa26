# API module wallet

Quy ước chung theo Lộ trình mục 3.4: tiền tố `/api/v1`, thời gian ISO-8601 UTC, thành công trả thẳng DTO (không bọc),
lỗi theo định dạng thống nhất bên dưới.

Người sở hữu: BE1. Chỉ có endpoint đọc ví. Nạp Credit (VNPay) chưa có; đổi số dư chỉ xảy ra bên trong
`CreditService` do module khác gọi (xem mục cuối).

## GET /api/v1/wallet

Ví Credit của người đang đăng nhập (F04). Id người dùng lấy từ token (claim `sub`), không nhận từ client.

- Xác thực: **bắt buộc** Bearer Token (JWT).
- Thành công: `200`, một `WalletResponse`.
- Lỗi: `401 UNAUTHORIZED` khi thiếu hoặc sai token; `404 WALLET_NOT_FOUND` khi tài khoản trong token không còn
  tồn tại (đã xoá mềm).

```json
{ "creditBalance": 20, "heldCredit": 5, "available": 15, "hasToppedUp": true }
```

## WalletResponse

| Trường          | Kiểu    | Ý nghĩa                                                              |
|-----------------|---------|----------------------------------------------------------------------|
| `creditBalance` | number  | Tổng Credit đang có                                                  |
| `heldCredit`    | number  | Credit đang bị giữ chờ duyệt bài (chưa mất)                          |
| `available`     | number  | Credit dùng được = `creditBalance - heldCredit`                      |
| `hasToppedUp`   | boolean | Đã từng nạp thành công; điều kiện mở hạn mức AI (C18)                |

## Mã lỗi

| code                               | HTTP | Khi nào                                                           | Lộ ra qua API       |
|------------------------------------|------|-------------------------------------------------------------------|---------------------|
| `WALLET_NOT_FOUND`                 | 404  | Không có tài khoản với id đã cho                                  | `GET /wallet`       |
| `WALLET_INSUFFICIENT_BALANCE`      | 402  | Credit khả dụng không đủ để giữ hoặc trừ                          | qua API của module gọi |
| `WALLET_INVALID_AMOUNT`            | 422  | Số Credit không dương                                             | qua API của module gọi |
| `WALLET_HELD_INSUFFICIENT`         | 409  | Số đang giữ nhỏ hơn số cần nhả hoặc trừ                           | qua API của module gọi |
| `WALLET_PAYMENT_ORDER_NOT_FOUND`   | 404  | Không có đơn nạp (dành cho `topUp`, chưa hiện thực)               | chưa dùng           |
| `WALLET_PAYMENT_ORDER_NOT_PAYABLE` | 409  | Đơn nạp không hợp lệ hoặc đã cộng (dành cho `topUp`)              | chưa dùng           |

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
| `getWallet(userId)`                          | Không đổi, đọc không khoá                      | Không               | -                                     |
| `topUp(userId, paymentOrderId)`              | Chưa hiện thực                                 | -                   | -                                     |

Truyền `type` ngoài danh sách của hàm là lỗi lập trình: ném `IllegalArgumentException`, không phải mã lỗi trên.
