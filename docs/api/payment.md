# API nạp Credit (module wallet, integration/payment)

Quy ước chung theo Lộ trình mục 3.4: tiền tố `/api/v1`, thời gian ISO-8601 UTC, thành công trả thẳng DTO (không bọc),
lỗi theo định dạng thống nhất (xem `docs/api/wallet.md`).

Người sở hữu: BE1. Hiện chỉ có **cổng giả lập** (`MockPaymentClient`): không có tiền thật, người dùng tự bấm xác nhận.
VNPay sẽ thay vào sau qua `PaymentGatewayClient` mà không đổi bước 1.

## Luồng (F04, UC-10)

1. FE gọi `POST /wallet/topup-orders` với số tiền → nhận `orderCode` và `redirectUrl`.
2. FE chuyển người dùng tới `redirectUrl` = trang giả lập `/wallet/topup/mock-checkout?orderCode=...`.
3. Trang giả lập gọi `POST /wallet/topup-orders/{orderCode}/mock-confirm` → nhận ví mới.

> **Việc của FE:** xác nhận giả lập ngay trong trang Credit (đọc `orderCode` trên URL, hiện số tiền, nút "Xác nhận
> thanh toán" gọi bước 3). Trang này làm ở issue **#83**.

Quy đổi: `credits = amountVnd / vnd_per_credit` (mặc định 1.000đ = 1 Credit). Số tiền phải là bội số của
`vnd_per_credit` và đạt tối thiểu `topup_min_credits` Credit (mặc định 10 Credit = 10.000đ). Cả hai đọc từ cấu hình
Admin, FE không nên viết cứng (BR-P02).

## POST /api/v1/wallet/topup-orders

Tạo đơn nạp PENDING (FR-11, FR-12).

- Xác thực: **bắt buộc** Bearer Token. Chủ đơn lấy từ token.
- Thành công: `201`, một `TopUpOrderResponse`.
- Lỗi: `400 VALIDATION_FAILED` khi thiếu `amountVnd` hoặc không dương; `422 WALLET_TOPUP_AMOUNT_INVALID` khi dưới mức
  tối thiểu hoặc không phải bội số; `401 UNAUTHORIZED`.

```json
// request
{ "amountVnd": 20000 }

// response 201
{
  "orderCode": "3f9a1c2e4b5d6f708192a3b4c5d6e7f8",
  "amountVnd": 20000,
  "credits": 20,
  "status": "PENDING",
  "provider": "MOCK",
  "redirectUrl": "/wallet/topup/mock-checkout?orderCode=3f9a1c2e4b5d6f708192a3b4c5d6e7f8"
}
```

## POST /api/v1/wallet/topup-orders/{orderCode}/mock-confirm

Xác nhận thanh toán giả lập và cộng Credit (FR-13, FR-14) trong một transaction: đơn → `SUCCESS`, cộng
`credit_balance`, đặt `hasToppedUp = true`, ghi `credit_ledger` loại `TOP_UP`.

> **Chỉ bật khi `app.payment.mock.enabled=true`** — bật ở dev và staging (biến môi trường `PAYMENT_MOCK_ENABLED=true`),
> tắt ở production. Khi tắt, endpoint không tồn tại và trả `404 NOT_FOUND`.

- Xác thực: **bắt buộc** Bearer Token; chỉ xác nhận được đơn của chính mình.
- Thành công: `200`, một `TopUpConfirmResponse`.
- Gọi lại cho đơn đã `SUCCESS` (kể cả hai lời gọi cùng lúc): vẫn `200` cùng dạng, **không cộng thêm**.
- Lỗi: `404 WALLET_PAYMENT_ORDER_NOT_FOUND` khi không có đơn hoặc đơn của người khác; `409
  WALLET_PAYMENT_ORDER_NOT_PAYABLE` khi đơn `FAILED` hoặc `EXPIRED`; `401 UNAUTHORIZED`.

```json
// response 200
{
  "orderCode": "3f9a1c2e4b5d6f708192a3b4c5d6e7f8",
  "status": "SUCCESS",
  "credits": 20,
  "wallet": { "creditBalance": 40, "heldCredit": 5, "available": 35, "hasToppedUp": true }
}
```

## TopUpOrderResponse

| Trường        | Kiểu   | Ý nghĩa                                               |
|---------------|--------|-------------------------------------------------------|
| `orderCode`   | string | Mã đơn (32 ký tự), dùng cho bước xác nhận             |
| `amountVnd`   | number | Số tiền nạp (VNĐ)                                     |
| `credits`     | number | Số Credit sẽ được cộng                                |
| `status`      | string | `PENDING` / `SUCCESS` / `FAILED` / `EXPIRED`          |
| `provider`    | string | `MOCK` (sau này thêm `VNPAY`)                         |
| `redirectUrl` | string | Địa chỉ FE chuyển người dùng tới để thanh toán        |

## TopUpConfirmResponse

| Trường      | Kiểu           | Ý nghĩa                                                       |
|-------------|----------------|---------------------------------------------------------------|
| `orderCode` | string         | Mã đơn                                                        |
| `status`    | string         | Luôn `SUCCESS` khi thành công                                 |
| `credits`   | number         | Số Credit của đơn                                             |
| `wallet`    | WalletResponse | Ví sau khi cộng (xem `docs/api/wallet.md`)                    |

## Mã lỗi

| code                               | HTTP | Khi nào                                                   |
|------------------------------------|------|-----------------------------------------------------------|
| `WALLET_TOPUP_AMOUNT_INVALID`      | 422  | Dưới mức nạp tối thiểu hoặc không phải bội số của giá 1 Credit |
| `WALLET_PAYMENT_ORDER_NOT_FOUND`   | 404  | Không có đơn hoặc đơn của người khác                      |
| `WALLET_PAYMENT_ORDER_NOT_PAYABLE` | 409  | Đơn `FAILED` hoặc `EXPIRED`                               |

## Chưa làm

- `GET /wallet/topup-orders/{orderCode}` (trang trả về đọc trạng thái đơn) và VNPay IPN.
- Job chuyển đơn PENDING quá `payment_order_expire_minutes` sang `EXPIRED` (FR-18).
