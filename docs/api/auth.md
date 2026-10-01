# API module auth

Quy ước chung theo Lộ trình mục 3.4: tiền tố `/api/v1`, thời gian ISO-8601 UTC, thành công trả thẳng DTO (không bọc),
lỗi theo định dạng thống nhất ở cuối file. Nguồn: SRS v10 (FR-01..FR-05, BR-U06, UC-01, UC-02), Luồng F01, F02.

Người sở hữu: BE1. File này gồm 5 endpoint: đăng ký, xác thực OTP, gửi lại OTP, đăng nhập, đăng xuất.
Quên mật khẩu, đổi mật khẩu, hồ sơ chưa thuộc file này (contract riêng).

## Quy tắc chung

- Cả 5 endpoint **không cần** header `Authorization` (`/api/v1/auth/**` là công khai). Các API khác gửi
  `Authorization: Bearer <accessToken>`.
- Đăng nhập bằng **email**; bảng `users` không có username.
- Email so khớp không phân biệt hoa thường, lưu dạng chữ thường; server tự `trim`.
- Số điện thoại: 10 số di động Việt Nam, đầu `03/05/07/08/09`, nhận cả dạng `+84`; server chuẩn hoá về `0xxxxxxxxx`.
  Không xác thực quyền sở hữu số (không gửi SMS).
- Các con số nghiệp vụ đọc từ `ConfigKey`, giá trị mặc định ghi trong ngoặc:

| ConfigKey                     | Mặc định | Ý nghĩa                                      |
|-------------------------------|----------|----------------------------------------------|
| `otp_ttl_minutes`             | 5        | OTP hết hạn sau số phút này                  |
| `otp_max_failed`              | 5        | Số lần nhập sai tối đa trước khi mã bị xoá   |
| `otp_resend_cooldown_seconds` | 60       | Khoảng cách tối thiểu giữa hai lần gửi OTP   |

- Hạn JWT lấy từ `app.jwt.ttl-minutes` (120 phút), nên `expiresIn` = `7200` (giây). JWT chứa `userId` (sub) và `roles`.
- OTP: 6 chữ số, lưu bản băm, gửi qua email sau khi transaction commit.
- Tài khoản chưa xác thực quá 24 giờ bị job tự xoá; client coi như phải đăng ký lại.

## POST /api/v1/auth/register

Đăng ký tài khoản và gửi OTP về email.

- Xác thực: **không cần**.
- Request: `RegisterRequest`.
- Thành công: `201`, `OtpSentResponse`. Tài khoản được tạo ở trạng thái `PENDING_VERIFICATION`, chưa đăng nhập được.
- Lỗi: `400 VALIDATION_FAILED` (thiếu trường, email/mật khẩu/họ tên sai định dạng, `field` = tên trường),
  `400 AUTH_PHONE_INVALID`, `400 AUTH_EMAIL_DOMAIN_NOT_ALLOWED`, `409 AUTH_EMAIL_ALREADY_REGISTERED`,
  `409 AUTH_PHONE_ALREADY_USED`.

Quy tắc:

- **Đăng ký lại khi email đang `PENDING_VERIFICATION`**: không báo trùng email. Server cập nhật mật khẩu, họ tên,
  số điện thoại theo request mới, sinh OTP mới (đặt lại `otp_failed_count = 0`, hạn mới) rồi gửi email; vẫn trả `201`.
  Lần đăng ký lại cũng phải chờ `otp_resend_cooldown_seconds` kể từ lần gửi trước, nếu chưa đủ thì trả
  `429 AUTH_OTP_RESEND_TOO_SOON`.
- Email đã thuộc tài khoản `ACTIVE` hoặc `BANNED` (chưa xoá mềm) → `409 AUTH_EMAIL_ALREADY_REGISTERED`.
- Số điện thoại chỉ tính trùng với tài khoản `ACTIVE`; tài khoản `PENDING_VERIFICATION` không giữ số.
- FR-10 (tối đa 3 tài khoản/IP/24 giờ, vượt thì đánh dấu chờ Admin xem xét) làm ở sprint sau; tuần 4 chỉ lưu `registration_ip`.

```json
{ "email": "an@example.com", "password": "MatKhau@123", "fullName": "Nguyễn Văn An", "phone": "0901234567" }
```

```json
{ "email": "an@example.com", "otpExpiresInSeconds": 300, "resendCooldownSeconds": 60 }
```

## POST /api/v1/auth/verify-otp

Nhập OTP đã gửi về email. Đúng thì kích hoạt tài khoản và trả luôn JWT như đăng nhập, FE không cần gọi `/login`.

