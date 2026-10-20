# API nạp Credit (module wallet, integration/payment)

Quy ước chung theo Lộ trình mục 3.4: tiền tố `/api/v1`, thời gian ISO-8601 UTC, thành công trả thẳng DTO (không bọc),
lỗi theo định dạng thống nhất (xem `docs/api/wallet.md`).

Người sở hữu: BE1. Chọn cổng qua `app.payment.provider=mock|vnpay|manual`, mặc định `mock`.
`MockPaymentClient` giả lập thanh toán; `VnPayClient` tạo URL chuyển sang VNPAY.

## QR minh họa, admin duyệt thủ công (MANUAL)

Backend dùng `PAYMENT_PROVIDER=manual`, `PAYMENT_MOCK_ENABLED=false`, kể cả profile prod.
Frontend không cần `VITE_DEMO_PAYMENT=true` hoặc mode staging. MANUAL không gọi ngân hàng, VietQR hay cổng bên ngoài.

1. User vào `/credit`, nhập số tiền, tạo đơn bằng API hiện có. Backend lấy chủ đơn từ token,
   tính Credit bằng ConfigService và lưu snapshot `credits`, `vnd_per_credit`.
2. FE mở `/wallet/topup/manual-checkout?orderCode=...`, đọc lại dữ liệu đơn qua API.
   QR local chứa `SHARELOOP-DEMO-NO-PAYMENT`, hiện rõ **“QR minh họa — không chuyển tiền thật”**.
3. User bấm **Đã thanh toán**; chỉ ghi thời điểm gửi, đơn vẫn PENDING, không đổi ví/ledger.
4. Admin vào `/admin/finance` → **Yêu cầu nạp Credit** → **Duyệt và cộng Credit**,
   xác nhận userId và số Credit; hoặc từ chối kèm lý do.
5. User bấm **Kiểm tra trạng thái**. Khi API trả SUCCESS, FE quay về Credit và tải lại ví/lịch sử qua API.
   Refresh checkout vẫn đọc được đơn; sessionStorage chỉ lưu `orderCode`, không quyết định trạng thái hay số tiền.

Migration `V202610201400__wallet_manual_topup_review.sql` thêm nullable `submitted_at`, `reviewed_by`,
`reviewed_at`, `review_note` và index hàng chờ vào `payment_orders`. Không thêm bảng, không dùng `callback_data`.

| Trạng thái DB | Điều kiện | Nhãn giao diện |
| --- | --- | --- |
| PENDING | `submittedAt=null` | Chờ thanh toán |
| PENDING | Đã gửi | Chờ admin duyệt |
| SUCCESS | Đã duyệt | Đã cộng Credit |
| FAILED | Đã từ chối | Yêu cầu bị từ chối |
| EXPIRED | Quá hạn trước khi gửi | Đơn đã hết hạn |

MANUAL chưa gửi áp dụng `payment_order_expire_minutes`, kiểm ngay khi đọc/mark-paid và qua job.
mark-paid quá hạn trả 409 và lưu EXPIRED. MANUAL đã gửi không bị job làm hết hạn.
Quy tắc hết hạn MOCK/VNPAY được giữ nguyên.

### GET /api/v1/wallet/topup-orders/{orderCode}

Bearer Token bắt buộc; chỉ chủ đơn được đọc (mọi provider). Không có đơn/khác chủ đơn đều trả
`404 WALLET_PAYMENT_ORDER_NOT_FOUND`. Thành công 200 trả `TopUpOrderDetailResponse`:

```json
{
  "orderCode": "3f9a1c2e4b5d6f708192a3b4c5d6e7f8",
  "userId": 42,
  "amountVnd": 20000,
  "credits": 20,
  "vndPerCredit": 1000,
  "status": "PENDING",
  "provider": "MANUAL",
  "createdAt": "2026-10-20T07:00:00Z",
  "expiresAt": "2026-10-20T07:15:00Z",
  "submittedAt": null,
  "reviewedBy": null,
  "reviewedAt": null,
  "reviewNote": null
}
```

Thời gian ISO-8601 UTC. `expiresAt` tính từ thời điểm tạo và cấu hình hạn hiện tại;
đối với MANUAL chỉ áp dụng khi chưa gửi, FE không tự hết hạn đơn đã gửi.

### POST /api/v1/wallet/topup-orders/{orderCode}/mark-paid

Bearer Token của chủ đơn, không body. Chỉ MANUAL PENDING còn hạn hoặc đã gửi trước đó.
Trả 200 `TopUpOrderDetailResponse` vẫn PENDING; gọi lặp giữ nguyên `submittedAt`, không đổi ví/ledger.
Khác chủ trả 404 như API đọc; sai provider/trạng thái trả `409 WALLET_PAYMENT_ORDER_NOT_PAYABLE`.

### GET /api/v1/admin/finance/topup-orders

Chỉ ADMIN (401 chưa đăng nhập, 403 user thường). Chỉ liệt kê MANUAL, query:

| Tham số | Mặc định | Ý nghĩa |
| --- | --- | --- |
| status | PENDING | PENDING/SUCCESS/FAILED/EXPIRED |
| submittedOnly | true | Chỉ đơn đã gửi; false cho phép xem cả chưa gửi |
| page | 0 | Số trang >= 0 |
| size | 20 | 1..100 |

