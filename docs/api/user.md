# API module user

Quy ước chung theo Lộ trình mục 3.4: tiền tố `/api/v1`, thời gian ISO-8601 UTC, thành công trả thẳng DTO (không bọc),
lỗi theo định dạng thống nhất ở cuối file. Nguồn: SRS v10 (FR-06, BR-U03, UC-03).

Người sở hữu: BE1. File này gồm 2 endpoint hồ sơ của người đang đăng nhập (xem và cập nhật) và các hàm của
`UserService` dành cho module khác gọi (khoá, mở khoá, lấy thông tin liên lạc), cùng job nền dọn tài khoản chưa xác thực (mục "Job nền"). Đổi số điện thoại (FR-07, OTP email)
và đổi mật khẩu không thuộc file này; đổi mật khẩu xem `docs/api/auth.md`.

## Quy tắc chung

- Cả 2 endpoint **bắt buộc** `Authorization: Bearer <accessToken>`. Id người dùng lấy từ token (claim `sub`),
  không nhận từ client, nên chỉ xem và sửa được hồ sơ của chính mình (BR-U03).
- Tài khoản bị khoá (FR-107) hoặc `BANNED`: **mọi** endpoint cần token trả `403 AUTH_ACCOUNT_LOCKED`, kể cả với token
  cấp trước lúc khoá (server kiểm lại tài khoản ở mỗi request). FE gặp mã này thì xoá phiên và về màn đăng nhập.
- Email và số điện thoại **không đổi** ở đây. Body có gửi kèm `email`/`phone` thì server bỏ qua.
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

## DTO

Tất cả là `record`, request có `@Valid`. Tên trường JSON là camelCase.

### UpdateProfileRequest

| Trường          | Kiểu           | Ràng buộc                                    |
|-----------------|----------------|----------------------------------------------|
| `fullName`      | string         | Bắt buộc, không toàn khoảng trắng, tối đa 100 ký tự |
| `areaId`        | number \| null | Số dương; khu vực đang hoạt động; `null` = bỏ |
| `avatarMediaId` | number \| null | Số dương; ảnh của chính mình; `null` = bỏ    |

### MeResponse

Có đủ các trường của `AuthUserResponse` (`docs/api/auth.md`) cộng thêm thông tin hồ sơ.

| Trường          | Kiểu           | Ý nghĩa                                                    |
|-----------------|----------------|------------------------------------------------------------|
| `id`            | number         | Id người dùng                                              |
| `email`         | string         | Email đăng nhập (không đổi được ở đây)                     |
| `fullName`      | string         | Họ tên hiển thị                                            |
| `phone`         | string         | Số điện thoại dạng `0xxxxxxxxx` (không đổi được ở đây)     |
| `isAdmin`       | boolean        | `true` nếu là Admin                                        |
| `areaId`        | number \| null | Khu vực mặc định                                           |
| `avatarMediaId` | number \| null | Id ảnh đại diện                                            |
| `avatarUrl`     | string \| null | Đường dẫn tương đối `GET /api/v1/media/{id}`; FE ghép với địa chỉ backend (`resolveMediaUrl`) |

## Mã lỗi

| code                 | HTTP | Khi nào                                                                  |
|----------------------|------|--------------------------------------------------------------------------|
| `UNAUTHORIZED`       | 401  | Thiếu, sai hoặc hết hạn token (`CommonErrorCode`)                        |
| `VALIDATION_FAILED`  | 400  | Body thiếu trường hoặc sai định dạng, `field` = tên trường               |
| `USER_AREA_INVALID`  | 400  | `areaId` không tồn tại hoặc khu vực đã ngừng hoạt động, `field` = `areaId` |
| `AUTH_ACCOUNT_LOCKED`| 403  | Tài khoản đã bị khoá hoặc `BANNED` (`AuthErrorCode`), kể cả token cũ     |
| `USER_NOT_FOUND`     | 404  | Tài khoản trong token không còn tồn tại                                  |
| `MEDIA_NOT_FOUND`    | 404  | `avatarMediaId` không tồn tại (`MediaErrorCode`)                         |
| `MEDIA_NOT_OWNER`    | 403  | Ảnh không do người này upload                                            |
| `MEDIA_NOT_UPLOADED` | 409  | Ảnh đã được gắn ở chỗ khác hoặc không còn dùng được                       |

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
- `authApi.me(userId)` (đang dùng trong `session.ts`) vẫn chạy: server bỏ qua tham số `userId` và trả `MeResponse`,
  có đủ các trường cũ. Khi tiện có thể đổi sang `userApi.getMe()`.
- Màn hồ sơ hiện cho sửa email và số điện thoại: hai trường này phải chỉ đọc. Đổi số điện thoại là luồng riêng (FR-07).