- Xác thực: **không cần**.
- Request: `VerifyOtpRequest`.
- Thành công: `200`, `AuthResponse`. Server đặt `status = ACTIVE`, xoá các cột `otp_*`.
- Lỗi: `400 VALIDATION_FAILED`, `400 AUTH_OTP_INVALID`, `400 AUTH_OTP_EXPIRED`,
  `422 AUTH_OTP_TOO_MANY_ATTEMPTS`, `409 AUTH_PHONE_ALREADY_USED`.

Quy tắc:

- Mã sai: tăng `otp_failed_count`, trả `AUTH_OTP_INVALID` kèm `details.remainingAttempts`.
- Lần sai thứ `otp_max_failed`: xoá mã, trả `AUTH_OTP_TOO_MANY_ATTEMPTS`. Từ lúc đó phải gọi `/resend-otp` để lấy mã mới.
- Quá `otp_ttl_minutes` hoặc không còn mã → `AUTH_OTP_EXPIRED` (FE gợi ý bấm "Gửi lại mã").
- Email không tồn tại hoặc không ở trạng thái chờ xác thực → `AUTH_OTP_INVALID` **không kèm** `details.remainingAttempts`
  (không lộ email nào đã đăng ký).
- Nếu trong lúc chờ có tài khoản `ACTIVE` khác đã giữ số điện thoại này → `AUTH_PHONE_ALREADY_USED`;
  tài khoản vẫn ở `PENDING_VERIFICATION`, người dùng đăng ký lại với số khác.

```json
{ "email": "an@example.com", "otp": "123456" }
```

```json
{
  "accessToken": "eyJhbGciOiJIUzI1NiJ9...",
  "tokenType": "Bearer",
  "expiresIn": 7200,
  "user": { "id": 42, "email": "an@example.com", "fullName": "Nguyễn Văn An", "isAdmin": false }
}
```

## POST /api/v1/auth/resend-otp

Gửi lại OTP cho tài khoản đang chờ xác thực.

- Xác thực: **không cần**.
- Request: `ResendOtpRequest`.
- Thành công: `200`, `OtpSentResponse` (mã cũ mất hiệu lực, hạn mới tính từ lúc gửi, `otp_failed_count` về 0).
- Lỗi: `400 VALIDATION_FAILED`, `429 AUTH_OTP_RESEND_TOO_SOON` (kèm `details.retryAfterSeconds`).

Quy tắc:

- Chỉ gửi cho tài khoản `PENDING_VERIFICATION`. Email không tồn tại hoặc tài khoản đã `ACTIVE` vẫn trả `200`
  với cùng nội dung nhưng **không gửi email**, để không dò được danh sách email.
- Gửi sớm hơn `otp_resend_cooldown_seconds` kể từ lần gửi trước → `AUTH_OTP_RESEND_TOO_SOON`.

```json
{ "email": "an@example.com" }
```

```json
{ "email": "an@example.com", "otpExpiresInSeconds": 300, "resendCooldownSeconds": 60 }
```

## POST /api/v1/auth/login

Đăng nhập bằng email và mật khẩu.

- Xác thực: **không cần**.
- Request: `LoginRequest`.
- Thành công: `200`, `AuthResponse`.
- Lỗi: `400 VALIDATION_FAILED`, `401 AUTH_INVALID_CREDENTIALS`, `403 AUTH_EMAIL_NOT_VERIFIED`,
  `403 AUTH_ACCOUNT_LOCKED`.

Quy tắc:

- Sai email và sai mật khẩu trả **cùng** `AUTH_INVALID_CREDENTIALS` (cùng message).
- Chỉ kiểm tra trạng thái sau khi mật khẩu đúng: `PENDING_VERIFICATION` → `AUTH_EMAIL_NOT_VERIFIED`
  (FE đưa người dùng sang màn nhập OTP, gọi `/resend-otp` nếu cần);
  `BANNED` hoặc `is_active = false` → `AUTH_ACCOUNT_LOCKED`. Chỉ `ACTIVE` và `is_active = true` mới được cấp JWT.
- Tài khoản đã xoá mềm coi như không tồn tại (`AUTH_INVALID_CREDENTIALS`).

```json
{ "email": "an@example.com", "password": "MatKhau@123" }
```

```json
{
  "accessToken": "eyJhbGciOiJIUzI1NiJ9...",
  "tokenType": "Bearer",
  "expiresIn": 7200,
  "user": { "id": 42, "email": "an@example.com", "fullName": "Nguyễn Văn An", "isAdmin": false }
}
```

## POST /api/v1/auth/logout

Đăng xuất.

- Xác thực: không bắt buộc (không đọc token).
- Request: không có body.
- Thành công: `204`, không có nội dung.