Mặc định hiện đơn đã gửi đang chờ duyệt. Trả 200 `PageResponse<TopUpOrderDetailResponse>`:
`items`, `page`, `size`, `totalElements`; sắp `submittedAt` giảm dần rồi `id` giảm dần.

### POST /api/v1/admin/finance/topup-orders/{orderCode}/approve

Chỉ ADMIN, body `{ "note": "Đã kiểm tra demo" }` hoặc `{}`; note tùy chọn, tối đa 500 ký tự.
Chỉ nhận MANUAL đã mark-paid, còn PENDING. Trả 200 `TopUpOrderDetailResponse` SUCCESS kèm dữ liệu duyệt.
Số Credit lấy đúng snapshot đơn; không nhận userId/credits/amount để tùy chỉnh số cộng.
Đơn SUCCESS trả dữ liệu hiện tại, không cộng hoặc ghi ledger/audit lần hai, kể cả gọi đồng thời.
Chưa gửi/sai provider/FAILED/EXPIRED trả `409 WALLET_PAYMENT_ORDER_NOT_PAYABLE`; không có đơn trả 404.

### POST /api/v1/admin/finance/topup-orders/{orderCode}/reject

Chỉ ADMIN, body `{ "reason": "Yêu cầu không hợp lệ" }`. Lý do bắt buộc 1..500 ký tự, không toàn khoảng trắng.
Chỉ nhận MANUAL đã gửi còn PENDING. Trả 200 `TopUpOrderDetailResponse` FAILED kèm dữ liệu duyệt, không cộng Credit.
Sai provider/trạng thái trả 409; không có đơn trả 404; DTO không hợp lệ trả 400 VALIDATION_FAILED.

Duyệt/từ chối khóa `users` → `payment_orders`, đọc lại trạng thái sau khóa. Mọi thay đổi tiền qua CreditService,
ghi TOP_UP, payment_order_id, balance_after, hasToppedUp; unique ledger theo đơn giữ nguyên.
Đơn, ví, ledger, dữ liệu duyệt, audit cùng transaction và rollback cùng nhau khi lỗi.
ActivityLogService ghi `TOP_UP_APPROVED`/`TOP_UP_REJECTED`, actor là admin, target `PAYMENT_ORDER`,
data chứa orderCode/userId/credits, reason chứa ghi chú/lý do. MANUAL luôn giữ `signature_verified=false`.
Khi provider hiện tại là manual, backend chặn mock-confirm cả đơn MOCK cũ, ngay cả nếu mock.enabled=true.

Báo cáo hiện có vẫn tổng hợp đơn SUCCESS, gồm MANUAL; “tiền thu” trong môi trường demo là số liệu minh họa,
không chứng minh đã thu tiền thật.

## Luồng MOCK (F04, UC-10)

1. FE gọi `POST /wallet/topup-orders` với số tiền → nhận `orderCode` và `redirectUrl`.
2. FE chuyển người dùng tới `redirectUrl` = trang giả lập `/wallet/topup/mock-checkout?orderCode=...`.
3. Trang giả lập gọi `POST /wallet/topup-orders/{orderCode}/mock-confirm` → nhận ví mới.

MOCK là cơ chế kiểm thử riêng; không dùng mock-confirm để xác nhận MANUAL.

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
| `provider`    | string | `MOCK`, `VNPAY` hoặc `MANUAL`                        |
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
FE đọc API trạng thái đơn bằng mã tham chiếu; không suy ra đã cộng tiền từ query return hoặc số dư tăng.

### Biến môi trường (profile prod)

| Biến | Ý nghĩa / mặc định |
| --- | --- |
| `PAYMENT_PROVIDER` | `mock` (mặc định), `vnpay` hoặc `manual` |
| `VNPAY_TMN_CODE` | Mã merchant; bắt buộc khi chọn VNPAY |
| `VNPAY_HASH_SECRET` | Khóa ký từ merchant; bắt buộc khi chọn VNPAY; không đưa vào FE |
| `VNPAY_PAY_URL` | `https://sandbox.vnpayment.vn/paymentv2/vpcpay.html` |
| `VNPAY_RETURN_URL` | URL backend `/api/v1/payments/vnpay/return`; mặc định rỗng, phải cấu hình khi chọn VNPAY |
| `VNPAY_FE_RESULT_URL` | URL FE kết quả; mặc định `http://localhost:5173/credit`, khi deploy đặt `https://<frontend-domain>/credit` |
| `PAYMENT_MOCK_ENABLED` | Bật endpoint mock-confirm; đặt false cho MANUAL; backend vẫn chặn tự cộng khi chọn manual nếu biến này vô tình bật |

Phải khai báo IPN URL `https://<render-domain>/api/v1/payments/vnpay/ipn` trong trang quản trị merchant sandbox của VNPAY.
Backend phải truy cập công khai bằng HTTPS để VNPAY gọi IPN.

Tham khảo [tài liệu thanh toán VNPAY 2.1.0](https://sandbox.vnpayment.vn/apis/docs/thanh-toan-pay/pay.html).

Job chuyển đơn PENDING quá `payment_order_expire_minutes` sang EXPIRED, trừ MANUAL đã mark-paid.
