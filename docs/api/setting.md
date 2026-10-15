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

## Admin lexicon

Các endpoint dưới đây chỉ dành cho Admin. Dữ liệu lưu trong `website_attributes` với `attr_group = BANNED_KEYWORD`; keyword nằm ở `attr_value`, còn `group` (category), `level` (severity), `matchMode`, `exceptions` và `scope` nằm trong JSONB `extra`. Danh sách GET gồm cả từ khóa đã ẩn.

### GET /api/v1/admin/lexicon

Trả danh sách từ khóa theo `sort_order`, gồm `id`, keyword, group, level, match mode, exceptions, scope, thứ tự và trạng thái active.

### POST /api/v1/admin/lexicon

Thêm từ khóa. `level` nhận `BLOCK` hoặc `REVIEW`; `matchMode` nhận `WORD`, `PHRASE` hoặc `REGEX` và mặc định là `WORD`; `scope` nhận `POST`, `CHAT`, `MESSAGE` hoặc `BOTH`. `exceptions` có thể là danh sách rỗng hoặc bỏ qua.

```json
{
  "keyword": "súng nước",
  "group": "WEAPONS",
  "level": "REVIEW",
  "matchMode": "PHRASE",
  "exceptions": [],
  "scope": "POST"
}
```

Thành công trả `201 Created` cùng mục vừa tạo.

### PUT /api/v1/admin/lexicon/{id}

Thay nội dung và metadata của mục theo cùng body như POST. Thành công trả mục đã cập nhật.

### PATCH /api/v1/admin/lexicon/{id}/active

Ẩn hoặc hiện mục bằng cách cập nhật `is_active`, không xóa cứng.

```json
{ "active": false }
```

- `400 VALIDATION_FAILED` hoặc `SETTING_LEXICON_INVALID_VALUE`: keyword rỗng, level/matchMode/scope không hợp lệ.
- `403 FORBIDDEN`: người gọi không phải Admin.
- `404 SETTING_LEXICON_NOT_FOUND`: không tìm thấy mục.
- `409 SETTING_LEXICON_DUPLICATE`: keyword đã có trong cùng group và scope (không phân biệt hoa thường và khoảng trắng liên tiếp).
### POST /api/v1/admin/lexicon/test

Thử các keyword đang active trên một đoạn văn. Endpoint áp dụng ngoại lệ; match nguyên văn trả level cấu hình, còn match chỉ có sau khi bỏ dấu trả `REVIEW` với `matchedAs = NORMALIZED`.

```json
{ "text": "Nội dung cần thử" }
```

Response là danh sách các match:

```json
[
  { "keyword": "bán", "level": "REVIEW", "group": "WEAPONS", "matchedAs": "NORMALIZED" }
]
```

- `400 VALIDATION_FAILED`: text rỗng.
- `403 FORBIDDEN`: người gọi không phải Admin.

## Admin review checklist (#37)

Các endpoint quản lý bộ câu hỏi checklist kiểm duyệt bài đăng (nhóm `CHECKLIST` trong `website_attributes`). Chỉ Admin được gọi.

### GET /api/v1/admin/checklist

Trả toàn bộ danh sách các câu hỏi checklist theo thứ tự `sort_order`, bao gồm cả các mục đã ẩn (`active: false`).

```json
[
  {
    "id": 1,
    "key": "ownership_transfer",
    "question": "Món đồ có cần sang tên, đăng ký, công chứng để đổi chủ không?",
    "sortOrder": 10,
    "active": true
  }
]
```

### POST /api/v1/admin/checklist

Thêm câu hỏi mới vào checklist. Tự động xếp vào cuối danh sách (`sort_order` lớn nhất + 10). Mặc định `active = true`.

```json
{
  "question": "Ảnh có bị mờ hoặc không nhận dạng được món đồ không?"
}
```

Thành công trả `201 Created` cùng mục vừa tạo.

### PUT /api/v1/admin/checklist/{id}

Chỉnh sửa nội dung câu hỏi của một mục checklist.

```json
{
  "question": "Nội dung câu hỏi sau khi điều chỉnh?"
}
```

Thành công trả `200 OK` cùng mục đã cập nhật.

### PATCH /api/v1/admin/checklist/{id}/active

Ẩn hoặc hiện mục checklist bằng cách cập nhật `is_active`, không xóa cứng.

```json
{ "active": false }
```

> [!IMPORTANT]
> **BR-A06**: Hệ thống bắt buộc luôn có ít nhất 1 mục checklist đang dùng (`active = true`). Nếu thao tác ẩn mục cuối cùng đang active, hệ thống sẽ từ chối và trả về `400 SETTING_CHECKLIST_AT_LEAST_ONE_REQUIRED`.

### PUT /api/v1/admin/checklist/reorder

Sắp xếp lại thứ tự toàn bộ câu hỏi checklist. Body là danh sách toàn bộ ID theo thứ tự mong muốn mới.

```json
{
  "itemIds": [3, 1, 2, 4]
}
```

Thành công trả về danh sách toàn bộ mục sau khi đã cập nhật lại `sort_order` theo bước nhảy 10, 20, 30...

### Mã lỗi
- `400 VALIDATION_FAILED`: body rỗng hoặc dữ liệu không hợp lệ.
- `400 SETTING_CHECKLIST_AT_LEAST_ONE_REQUIRED`: vi phạm BR-A06 khi cố ẩn mục active duy nhất.
- `403 FORBIDDEN`: tài khoản không phải Admin.
- `404 SETTING_CHECKLIST_NOT_FOUND`: id câu hỏi không tồn tại.