JWT không lưu trạng thái ở server nên **logout chỉ là client xoá token** (và dữ liệu phiên đang giữ). Server không
thu hồi token: token cũ vẫn dùng được đến khi hết hạn (`app.jwt.ttl-minutes`). Endpoint này tồn tại để FE có điểm
gọi thống nhất; FE phải xoá token dù gọi thành công hay không.

## DTO

Tất cả là `record`, request có `@Valid`. Tên trường JSON là camelCase.

### RegisterRequest

| Trường     | Kiểu   | Ràng buộc                                                                  |
|------------|--------|----------------------------------------------------------------------------|
| `email`    | string | Bắt buộc, đúng định dạng email, tối đa 255 ký tự                           |
| `password` | string | Bắt buộc, 8–64 ký tự (BCrypt giới hạn 72 byte) |
| `fullName` | string | Bắt buộc, 1–100 ký tự sau khi `trim`                                       |
| `phone`    | string | Bắt buộc, số di động VN: `0[35789]xxxxxxxx` hoặc `+84[35789]xxxxxxxx`      |

### VerifyOtpRequest

| Trường  | Kiểu   | Ràng buộc                    |
|---------|--------|------------------------------|
| `email` | string | Bắt buộc, đúng định dạng email |
| `otp`   | string | Bắt buộc, đúng 6 chữ số `^\d{6}$` |

### ResendOtpRequest

| Trường  | Kiểu   | Ràng buộc                    |
|---------|--------|------------------------------|
| `email` | string | Bắt buộc, đúng định dạng email |

### LoginRequest

| Trường     | Kiểu   | Ràng buộc                      |
|------------|--------|--------------------------------|
| `email`    | string | Bắt buộc, đúng định dạng email |
| `password` | string | Bắt buộc, không rỗng           |

### OtpSentResponse

Trả về từ `/register` và `/resend-otp`.

| Trường                  | Kiểu   | Ý nghĩa                                              |
|-------------------------|--------|------------------------------------------------------|
| `email`                 | string | Email đã gửi OTP (đã chuẩn hoá chữ thường)           |
| `otpExpiresInSeconds`   | number | Số giây OTP còn hiệu lực (`otp_ttl_minutes` × 60)    |
| `resendCooldownSeconds` | number | Số giây phải chờ trước khi gọi lại `/resend-otp`     |

### AuthResponse

Trả về từ `/verify-otp` và `/login`.

| Trường        | Kiểu             | Ý nghĩa                                           |
|---------------|------------------|---------------------------------------------------|
| `accessToken` | string           | JWT, gửi qua `Authorization: Bearer <accessToken>` |
| `tokenType`   | string           | Luôn là `Bearer`                                  |
| `expiresIn`   | number           | Số giây token còn hiệu lực (`app.jwt.ttl-minutes` × 60) |
| `user`        | `AuthUserResponse` | Thông tin tối thiểu của người dùng              |

### AuthUserResponse

| Trường     | Kiểu    | Ý nghĩa                              |
|------------|---------|--------------------------------------|
| `id`       | number  | Id người dùng                        |
| `email`    | string  | Email đăng nhập                      |
| `fullName` | string  | Họ tên hiển thị                      |
| `isAdmin`  | boolean | `true` nếu là Admin                  |

## Mã lỗi (AuthErrorCode)

| code                            | HTTP | Khi nào                                                                             |
|---------------------------------|------|-------------------------------------------------------------------------------------|
| `AUTH_EMAIL_ALREADY_REGISTERED` | 409  | Email đã thuộc tài khoản `ACTIVE` hoặc `BANNED`                                     |
| `AUTH_PHONE_ALREADY_USED`       | 409  | Số điện thoại đang gắn với tài khoản `ACTIVE` khác                                  |
| `AUTH_PHONE_INVALID`            | 400  | Số điện thoại không đúng định dạng di động VN (`field` = `phone`)                   |
| `AUTH_EMAIL_DOMAIN_NOT_ALLOWED` | 400  | Email thuộc danh sách tên miền dùng một lần (FR-04, cấu hình được; `field` = `email`). Chưa bật ở tuần 4 |
| `AUTH_OTP_INVALID`              | 400  | OTP sai, hoặc email không có tài khoản chờ xác thực; `details.remainingAttempts`    |
| `AUTH_OTP_EXPIRED`              | 400  | OTP quá `otp_ttl_minutes` hoặc không còn mã                                         |
| `AUTH_OTP_TOO_MANY_ATTEMPTS`    | 422  | Sai đủ `otp_max_failed` lần, mã đã bị xoá; phải gửi lại OTP                         |
| `AUTH_OTP_RESEND_TOO_SOON`      | 429  | Gửi lại trước `otp_resend_cooldown_seconds`; `details.retryAfterSeconds`            |
| `AUTH_EMAIL_NOT_VERIFIED`       | 403  | Đăng nhập khi tài khoản còn `PENDING_VERIFICATION`                                  |
| `AUTH_INVALID_CREDENTIALS`      | 401  | Sai email hoặc sai mật khẩu (không phân biệt)                                       |
| `AUTH_ACCOUNT_LOCKED`           | 403  | Tài khoản `BANNED` hoặc `is_active = false`                                         |
| `VALIDATION_FAILED`             | 400  | Body thiếu trường hoặc sai định dạng (`CommonErrorCode`), `field` = tên trường      |

