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
| `FEES` | Phí và nạp tiền | `vnd_per_credit`, `topup_min_credits`, `post_fee`, `post_fee_over_quota`, `edit_fee`, `renew_fee`, `boost_fee`, `ai_search_fee`, `payment_order_expire_minutes` |
| `POSTING` | Đăng bài và hiển thị | `post_display_days`, `boost_days`, `boost_max_days` (số ngày đẩy liên tục tối đa trong một đợt, mặc định 6), `boost_cooldown_days`, `boost_max_per_user`, `boost_max_active`, `boost_rotate_seconds`, `boost_max_per_page` (bật lại ở V202611061200), `boost_ending_notice_hours`, `item_photos_min`, `item_photos_max`, `image_max_mb` (`renew_expired_window_days`, `media_orphan_hours` đã xoá mềm ở L13) |
| `AI` | AI tìm đồ và gợi ý mô tả | `ai_free_per_day`, `ai_trial_uses`, `ai_search_timeout_seconds`, `ai_describe_per_day`, `ai_describe_timeout_seconds` (`chat_ai_*` và cấu hình AI sàng lọc bài cũ đã bỏ) |
| `MODERATION` | Kiểm duyệt và báo cáo | `chat_violation_alert`, `report_hide_threshold`, `report_max_items_per_owner`, `report_window_days`, `review_newcomer_max_approved` (số bài đã từng được duyệt tối đa để người đăng được xem là người mới, mặc định 3), `report_serious_reasons` (kiểu danh sách mã lý do cách nhau dấu phẩy, mỗi mã phải là một `ReportReason`, sai thì `SETTING_CONFIG_INVALID_TYPE`) (`review_sla_hours` đã bỏ, KD-06) |
| `ACCOUNT` | Tài khoản và OTP | `otp_ttl_minutes`, `otp_max_failed`, `otp_resend_cooldown_seconds`, `pending_account_ttl_hours`, `phone_change_interval_days` |
| `TRANSACTION` | Yêu cầu và giao dịch | `max_pending_requests`, `request_pending_days`, `chat_open_days`, `auto_confirm_days`, `stuck_request_hours` |
| `REPUTATION` | Hạng và Uy tín | `post_display_days_gold`, `max_active_items_*` (ngưỡng phí thường Trao đổi: Đồng 5, Bạc 8, Vàng 15, Kim cương -1), `max_active_give_items` (Cho tặng, mặc định 5), `daily_points_cap`, `reputation_*`, `ip_alert_window_days`, `ip_alert_min_accounts` |

Trang Cấu hình hệ thống bỏ nhóm `REPUTATION`; nhóm này hiện ở trang Uy tín và Hạng.

`ip_alert_min_accounts` mặc định là 5, tối thiểu 2. `ip_alert_window_days` mặc định là 30. Hai giá trị chỉ dùng để đưa các nhóm tài khoản đã xác thực cùng IP lên trang Cảnh báo.

Hai khoá cố định `complaint_window_hours` (24) và `response_window_hours` (48) (U08) không có trong `GET /admin/configs`;
`PUT` vào hai khoá này trả `400 SETTING_CONFIG_NOT_EDITABLE`. Backend vẫn đọc chúng qua `ConfigService`, chỉ đổi bằng migration.

## PUT /api/v1/admin/configs/{key}

Cập nhật giá trị cấu hình đã được seed. Yêu cầu role `ADMIN` và body có giá trị mới cùng lý do.

```json
{ "value": "8", "reason": "Điều chỉnh mức phí theo chính sách mới" }
```

Thành công trả về `ConfigResponse` và ghi một dòng `activity_logs` loại `CONFIG_UPDATED`, gồm admin, key, giá trị cũ/mới và lý do. Key không tồn tại trong `ConfigKey` hoặc chưa được seed sẽ bị từ chối.

