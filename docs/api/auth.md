# API module auth

Quy ước chung theo Lộ trình mục 3.4: tiền tố `/api/v1`, thời gian ISO-8601 UTC, thành công trả thẳng DTO (không bọc),
lỗi theo định dạng thống nhất ở cuối file. Nguồn: SRS v10 (FR-01..FR-05, FR-08, FR-09, BR-U06, UC-01, UC-02, UC-04),
Luồng F01, F02.

Người sở hữu: BE1. File này gồm 8 endpoint: đăng ký, xác thực OTP, gửi lại OTP, đăng nhập, đăng xuất, đổi mật khẩu,
quên mật khẩu, đặt lại mật khẩu. Hồ sơ (`GET`/`PUT /api/v1/me`) xem `docs/api/user.md`.

## Quy tắc chung

- Mọi endpoint ở đây **không cần** header `Authorization` (`/api/v1/auth/**` là công khai), trừ `/change-password`
  **bắt buộc** token. Các API khác gửi `Authorization: Bearer <accessToken>`.
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
| `accounts_per_ip_24h`         | 3        | Số tài khoản mới tối đa mỗi IP trong 24 giờ  |

- Hạn JWT lấy từ `app.jwt.ttl-minutes` (120 phút), nên `expiresIn` = `7200` (giây). JWT chứa `userId` (sub) và `roles`.
- OTP: 6 chữ số, lưu bản băm kèm mục đích (`otp_purpose` = `REGISTER` hoặc `RESET_PASSWORD`), gửi qua email sau khi
  transaction commit. Mã của mục đích này không dùng được cho mục đích kia. Mỗi tài khoản chỉ giữ một mã: gửi mã mới
  làm mã cũ mất hiệu lực.
- Tài khoản chưa xác thực quá 24 giờ bị job tự xoá; client coi như phải đăng ký lại.

## POST /api/v1/auth/register

Đăng ký tài khoản và gửi OTP về email.

- Xác thực: **không cần**.
- Request: `RegisterRequest`.
- Thành công: `201`, `OtpSentResponse`. Tài khoản được tạo ở trạng thái `PENDING_VERIFICATION`, chưa đăng nhập được.
- Lỗi: `400 VALIDATION_FAILED` (thiếu trường, email/mật khẩu/họ tên sai định dạng, `field` = tên trường),
  `400 AUTH_PHONE_INVALID`, `400 AUTH_EMAIL_DOMAIN_NOT_ALLOWED`, `409 AUTH_EMAIL_ALREADY_REGISTERED`,
  `409 AUTH_PHONE_ALREADY_USED`, `429 AUTH_TOO_MANY_ACCOUNTS_FROM_IP`, `429 AUTH_OTP_RESEND_TOO_SOON`.

Quy tắc:

- **Đăng ký lại khi email đang `PENDING_VERIFICATION`**: không báo trùng email. Server cập nhật mật khẩu, họ tên,
  số điện thoại theo request mới, sinh OTP mới (đặt lại `otp_failed_count = 0`, hạn mới) rồi gửi email; vẫn trả `201`.
  Lần đăng ký lại cũng phải chờ `otp_resend_cooldown_seconds` kể từ lần gửi trước, nếu chưa đủ thì trả
  `429 AUTH_OTP_RESEND_TOO_SOON`.
- Email đã thuộc tài khoản `ACTIVE` hoặc `BANNED` (chưa xoá mềm) → `409 AUTH_EMAIL_ALREADY_REGISTERED`.
- Số điện thoại chỉ tính trùng với tài khoản `ACTIVE`; tài khoản `PENDING_VERIFICATION` không giữ số.
- **Email dùng một lần (FR-04)**: tên miền sau `@` nằm trong `src/main/resources/disposable-email-domains.txt` thì
  trả `400 AUTH_EMAIL_DOMAIN_NOT_ALLOWED`, không tạo tài khoản, không gửi OTP. So khớp không phân biệt hoa thường, chỉ
  đúng tên miền (không khớp tên miền con). Danh sách đọc một lần lúc khởi động; sửa file phải build lại. Lệch SRS: ERD
  để danh sách ở `website_attributes` nhóm `DISPOSABLE_DOMAIN` cho Admin sửa, sẽ chuyển khi module setting hỗ trợ.
