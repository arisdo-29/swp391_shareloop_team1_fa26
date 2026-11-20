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
- **Mỗi người một đơn đang chờ thanh toán** (PENDING còn trong hạn). Còn đơn đang chờ thì:
  - không gửi `replacePending` (hoặc `false`): `409 WALLET_TOPUP_PENDING_EXISTS`, `details` có `orderCode`, `amountVnd`,
    `credits`, `expiresAt` của đơn đang chờ; không đổi dữ liệu.
  - `"replacePending": true` ("Hủy đơn cũ & tạo đơn mới"): trong **một transaction**, hủy đơn cũ bằng
    `UPDATE ... WHERE status = 'PENDING'` (thành `CANCELLED`, lý do "Bạn đã hủy đơn này để tạo đơn nạp mới.") rồi tạo đơn mới.
  Khoá dòng `users` trước (cùng thứ tự với IPN) nên hai lần tạo đơn cùng lúc chạy lần lượt. Đơn PENDING đã quá hạn cộng
  dung sai được chuyển `EXPIRED` trước khi kiểm; đơn đã qua `expiresAt` (còn trong dung sai) không chặn tạo đơn mới.
- URL thanh toán (`redirectUrl`) được lưu vào cột `payment_url`; "Tiếp tục thanh toán" dùng lại đúng URL đó (cùng
  `vnp_TxnRef`, `vnp_ExpireDate`), không tạo mã giao dịch mới.