**Đi qua bộ luật liên khoá.** Cả `PUT /{key}` và `PUT /` đều chạy bộ luật (xem mục bộ luật bên dưới). Có lỗi luật thì `400` như thường. Nếu thay đổi **kéo theo cấu hình khác** (đồng bộ) hoặc **có cảnh báo** thì **không lưu gì** và trả `409 SETTING_CONFIG_SYNC_REQUIRED`, kèm bản xem trước trong `details.preview` (cùng dạng với response của `POST /preview`). Giao diện dùng bản xem trước này rồi gọi `PUT /batch`. Không kéo theo gì và không có cảnh báo thì lưu như cũ.

```json
{
  "code": "SETTING_CONFIG_SYNC_REQUIRED",
  "message": "Thay đổi này kéo theo cấu hình liên quan hoặc có cảnh báo, hãy xem trước rồi lưu theo lô.",
  "details": {
    "preview": {
      "changes": [
        { "key": "boost_days", "label": "Số ngày mỗi gói đẩy bài", "oldValue": "3", "newValue": "4", "causedByKey": null, "causedByLabel": null, "note": null },
        { "key": "boost_max_days", "label": "Số ngày đẩy tối đa", "oldValue": "6", "newValue": "8", "causedByKey": "boost_days", "causedByLabel": "Số ngày mỗi gói đẩy bài", "note": "Đồng bộ theo Số ngày mỗi gói đẩy bài" }
      ],
      "errors": [],
      "warnings": []
    }
  }
}
```

## POST /api/v1/admin/configs/preview

Xem trước một thay đổi, **không ghi gì vào cơ sở dữ liệu** và không khoá dòng. Chạy bộ luật trên giá trị đang lưu rồi trả các cấu hình sẽ đổi kèm, lỗi và cảnh báo. Chỉ Admin được gọi.

```json
{ "key": "boost_days", "value": "4" }
```

Thành công `200` (kể cả khi `errors` không rỗng: lỗi luật là một phần của kết quả xem trước):

```json
{
  "changes": [
    { "key": "boost_days", "label": "Số ngày mỗi gói đẩy bài", "oldValue": "3", "newValue": "4", "causedByKey": null, "causedByLabel": null, "note": null },
    { "key": "boost_max_days", "label": "Số ngày đẩy tối đa", "oldValue": "6", "newValue": "8", "causedByKey": "boost_days", "causedByLabel": "Số ngày mỗi gói đẩy bài", "note": "Đồng bộ theo Số ngày mỗi gói đẩy bài" }
  ],
  "errors": [],
  "warnings": []
}
```

- `changes`: khoá Admin gửi đứng trước, khoá đồng bộ đứng sau. Khoá đồng bộ có `causedByKey`, `causedByLabel` và `note`.
- `errors[]` và `warnings[]`: mỗi mục có `message` (tiếng Việt, dùng tên hiển thị của khoá), `keys` (các khoá liên quan để tô đúng ô) và `errorCode` (`null` với cảnh báo).
- Ví dụ lỗi khi `boost_days` = 20 (kéo `boost_max_days` thành 40, vượt 30 ngày hiển thị bài):

```json
{
  "changes": [ "... boost_days 3 → 20, boost_max_days 6 → 40 ..." ],
  "errors": [
    {
      "message": "Số ngày đẩy tối đa (40) không được lớn hơn Số ngày hiển thị bài (30), vì đợt đẩy không thể dài hơn thời gian bài được hiển thị.",
      "keys": ["boost_max_days", "post_display_days"],
      "errorCode": "SETTING_CONFIG_RULE_VIOLATION"
    }
  ],
  "warnings": []
}
```

Lỗi: `400 VALIDATION_FAILED` (thiếu key hoặc value), `400 SETTING_CONFIG_INVALID_TYPE`, `400 SETTING_CONFIG_OUT_OF_RANGE`, `400 SETTING_CONFIG_NOT_EDITABLE`, `401`, `403`, `404 SETTING_CONFIG_NOT_FOUND`.

## PUT /api/v1/admin/configs/batch

Lưu nhiều cấu hình trong **một transaction**: hoặc lưu hết hoặc không lưu gì. Chỉ Admin được gọi.

