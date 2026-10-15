# API module user

Quy ước chung theo Lộ trình mục 3.4: tiền tố `/api/v1`, thời gian ISO-8601 UTC, thành công trả thẳng DTO (không bọc),
lỗi theo định dạng thống nhất ở cuối file. Nguồn: SRS v10 (FR-06, BR-U03, UC-03).

Người sở hữu: BE1. File này gồm 2 endpoint hồ sơ của người đang đăng nhập: xem và cập nhật. Đổi số điện thoại
(FR-07, OTP email) và đổi mật khẩu không thuộc file này; đổi mật khẩu xem `docs/api/auth.md`.

## Quy tắc chung

- Cả 2 endpoint **bắt buộc** `Authorization: Bearer <accessToken>`. Id người dùng lấy từ token (claim `sub`),
  không nhận từ client, nên chỉ xem và sửa được hồ sơ của chính mình (BR-U03).
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
| `USER_NOT_FOUND`     | 404  | Tài khoản trong token không còn tồn tại                                  |
| `MEDIA_NOT_FOUND`    | 404  | `avatarMediaId` không tồn tại (`MediaErrorCode`)                         |
| `MEDIA_NOT_OWNER`    | 403  | Ảnh không do người này upload                                            |
| `MEDIA_NOT_UPLOADED` | 409  | Ảnh đã được gắn ở chỗ khác hoặc không còn dùng được                       |

Định dạng lỗi (`field` và `details` chỉ có khi liên quan):

```json
{ "code": "USER_AREA_INVALID", "message": "Khu vực không tồn tại hoặc đã ngừng hoạt động.", "field": "areaId", "traceId": "a1b2c3d4" }
```

## Ghi chú cho frontend

- Dùng `frontend/src/services/userApi.ts`: `userApi.getMe()`, `userApi.updateMe(input)`.
- `authApi.me(userId)` (đang dùng trong `session.ts`) vẫn chạy: server bỏ qua tham số `userId` và trả `MeResponse`,
  có đủ các trường cũ. Khi tiện có thể đổi sang `userApi.getMe()`.
- Màn hồ sơ hiện cho sửa email và số điện thoại: hai trường này phải chỉ đọc. Đổi số điện thoại là luồng riêng (FR-07).
