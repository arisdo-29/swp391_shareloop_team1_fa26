# API module setting

## GET /api/v1/admin/configs

Trả danh sách cấu hình nghiệp vụ cho Admin. Endpoint đọc giá trị đã cấu hình từ `website_attributes` qua `ConfigService`; nếu chưa có bản ghi active cho một `ConfigKey`, dùng giá trị mặc định khai báo trong `ConfigKey`.

- Xác thực: `Authorization: Bearer <accessToken>` với role `ADMIN`.
- Không có token: `401 UNAUTHORIZED`.
- Token user: `403 FORBIDDEN`.
- Thành công: `200`, danh sách DTO (không bọc envelope).
- Danh sách gồm mọi `ConfigKey`, kể cả khi database chưa seed cấu hình.

```json
[
  { "key": "vnd_per_credit", "value": "1000", "defaultValue": "1000" },
  { "key": "post_fee", "value": "5", "defaultValue": "5" }
]
```

`value` và `defaultValue` là chuỗi để giữ nguyên biểu diễn được lưu trong `website_attributes.attr_value`.

## PUT /api/v1/admin/configs/{key}

Cập nhật giá trị cấu hình đã được seed. Yêu cầu role `ADMIN` và body có giá trị mới cùng lý do.

```json
{ "value": "8", "reason": "Điều chỉnh mức phí theo chính sách mới" }
```

Thành công trả về `ConfigResponse` và ghi một dòng `activity_logs` loại `CONFIG_UPDATED`, gồm admin, key, giá trị cũ/mới và lý do. Key không tồn tại trong `ConfigKey` hoặc chưa được seed sẽ bị từ chối.

## PUT /api/v1/admin/configs

Cập nhật cấu hình bằng key trong request body. Chỉ Admin được gọi. Giá trị được kiểm tra theo kiểu và khoảng đã khai báo cho key; ví dụ `chat_ai_threshold` là số thập phân từ `0` đến `1`. Thay đổi được đọc từ database ngay ở lần gọi kế tiếp, không dùng cache.

```json
{
  "key": "chat_ai_threshold",
  "value": "0.8"
}
```

Có thể gửi thêm `reason` để ghi lý do vào `activity_logs`. Thành công trả `ConfigResponse` và ghi log `CONFIG_UPDATED`.

- `400 VALIDATION_FAILED`: thiếu key/value.
- `400 SETTING_CONFIG_INVALID_TYPE`: value sai kiểu theo key.
- `400 SETTING_CONFIG_OUT_OF_RANGE`: value ngoài khoảng cho phép.
- `401 UNAUTHORIZED`: chưa đăng nhập.
- `403 FORBIDDEN`: tài khoản không phải Admin.
- `404 SETTING_CONFIG_NOT_FOUND`: key không tồn tại hoặc cấu hình chưa được seed.