```json
{
  "changes": [{ "key": "boost_days", "value": "4" }],
  "reason": "Đổi gói đẩy bài sang 4 ngày",
  "acknowledgedWarnings": false
}
```

- `changes` không được rỗng, mỗi `key` chỉ xuất hiện một lần (gửi trùng thì `400 SETTING_CONFIG_RULE_VIOLATION`). Chỉ cần gửi khoá chính, cấu hình đồng bộ do server tự tính lại và lưu kèm. Khoá có trong `changes` thì server **không ghi đè**, ví dụ gửi cả `boost_days` = 4 và `boost_max_days` = 12 thì giữ 12 rồi để luật lỗi tự kiểm tra.
- `reason` bắt buộc, dùng chung cho mọi dòng nhật ký.
- `acknowledgedWarnings`: `true` khi Admin đã tick "Tôi đã hiểu cảnh báo". Thiếu thì coi là `false`.
- Server khoá các dòng cấu hình (`SELECT ... FOR UPDATE`, theo thứ tự `attr_key`) rồi chạy lại bộ luật trên toàn bộ `changes`, nên hai lô gửi cùng lúc sẽ chạy lần lượt, không chạy song song.
- Thay đổi chỉ áp dụng cho lượt mua hoặc duyệt mới, không sửa ngược đợt đẩy hay bài đang chạy.

Thành công `200`: danh sách mọi cấu hình đã đổi, kể cả cấu hình đồng bộ.

```json
{
  "configs": [
    { "key": "boost_days", "value": "4", "defaultValue": "3", "label": "Số ngày mỗi gói đẩy bài", "description": "…", "group": "POSTING", "groupLabel": "Đăng bài và hiển thị" },
    { "key": "boost_max_days", "value": "8", "defaultValue": "6", "label": "Số ngày đẩy tối đa", "description": "…", "group": "POSTING", "groupLabel": "Đăng bài và hiển thị" }
  ]
}
```

Mỗi khoá đã đổi ghi một dòng `activity_logs` loại `CONFIG_UPDATE`, cùng `reason`, `data` gồm `key`, `oldValue`, `newValue`. Khoá đồng bộ ghi thêm `data.syncedFrom` (key nguồn) và `data.note` (`"Đồng bộ theo <tên nguồn>"`). Trang nhật ký hiện `note` trong ngoặc sau thay đổi, log cũ không có `note` thì hiện như trước.

Lỗi:

- `400 VALIDATION_FAILED`: `changes` rỗng hoặc thiếu `reason`.
- `400 SETTING_CONFIG_INVALID_TYPE`, `400 SETTING_CONFIG_OUT_OF_RANGE`, `400 SETTING_CONFIG_NOT_EDITABLE`: giá trị của khoá Admin gửi không hợp lệ.
- `400 SETTING_CONFIG_RULE_VIOLATION`, `400 SETTING_RANK_THRESHOLD_ORDER`, `400 SETTING_CONFIG_SYNC_CONFLICT`: lỗi bộ luật. `message` là lỗi đầu tiên, `details.errors[]` là mọi lỗi (cùng dạng `errors` của preview). Giá trị đồng bộ ra ngoài khoảng cho phép của khoá đích trả `400 SETTING_CONFIG_OUT_OF_RANGE` kèm `details.errors[]`.
- `404 SETTING_CONFIG_NOT_FOUND`: key không tồn tại hoặc chưa được seed.
- `409 SETTING_CONFIG_WARNING_NOT_ACKNOWLEDGED`: có cảnh báo mà `acknowledgedWarnings` là `false`. Không lưu gì, `details.preview` chứa kết quả xem trước để hiện cảnh báo.
- `401`, `403` như các endpoint khác.

## Bộ luật liên khoá của cấu hình

Chạy ở `POST /preview`, `PUT /batch`, `PUT /{key}` và `PUT /`. Giá trị mặc định luôn đọc từ cấu hình đang lưu, không viết cứng. Luật lỗi và cảnh báo chỉ báo khi một khoá của luật vừa bị đổi.

