# API module user

Quy ước chung theo Lộ trình mục 3.4: tiền tố `/api/v1`, thời gian ISO-8601 UTC, thành công trả thẳng DTO (không bọc),
lỗi theo định dạng thống nhất ở cuối file. Nguồn: SRS v10 (FR-06, BR-U03, UC-03).

Người sở hữu: BE1 (review: BE3 @dwargon73-sketch theo Issue #147). File này gồm các endpoint hồ sơ của người đang đăng nhập (xem, cập nhật, đổi số điện thoại qua OTP email) và các hàm của
`UserService` dành cho module khác gọi (khoá, mở khoá, lấy thông tin liên lạc), cùng job nền dọn tài khoản chưa xác thực (mục "Job nền"). Đổi mật khẩu xem `docs/api/auth.md`.

## Quy tắc chung

- Các endpoint **bắt buộc** `Authorization: Bearer <accessToken>`. Id người dùng lấy từ token (claim `sub`),
  không nhận từ client, nên chỉ xem và sửa được hồ sơ của chính mình (BR-U03).
- Tài khoản bị khoá (FR-107) hoặc `BANNED`: **mọi** endpoint cần token trả `403 AUTH_ACCOUNT_LOCKED`, kể cả với token
  cấp trước lúc khoá (server kiểm lại tài khoản ở mỗi request). FE gặp mã này thì xoá phiên và về màn đăng nhập.
- Email **không đổi** ở đây. Body có gửi kèm `email` thì server bỏ qua. Đổi số điện thoại qua luồng riêng (FR-07, OTP email).
- Khu vực lấy từ `GET /api/v1/areas` (`docs/api/catalog.md`). Ảnh đại diện upload trước qua `POST /api/v1/media`
  (`docs/api/media.md`), lấy `mediaId` gửi vào `avatarMediaId`.

## GET /api/v1/me

Hồ sơ của người đang đăng nhập.

- Xác thực: **bắt buộc**.
- Thành công: `200`, `MeResponse`.
- Lỗi: `401 UNAUTHORIZED` (thiếu, sai hoặc hết hạn token), `404 USER_NOT_FOUND` (tài khoản trong token đã bị xoá).

```json
{
  "id": 42,
  "email": "an@example.com",
  "fullName": "Nguyễn Văn An",
  "phone": "0901234567",
  "isAdmin": false,
  "areaId": 12,
  "avatarMediaId": 30,
  "avatarUrl": "/api/v1/media/30"
}
```

Người dùng mới đăng ký chưa có khu vực và ảnh: `areaId`, `avatarMediaId`, `avatarUrl` là `null`.

## PUT /api/v1/me

Cập nhật họ tên, khu vực mặc định, ảnh đại diện.

- Xác thực: **bắt buộc**.
- Request: `UpdateProfileRequest`. Gửi **đủ cả 3 trường** mỗi lần (PUT thay toàn bộ phần hồ sơ được sửa).
- Thành công: `200`, `MeResponse` sau khi cập nhật.
- Lỗi: `401 UNAUTHORIZED`, `400 VALIDATION_FAILED` (`field` = tên trường), `400 USER_AREA_INVALID`,
  `404 MEDIA_NOT_FOUND`, `403 MEDIA_NOT_OWNER`, `409 MEDIA_NOT_UPLOADED`, `404 USER_NOT_FOUND`.

Quy tắc:

- `fullName`: server tự `trim` trước khi lưu.
- `areaId = null`: bỏ khu vực mặc định. Có giá trị thì khu vực phải **tồn tại và đang hoạt động**, nếu không
  → `400 USER_AREA_INVALID` (`field` = `areaId`). Chọn được cả khu vực cấp 1 và cấp 2.
- `avatarMediaId = null`: bỏ ảnh đại diện.
- `avatarMediaId` **giống** ảnh hiện tại: giữ nguyên, không kiểm lại.
- `avatarMediaId` **mới**: ảnh phải do chính người này upload và chưa gắn vào đâu (trạng thái `UPLOADED`). Server gắn
  ảnh (chuyển sang `ATTACHED`) trong cùng transaction với việc lưu hồ sơ; lỗi ở bước nào thì không có gì thay đổi.
  Ảnh không tồn tại → `MEDIA_NOT_FOUND`; ảnh của người khác → `MEDIA_NOT_OWNER`; ảnh đã gắn ở chỗ khác (ví dụ bài đăng)
  → `MEDIA_NOT_UPLOADED`. Muốn đổi ảnh thì upload ảnh mới, không dùng lại ảnh của bài đăng.
- Ảnh đại diện cũ khi đổi sang ảnh khác không bị xoá (vẫn đọc được qua URL cũ).

```json
{ "fullName": "Nguyễn Văn An", "areaId": 12, "avatarMediaId": 30 }
```

Bỏ khu vực và ảnh đại diện:

```json
{ "fullName": "Nguyễn Văn An", "areaId": null, "avatarMediaId": null }
```

Response giống `GET /api/v1/me`.

## POST /api/v1/me/phone/otp

Yêu cầu gửi mã OTP về email để chuẩn bị đổi số điện thoại (FR-07, BR-U10).

- Xác thực: **bắt buộc**.
- Request: `SendChangePhoneOtpRequest` (`newPhone`).
- Thành công: `200`, `ChangePhoneOtpResponse`.
- Lỗi: `401 UNAUTHORIZED`, `400 VALIDATION_FAILED` (số sai định dạng regex `^(0|\+84)\d{9}$`), `400 USER_PHONE_SAME_AS_CURRENT`, `409 USER_PHONE_ALREADY_USED`, `400 USER_PHONE_CHANGE_COOLDOWN` (`details.retryAfterDays`), `409 USER_PHONE_CHANGE_ACTIVE_DELIVERY`, `429 AUTH_OTP_COOLDOWN`.

Quy tắc:
- Số điện thoại được chuẩn hoá tự động về dạng `0xxxxxxxxx` (nếu bắt đầu bằng `+84`).
- Không được trùng với số điện thoại hiện tại của chính người dùng (`400 USER_PHONE_SAME_AS_CURRENT`).
- Không được trùng với số điện thoại đang hoạt động của tài khoản khác (`status = ACTIVE`) (`409 USER_PHONE_ALREADY_USED`).
- Giới hạn tần suất đổi số điện thoại: tối đa 1 lần trong 30 ngày (cấu hình `phone_change_interval_days`). Nếu chưa đủ thời gian → `400 USER_PHONE_CHANGE_COOLDOWN` kèm `details.retryAfterDays`.
- Chặn đổi số khi người dùng đang tham gia vào giao dịch đang diễn ra đã trao đổi liên lạc nhưng chưa hoàn tất (Request ở trạng thái `LOGISTICS_CONFIRMED` hoặc `DELIVERING`) (`409 USER_PHONE_CHANGE_ACTIVE_DELIVERY`).
- OTP gửi về email hiện tại của tài khoản với mục đích `CHANGE_PHONE`, thời hạn theo `otp_ttl_minutes` (5 phút) và giới hạn gửi lại `otp_resend_cooldown_seconds` (60 giây).

```json
{
  "newPhone": "0987654321"
}
```

Response:

```json
{
  "email": "an@example.com",
  "otpExpiresInSeconds": 300,
  "resendCooldownSeconds": 60
}
```

## PUT /api/v1/me/phone

Xác nhận mã OTP và hoàn tất đổi số điện thoại (FR-07, BR-U10).

- Xác thực: **bắt buộc**.
- Request: `ChangePhoneRequest` (`newPhone`, `otp`).
- Thành công: `200`, `MeResponse` với số điện thoại mới đã được cập nhật.
- Lỗi: `401 UNAUTHORIZED`, `400 VALIDATION_FAILED`, `400 AUTH_OTP_INVALID`, `400 AUTH_OTP_EXPIRED`, `429 AUTH_OTP_MAX_ATTEMPTS`, `400 USER_PHONE_SAME_AS_CURRENT`, `409 USER_PHONE_ALREADY_USED`, `400 USER_PHONE_CHANGE_COOLDOWN`, `409 USER_PHONE_CHANGE_ACTIVE_DELIVERY`.

Quy tắc:
- Xác thực mã OTP gửi về email. Nhập sai quá số lần tối đa (5 lần) → `429 AUTH_OTP_MAX_ATTEMPTS` và mã OTP bị hủy.
- Kiểm tra lại các điều kiện: không có giao dịch đang hoạt động, không trùng số hiện tại, chưa bị tài khoản ACTIVE khác dùng, đủ điều kiện giãn cách 30 ngày.
- Khi thành công: cập nhật `phone = newPhone`, cập nhật `phone_changed_at = now()`, xoá `pending_phone` và huỷ OTP đã dùng.

```json
{
  "newPhone": "0987654321",
  "otp": "123456"
}
```

Response: Giống `GET /api/v1/me` (với trường `phone` mang giá trị mới).

## DTO

Tất cả là `record`, request có `@Valid`. Tên trường JSON là camelCase.

### UpdateProfileRequest

| Trường          | Kiểu           | Ràng buộc                                    |
|-----------------|----------------|----------------------------------------------|
| `fullName`      | string         | Bắt buộc, không toàn khoảng trắng, tối đa 100 ký tự |
| `areaId`        | number \| null | Số dương; khu vực đang hoạt động; `null` = bỏ |
| `avatarMediaId` | number \| null | Số dương; ảnh của chính mình; `null` = bỏ    |

### SendChangePhoneOtpRequest

| Trường          | Kiểu           | Ràng buộc                                    |
|-----------------|----------------|----------------------------------------------|
| `newPhone`      | string         | Bắt buộc, regex `^(0\|\+84)\d{9}$`           |

### ChangePhoneRequest

| Trường          | Kiểu           | Ràng buộc                                    |
|-----------------|----------------|----------------------------------------------|
| `newPhone`      | string         | Bắt buộc, regex `^(0\|\+84)\d{9}$`           |
| `otp`           | string         | Bắt buộc, 6 chữ số (`^\d{6}$`)              |

### ChangePhoneOtpResponse

| Trường                 | Kiểu           | Ý nghĩa                                         |
|------------------------|----------------|-------------------------------------------------|
| `email`                | string         | Email nhận mã OTP                               |
| `otpExpiresInSeconds`  | number         | Thời gian sống của mã OTP (giây, mặc định 300s) |
| `resendCooldownSeconds`| number         | Thời gian chờ trước khi gửi lại (giây, mặc định 60s) |

### MeResponse

Có đủ các trường của `AuthUserResponse` (`docs/api/auth.md`) cộng thêm thông tin hồ sơ.

| Trường          | Kiểu           | Ý nghĩa                                                    |
|-----------------|----------------|------------------------------------------------------------|
| `id`            | number         | Id người dùng                                              |
| `email`         | string         | Email đăng nhập (không đổi được ở đây)                     |
| `fullName`      | string         | Họ tên hiển thị                                            |
| `phone`         | string         | Số điện thoại dạng `0xxxxxxxxx` (đổi qua OTP email)        |
| `isAdmin`       | boolean        | `true` nếu là Admin                                        |
| `areaId`        | number \| null | Khu vực mặc định                                           |
| `avatarMediaId` | number \| null | Id ảnh đại diện                                            |
| `avatarUrl`     | string \| null | Đường dẫn tương đối `GET /api/v1/media/{id}`; FE ghép với địa chỉ backend (`resolveMediaUrl`) |

## Mã lỗi

| code                               | HTTP | Khi nào                                                                  |
|------------------------------------|------|--------------------------------------------------------------------------|
| `UNAUTHORIZED`                     | 401  | Thiếu, sai hoặc hết hạn token (`CommonErrorCode`)                        |
| `VALIDATION_FAILED`                | 400  | Body thiếu trường hoặc sai định dạng, `field` = tên trường               |
| `USER_AREA_INVALID`                | 400  | `areaId` không tồn tại hoặc khu vực đã ngừng hoạt động, `field` = `areaId` |
| `AUTH_ACCOUNT_LOCKED`              | 403  | Tài khoản đã bị khoá hoặc `BANNED` (`AuthErrorCode`), kể cả token cũ     |
| `USER_NOT_FOUND`                   | 404  | Tài khoản trong token không còn tồn tại                                  |
| `MEDIA_NOT_FOUND`                  | 404  | `avatarMediaId` không tồn tại (`MediaErrorCode`)                         |
| `MEDIA_NOT_OWNER`                  | 403  | Ảnh không do người này upload                                            |
| `MEDIA_NOT_UPLOADED`               | 409  | Ảnh đã được gắn ở chỗ khác hoặc không còn dùng được                       |
| `USER_PHONE_SAME_AS_CURRENT`       | 400  | Số điện thoại mới trùng với số hiện tại của tài khoản                    |
| `USER_PHONE_ALREADY_USED`          | 409  | Số điện thoại đã được đăng ký bởi tài khoản ACTIVE khác                  |
| `USER_PHONE_CHANGE_COOLDOWN`       | 400  | Đã đổi số điện thoại trong vòng 30 ngày qua (kèm `details.retryAfterDays`) |
| `USER_PHONE_CHANGE_ACTIVE_DELIVERY`| 409  | Đang có giao dịch chưa hoàn tất (đã trao thông tin liên lạc)              |
| `AUTH_OTP_INVALID`                 | 400  | Mã OTP không đúng                                                        |
| `AUTH_OTP_EXPIRED`                 | 400  | Mã OTP đã hết hạn                                                        |
| `AUTH_OTP_MAX_ATTEMPTS`            | 429  | Nhập sai OTP quá 5 lần, mã đã bị huỷ                                     |
| `AUTH_OTP_COOLDOWN`                | 429  | Gửi lại OTP quá nhanh (chưa hết 60s cooldown)                           |

Định dạng lỗi (`field` và `details` chỉ có khi liên quan):

```json
{ "code": "USER_AREA_INVALID", "message": "Khu vực không tồn tại hoặc đã ngừng hoạt động.", "field": "areaId", "traceId": "a1b2c3d4" }
```

## Dành cho module khác: UserService

Không phải endpoint HTTP. Module khác gọi `com.shareloop.user.service.UserService`, **không** inject `UserRepository`.
Lỗi là `BusinessException` với mã của `UserErrorCode`; nếu để lọt ra controller thì thành HTTP theo bảng dưới.

| Hàm | Người gọi | Transaction | Lỗi |
|-----|-----------|-------------|-----|
| `void lock(long userId, String reason)` | admin (#116), reputation | REQUIRED | `USER_NOT_FOUND` (404), `USER_ALREADY_LOCKED` (409) |
| `void unlock(long userId)` | admin (#116), reputation | REQUIRED | `USER_NOT_FOUND` (404), `USER_NOT_LOCKED` (409) |
| `ContactInfo findContact(long userId)` | request (#93) | read-only | `USER_NOT_FOUND` (404) |

### lock / unlock (FR-107, UC-50)

- Khoá: `is_active = false`; mở khoá: `is_active = true`. Cột `status` **không đổi**.
- `reason` **không được lưu** ở users (bảng không có cột lý do). Người gọi tự ghi lý do vào nhật ký qua
  ActivityLogService (admin bắt buộc lý do theo #116).
- Có hiệu lực ngay: đăng nhập trả `403 AUTH_ACCOUNT_LOCKED`, token đã cấp cũng bị chặn ở request kế tiếp.
- REQUIRED: gọi trong transaction của người gọi thì cùng commit / rollback với việc ghi nhật ký.

### findContact (FR-82, BR-T11, BR-T12)

- Trả `ContactInfo` gồm **đúng 4 trường**: `userId`, `fullName`, `email`, `phone`. Không trả gì khác.
- **Không** kiểm Request: người gọi (module request) phải tự bảo đảm Request đã `LogisticsConfirmed` và người xem là
  một trong hai bên, rồi mới gọi để lấy liên lạc của bên còn lại.
- Vẫn trả khi tài khoản kia đã bị khoá (BR-T12: đã trao thì không ẩn lại).

## Job nền

Không phải endpoint. Profile `test` đặt `app.jobs.enabled=false` nên bean job không được tạo; test gọi thẳng service.

| Job | Lịch | Service | Việc làm |
|-----|------|---------|----------|
| `PendingAccountCleanupJob` (#106) | `0 30 * * * *` (phút 30 mỗi giờ) | `PendingAccountCleanupService.deleteExpiredPendingAccounts(now)` | Xoá mềm tài khoản chưa xác thực quá hạn |

### Xoá tài khoản chưa xác thực (FR-117, BR-U06)

- **Chọn**: `status = PENDING_VERIFICATION`, `created_at < now − pending_account_ttl_hours` (mặc định 24 giờ, đọc từ
  ConfigService), `is_deleted = false`, và **không** có OTP còn hạn (`otp_expires_at` null hoặc `≤ now`). Đọc theo lô
  100 id, phân trang keyset.
- **OTP còn hạn thì để lại** tới lượt sau: người dùng có thể vừa gửi lại mã gần mốc 24 giờ. OTP chỉ sống
  `otp_ttl_minutes` (5 phút), nên chậm nhất lượt kế tiếp sẽ xoá.
- Hạn tính từ `created_at`; đăng ký lại một email đang PENDING dùng lại dòng cũ nên **không** làm mới mốc này.
- Tài khoản `ACTIVE` / `BANNED` không bao giờ bị chọn.
- **Xoá mềm**: `markDeleted()` (`is_deleted = true`), không xoá cứng. Index `ux_users_email` chỉ tính dòng chưa xoá
  nên email đó đăng ký lại được ngay; số điện thoại vốn chỉ tính trùng với tài khoản ACTIVE.
- **Mỗi tài khoản một transaction**: đọc lại và kiểm lại ba điều kiện trên rồi mới xoá (người dùng có thể vừa xác thực
  hoặc gửi lại OTP). Một dòng lỗi chỉ rollback chính nó, ghi log WARN, lượt sau thử lại. Job ghi log INFO số tài khoản
  đã xoá mỗi lượt.
- **Idempotent**: dòng đã xoá mềm bị loại khỏi truy vấn, chạy lại không có tác dụng kép.

## Ghi chú cho frontend

- Dùng `frontend/src/services/userApi.ts`: `userApi.getMe()`, `userApi.updateMe(input)`.
- Đổi số điện thoại (FR-07): gọi `userApi.sendChangePhoneOtp({ newPhone })` (`POST /api/v1/me/phone/otp`) để gửi mã OTP về email, sau đó gọi `userApi.changePhone({ newPhone, otp })` (`PUT /api/v1/me/phone`) để xác thực và cập nhật.
- `authApi.me(userId)` (đang dùng trong `session.ts`) vẫn chạy: server bỏ qua tham số `userId` và trả `MeResponse`, có đủ các trường cũ. Khi tiện có thể đổi sang `userApi.getMe()`.
- Màn hồ sơ hiện cho sửa email và số điện thoại: hai trường này phải chỉ đọc trên form thông thường; đổi số điện thoại là nút/hành động riêng mở modal OTP.