- **Giới hạn theo IP (FR-10, C30)**: chỉ xét khi tạo tài khoản **mới** (đăng ký lại email `PENDING_VERIFICATION` không
  xét). Nếu IP đã có từ `accounts_per_ip_24h` tài khoản trở lên tạo trong 24 giờ qua (không tính tài khoản đã xoá mềm)
  thì trả `429 AUTH_TOO_MANY_ACCOUNTS_FROM_IP`. Lệch SRS: FR-10 ghi "đánh dấu chờ Admin xem xét" nhưng ERD không có cột
  đánh dấu, nên chặn luôn theo SRS 5.3. IP lấy từ `remoteAddr`; prod bật `forward-headers-strategy: framework` nên
  sau proxy vẫn là IP thật của client.

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

## POST /api/v1/auth/change-password

Đổi mật khẩu cho người đang đăng nhập (FR-08), phải nhập mật khẩu hiện tại.

- Xác thực: **bắt buộc** Bearer Token, dù đường dẫn nằm dưới `/api/v1/auth`. Id người dùng lấy từ token.
- Request: `ChangePasswordRequest`.
- Thành công: `204`, không có nội dung.
- Lỗi: `401 UNAUTHORIZED` (thiếu, sai hoặc hết hạn token), `400 VALIDATION_FAILED` (`field` = tên trường),
  `400 AUTH_CURRENT_PASSWORD_INCORRECT`, `400 AUTH_NEW_PASSWORD_SAME_AS_CURRENT`, `404 USER_NOT_FOUND`.

Quy tắc:

- `newPassword` theo **đúng luật của đăng ký** (8–64 ký tự, cùng một annotation `@ValidPassword` với `RegisterRequest`).
- Sai mật khẩu hiện tại → `AUTH_CURRENT_PASSWORD_INCORRECT` (`field` = `currentPassword`). Cố ý dùng `400`, không
  dùng `401`: FE gặp `401` sẽ coi là hết phiên và đăng xuất.
- Mật khẩu mới trùng mật khẩu hiện tại → `AUTH_NEW_PASSWORD_SAME_AS_CURRENT` (`field` = `newPassword`).
- Mật khẩu mới lưu dạng BCrypt.
- JWT không lưu trạng thái: sau khi đổi, **token đang dùng vẫn hợp lệ** đến khi hết hạn; FE không cần đăng nhập lại.
  Thu hồi token cũ nằm ngoài phạm vi.

```json
{ "currentPassword": "MatKhau@123", "newPassword": "MatKhauMoi@456" }
```

```json
{ "code": "AUTH_CURRENT_PASSWORD_INCORRECT", "message": "Mật khẩu hiện tại không đúng.", "field": "currentPassword", "traceId": "a1b2c3d4" }
```

## POST /api/v1/auth/forgot-password

Quên mật khẩu (FR-09): gửi OTP đặt lại mật khẩu về email. Luồng 2 bước: `/forgot-password` → `/reset-password`
(không có bước xác nhận OTP riêng; FE giữ mã ở màn nhập OTP rồi gửi kèm mật khẩu mới).

- Xác thực: **không cần**.
- Request: `ForgotPasswordRequest`.
- Thành công: **luôn** `200`, `OtpSentResponse`.
- Lỗi: chỉ `400 VALIDATION_FAILED` (email trống hoặc sai định dạng).

Quy tắc:

- **Không lộ email**: chỉ gửi mail khi tài khoản `ACTIVE` và `is_active = true`. Email không tồn tại, tài khoản
  `PENDING_VERIFICATION` (dùng `/resend-otp`), `BANNED`, bị khoá, hoặc gọi lại khi chưa đủ `otp_resend_cooldown_seconds`
  → **không gửi mail** nhưng vẫn trả `200` với cùng nội dung. Endpoint này cố ý **không** trả `429`, vì `429` chỉ xảy ra
  với email có tài khoản.
- Mã cùng luật với OTP đăng ký: hạn `otp_ttl_minutes`, tối đa `otp_max_failed` lần sai, cách nhau tối thiểu
  `otp_resend_cooldown_seconds`. Gửi lại mã = gọi lại endpoint này; FE dùng `resendCooldownSeconds` để đếm ngược.