Quyết định đã chốt: dùng `429` cho gọi quá nhanh (`AUTH_OTP_RESEND_TOO_SOON`). FE chỉ dựa vào `code`.

Định dạng lỗi (`field` và `details` chỉ có khi liên quan):

```json
{ "code": "AUTH_OTP_INVALID", "message": "Mã OTP không đúng.", "details": { "remainingAttempts": 3 }, "traceId": "a1b2c3d4" }
```

```json
{ "code": "AUTH_OTP_RESEND_TOO_SOON", "message": "Vui lòng chờ trước khi gửi lại mã.", "details": { "retryAfterSeconds": 42 }, "traceId": "a1b2c3d4" }
```

## Khác với frontend/src/services/authApi.ts

Bản hiện tại của FE là contract cũ. Cần đổi:

1. **Tiền tố đường dẫn**: tất cả thêm `/v1`: `/api/auth/...` → `/api/v1/auth/...`.
2. **`register`**: `/api/auth/register` → `/api/v1/auth/register`.
3. **`verifyEmailOtp`**: `/api/auth/email/verify-otp` → `/api/v1/auth/verify-otp` (bỏ đoạn `/email`).
4. **`resendOtp`**: `/api/auth/email/resend-otp` → `/api/v1/auth/resend-otp` (bỏ đoạn `/email`).
5. **`login`**: `/api/auth/login` → `/api/v1/auth/login`.
6. **`sendEmailOtp`** (`/api/auth/email/send-otp`): bỏ hẳn. BE tự gửi OTP ngay trong `/register`; dùng `resendOtp` để gửi lại.
7. **Thêm `logout`**: `POST /api/v1/auth/logout` (204) và hàm xoá token phía client.
8. **`RegisterRequest`**: bỏ `username`; đổi `name` → `fullName`; giữ `email`, `phone`, `password`.
9. **`RegisterResponse`**: bỏ `verificationId`; trả `{ email, otpExpiresInSeconds, resendCooldownSeconds }` (`OtpSentResponse`).
   FE giữ `email` để đưa sang màn OTP và dùng `resendCooldownSeconds` cho bộ đếm gửi lại.
10. **`EmailOtpRequest`** (dùng cho resend): bỏ `verificationId`, chỉ còn `{ email }`.
11. **`VerifyOtpRequest`**: bỏ `verificationId`, thay bằng `{ email, otp }`.
12. **`verifyEmailOtp`**: kiểu trả về từ `Promise<void>` → `Promise<AuthResponse>`; FE lưu `accessToken` ngay, không gọi `login` sau đó.
    `resendOtp` trả `OtpSentResponse` thay vì `void`.
13. **`LoginRequest`**: `usernameOrEmail` → `email`.
14. **`LoginResponse`**: `{ accessToken, role: 'user' | 'admin' }` → `AuthResponse`
    `{ accessToken, tokenType, expiresIn, user { id, email, fullName, isAdmin } }`. Bỏ `role`, dùng `user.isAdmin`.
    Interceptor gắn header `Authorization: ${tokenType} ${accessToken}` và coi token hết hạn sau `expiresIn` giây.
15. **Xử lý lỗi**: đọc `code` trong `ErrorResponse` (`AUTH_*`), không dựa vào `message`. `401 AUTH_INVALID_CREDENTIALS`
    của `/login` là sai thông tin, khác với 401 do phiên hết hạn ở các API khác. `AUTH_EMAIL_NOT_VERIFIED` chuyển sang màn OTP.
16. **Ngoài phạm vi file này** (`forgotPassword`, `verifyResetOtp`, `resetPassword`, `changePassword`, `getProfile`,
    `updateProfile`): giữ nguyên chờ contract riêng, nhưng đường dẫn cũng sẽ phải đổi sang `/api/v1/...`
    (ERD gợi ý `/api/v1/auth/forgot-password`, `/api/v1/auth/reset-password`, `PUT /api/v1/me/password`).
