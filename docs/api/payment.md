# API nạp Credit (module wallet, integration/payment)

Quy ước chung: tiền tố `/api/v1`, thời gian ISO-8601 UTC, thành công trả thẳng DTO (không bọc),
lỗi theo định dạng thống nhất (xem `docs/api/wallet.md`).

Người sở hữu: BE1. Chọn cổng qua `app.payment.provider=mock|vnpay`. Mặc định `mock` ở dev/test, **`vnpay` ở profile prod** (deploy).
`MockPaymentClient` giả lập thanh toán; `VnPayClient` tạo URL chuyển sang VNPAY.

### GET /api/v1/admin/finance/topup-orders

Chỉ ADMIN (401 chưa đăng nhập, 403 user thường). Liệt kê đơn nạp của mọi provider (VNPAY, MOCK, và các đơn cũ nếu có), query:

| Tham số | Mặc định | Ý nghĩa |
| --- | --- | --- |
| provider | (bỏ trống) | VNPAY/MOCK/MANUAL; bỏ trống là mọi provider |
| status | (bỏ trống) | PENDING/SUCCESS/FAILED/EXPIRED; bỏ trống là mọi trạng thái |
| submittedOnly | false | true chỉ lấy đơn người dùng đã bấm xác nhận thanh toán; false lấy cả đơn chưa gửi |
| page | 0 | Số trang >= 0 |
| size | 20 | 1..100 |

Mặc định (không truyền gì) hiện tất cả đơn nạp ở mọi provider và mọi trạng thái. Trả 200 `PageResponse<TopUpOrderDetailResponse>`:
`items`, `page`, `size`, `totalElements`; sắp theo thời điểm tạo `createdAt` giảm dần (đơn mới nhất lên đầu) rồi `id` giảm dần.

API của Admin trả thêm tên để giao diện không hiện id: `userName` và `userEmail` (chủ đơn), `reviewedByName` (Admin đã duyệt hoặc từ chối đối với đơn lịch sử, `null` khi đơn chưa được xử lý).

## Luồng MOCK (F04, UC-10)

1. FE gọi `POST /wallet/topup-orders` với số tiền → nhận `orderCode` và `redirectUrl`.
2. FE chuyển người dùng tới `redirectUrl` = trang giả lập `/wallet/topup/mock-checkout?orderCode=...`.
3. Trang giả lập gọi `POST /wallet/topup-orders/{orderCode}/mock-confirm` → nhận ví mới.

Quy đổi: `credits = amountVnd / vnd_per_credit` (mặc định 1.000đ = 1 Credit). Số tiền phải là bội số của
`vnd_per_credit` và đạt tối thiểu `topup_min_credits` Credit (mặc định 10 Credit = 10.000đ). Cả hai đọc từ cấu hình
Admin, FE không nên viết cứng (VI-03).

## POST /api/v1/wallet/topup-orders

Tạo đơn nạp PENDING (VI-03).

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

Xác nhận thanh toán giả lập và cộng Credit (VI-04, VI-07) trong một transaction: đơn → `SUCCESS`, cộng
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
| `provider`    | string | `MOCK` hoặc `VNPAY`                                   |
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

IPN trả HTTP 200 với JSON (đúng chữ hoa); kết quả nghiệp vụ nằm trong `RspCode`, không phải HTTP status.
Ví dụ khi đã xử lý kết quả: `{"RspCode":"00","Message":"Confirm Success"}`.
`00` cũng có thể là đã ghi nhận thanh toán thất bại thành FAILED, không tự chứng minh đã cộng Credit.

| RspCode | Message | Ý nghĩa |
| --- | --- | --- |
| `00` | Confirm Success | Đã xử lý kết quả thành công hoặc thất bại của cổng |
| `01` | Order not found | Không tìm thấy đơn VNPAY |
| `02` | Order already confirmed | Đơn không còn PENDING |
| `02` | Order expired | Thanh toán thành công nhưng đơn đã quá hạn: đơn thành EXPIRED, không cộng Credit |
| `04` | Invalid amount | Số tiền thiếu, sai định dạng hoặc không khớp |
| `97` | Invalid signature | Chữ ký sai hoặc thiếu |
| `99` | Unknown error | Lỗi xử lý khác; transaction được rollback |

### GET /api/v1/payments/vnpay/return

