# API module item

## Service cửa ngõ nội bộ

- `ItemService.reserve(Long itemId)` chuyển `APPROVED → RESERVED`; `release(long itemId)` chuyển `RESERVED → APPROVED`. Trạng thái khác trả `ITEM_INVALID_STATUS_TRANSITION`.
- `markTraded` và `markExpired` chuyển trạng thái qua `canTransition()`; `hide/unhide/hideAllOfUser` chỉ đổi `is_active`.
- `remove(itemId, userId)` phục vụ DELETE của chủ bài; `remove(itemId)` là cửa ngõ nội bộ. Các hàm vòng đời khác chưa có endpoint HTTP riêng.

Quy ước chung theo Lộ trình mục 3.4: tiền tố `/api/v1`, thời gian ISO-8601 UTC, thành công trả thẳng DTO (không bọc),
lỗi theo định dạng thống nhất bên dưới.

POST /items đã tích hợp kiểm duyệt tầng 1, giữ phí đăng bài và gắn ảnh trong một transaction.

## AI sàng lọc sau khi gửi bài

> **SRS v11: tầng này đang tắt.** Cấu hình `item_ai_screening_enabled` mặc định `false`: đăng hoặc sửa bài không gọi LLM, không ghi `AiUsage`, `ai_risk` giữ `UNCHECKED` và bài vào thẳng hàng chờ duyệt. Code vẫn giữ; Admin đặt cấu hình này thành `true` thì các mục dưới đây áp dụng lại.

- Sau khi transaction tạo hoặc sửa bài commit thành công, luồng nền gửi nội dung bài đến OpenRouter. Chỉ gửi tiêu đề, mô tả, món mong muốn, danh mục, tình trạng và loại bài; không gửi ảnh hay dữ liệu tài khoản người đăng.
- Tầng 2 ghi `items.ai_risk` (`LOW`, `MEDIUM`, `HIGH`) và `items.ai_result` đã kiểm tra schema, gồm kết quả, model và thời điểm kiểm. Kết quả nghi vấn có cờ `REVIEW` và lý do cho hàng chờ duyệt. Tầng 2 không đổi trạng thái hay phí bài đăng, và giữ nguyên lý do `REVIEW` của tầng 1 nếu đã có.
- Khi nhà cung cấp lỗi, quá hạn hoặc trả JSON sai, `ai_risk` vẫn là `UNCHECKED` (giao diện hiện "AI chưa kiểm"); bài vẫn ở hàng chờ duyệt. Mỗi lượt gọi đã thử được ghi `AiUsage` với lần thử, kết quả, thời lượng và model khi có, không ghi nội dung bài hay API key.
- Model qua OpenRouter API: `cohere/north-mini-code:free`, trả kết quả qua lời gọi hàm có schema. Đặt OpenRouter API key vào `OPENROUTER_API_KEY` trong môi trường chạy của profile production, không đưa vào Git. `item_ai_timeout_seconds` mặc định 15 giây tổng cộng cho tối đa một lần thử lại; có thể chỉnh `item_ai_retries` bằng cấu hình admin. Giới hạn lượt của model miễn phí có thể khiến sàng lọc AI không khả dụng; khi đó hệ thống giữ nhánh dự phòng hiện có.

---

## 1. POST /api/v1/items

Đăng bài viết mới cho tặng (GIVE) hoặc trao đổi (SWAP) (UC-20, F07 bước 1).

- **Xác thực:** Bắt buộc Bearer Token (JWT).
- **Request body:**

```json
{
  "categoryId": 1,
  "areaId": 1,
  "offerType": "GIVE",
  "title": "Giáo trình Toán rời rạc",
  "description": "Sách còn mới 95%, tặng cho bạn nào cần ôn thi",
  "desiredItem": null,
  "condition": "LIKE_NEW",
  "defectNote": null,
  "brand": "NXB Giáo Dục",
  "images": ["/api/v1/media/42", "/api/v1/media/43", "/api/v1/media/44"]
}
```

