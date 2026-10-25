# API module item

## Service cửa ngõ nội bộ

- `ItemService.reserve(Long itemId)` chuyển `APPROVED → RESERVED`; `release(long itemId)` chuyển `RESERVED → APPROVED`. Trạng thái khác trả `ITEM_INVALID_STATUS_TRANSITION`.
- `markTraded` và `markExpired` chuyển trạng thái qua `canTransition()`; `hide/unhide/hideAllOfUser` chỉ đổi `is_active`.
- `remove(itemId, userId)` phục vụ DELETE của chủ bài; `remove(itemId)` là cửa ngõ nội bộ. Các hàm vòng đời khác chưa có endpoint HTTP riêng.

Quy ước chung theo Lộ trình mục 3.4: tiền tố `/api/v1`, thời gian ISO-8601 UTC, thành công trả thẳng DTO (không bọc),
lỗi theo định dạng thống nhất bên dưới.

POST /items đã tích hợp kiểm duyệt tầng 1, giữ phí đăng bài và gắn ảnh trong một transaction.

## AI sàng lọc sau khi gửi bài

- Sau khi transaction tạo hoặc sửa bài commit thành công, luồng nền gửi nội dung bài đến OpenRouter. Chỉ gửi tiêu đề, mô tả, món mong muốn, danh mục, tình trạng và loại bài; không gửi ảnh hay dữ liệu tài khoản người đăng.
- Tầng 2 ghi `items.ai_risk` (`LOW`, `MEDIUM`, `HIGH`) và `items.ai_result` đã kiểm tra schema, gồm kết quả, model và thời điểm kiểm. Kết quả nghi vấn có cờ `REVIEW` và lý do cho hàng chờ duyệt. Tầng 2 không đổi trạng thái hay phí bài đăng, và giữ nguyên lý do `REVIEW` của tầng 1 nếu đã có.
- Khi nhà cung cấp lỗi, quá hạn hoặc trả JSON sai, `ai_risk` vẫn là `UNCHECKED` (giao diện hiện "AI chưa kiểm"); bài vẫn ở hàng chờ duyệt. Mỗi lượt gọi đã thử được ghi `AiUsage` với lần thử, kết quả, thời lượng và model khi có, không ghi nội dung bài hay API key.
- Model qua OpenRouter API: ưu tiên `google/gemma-4-31b-it:free`, chuyển sang `google/gemma-4-26b-a4b-it:free` khi model đầu không khả dụng. Đặt OpenRouter API key vào `OPENROUTER_API_KEY` trong môi trường chạy của profile production, không đưa vào Git. `item_ai_timeout_seconds` mặc định 15 giây tổng cộng cho tối đa một lần thử lại; có thể chỉnh `item_ai_retries` bằng cấu hình admin. Giới hạn lượt của model miễn phí có thể khiến sàng lọc AI không khả dụng; khi đó hệ thống giữ nhánh dự phòng hiện có.

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
- Số bài `PENDING_REVIEW` hoặc `APPROVED` còn active của mỗi người đăng bị giới hạn theo hạng hiện tại (`ReputationService.getRank`): Đồng 3, Bạc 5, Vàng 10, Kim Cương không giới hạn theo cấu hình `MAX_ACTIVE_ITEMS_*`. Bài GIVE và SWAP được tính chung.
- Kiểm duyệt tầng 1 áp dụng cho tiêu đề, mô tả và món mong muốn: `BLOCK` từ chối; `REVIEW` vẫn tạo bài và gắn cờ trong hàng chờ Admin.
- Khi tạo thành công, gọi `PostFeeService.holdPostFee`. Phí đăng chỉ áp dụng cho bài `SWAP` (mặc định 5 Credit, theo cấu hình `post_fee`); phí chỉ bị trừ lúc Admin duyệt. Bài `GIVE` miễn phí đăng: tài khoản 0 Credit vẫn đăng được, bài vẫn ở `fee_state = HELD` với `pending_fee = 0` và không đụng tới ví. Lỗi ở bất kỳ bước nào sẽ rollback bài, phí giữ và trạng thái ảnh.

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
  - `409 ITEM_ACTIVE_LIMIT_REACHED` (`details.limit`, `details.activeCount`): Đã đạt hạn mức bài đang hiển thị hoặc chờ duyệt; thông báo nêu hạn mức hiện tại.
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
- **Luồng:** Từ chối bài đã `RESERVED`/`TRADED`/`EXPIRED` hoặc có Request từ `RESERVED` trở lên. Không cho đổi bài `GIVE` sang `SWAP` (bài Cho tặng miễn phí đăng, đổi sang Trao đổi sẽ né được phí đăng): trả `409 ITEM_OFFER_TYPE_CHANGE_NOT_ALLOWED`, không giữ phí hay đổi bài; muốn trao đổi thì đăng bài `SWAP` mới. Chiều `SWAP` sang `GIVE` vẫn cho phép. Kiểm duyệt tầng 1 tiêu đề, mô tả và món mong muốn; nội dung bị chặn trả `422`, không giữ phí hay đổi bài. Sau đó gọi `quoteEdit` và `holdEditFee` theo [luật phí sửa](listingfee.md). Bài thành `PENDING_REVIEW`, tạm ẩn để Admin duyệt lại.
- **Thành công:** `200 OK`, `ItemResponse` mới.
- **Lỗi chính:** `401 UNAUTHORIZED`, `403 ITEM_NOT_OWNER`, `403 ITEM_REPUTATION_BLOCKED` (chủ bài có 0 sao uy tín, kiểm tra ngay sau khi xác nhận chủ bài, không giữ phí sửa), `404 ITEM_NOT_FOUND`, `409 ITEM_EDIT_NOT_ALLOWED`, `409 ITEM_OFFER_TYPE_CHANGE_NOT_ALLOWED`, `422 ITEM_MODERATION_BLOCKED` (`field: content`), `422 ITEM_INVALID_IMAGE_COUNT`, lỗi media và `402 WALLET_INSUFFICIENT_BALANCE`.

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