Không cần Bearer Token. Chỉ kiểm chữ ký và redirect 302; không ghi đơn, không cộng Credit.
Query gửi FE: `orderCode` và `status=SUCCESS|FAILED|INVALID_SIGNATURE`. Chữ ký sai trả `orderCode` rỗng.
`SUCCESS` ở đây chỉ phản ánh dữ liệu return của cổng; IPN có thể đến sau. FE cần tải lại ví và không được suy ra đã cộng tiền từ query này.
FE đọc API trạng thái đơn bằng mã tham chiếu; không suy ra đã cộng tiền từ query return hoặc số dư tăng.

### Biến môi trường (dev và prod)

Mapping `VNPAY_*` nằm trong `application.yml` dùng chung; prod kế thừa cùng giá trị mặc định.
Chỉ chọn VNPAY mới cần cấu hình merchant; MOCK khởi động được khi thiếu thông tin VNPay.
Thiếu hoặc để trắng pay-url, return-url, tmn-code, hash-secret hay fe-result-url khi chọn VNPAY sẽ làm startup thất bại,
thông báo nêu đúng tên các biến thiếu (`VNPAY_RETURN_URL`, `VNPAY_TMN_CODE`...). Profile prod mặc định `PAYMENT_PROVIDER=vnpay`
và không có mặc định localhost cho bất kỳ URL nào.

IPN muộn: đơn còn PENDING nhưng đã quá `payment_order_expire_minutes` cộng dung sai `app.payment.vnpay.expiry-grace-minutes`
(mặc định 2 phút, bù lệch đồng hồ với VNPAY) thì IPN hợp lệ, thành công **không** cộng Credit: đơn chuyển EXPIRED, trả `RspCode=02`
(`Order expired`) và ghi log WARN gồm `orderCode`, số tiền, `vnp_TransactionNo` để đối soát tay. URL thanh toán luôn gửi
`vnp_ExpireDate` = thời điểm tạo + `payment_order_expire_minutes`.

| Biến | Ý nghĩa / mặc định |
| --- | --- |
| `PAYMENT_PROVIDER` | `vnpay` (mặc định ở prod), `mock` (mặc định ở dev/test) |
| `VNPAY_TMN_CODE` | Mã merchant; bắt buộc khi chọn VNPAY |
| `VNPAY_HASH_SECRET` | Khóa ký từ merchant; bắt buộc khi chọn VNPAY; không đưa vào FE |
| `VNPAY_PAY_URL` | `https://sandbox.vnpayment.vn/paymentv2/vpcpay.html` |
| `VNPAY_RETURN_URL` | URL backend `/api/v1/payments/vnpay/return`; mặc định rỗng, phải cấu hình khi chọn VNPAY |
| `VNPAY_FE_RESULT_URL` | URL FE kết quả, bắt buộc khi chọn VNPAY ở prod (không có mặc định); đặt `https://<frontend-domain>/credit`. Dev mặc định `http://localhost:5173/credit` |
| `PAYMENT_MOCK_ENABLED` | Bật endpoint mock-confirm (tự cộng Credit không cần tiền); chỉ bật ở dev, staging |

Phải khai báo IPN URL `https://<render-domain>/api/v1/payments/vnpay/ipn` trong trang quản trị merchant sandbox của VNPAY.
Backend phải truy cập công khai bằng HTTPS để VNPAY gọi IPN.

Local: khai báo các biến trong môi trường của IDE/terminal chạy backend (Spring không tự đọc file `.env`).
Riêng mock-confirm: profile dev mặc định bật; muốn tắt ở dev dùng `APP_PAYMENT_MOCK_ENABLED=false`.
`PAYMENT_MOCK_ENABLED` là mapping của profile prod; không thay đổi mặc định các profile trong task này.
Chạy profile `dev`; sau khi có merchant dùng `PAYMENT_PROVIDER=vnpay`,
`VNPAY_RETURN_URL=https://<public-backend>/api/v1/payments/vnpay/return` và
`VNPAY_FE_RESULT_URL=http://localhost:5173/credit`. Backend local cần tunnel HTTPS công khai để nhận IPN;
đăng ký `https://<public-backend>/api/v1/payments/vnpay/ipn` tại merchant. Không trỏ return URL thẳng tới FE.

Chưa có merchant: automated tests dùng `TESTONLY`/`test-secret` trong test context, không cần gọi cổng thật.
Không dùng các giá trị giả này để thanh toán. Giữ provider đang deploy.

Checklist Render/merchant, SQL đối chiếu và giới hạn kiểm chứng: [nghiệm thu VNPay #122](../testing/vnpay-acceptance-122.md).

Tham khảo [tài liệu thanh toán VNPAY 2.1.0](https://sandbox.vnpayment.vn/apis/docs/thanh-toan-pay/pay.html).

Job chuyển đơn PENDING quá `payment_order_expire_minutes` sang EXPIRED.
