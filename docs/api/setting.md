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
  {
    "key": "post_fee",
    "value": "5",
    "defaultValue": "5",
    "label": "Phí đăng bài",
    "description": "Số Credit được giữ khi đăng bài và chỉ bị trừ khi Admin duyệt. Bài bị từ chối thì được hoàn lại.",
    "group": "FEES",
    "groupLabel": "Phí và nạp tiền"
  }
]
```

`value` và `defaultValue` là chuỗi để giữ nguyên biểu diễn được lưu trong `website_attributes.attr_value`.

`label`, `description`, `group` và `groupLabel` là dữ liệu mô tả lấy từ enum `ConfigKey` (không lưu trong DB), để trang Admin hiện tên tiếng Việt thay cho khoá kỹ thuật. `group` nhận một trong các mã sau:

| `group` | `groupLabel` | Gồm các khoá |
|---|---|---|
| `FEES` | Phí và nạp tiền | `vnd_per_credit`, `topup_min_credits`, `post_fee`, `edit_fee`, `renew_fee`, `boost_fee`, `ai_search_fee`, `payment_order_expire_minutes` |
| `POSTING` | Đăng bài và hiển thị | `post_display_days`, `renew_expired_window_days`, `boost_days`, `boost_max_days`, `item_photos_min`, `item_photos_max`, `image_max_mb`, `media_orphan_hours` |
| `AI` | AI trợ lý và sàng lọc | `ai_free_per_day`, `ai_trial_uses`, `ai_search_timeout_seconds`, `chat_ai_threshold`, `chat_ai_timeout_seconds`, `item_ai_screening_enabled` (`true`/`false`, mặc định `false` theo SRS v11), `item_ai_timeout_seconds`, `item_ai_retries` |
| `MODERATION` | Kiểm duyệt và báo cáo | `chat_violation_alert`, `review_sla_hours`, `report_hide_threshold` |
| `ACCOUNT` | Tài khoản và OTP | `otp_ttl_minutes`, `otp_max_failed`, `otp_resend_cooldown_seconds`, `pending_account_ttl_hours`, `accounts_per_ip_24h`, `phone_change_interval_days` |
| `TRANSACTION` | Yêu cầu và giao dịch | `max_pending_requests`, `request_pending_days`, `chat_open_days`, `auto_confirm_days`, `stuck_request_hours`, `complaint_window_days`, `response_window_days` |
| `REPUTATION` | Uy tín và hạng | `post_display_days_gold`, `max_active_items_*` (Trao đổi theo hạng: Đồng 5, Bạc 8, Vàng 15, Kim cương -1), `max_active_give_items` (Cho tặng, mặc định 5), `daily_points_cap`, `reputation_*` |

Trang Cấu hình hệ thống bỏ nhóm `REPUTATION`; nhóm này hiện ở trang Uy tín và Hạng.

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
- `400 SETTING_RANK_THRESHOLD_ORDER`: sửa `reputation_silver_min`, `reputation_gold_min` hoặc `reputation_diamond_min` làm điểm tối thiểu các hạng không còn tăng dần. Phải thoả Bạc < Vàng < Kim cương (BR-U01), so với giá trị đang lưu của hai khoá còn lại. Muốn nâng cả ba mốc thì sửa từ mốc cao nhất xuống mốc thấp nhất.
- `401 UNAUTHORIZED`: chưa đăng nhập.
- `403 FORBIDDEN`: tài khoản không phải Admin.
- `404 SETTING_CONFIG_NOT_FOUND`: key không tồn tại hoặc cấu hình chưa được seed.

Quy tắc `SETTING_RANK_THRESHOLD_ORDER` áp dụng cho cả `PUT /api/v1/admin/configs/{key}`.

## Admin lexicon

Các endpoint dưới đây chỉ dành cho Admin. Dữ liệu lưu trong `website_attributes` với `attr_group = BANNED_KEYWORD`; keyword nằm ở `attr_value`, còn `group` (category), `level` (severity), `matchMode`, `exceptions` và `scope` nằm trong JSONB `extra`. Danh sách GET gồm cả từ khóa đã ẩn.

### GET /api/v1/admin/lexicon

Trả danh sách từ khóa theo `sort_order`, gồm `id`, keyword, group, level, match mode, exceptions, scope, thứ tự và trạng thái active.

### POST /api/v1/admin/lexicon

Thêm từ khóa. `level` nhận `BLOCK` hoặc `REVIEW`; `matchMode` nhận `WORD`, `PHRASE` hoặc `REGEX` và mặc định là `WORD`; `scope` nhận `POST`, `CHAT`, `MESSAGE` hoặc `BOTH`. `exceptions` có thể là danh sách rỗng hoặc bỏ qua.

Với `REGEX`, `keyword` phải là biểu thức chính quy Java biên dịch được, nếu không sẽ bị từ chối ngay khi lưu. Biểu thức giữ nguyên hoa thường (`\D`, `\S`, `\W` giữ đúng nghĩa) và được khớp không phân biệt hoa thường.

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

- `400 VALIDATION_FAILED` hoặc `SETTING_LEXICON_INVALID_VALUE`: keyword rỗng, level/matchMode/scope không hợp lệ, hoặc `matchMode = REGEX` mà keyword không phải REGEX hợp lệ (áp dụng cả khi bật lại một mục cũ qua PATCH).
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

Các endpoint quản lý bộ điều kiện đạt checklist kiểm duyệt bài đăng (nhóm `CHECKLIST` trong `website_attributes`). Chỉ Admin được gọi. Dùng câu “đạt thì tích”: `true` nghĩa là bài đáp ứng điều kiện. API quản lý vẫn giữ tên trường `question`; API hàng chờ trả mục active dạng `{id, key, statement}` theo `sort_order`, rồi `id` (xem [Review API](review.md)).

### GET /api/v1/admin/checklist

Trả toàn bộ danh sách các câu hỏi checklist theo thứ tự `sort_order`, bao gồm cả các mục đã ẩn (`active: false`).

```json
[
  {
    "id": 1,
    "key": "ownership_transfer",
    "question": "Không cần sang tên, đăng ký hay công chứng để đổi chủ.",
    "sortOrder": 10,
    "active": true
  }
]
```

### POST /api/v1/admin/checklist

Thêm câu hỏi mới vào checklist. Tự động xếp vào cuối danh sách (`sort_order` lớn nhất + 10). Mặc định `active = true`.

```json
{
  "question": "Ảnh rõ nét, nhận dạng được món đồ."
}
```

Thành công trả `201 Created` cùng mục vừa tạo.

### PUT /api/v1/admin/checklist/{id}

Chỉnh sửa nội dung câu hỏi của một mục checklist.

```json
{
  "question": "Điều kiện đạt sau khi điều chỉnh."
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