**Đồng bộ** (tính từ giá trị đang lưu, không ghi đè khoá Admin đã gửi, tối đa 5 vòng):

| Khi đổi | Tự đổi theo |
|---|---|
| `boost_days` | `boost_max_days` = số gói cũ × số ngày mới (số gói = làm tròn `boost_max_days` cũ chia `boost_days` cũ, tối thiểu 1). Ví dụ 3 → 4 thì 6 → 8 |
| `post_fee` | `edit_fee` và `renew_fee` nếu đang bằng `post_fee` cũ. `post_fee_over_quota` giữ tỉ lệ, làm tròn lên. Ví dụ 5 → 7 thì 10 → 14 |
| `post_display_days` | `post_display_days_gold` giữ khoảng chênh với mức thường, không nhỏ hơn mức thường mới |

Giá trị đồng bộ ra ngoài khoảng cho phép của khoá đích, hoặc khoá đích là khoá cố định, thì không tự sửa mà trả lỗi nêu rõ khoá và giá trị đề xuất.

**Lỗi** (chặn lưu):

- `item_photos_min` không lớn hơn `item_photos_max`.
- `boost_days` ≤ `boost_max_days` ≤ `post_display_days`.
- `boost_max_days` phải là bội số của `boost_days`, kèm gợi ý số gần nhất ("Gợi ý: 6 hoặc 9").
- `boost_max_per_page` ≤ `boost_max_active` và `boost_max_per_user` ≤ `boost_max_active`.
- `max_active_items_bronze` ≤ `silver` ≤ `gold` ≤ `diamond`. `-1` là không giới hạn, chỉ hợp lệ khi mọi hạng cao hơn cũng là `-1`.
- `reputation_silver_min` < `reputation_gold_min` < `reputation_diamond_min` (`SETTING_RANK_THRESHOLD_ORDER`, giữ nguyên câu chữ cũ).

**Cảnh báo** (phải xác nhận):

- `boost_ending_notice_hours` ≥ `boost_days` × 24 giờ.
- `post_display_days_gold` < `post_display_days`.
- Một khoá phí (tên kết thúc bằng `_fee`) vừa được đặt bằng 0.

### Mã lỗi mới

| Mã | HTTP | Ý nghĩa |
|---|---|---|
| `SETTING_CONFIG_RULE_VIOLATION` | 400 | Giá trị mâu thuẫn với cấu hình liên quan, hoặc gửi trùng khoá trong lô |
| `SETTING_CONFIG_SYNC_CONFLICT` | 400 | Hai luật đồng bộ đòi hai giá trị khác nhau cho một khoá, hoặc đồng bộ quá 5 vòng |
| `SETTING_CONFIG_SYNC_REQUIRED` | 409 | `PUT` đơn kéo theo cấu hình khác hoặc có cảnh báo, kèm `details.preview` |
| `SETTING_CONFIG_WARNING_NOT_ACKNOWLEDGED` | 409 | `PUT /batch` có cảnh báo mà chưa xác nhận |

## PUT /api/v1/admin/configs

Cập nhật cấu hình bằng key trong request body. Chỉ Admin được gọi. Giá trị được kiểm tra theo kiểu và khoảng đã khai báo cho key; ví dụ `post_fee` là số nguyên không âm. Thay đổi được đọc từ database ngay ở lần gọi kế tiếp, không dùng cache.

```json
{
  "key": "post_fee",
  "value": "10"
}
```

Có thể gửi thêm `reason` để ghi lý do vào `activity_logs`. Thành công trả `ConfigResponse` và ghi log `CONFIG_UPDATED`.