*Lưu ý nghiệp vụ:*
- Nếu `offerType` là `SWAP`, bắt buộc trường `desiredItem` không được rỗng.
- Nếu `condition` là `DEFECTIVE`, bắt buộc trường `defectNote` không được rỗng.
- Nếu `condition` là `DEFECTIVE`, `defectImages` phải có ít nhất một URL ảnh lỗi đã upload của chủ bài; ảnh này lưu với role `DEFECT`, tách khỏi `images` (3–8 ảnh chính).
- Danh mục điện tử bắt buộc `operatingStatus`: `NORMAL`, `PARTIAL` hoặc `NOT_WORKING`; giá trị lưu trong `item_attribute_mappings`.
- `images` bắt buộc có 3–8 URL ảnh đã upload của chính người đăng (giới hạn từ `ConfigKey.ITEM_PHOTOS_MIN/MAX`); thứ tự gửi là thứ tự hiển thị.
- Bài đăng mới tạo có trạng thái mặc định là `PENDING_REVIEW`.
- Bài Cho tặng có hạn mức riêng `max_active_give_items` (mặc định 5). Bài Trao đổi không bị chặn theo số lượng; `max_active_items_*` là ngưỡng phí thường theo hạng: Đồng 5, Bạc 8, Vàng 15, Kim cương không giới hạn. Chỉ đếm bài `PENDING_REVIEW` hoặc `APPROVED` đang hoạt động và chưa hết hạn.
- Kiểm duyệt tầng 1 áp dụng cho tiêu đề, mô tả và món mong muốn: `BLOCK` từ chối; `REVIEW` vẫn tạo bài và gắn cờ trong hàng chờ Admin.
- Khi tạo thành công, gọi `PostFeeService.holdPostFee`. Bài `SWAP` trong ngưỡng theo hạng giữ `post_fee` (mặc định 5 Credit); bài vượt ngưỡng giữ `post_fee_over_quota` (mặc định 10 Credit) và vẫn đăng được. Phí chỉ bị trừ lúc Admin duyệt. Bài `GIVE` miễn phí đăng: tài khoản 0 Credit vẫn đăng được, bài vẫn ở `fee_state = HELD` với `pending_fee = 0`. Lỗi ở bất kỳ bước nào sẽ rollback bài, phí giữ và trạng thái ảnh.

- **Thành công:** `201 Created`, trả về `ItemResponse`.

```json
{
  "id": 1,
  "donorId": 2,
  "categoryId": 1,
  "areaId": 1,
  "offerType": "GIVE",
  "title": "Giáo trình Toán rời rạc",
  "description": "Sách còn mới 95%, tặng cho bạn nào cần ôn thi",
  "desiredItem": null,
  "condition": "LIKE_NEW",
  "defectNote": null,
  "brand": "NXB Giáo Dục",
  "images": ["/api/v1/media/42", "/api/v1/media/43", "/api/v1/media/44"],
  "status": "PENDING_REVIEW",
  "createdAt": "2026-10-05T14:00:00Z",
  "updatedAt": "2026-10-05T14:00:00Z",
  "expireAt": null,
  "boostedUntil": null
}
```