- Email có tiêu đề "Đặt lại mật khẩu ShareLoop" (mẫu `otp.html`).

```json
{ "email": "an@example.com" }
```

```json
{ "email": "an@example.com", "otpExpiresInSeconds": 300, "resendCooldownSeconds": 60 }
```

## POST /api/v1/auth/reset-password

Đặt lại mật khẩu bằng OTP đã gửi qua email (FR-09).

- Xác thực: **không cần**.
- Request: `ResetPasswordRequest`.
- Thành công: `204`, không có nội dung. FE chuyển sang màn đăng nhập (không tự cấp JWT).
- Lỗi: `400 VALIDATION_FAILED` (`field` = tên trường), `400 AUTH_OTP_EXPIRED`, `400 AUTH_OTP_INVALID`,
  `422 AUTH_OTP_TOO_MANY_ATTEMPTS`, `400 AUTH_NEW_PASSWORD_SAME_AS_CURRENT`.

Quy tắc:

- Thứ tự kiểm: tài khoản → OTP → mật khẩu mới khác mật khẩu cũ. Kiểm OTP trước để người không có mã không dò được
  mật khẩu hiện tại.
- Email không có tài khoản `ACTIVE` chưa khoá → `AUTH_OTP_EXPIRED`, giống hệt tài khoản thật chưa xin mã (không dò được).
- Mã sai: tăng `otp_failed_count`, `AUTH_OTP_INVALID` kèm `details.remainingAttempts`. Lần sai thứ `otp_max_failed`:
  xoá mã, `AUTH_OTP_TOO_MANY_ATTEMPTS`; phải gọi lại `/forgot-password`.
- Quá `otp_ttl_minutes`, không còn mã, hoặc mã đang lưu là mã đăng ký → `AUTH_OTP_EXPIRED`.
- `newPassword` theo **đúng luật của đăng ký** (`@ValidPassword`, 8–64 ký tự), lưu BCrypt.
- Mật khẩu mới trùng mật khẩu cũ → `AUTH_NEW_PASSWORD_SAME_AS_CURRENT` (`field` = `newPassword`). Lúc này OTP **đã được
  dùng**, FE báo lỗi và cho bấm "Gửi lại mã".
- Thành công thì xoá các cột `otp_*`: mã không dùng lại được.
- **Ngoài phạm vi**: JWT không lưu trạng thái nên token đã cấp trước khi đặt lại **vẫn hợp lệ** đến khi hết hạn
  (`app.jwt.ttl-minutes`), giống `/change-password`. Chưa ghi `activity_logs` SECURITY (ERD F02 bước 4) và chưa giới hạn
  số lần gọi theo IP/ngày.

```json
{ "email": "an@example.com", "otp": "123456", "newPassword": "MatKhauMoi@456" }
```

```json
{ "code": "AUTH_OTP_INVALID", "message": "Mã OTP không đúng.", "details": { "remainingAttempts": 4 }, "traceId": "a1b2c3d4" }
```

## DTO

Tất cả là `record`, request có `@Valid`. Tên trường JSON là camelCase.

### RegisterRequest

| Trường     | Kiểu   | Ràng buộc                                                                  |
|------------|--------|----------------------------------------------------------------------------|
| `email`    | string | Bắt buộc, đúng định dạng email, tối đa 255 ký tự                           |
| `password` | string | Bắt buộc, 8–64 ký tự (BCrypt giới hạn 72 byte); sai thì message "Mật khẩu phải từ 8 đến 64 ký tự." |
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

### ChangePasswordRequest

| Trường            | Kiểu   | Ràng buộc                                        |
|-------------------|--------|--------------------------------------------------|
| `currentPassword` | string | Bắt buộc, không rỗng                             |
| `newPassword`     | string | Bắt buộc, 8–64 ký tự (cùng luật với `RegisterRequest.password`) |

### ForgotPasswordRequest

| Trường  | Kiểu   | Ràng buộc                      |
|---------|--------|--------------------------------|
| `email` | string | Bắt buộc, đúng định dạng email |

### ResetPasswordRequest