- `400 VALIDATION_FAILED`: thiếu key/value.
- `400 SETTING_CONFIG_INVALID_TYPE`: value sai kiểu theo key.
- `400 SETTING_CONFIG_OUT_OF_RANGE`: value ngoài khoảng cho phép.
- `400 SETTING_RANK_THRESHOLD_ORDER`: sửa `reputation_silver_min`, `reputation_gold_min` hoặc `reputation_diamond_min` làm điểm tối thiểu các hạng không còn tăng dần. Phải thoả Bạc < Vàng < Kim cương (UT-06), so với giá trị đang lưu của hai khoá còn lại. Muốn nâng cả ba mốc thì sửa từ mốc cao nhất xuống mốc thấp nhất.
- `409 SETTING_CONFIG_SYNC_REQUIRED`: thay đổi kéo theo cấu hình khác hoặc có cảnh báo, chưa lưu gì (xem `PUT /{key}`).
- `401 UNAUTHORIZED`: chưa đăng nhập.
- `403 FORBIDDEN`: tài khoản không phải Admin.
- `404 SETTING_CONFIG_NOT_FOUND`: key không tồn tại hoặc cấu hình chưa được seed.

Quy tắc `SETTING_RANK_THRESHOLD_ORDER` và các luật liên khoá khác áp dụng cho cả `PUT /api/v1/admin/configs/{key}`.

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

Các endpoint quản lý bộ điều kiện đạt checklist kiểm duyệt bài đăng (nhóm `CHECKLIST` trong `website_attributes`). Chỉ Admin được gọi. Dùng câu “đạt thì tích”: `true` nghĩa là bài đáp ứng điều kiện. API quản lý vẫn giữ tên trường `question`; API hàng chờ trả mục active dạng `{id, key, statement, description}` theo `sort_order`, rồi `id` (xem [Review API](review.md)).

**Mô tả điều kiện đạt (Q01):** mỗi mục có thêm `description` dạng `Đạt khi ... Từ chối nếu ...`, lưu ở `extra.description` của chính dòng `CHECKLIST`, không thêm bảng hay cột. Trang Duyệt bài hiện mô tả ngay dưới tên mục. `GET` trả `description` (`null` khi chưa có). `POST`/`PUT` nhận `description` không bắt buộc, tối đa 1000 ký tự: bỏ trường thì giữ mô tả cũ, chuỗi rỗng thì xoá mô tả. Migration `V202610291200` đổi 10 câu seed theo bảng của Tân (chỉ khi câu chưa bị Admin sửa) và thêm mô tả cho mục chưa có.

**Bỏ thực phẩm (M6):** migration `V202611011600__setting_retire_food_safety_checklist` xoá mềm mục `food_safety`, checklist seed còn 9 câu. Các từ khoá cấm nhóm `FOOD_BEVERAGE` (`V202610241200`) vẫn giữ để chặn tại form.

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
> **KD-04**: Hệ thống bắt buộc luôn có ít nhất 1 mục checklist đang dùng (`active = true`). Nếu thao tác ẩn mục cuối cùng đang active, hệ thống sẽ từ chối và trả về `400 SETTING_CHECKLIST_AT_LEAST_ONE_REQUIRED`.

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
- `400 SETTING_CHECKLIST_AT_LEAST_ONE_REQUIRED`: vi phạm KD-04 khi cố ẩn mục active duy nhất.
- `403 FORBIDDEN`: tài khoản không phải Admin.
- `404 SETTING_CHECKLIST_NOT_FOUND`: id câu hỏi không tồn tại.

## GET /api/v1/configs/public (G1)

Công khai, không cần đăng nhập. Trả map khoá cấu hình sang số nguyên cho một danh sách khoá chọn lọc (`PublicConfigController.PUBLIC_KEYS`): hạn mức yêu cầu và giao dịch, số ngày chờ, cửa sổ khiếu nại và giải trình, ngưỡng báo cáo, điểm và sao, lượt AI miễn phí, các giới hạn đẩy bài, số ngày hiển thị bài. Không trả khoá nội bộ (phí, từ khoá cấm, lý do nghiêm trọng...). Đây là đường duy nhất được thêm vào danh sách công khai của `SecurityConfig`, các đường `/api/v1/configs/**` khác vẫn cần đăng nhập. Frontend dùng cho trang Giới thiệu và các câu có số ngày, số giờ, có giá trị dự phòng khi gọi lỗi.