- **Lỗi:**
  - `403 ITEM_REPUTATION_BLOCKED`: Người đăng có 0 sao uy tín (FR-92, BR-U05). Được kiểm tra đầu tiên, trước hạn mức bài, kiểm duyệt và giữ phí; không tạo bài, không giữ phí.
  - `400 VALIDATION_FAILED`: Dữ liệu đầu vào không hợp lệ (thiếu trường bắt buộc, vượt độ dài).
  - `400 ITEM_SWAP_DESIRED_ITEM_REQUIRED`: Đăng bài SWAP nhưng thiếu món mong muốn.
  - `400 ITEM_DEFECT_NOTE_REQUIRED`: Đồ DEFECTIVE nhưng thiếu mô tả lỗi.
  - `400 ITEM_INVALID_IMAGE`: `images[]` chứa URL không phải URL trả về từ `POST /api/v1/media`.
  - `422 ITEM_INVALID_IMAGE_COUNT` (`field: images`): Có ít hơn 3 hoặc nhiều hơn 8 ảnh.
  - `409 ITEM_GIVE_LIMIT_REACHED` (`details.limit`, `details.activeCount`): Bài Cho tặng (`GIVE`) đã đạt hạn mức chung mọi hạng (`max_active_give_items`, mặc định 5); chỉ đếm bài `GIVE`. Tài khoản trong danh sách bỏ qua giới hạn của cấu hình test vẫn được bỏ qua cả hai hạn mức.
  - `422 ITEM_DEFECT_IMAGES_REQUIRED` (`field: defectImages`): Bài `DEFECTIVE` không có ảnh lỗi.
  - `422 ITEM_OPERATING_STATUS_REQUIRED` (`field: operatingStatus`): Đồ điện tử chưa khai trạng thái hoạt động.
  - `422 ITEM_DEFECT_IMAGES_NOT_ALLOWED` / `ITEM_OPERATING_STATUS_NOT_ALLOWED`: Gửi dữ liệu dành cho điều kiện hoặc danh mục khác.
  - `403 MEDIA_NOT_OWNER`: Ảnh không thuộc người đăng bài.
  - `404 MEDIA_NOT_FOUND`: Không tìm thấy ảnh đã upload.
  - `409 MEDIA_NOT_UPLOADED`: Ảnh đã gắn vào bài khác hoặc không còn dùng được.
  - `422 ITEM_MODERATION_BLOCKED` (`field: content`, `details.reasonCode`, `details.matchedTerms`): Nội dung bị kiểm duyệt tầng 1 chặn.
  - `402 WALLET_INSUFFICIENT_BALANCE`: Credit khả dụng không đủ để giữ phí đăng bài (chỉ xảy ra với bài `SWAP`).
  - `401 UNAUTHORIZED`: Chưa đăng nhập.

---

## 2. GET /api/v1/items/{id}

Xem chi tiết một bài đăng theo ID.

- **Xác thực:** Không bắt buộc (công khai đối với bài đã duyệt).
- **Quy tắc hiển thị:**
  - Bài ở trạng thái `APPROVED`: Bất kỳ ai (khách vãng lai hay thành viên khác) đều xem được.
  - Bài ở các trạng thái khác (`PENDING_REVIEW`, `REJECTED`, `RESERVED`, `TRADED`, `EXPIRED`): Chỉ chính **chủ bài đăng** (`donorId`) mới xem được.
  - Trường hợp bài không tồn tại, đã xoá mềm, hoặc người xem không phải chủ bài: Trả về `404 ITEM_NOT_FOUND`.
- **Thành công:** `200 OK`, trả về `ItemResponse`.
- **Lỗi:**
  - `404 ITEM_NOT_FOUND`: Không tìm thấy bài đăng hoặc không có quyền xem.

---

## PUT /api/v1/items/{id}

- **Xác thực:** Bearer Token, chỉ chủ bài. Body gồm các trường nội dung như `CreateItemRequest`; `images` và `defectImages` có thể bỏ qua để giữ ảnh hiện tại, hoặc gửi danh sách URL media của chính chủ bài để thay và sắp lại thứ tự. Khi đổi sang `DEFECTIVE` phải có ít nhất một ảnh lỗi; khi chọn danh mục điện tử phải gửi `operatingStatus`.
- **Luồng:** Từ chối bài đã `RESERVED`/`TRADED`/`EXPIRED` hoặc có Request từ `RESERVED` trở lên. Cho phép đổi `GIVE` ↔ `SWAP`. Đổi sang `GIVE` phải còn chỗ trong hạn mức Cho tặng; phí đăng Trao đổi đã thu không hoàn. Đổi sang `SWAP` khi bài chưa từng trả phí đăng Trao đổi sẽ giữ 5 hoặc 10 Credit theo số bài Trao đổi đang hoạt động và ngưỡng của hạng; bài đã trả phí đăng thì không thu lại. Kiểm duyệt tầng 1 tiêu đề, mô tả và món mong muốn; nội dung bị chặn trả `422`, không giữ phí hay đổi bài. Sau đó gọi `quoteEdit` và `holdEditFee` theo [luật phí sửa](listingfee.md). Bài thành `PENDING_REVIEW`, tạm ẩn để Admin duyệt lại. Nếu đổi hình thức, các Request `PENDING` của bài được huỷ trong cùng transaction và người gửi nhận thông báo; lỗi lưu bài hoặc giữ phí thì không huỷ yêu cầu.
- **Thành công:** `200 OK`, `ItemResponse` mới.
- **Lỗi chính:** `401 UNAUTHORIZED`, `403 ITEM_NOT_OWNER`, `403 ITEM_REPUTATION_BLOCKED` (chủ bài có 0 sao uy tín), `404 ITEM_NOT_FOUND`, `409 ITEM_EDIT_NOT_ALLOWED`, `409 ITEM_GIVE_LIMIT_REACHED`, `422 ITEM_MODERATION_BLOCKED` (`field: content`), `422 ITEM_INVALID_IMAGE_COUNT`, lỗi media và `402 WALLET_INSUFFICIENT_BALANCE`.

