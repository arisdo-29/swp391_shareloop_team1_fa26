# API nạp Credit (module wallet, integration/payment)

Quy ước chung theo Lộ trình mục 3.4: tiền tố `/api/v1`, thời gian ISO-8601 UTC, thành công trả thẳng DTO (không bọc),
lỗi theo định dạng thống nhất (xem `docs/api/wallet.md`).

Người sở hữu: BE1. Chọn cổng qua `app.payment.provider=mock|vnpay`, mặc định `mock`.
`MockPaymentClient` giả lập thanh toán; `VnPayClient` tạo URL chuyển sang VNPAY.

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
| `provider`    | string | `MOCK` hoặc `VNPAY`                                  |
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
| `WALLET_PAYMENT_ORDER_NOT_PAYABLE` | 409  | Đơn `FAILED`, `EXPIRED` hoặc dùng mock-confirm cho đơn khác `MOCK` |

## Luồng VNPAY

1. FE gọi tạo đơn như trên; nhận `provider=VNPAY` và chuyển trình duyệt tới `redirectUrl`.
2. URL có số tiền nhân 100, thời gian `Asia/Ho_Chi_Minh`, hạn từ `PAYMENT_ORDER_EXPIRE_MINUTES`, chữ ký HMAC-SHA512 trên tham số đã sắp xếp và URL-encode.
3. VNPAY gọi IPN để backend ghi kết quả và cộng Credit. Chỉ IPN được cộng tiền.
4. Trình duyệt quay về return, backend kiểm chữ ký rồi chuyển hướng 302 tới `app.payment.vnpay.fe-result-url`, mặc định `http://localhost:5173/credit` (route `/credit` đã có trong FE).

### GET /api/v1/payments/vnpay/ipn

Không cần Bearer Token. Xác thực bằng `vnp_SecureHash`; loại `vnp_SecureHash` và `vnp_SecureHashType` khỏi dữ liệu ký.
Tìm đơn VNPAY bằng `vnp_TxnRef`, kiểm chính xác `vnp_Amount = amount_vnd * 100` (không làm tròn).
Chỉ khi cả `vnp_ResponseCode` và `vnp_TransactionStatus` bằng `00` mới cộng Credit; kết quả khác chuyển đơn PENDING sang FAILED.
Khóa `users` trước `payment_orders`; cập nhật đơn, số dư và ledger trong cùng transaction. IPN lặp lại trả `02`, không cộng thêm.

Callback có chữ ký hợp lệ và tìm được đơn VNPAY sẽ lưu `provider_txn_id`, `signature_verified`, toàn bộ tham số trong `callback_data`, kể cả sai số tiền hoặc đơn đã xác nhận.
Chữ ký sai không cập nhật đơn. Không lưu được callback khi không tìm thấy đơn hoặc transaction gặp lỗi.

Luôn trả HTTP 200 với JSON (đúng chữ hoa): `{"RspCode":"00","Message":"Confirm Success"}`.

| RspCode | Message | Ý nghĩa |
| --- | --- | --- |
| `00` | Confirm Success | Đã xử lý kết quả thành công hoặc thất bại của cổng |
| `01` | Order not found | Không tìm thấy đơn VNPAY |
| `02` | Order already confirmed | Đơn không còn PENDING |
| `04` | Invalid amount | Số tiền thiếu, sai định dạng hoặc không khớp |
| `97` | Invalid signature | Chữ ký sai hoặc thiếu |
| `99` | Unknown error | Lỗi xử lý khác; transaction được rollback |

### GET /api/v1/payments/vnpay/return

Không cần Bearer Token. Chỉ kiểm chữ ký và redirect 302; không ghi đơn, không cộng Credit.
Query gửi FE: `orderCode` và `status=SUCCESS|FAILED|INVALID_SIGNATURE`. Chữ ký sai trả `orderCode` rỗng.
`SUCCESS` ở đây chỉ phản ánh dữ liệu return của cổng; IPN có thể đến sau. FE cần tải lại ví và không được suy ra đã cộng tiền từ query này.
FE hiện có route `/credit`; phần hiển thị kết quả query và API đọc trạng thái đơn riêng chưa được triển khai trong task này.

### Biến môi trường (profile prod)

| Biến | Ý nghĩa / mặc định |
| --- | --- |
| `PAYMENT_PROVIDER` | `mock` (mặc định) hoặc `vnpay` |
| `VNPAY_TMN_CODE` | Mã merchant; bắt buộc khi chọn VNPAY |
| `VNPAY_HASH_SECRET` | Khóa ký từ merchant; bắt buộc khi chọn VNPAY; không đưa vào FE |
| `VNPAY_PAY_URL` | `https://sandbox.vnpayment.vn/paymentv2/vpcpay.html` |
| `VNPAY_RETURN_URL` | URL backend `/api/v1/payments/vnpay/return`; mặc định rỗng, phải cấu hình khi chọn VNPAY |
| `VNPAY_FE_RESULT_URL` | URL FE kết quả; mặc định `http://localhost:5173/credit`, khi deploy đặt `https://<frontend-domain>/credit` |
| `PAYMENT_MOCK_ENABLED` | Bật endpoint mock-confirm; không cho phép xác nhận đơn VNPAY |

Phải khai báo IPN URL `https://<render-domain>/api/v1/payments/vnpay/ipn` trong trang quản trị merchant sandbox của VNPAY.
Backend phải truy cập công khai bằng HTTPS để VNPAY gọi IPN.

Tham khảo [tài liệu thanh toán VNPAY 2.1.0](https://sandbox.vnpayment.vn/apis/docs/thanh-toan-pay/pay.html).

Job hết hạn đã chuyển đơn PENDING quá `payment_order_expire_minutes` sang EXPIRED.
Chưa có `GET /wallet/topup-orders/{orderCode}` để FE truy vấn trạng thái đơn.