| Trường        | Kiểu   | Ràng buộc                                                       |
|---------------|--------|-----------------------------------------------------------------|
| `email`       | string | Bắt buộc, đúng định dạng email                                  |
| `otp`         | string | Bắt buộc, đúng 6 chữ số `^\d{6}$`                               |
| `newPassword` | string | Bắt buộc, 8–64 ký tự (cùng luật với `RegisterRequest.password`) |

### OtpSentResponse

Trả về từ `/register`, `/resend-otp` và `/forgot-password`.

| Trường                  | Kiểu   | Ý nghĩa                                              |
|-------------------------|--------|------------------------------------------------------|
| `email`                 | string | Email đã gửi OTP (đã chuẩn hoá chữ thường)           |
| `otpExpiresInSeconds`   | number | Số giây OTP còn hiệu lực (`otp_ttl_minutes` × 60)    |
| `resendCooldownSeconds` | number | Số giây phải chờ trước khi gửi lại mã (`/resend-otp` hoặc `/forgot-password`) |

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
| `AUTH_EMAIL_DOMAIN_NOT_ALLOWED` | 400  | `/register`: email thuộc danh sách tên miền dùng một lần (FR-04; `field` = `email`) |
| `AUTH_TOO_MANY_ACCOUNTS_FROM_IP` | 429 | `/register` tạo tài khoản mới khi IP đã có đủ `accounts_per_ip_24h` tài khoản trong 24 giờ (FR-10) |
| `AUTH_OTP_INVALID`              | 400  | OTP sai, hoặc (`/verify-otp`) email không có tài khoản chờ xác thực; `details.remainingAttempts` |
| `AUTH_OTP_EXPIRED`              | 400  | OTP quá `otp_ttl_minutes`, không còn mã, mã khác mục đích; `/reset-password` với email không có tài khoản hợp lệ |
| `AUTH_OTP_TOO_MANY_ATTEMPTS`    | 422  | Sai đủ `otp_max_failed` lần, mã đã bị xoá; phải gửi lại OTP                         |
| `AUTH_OTP_RESEND_TOO_SOON`      | 429  | `/register`, `/resend-otp` gửi lại trước `otp_resend_cooldown_seconds`; `details.retryAfterSeconds`. `/forgot-password` không trả mã này |
| `AUTH_EMAIL_NOT_VERIFIED`       | 403  | Đăng nhập khi tài khoản còn `PENDING_VERIFICATION`                                  |
| `AUTH_INVALID_CREDENTIALS`      | 401  | Sai email hoặc sai mật khẩu (không phân biệt)                                       |
| `AUTH_ACCOUNT_LOCKED`           | 403  | Tài khoản `BANNED` hoặc `is_active = false`                                         |
| `AUTH_CURRENT_PASSWORD_INCORRECT` | 400 | Đổi mật khẩu: mật khẩu hiện tại không đúng, `field` = `currentPassword`            |
| `AUTH_NEW_PASSWORD_SAME_AS_CURRENT` | 400 | Đổi / đặt lại mật khẩu: mật khẩu mới trùng mật khẩu hiện tại, `field` = `newPassword` |
| `UNAUTHORIZED`                  | 401  | `/change-password` thiếu, sai hoặc hết hạn token (`CommonErrorCode`)                |
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
16. **Đổi mật khẩu**: đã có `authApi.changePassword(input)` gọi `POST /api/v1/auth/change-password` (204). Đường dẫn
    theo issue #73, khác gợi ý `PUT /api/v1/me/password` của ERD. Lỗi `AUTH_CURRENT_PASSWORD_INCORRECT` hiển thị dưới ô
    mật khẩu hiện tại (`field`). Hồ sơ (`getProfile`, `updateProfile`) chuyển sang `userApi.ts` (`docs/api/user.md`).
17. **Quên mật khẩu** (issue #137): đã có `authApi.forgotPassword(email)` → `OtpSentResponse` và
    `authApi.resetPassword({ email, otp, newPassword })` (204). **Không có** `verifyResetOtp` và không có `resetToken`:
    màn nhập OTP chỉ giữ mã ở phía FE, bước cuối gửi cả ba trường. Trang `ForgotPassword.tsx` hiện vẫn dùng dữ liệu
    giả trong store, chưa nối API.