- Thành công: `201`, một `TopUpOrderResponse`.
- Lỗi: `400 VALIDATION_FAILED` khi thiếu `amountVnd` hoặc không dương; `422 WALLET_TOPUP_AMOUNT_INVALID` khi dưới mức
  tối thiểu hoặc không phải bội số; `409 WALLET_TOPUP_PENDING_EXISTS`; `401 UNAUTHORIZED`.

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
  "redirectUrl": "/wallet/topup/mock-checkout?orderCode=3f9a1c2e4b5d6f708192a3b4c5d6e7f8",
  "expiresAt": "2026-11-17T08:15:00Z",
  "serverTime": "2026-11-17T08:00:00Z"
}
```

## GET /api/v1/wallet/topup-orders

Danh sách đơn nạp của **chính mình** (chủ đơn lấy từ token, không bao giờ trả đơn của người khác), mới nhất trước.

- Query: `status` (tuỳ chọn), `page` (mặc định 0), `size` (mặc định 10, tối đa 50).
  - `status=PENDING`: chỉ đơn còn trong hạn thanh toán, dùng cho mục "Đơn đang chờ thanh toán" (tối đa một đơn).
  - Không có `status`: toàn bộ lịch sử nạp ("Lịch sử nạp").
- Trước khi đọc, đơn PENDING đã qua `expiresAt + expiry-grace-minutes` của người gọi được chuyển `EXPIRED`.
- Thành công: `200`, `{ items: TopUpOrderDetailResponse[], page, size, totalElements }`. Mỗi đơn có `expiresAt`,
  `serverTime`, `failureReason` và `paymentUrl` (chỉ khi đơn còn PENDING và chưa qua `expiresAt`).

## GET /api/v1/wallet/topup-orders/{orderCode}

Xem một đơn nạp của chính mình (B29). Trang Ví gọi khi người dùng quay về từ cổng thanh toán (`?orderCode=`) để biết
đơn đã được cộng chưa và còn hạn thanh toán tới lúc nào. Endpoint này từng bị gỡ cùng luồng nạp thủ công (#435) trong
khi FE vẫn gọi; nay khôi phục, chỉ đọc.

- Xác thực: **bắt buộc** Bearer Token; chỉ xem được đơn của chính mình.
- Thành công: `200`, một `TopUpOrderDetailResponse` (cùng dạng với API Admin, `userName`, `userEmail`,
  `reviewedByName` luôn `null`).
- Đơn PENDING đã qua `expiresAt + expiry-grace-minutes` (mặc định 2 phút) thì chuyển `EXPIRED` ngay khi xem. Trong khoảng
  dung sai vẫn trả `PENDING` (chờ IPN tới muộn); FE hiện "Đơn đã hết hạn" theo `expiresAt` và tiếp tục hỏi tới khi có kết quả cuối.
- Lỗi: `404 WALLET_PAYMENT_ORDER_NOT_FOUND` khi không có đơn hoặc đơn của người khác; `401 UNAUTHORIZED`.

```json
// response 200
{
  "orderCode": "3f9a1c2e4b5d6f708192a3b4c5d6e7f8",
  "userId": 10,
  "amountVnd": 20000,
  "credits": 20,
  "vndPerCredit": 1000,
  "status": "PENDING",
  "provider": "VNPAY",
  "createdAt": "2026-11-17T08:00:00Z",
  "expiresAt": "2026-11-17T08:15:00Z",
  "submittedAt": null,
  "reviewedBy": null,
  "reviewedAt": null,
  "reviewNote": null,
  "userName": null,
  "userEmail": null,
  "reviewedByName": null,
  "failureReason": null
}
```

## POST /api/v1/wallet/topup-orders/{orderCode}/cancel

Người dùng tự hủy đơn nạp đang chờ thanh toán ("Xác nhận hủy" ở trang Ví).

- Xác thực: **bắt buộc** Bearer Token; chỉ hủy được đơn của chính mình.
- Đơn PENDING chuyển `CANCELLED` ngay bằng một câu `UPDATE ... WHERE status = 'PENDING'`, `review_note` = "Bạn đã hủy đơn nạp.".
  Không đụng ví (đơn chưa thanh toán không giữ hay cộng Credit).
- Idempotent: gọi lại khi đơn đã FAILED, hoặc đơn đã SUCCESS / EXPIRED, không đổi gì và vẫn trả `200`.
- Thành công: `200`, một `TopUpOrderDetailResponse` (trạng thái hiện tại của đơn, kèm `expiresAt`, `failureReason`).
- Lỗi: `404 WALLET_PAYMENT_ORDER_NOT_FOUND` khi không có đơn hoặc đơn của người khác; `401 UNAUTHORIZED`.

`failureReason` (mọi API trả `TopUpOrderDetailResponse`): chỉ có khi đơn `FAILED`. Lấy `review_note` nếu có (tự hủy, cần hoàn
tiền); không thì theo `vnp_ResponseCode` đã lưu: `24` → "Bạn đã hủy giao dịch tại VNPay.", mã khác → "VNPay báo thanh toán
không thành công (mã X).", không có mã → "Thanh toán không thành công.".

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
| `status`      | string | `PENDING` / `SUCCESS` / `FAILED` / `EXPIRED` / `CANCELLED` |
| `provider`    | string | `MOCK` hoặc `VNPAY`                                   |
| `redirectUrl` | string | Địa chỉ FE chuyển người dùng tới để thanh toán        |
| `expiresAt`   | string | Hạn thanh toán (ISO-8601 UTC) = `created_at + payment_order_expire_minutes`, làm tròn xuống giây. Đúng bằng `vnp_ExpireDate` gửi VNPay. FE đếm ngược theo mốc này, không tự tính |
| `serverTime`  | string | Giờ server lúc trả response (ISO-8601 UTC). FE lấy `serverTime - giờ máy` để bù lệch đồng hồ: còn lại = `expiresAt - (giờ máy + độ lệch)` |

`TopUpOrderDetailResponse` (GET đơn, hủy đơn, danh sách Admin) cũng có `expiresAt` và `serverTime` cùng nghĩa.

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
| `WALLET_PAYMENT_ORDER_NOT_PAYABLE` | 409  | Đơn `FAILED`, `EXPIRED`, `CANCELLED` hoặc dùng mock-confirm cho đơn khác `MOCK` |
| `WALLET_TOPUP_PENDING_EXISTS`      | 409  | Tạo đơn khi còn đơn đang chờ thanh toán mà không gửi `replacePending: true`; `details` là đơn đang chờ |

### Trạng thái đơn

| `status` | Khi nào |
|----------|---------|
| `PENDING` | Vừa tạo, chờ thanh toán |
| `SUCCESS` | IPN thành công (hoặc mock-confirm), đã cộng Credit |
| `CANCELLED` | Người dùng tự hủy, hủy để tạo đơn mới, hoặc khách hủy tại VNPay (`vnp_ResponseCode = 24`) |
| `FAILED` | VNPay báo thanh toán lỗi (mã khác `00` và `24`) |
| `EXPIRED` | Quá hạn thanh toán (job, xem đơn, liệt kê đơn, hoặc IPN tới sau hạn) |

`CANCELLED` thêm ở migration `V202611211100`; đơn `FAILED` cũ do tự hủy hoặc mã 24 được chuyển sang `CANCELLED`.

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

Không cần Bearer Token. Kiểm chữ ký rồi redirect 302; không bao giờ cộng Credit (chỉ IPN cộng tiền).
Chữ ký hợp lệ mà `vnp_ResponseCode` khác `00` (vd `24` = khách hủy): đơn VNPAY còn PENDING, đúng số tiền chuyển ngay sang **CANCELLED** (mã `24`) hoặc **FAILED** (mã khác)
và lưu tham số vào `callback_data` (giữ mã lỗi), không chờ IPN hay job hết hạn. Lỗi ở bước này chỉ ghi log, không chặn redirect.
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
`vnp_ExpireDate` = `expiresAt` của đơn (cùng mốc trả FE), `vnp_CreateDate` = `created_at` của đơn; cả hai định dạng
`yyyyMMddHHmmss` theo giờ `Asia/Ho_Chi_Minh` và nằm trong tập tham số được sort, ký `vnp_SecureHash`. Thời lượng chỉ đọc từ
ConfigKey `payment_order_expire_minutes` (mặc định 30, Admin chỉnh ở trang Cấu hình), tính ở một chỗ duy nhất
(`wallet.service.PaymentDeadline`).

Hết hạn phía server: `PaymentExpiryJob` (5 phút một lần) chuyển PENDING quá `expiresAt` sang EXPIRED; ngoài ra
`GET /api/v1/wallet/topup-orders/{orderCode}` tự chuyển EXPIRED khi đơn PENDING đã qua `expiresAt + expiry-grace-minutes`
(câu UPDATE có điều kiện status = PENDING). Trong khoảng dung sai đơn vẫn PENDING để IPN hợp lệ tới muộn còn được cộng.

IPN thành công tới khi đơn đã đóng mà chưa cộng (FAILED do hủy, hoặc EXPIRED): theo cùng cách với IPN muộn ở trên,
**không** cộng Credit, giữ trạng thái, trả `RspCode=02`; ghi log WARN và ghi `review_note` "VNPay báo đã thu tiền sau khi đơn
đã đóng; đơn chờ đối soát để hoàn tiền (mã giao dịch VNPay: …)" để Admin hoàn tiền tay. `callback_data` cũ không bị ghi đè.

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