## 4. GET /api/v1/items

Danh sách bài `APPROVED` còn hiển thị, mới nhất trước. Không cần đăng nhập.

Tham số tùy chọn: `q` (tìm trong tiêu đề và mô tả, không phân biệt dấu/hoa thường),
`categoryId`, `areaId` (lọc đúng id, kết hợp được với `q`),
`condition` (`NEW`, `LIKE_NEW`, `GOOD`, `DEFECTIVE`), `offerType` (`GIVE`, `SWAP`),
`page` (từ 0, mặc định 0), `size` (1–100, mặc định 20). Tất cả bộ lọc đều không bắt buộc và kết hợp được với nhau;
lọc ở server nên `totalElements` và phân trang tính trên kết quả đã lọc (FR-50). Kết quả là `PageResponse<ItemResponse>`:

Ví dụ: `GET /api/v1/items?q=ao%20khoac&categoryId=1&areaId=2&condition=GOOD&offerType=GIVE`. `q` rỗng hoặc chỉ có khoảng trắng tương đương không tìm kiếm; `%` và `_` được xem là ký tự thường.

```json
{ "items": [], "page": 0, "size": 20, "totalElements": 0 }
```

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
| `ITEM_ACTIVE_LIMIT_REACHED` | 409 | Đã đạt hạn mức bài active theo hạng; thông báo nêu hạn mức |
| `ITEM_DEFECT_IMAGES_REQUIRED` | 422 | Bài `DEFECTIVE` thiếu ảnh lỗi |
| `ITEM_DEFECT_IMAGES_NOT_ALLOWED` | 422 | Bài không `DEFECTIVE` lại gửi ảnh lỗi |
| `ITEM_OPERATING_STATUS_REQUIRED` | 422 | Bài điện tử thiếu trạng thái hoạt động |
| `ITEM_OPERATING_STATUS_NOT_ALLOWED` | 422 | Bài không thuộc danh mục cần trạng thái hoạt động lại gửi trường này |
| `ITEM_NOT_OWNER` | 403 | Sửa bài không phải của mình |
| `ITEM_EDIT_NOT_ALLOWED` | 409 | Bài hoặc Request đã ở giai đoạn không được sửa |
| `ITEM_REMOVE_NOT_ALLOWED` | 409 | Bài hoặc Request đã ở giai đoạn không được gỡ |
| `ITEM_OFFER_TYPE_CHANGE_NOT_ALLOWED` | 409 | Sửa bài Cho tặng (`GIVE`) sang Trao đổi (`SWAP`); phải đăng bài Trao đổi mới |
| `ITEM_REPUTATION_BLOCKED` | 403 | Người đăng hoặc sửa bài có 0 sao uy tín (FR-92, BR-U05) |
| `ITEM_MODERATION_BLOCKED` | 422 | Nội dung tạo hoặc sửa bị kiểm duyệt tầng 1 chặn |
| `VALIDATION_FAILED` | 400 | Sai định dạng hoặc thiếu trường bắt buộc |
| `UNAUTHORIZED` | 401 | Thiếu token hoặc token không hợp lệ |