---

## DELETE /api/v1/items/{id}

- **Xác thực:** Bearer Token, chỉ chủ bài.
- **Quy tắc:** Không gỡ bài `RESERVED`, `TRADED`, `EXPIRED` hoặc có Request từ `RESERVED` trở lên. Xoá mềm (`is_deleted = true`); phí `HELD` được nhả qua `PostFeeService.releaseHeldFee` trong cùng transaction.
- **Thành công:** `204 No Content`.
- **Lỗi:** `401 UNAUTHORIZED`, `403 ITEM_NOT_OWNER`, `404 ITEM_NOT_FOUND`, `409 ITEM_REMOVE_NOT_ALLOWED`.

---

## 3. GET /api/v1/me/items

Xem danh sách tất cả các bài đăng do chính mình tạo (sắp xếp mới nhất lên trước).

- **Xác thực:** Bắt buộc Bearer Token (JWT).
- **Thành công:** `200 OK`, mảng `ItemResponse` (mảng rỗng nếu chưa đăng bài nào).
- **Lỗi:**
  - `401 UNAUTHORIZED`: Chưa đăng nhập.

---

## 3b. GET /api/v1/me/posting-quota

Hạn mức và phí bài kế tiếp cho trang Đăng tin (cần đăng nhập). Cho tặng và Trao đổi đếm riêng; chỉ tính bài `PENDING_REVIEW` và `APPROVED` còn hạn, đang hoạt động. `swap.limit` là ngưỡng phí thường, không phải mức chặn đăng bài.

- **Thành công:** `200 OK`

```json
{
  "give": { "active": 2, "limit": 5 },
  "swap": { "active": 9, "limit": 15 },
  "rank": "GOLD",
  "nextPostFee": 5
}
```

- `limit = -1` nghĩa là không giới hạn (hạng Kim cương với Trao đổi, hoặc tài khoản được bỏ qua hạn mức).
- `rank`: `BRONZE`, `SILVER`, `GOLD`, `DIAMOND`.
- `nextPostFee`: phí Credit cho bài Trao đổi tiếp theo; `post_fee` khi dưới ngưỡng hoặc tài khoản được bỏ qua hạn mức, `post_fee_over_quota` khi đã đạt ngưỡng.
- **Lỗi:** `401` khi chưa đăng nhập.

---

## 4. GET /api/v1/items

Danh sách bài `APPROVED` còn hiển thị (`is_active = true`, chưa xoá mềm, `expire_at` rỗng hoặc lớn hơn thời điểm hiện tại). Trong cùng bộ lọc, tất cả bài đang được đẩy xếp trước bài thường. Không cần đăng nhập.

Tham số tùy chọn: `q` (tìm trong tiêu đề và mô tả, không phân biệt dấu/hoa thường),
`categoryId`, `areaId` (lọc đúng id, kết hợp được với `q`),
`condition` (`NEW`, `LIKE_NEW`, `GOOD`, `DEFECTIVE`), `offerType` (`GIVE`, `SWAP`),
`page` (từ 0, mặc định 0), `size` (1–100, mặc định 24), `boosted` (mặc định `false`; `true` chỉ lấy bài đang được đẩy), `sort` (`newest` hoặc `expiring`, mặc định `newest`). Tất cả bộ lọc đều không bắt buộc và kết hợp được với nhau;
lọc ở server nên `totalElements` và phân trang tính trên kết quả đã lọc (FR-50). Kết quả là `PageResponse<ItemResponse>`:

Ví dụ: `GET /api/v1/items?q=ao%20khoac&categoryId=1&areaId=2&condition=GOOD&offerType=GIVE`. `q` rỗng hoặc chỉ có khoảng trắng tương đương không tìm kiếm; `%` và `_` được xem là ký tự thường.

```json
{ "items": [], "page": 0, "size": 24, "totalElements": 0, "totalPages": 0 }
```

`totalPages` là số trang thực tế. Backend lọc, sắp xếp, đếm và phân trang trong database; mỗi trang trừ trang cuối có đủ `size` bài nếu dữ liệu không đổi giữa các request. Ví dụ có 30 bài đẩy và 18 bài thường, `size=24`: trang 1 gồm 24 bài đẩy; trang 2 gồm 6 bài đẩy và 18 bài thường. Chế độ `boosted=true` phân trang riêng các bài đang được đẩy, tối đa `size` bài mỗi trang.

- `sort=newest`: bài đẩy xếp theo lần kích hoạt hoặc gia hạn đẩy gần nhất; sau đó bài thường xếp theo thời điểm tạo mới nhất.
- `sort=expiring`: bài đẩy xếp theo `expire_at` tăng dần; sau đó bài thường cũng xếp theo `expire_at` tăng dần. Bài có `expire_at` rỗng đứng cuối trong từng nhóm. Nếu trùng hạn, ưu tiên bài tạo mới hơn rồi đến ID lớn hơn.

`400` khi `sort` không thuộc hai giá trị trên.

`400 VALIDATION_FAILED` khi tham số phân trang sai. `400` khi `condition` hoặc `offerType` không thuộc các giá trị trên.

---

## DTO Models

### CreateItemRequest

| Trường | Kiểu | Bắt buộc | Ràng buộc |
|---|---|---|---|
| `categoryId` | number | Có | Id danh mục |
| `areaId` | number | Có | Id khu vực |
| `offerType` | string | Có | `GIVE` hoặc `SWAP` |
| `title` | string | Có | Tối đa 150 ký tự |
| `description` | string | Có | Không được để trống |
| `desiredItem` | string? | Điều kiện | Bắt buộc nếu `offerType` là `SWAP` |
| `condition` | string | Có | `NEW`, `LIKE_NEW`, `GOOD`, `DEFECTIVE` |
| `defectNote` | string? | Điều kiện | Bắt buộc nếu `condition` là `DEFECTIVE` |
| `brand` | string? | Không | Tối đa 80 ký tự |
| `images` | array[string] | Có | 3–8 URL `/api/v1/media/{id}` nhận từ `POST /api/v1/media`, theo thứ tự hiển thị; chỉ gắn ảnh của chính người đăng |
| `defectImages` | array[string] | Điều kiện | Ít nhất một URL ảnh lỗi nếu `condition=DEFECTIVE`; role `DEFECT` |
| `operatingStatus` | string | Điều kiện | Bắt buộc với điện tử: `NORMAL`, `PARTIAL`, `NOT_WORKING` |

### ItemResponse