Câu lỗi nghiệp vụ có số (yêu cầu chờ tối đa, hạn khiếu nại, hạn giải trình, đổi số điện thoại, huỷ tự động) được dựng từ cấu hình lúc ném lỗi. Câu mặc định của `ErrorCode` không còn ghi số cứng.

## Khoá cấu hình mới của đợt 3 (23/11/2026)

Admin xem và sửa ở trang Cấu hình (nhóm Giao dịch), mô tả tiếng Việt lấy từ `ConfigKey`. Các khoá người dùng cần thấy số cũng nằm trong `GET /api/v1/configs/public`.

| Khoá | Mặc định | Công khai | Ý nghĩa |
|---|---|---|---|
| `cancel_request_response_hours` | 24 | có | Số giờ bên kia được phản hồi đề nghị huỷ hoặc đề nghị dời lịch |
| `cancel_after_contact_alert_count` | 3 | không | Số lần đề nghị huỷ sau khi thấy liên hệ thì hiện ở màn Giám sát bất thường |
| `cancel_after_contact_window_days` | 30 | không | Số ngày theo dõi cho cảnh báo trên |
| `meeting_max_days_ahead` | 7 | có | Giờ hẹn hoặc ngày gửi hàng dự kiến xa nhất, tính từ lúc đề xuất |
| `reschedule_max_count` | 2 | có | Số lần dời lịch tối đa mỗi giao dịch, chỉ tính lần được đồng ý |
| `confirmed_stale_cancel_days` | 3 | có | Giao dịch đã chốt lịch qua giờ hẹn (hoặc hạn gửi) quá số ngày này mà không ai làm gì thì tự huỷ |
| `shipping_send_deadline_days` | 3 | có | Hạn gửi của mỗi chiều = ngày gửi dự kiến cộng số ngày này |
| `deadline_reminder_hours_before_response` | 6 | có | Nhắc bên được hỏi khi còn số giờ này trước hạn phản hồi |

`auto_confirm_days_shipping` (7) tính từ lúc gửi với Cho tặng. Với Trao đổi, tính từ lúc đủ hai lượt gửi và lượt xác nhận nhận đầu tiên. Cột mới ở bảng `requests` (ERD vẫn 17 bảng): `cancel_requested_by`, `cancel_requested_at`, `cancel_request_code`, `cancel_request_reason`, `reschedule_proposed_by`, `reschedule_proposed_at`, `reschedule_meeting_time`, `reschedule_meeting_place`, `reschedule_count`, `receiver_address`, `donor_address`, `donor_shipped_at`, `receiver_shipped_at`. Hai cột mã vận đơn `donor_tracking_code` và `receiver_tracking_code` chỉ lưu lịch sử.

## Cấu hình Trao đổi qua giao hàng

Các khóa dưới đây được trả trong cấu hình công khai. Khung làm việc được lưu dưới dạng số phút tính từ 00:00 theo giờ Việt Nam.

| Khóa | Mặc định | Ý nghĩa |
|---|---|---|
| `exchange_shipping_window_minutes` | 30 | Số phút được xác nhận gửi trước giờ hẹn (Admin chỉnh được từ 1 đến 120) |
| `exchange_shipping_lead_minutes` | 60 | Số phút tối thiểu giữa lúc lưu hoặc chốt lịch với giờ hẹn (Admin chỉnh được từ 1 đến 1440, đặt 1 khi demo) |
| `exchange_shipping_morning_start` | 480 | Bắt đầu khung làm việc buổi sáng lúc 08:00 |
| `exchange_shipping_morning_end` | 720 | Kết thúc khung làm việc buổi sáng lúc 12:00 |
| `exchange_shipping_afternoon_start` | 780 | Bắt đầu khung làm việc buổi chiều lúc 13:00 |
| `exchange_shipping_afternoon_end` | 1020 | Kết thúc khung làm việc buổi chiều lúc 17:00 |
| `exchange_shipping_reopen_hours` | 24 | Số giờ tính từ lúc hủy hoặc bác khiếu nại cho đến khi bài được mở lại |