| Trường | Kiểu | Ý nghĩa |
|---|---|---|
| `id` | number | ID bài đăng |
| `donorId` | number | ID người đăng |
| `categoryId` | number | ID danh mục |
| `areaId` | number | ID khu vực |
| `offerType` | string | `GIVE` hoặc `SWAP` |
| `title` | string | Tiêu đề bài đăng |
| `description` | string | Nội dung mô tả |
| `desiredItem` | string? | Món đồ mong muốn đổi (nếu SWAP) |
| `condition` | string | Tình trạng đồ |
| `defectNote` | string? | Mô tả lỗi (nếu DEFECTIVE) |
| `brand` | string? | Nhãn hiệu / Thương hiệu |
| `images` | array[string] | Danh sách URL `/api/v1/media/{id}` của ảnh đã lưu |
| `defectImages` | array[string] | Danh sách URL ảnh lỗi, role `DEFECT` |
| `operatingStatus` | string? | Trạng thái hoạt động của đồ điện tử |
| `status` | string | `PENDING_REVIEW`, `APPROVED`, `REJECTED`, `RESERVED`, `TRADED`, `EXPIRED` |
| `createdAt` | string | Thời điểm tạo ISO-8601 UTC |
| `updatedAt` | string | Thời điểm cập nhật ISO-8601 UTC |
| `expireAt` | string? | Hạn hiển thị ISO-8601 UTC (cột `expire_at`, module listingfee). `null` khi bài chưa từng được duyệt |
| `boostedUntil` | string? | Thời điểm hết đẩy bài ISO-8601 UTC (cột `boosted_until`, module listingfee). `null` khi bài chưa từng được đẩy; giá trị đã qua nghĩa là hết đẩy |
| `donorName` | string? | Tên người đăng; chỉ có ở API chi tiết |
| `donorTrustStars` | number? | Sao uy tín người đăng; chỉ có ở API chi tiết |
| `donorRank` | string? | Hạng người đăng; chỉ có ở API chi tiết |
| `donorAsDescribedRate` | number? | Tỉ lệ đúng mô tả thang 0–1; chỉ có ở API chi tiết |
| `hidden` | boolean? | Bài đang tạm ẩn (`is_active = false`), ví dụ tự ẩn do đủ báo cáo đang chờ (U04). Chỉ có khi **chủ bài** xem (chi tiết, sửa, bài của tôi) hoặc Admin xem; người khác nhận `null` |
| `hiddenReason` | string? | Lý do ẩn. Hiện luôn `null` vì chưa lưu lý do ẩn (C06); FE hiện câu mặc định |

`expireAt` và `boostedUntil` có trong mọi API trả `ItemResponse` (tạo, sửa, chi tiết, bài của tôi, danh sách công khai).
Với danh sách, hai trường này được đọc bằng một query cho cả trang qua `ListingFeeQueryService.findDisplayWindows`.

---

## Mã lỗi

| code | HTTP | Khi nào |
|---|---|---|
| `ITEM_NOT_FOUND` | 404 | Không tìm thấy bài đăng hoặc bài chưa duyệt mà người xem không phải chủ |
| `ITEM_SWAP_DESIRED_ITEM_REQUIRED` | 400 | Bài đăng SWAP nhưng thiếu món mong muốn |
| `ITEM_DEFECT_NOTE_REQUIRED` | 400 | Bài đăng có tình trạng DEFECTIVE nhưng thiếu mô tả lỗi |
| `ITEM_INVALID_IMAGE` | 400 | URL ảnh không phải URL do API media trả về |
| `ITEM_INVALID_IMAGE_COUNT` | 422 | `images` có ít hơn 3 hoặc nhiều hơn 8 phần tử |
| `ITEM_GIVE_LIMIT_REACHED` | 409 | Bài Cho tặng đã đạt hạn mức chung (`max_active_give_items`); có `details.limit`, `details.activeCount` |
| `ITEM_DEFECT_IMAGES_REQUIRED` | 422 | Bài `DEFECTIVE` thiếu ảnh lỗi |
| `ITEM_DEFECT_IMAGES_NOT_ALLOWED` | 422 | Bài không `DEFECTIVE` lại gửi ảnh lỗi |
| `ITEM_OPERATING_STATUS_REQUIRED` | 422 | Bài điện tử thiếu trạng thái hoạt động |
| `ITEM_OPERATING_STATUS_NOT_ALLOWED` | 422 | Bài không thuộc danh mục cần trạng thái hoạt động lại gửi trường này |
| `ITEM_NOT_OWNER` | 403 | Sửa bài không phải của mình |
| `ITEM_EDIT_NOT_ALLOWED` | 409 | Bài hoặc Request đã ở giai đoạn không được sửa |
| `ITEM_REMOVE_NOT_ALLOWED` | 409 | Bài hoặc Request đã ở giai đoạn không được gỡ |
| `ITEM_REPUTATION_BLOCKED` | 403 | Người đăng hoặc sửa bài có 0 sao uy tín (FR-92, BR-U05) |
| `ITEM_MODERATION_BLOCKED` | 422 | Nội dung tạo hoặc sửa bị kiểm duyệt tầng 1 chặn |
| `VALIDATION_FAILED` | 400 | Sai định dạng hoặc thiếu trường bắt buộc |
| `UNAUTHORIZED` | 401 | Thiếu token hoặc token không hợp lệ |
