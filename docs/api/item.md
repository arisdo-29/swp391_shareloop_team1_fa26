# API module item

## Service cửa ngõ nội bộ

- `ItemService.reserve(Long itemId)` chuyển `APPROVED → RESERVED`; `release(long itemId)` chuyển `RESERVED → APPROVED`. Trạng thái khác trả `ITEM_INVALID_STATUS_TRANSITION`.
- `markTraded` và `markExpired` chuyển trạng thái qua `canTransition()`; `hide/unhide/hideAllOfUser` chỉ đổi `is_active`.
- `remove(itemId, userId)` phục vụ DELETE của chủ bài; `remove(itemId)` là cửa ngõ nội bộ. Các hàm vòng đời khác chưa có endpoint HTTP riêng.

Quy ước chung theo Lộ trình mục 3.4: tiền tố `/api/v1`, thời gian ISO-8601 UTC, thành công trả thẳng DTO (không bọc),
lỗi theo định dạng thống nhất bên dưới.

POST /items đã tích hợp kiểm duyệt tầng 1, giữ phí đăng bài và gắn ảnh trong một transaction.

## Kiểm duyệt khi gửi bài

Đăng hoặc sửa bài chạy kiểm tra nội dung tự động bằng `ContentModerationService`, sau đó đưa bài vào hàng chờ Admin duyệt. Hệ thống không gọi mô hình AI để kiểm duyệt bài. Trường `ai_risk` cũ giữ giá trị `UNCHECKED`; `ai_result` vẫn lưu cờ `REVIEW`, lý do và từ khoá khớp của bước kiểm tra nội dung khi cần. Các cấu hình sàng lọc AI cũ đã được ngừng sử dụng.

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
  - `403 ITEM_ADMIN_BLOCKED`: Quản trị viên không được đăng bài giao dịch (TK-13).
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
  - Bài ở trạng thái `TRADED` (đã giao dịch thành công): bất kỳ ai cũng xem được ở **chế độ chỉ xem**, để mở từ tab
    "Đã giao dịch" của hồ sơ công khai. Response có `requestable = false` và `completedAt`; không có thông tin người
    nhận hay yêu cầu liên quan. Gọi thẳng `POST /api/v1/items/{id}/requests` vẫn bị chặn `409 REQUEST_ITEM_NOT_AVAILABLE`.
  - Với cả hai trường hợp trên, bài phải đang hiển thị (`is_active = true`) và chủ bài không bị khoá.
  - Bài ở các trạng thái khác (`PENDING_REVIEW`, `REJECTED`, `RESERVED`, `EXPIRED`): Chỉ chính **chủ bài đăng** (`donorId`) mới xem được.
  - Trường hợp bài không tồn tại, đã xoá mềm, hoặc người xem không phải chủ bài: Trả về `404 ITEM_NOT_FOUND`.
- `requestable`: `true` khi bài `APPROVED`, đang hiển thị, chủ bài không bị khoá. Frontend ẩn nút "Gửi yêu cầu" khi
  `false`. Cờ này không xét người xem (chủ bài xem bài của mình vẫn thấy `true`); các luật theo người gửi (bài của mình,
  0 sao, Admin) vẫn do `POST /items/{id}/requests` kiểm.
- Ba mốc đợt đẩy (`boostStartedAt`, `boostChainEndsAt`, `boostCooldownUntil`) chỉ có khi chủ bài xem; người khác nhận
  `null`. `boostedUntil` có với mọi người xem.
- **Thành công:** `200 OK`, trả về `ItemResponse`.
- **Lỗi:**
  - `404 ITEM_NOT_FOUND`: Không tìm thấy bài đăng hoặc không có quyền xem.

---

## PUT /api/v1/items/{id}

- **Xác thực:** Bearer Token, chỉ chủ bài. Body gồm các trường nội dung như `CreateItemRequest`; `images` và `defectImages` có thể bỏ qua để giữ ảnh hiện tại, hoặc gửi danh sách URL media của chính chủ bài để thay và sắp lại thứ tự. Khi đổi sang `DEFECTIVE` phải có ít nhất một ảnh lỗi; khi chọn danh mục điện tử phải gửi `operatingStatus`.
- **Luồng:** Từ chối bài đã `RESERVED`/`TRADED`/`EXPIRED` hoặc có Request từ `RESERVED` trở lên. **Không cho phép đổi hình thức** (`GIVE` ↔ `SWAP`): hình thức là thuộc tính cố định từ lúc tạo bài (BD-10); nếu gửi `offerType` khác hình thức hiện tại của bài, hệ thống trả `409 ITEM_OFFER_TYPE_CHANGE_NOT_ALLOWED` với thông điệp: "Không đổi hình thức Cho tặng / Trao đổi khi sửa bài." (D-02). Muốn đổi hình thức, người dùng phải đóng/xoá bài cũ và đăng bài mới. Kiểm duyệt tầng 1 tiêu đề, mô tả và món mong muốn; nội dung bị chặn trả `422`, không giữ phí hay đổi bài. Sau đó gọi `quoteEdit` và `holdEditFee` theo [luật phí sửa](listingfee.md). Bài thành `PENDING_REVIEW`, tạm ẩn để Admin duyệt lại.
- **Thành công:** `200 OK`, `ItemResponse` mới.
- **Lỗi chính:** `401 UNAUTHORIZED`, `403 ITEM_NOT_OWNER`, `403 ITEM_ADMIN_BLOCKED` (quản trị viên), `403 ITEM_REPUTATION_BLOCKED` (chủ bài có 0 sao uy tín), `404 ITEM_NOT_FOUND`, `409 ITEM_EDIT_NOT_ALLOWED`, `409 ITEM_OFFER_TYPE_CHANGE_NOT_ALLOWED`, `409 ITEM_GIVE_LIMIT_REACHED`, `422 ITEM_MODERATION_BLOCKED` (`field: content`), `422 ITEM_INVALID_IMAGE_COUNT`, lỗi media và `402 WALLET_INSUFFICIENT_BALANCE`.

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
- **Thành công:** `200 OK`, mảng `ItemResponse` (mảng rỗng nếu chưa đăng bài nào). Bài đã từng đẩy có đủ ba mốc
  `boostStartedAt`, `boostChainEndsAt`, `boostCooldownUntil` để hiện thông tin đợt đẩy.
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

Danh sách bài `APPROVED` còn hiển thị (`is_active = true`, chưa xoá mềm, `expire_at` rỗng hoặc lớn hơn thời điểm hiện tại). Mỗi trang có tối đa `boost_max_per_page` (mặc định 3) bài đang đẩy ở đầu, chọn theo **vòng xoay** (mục dưới); phần còn lại là bài thường. Không cần đăng nhập; có token thì backend dùng id người dùng làm khoá xoay.

Tham số tùy chọn: `q` (tìm theo cụm từ trong tiêu đề món đồ, không phân biệt dấu/hoa thường; không tìm trong mô tả hoặc khớp một phần của từ khác),
`categoryId`, `areaId` (lọc đúng id, kết hợp được với `q`),
`condition` (`NEW`, `LIKE_NEW`, `GOOD`, `DEFECTIVE`), `offerType` (`GIVE`, `SWAP`),
`page` (từ 0, mặc định 0), `size` (1–100, mặc định 24), `boosted` (mặc định `false`; `true` chỉ lấy bài đang được đẩy), `sort` (`newest` hoặc `expiring`, mặc định `newest`),
`rotationSeed` (số nguyên ≥ 0; lượt xoay lấy từ response trang trước, gửi lại khi chuyển trang),
`viewerKey` (số nguyên ≥ 0; khoá xoay của khách chưa đăng nhập, FE tự sinh một lần và giữ lại; bỏ qua khi có token). Tất cả bộ lọc đều không bắt buộc và kết hợp được với nhau;
lọc ở server nên `totalElements` và phân trang tính trên kết quả đã lọc (FR-50). Kết quả là `ItemListPageResponse`:

Ví dụ: `GET /api/v1/items?q=ao%20khoac&categoryId=1&areaId=2&condition=GOOD&offerType=GIVE`. `q` rỗng hoặc chỉ có khoảng trắng tương đương không tìm kiếm; `%` và `_` được xem là ký tự thường.

```json
{ "items": [], "page": 0, "size": 24, "totalElements": 0, "totalPages": 0, "rotationSeed": 9842213 }
```

`rotationSeed` là lượt xoay đã dùng cho trang này. FE gửi lại đúng số này khi sang trang khác (cùng bộ lọc) để các trang
thuộc cùng một lượt: không trùng bài, không sót bài. Không gửi thì backend tính theo giờ hiện tại, nên trang 2 gọi sau
khi đã đổi lượt có thể lệch với trang 1.

### Cách xếp bài đẩy (vòng xoay)

- **Vòng**: id các bài đang đẩy (`boosted_until > now`) khớp bộ lọc, sắp theo id **tăng dần**. Thứ tự cố định, không
  phụ thuộc lúc mua gói, nên người đẩy sau không đè người đẩy trước.
- **Lượt**: `round = epochSeconds / boost_rotate_seconds` (mặc định 180 giây, tức 3 phút đổi nhóm một lần), hoặc
  `rotationSeed` nếu request có gửi.
- **Người xem**: `viewerKey` = id người dùng nếu đã đăng nhập; khách thì lấy tham số `viewerKey`; không có thì `0`. Mỗi
  người bắt đầu ở một vị trí khác trên vòng.
- **Chọn bài**: vị trí bắt đầu `(round * perPage + viewerKey) mod n`, lấy liên tiếp, hết vòng thì quay lại đầu, với
  `perPage = min(boost_max_per_page, size)`.
- **Trang Tìm đồ** (`boosted=false`): chỉ `min(n, perPage * totalPages)` bài đầu của vòng (gọi là `slotIds`) được xếp
  lên đầu trang; trang `p` lấy `slotIds` từ vị trí `p * perPage`, tối đa `perPage` bài, theo đúng thứ tự xoay. Phần
  còn lại của trang là **mọi bài khớp bộ lọc trừ `slotIds`** (kể cả bài đẩy không được lên đầu), sắp theo `sort`. Nhờ
  vậy bài đẩy không bao giờ biến mất khỏi kết quả, và ghép các trang của cùng một lượt thì đủ mọi bài, không trùng.
- **Trang "Tin nổi bật"** (`boosted=true`): mọi bài đang đẩy khớp bộ lọc, theo thứ tự xoay (cả vòng), phân trang bình
  thường `size` bài mỗi trang. `totalElements` là số bài đang đẩy.
- `sort` chỉ áp dụng cho phần bài thường: `newest` là mới tạo trước; `expiring` là `expire_at` tăng dần, `expire_at`
  rỗng đứng cuối, trùng hạn thì bài tạo mới hơn rồi ID lớn hơn. Bài đẩy ở đầu trang và trang `boosted=true` luôn theo
  thứ tự xoay.
- Không có trường nào để hiện nhãn "Được đẩy"; FE không cần phân biệt bài đẩy với bài thường.

Ví dụ 48 bài, 30 bài đẩy, `size=24`, `boost_max_per_page=3`: có 2 trang và `slotIds` là 6 bài đầu vòng. Trang 1 gồm 3
bài đẩy rồi 21 bài thường mới nhất; trang 2 gồm 3 bài đẩy kế tiếp rồi 21 bài thường còn lại. 24 bài đẩy không lên đầu
trang vẫn nằm trong phần bài thường. Lượt sau (3 phút) nhóm 6 bài đầu trang trượt sang 6 bài khác của vòng.

`400 VALIDATION_FAILED` khi `sort` không thuộc hai giá trị trên, tham số phân trang sai, hoặc `rotationSeed` /
`viewerKey` âm. `400` khi `condition` hoặc `offerType` không thuộc các giá trị trên.

---

## 4b. GET /api/v1/items/featured

Banner bài đẩy ở trang chủ: một nhóm bài đang đẩy, đổi nhóm mỗi `boost_rotate_seconds` giây (mặc định 3 phút). Không
cần đăng nhập. Đường dẫn chuỗi cố định nên không bị hiểu là `GET /items/{id}`.

- **Tham số:** `limit` (1–10, mặc định 5), `viewerKey` (số nguyên ≥ 0, như ở `GET /items`; bỏ qua khi có token).
- **Chọn bài:** vòng là **mọi** bài đang đẩy (không lọc), sắp id tăng dần; `round = epochSeconds / boost_rotate_seconds`;
  lấy `limit` bài liên tiếp bắt đầu từ `(round * limit + viewerKey) mod n`. Có ít hơn `limit` bài thì trả hết.
- **Thành công:** `200 OK`, `FeaturedItemsResponse`. `items` cùng dạng `ItemResponse` với trang Tìm đồ.
- **Lỗi:** `400 VALIDATION_FAILED` khi `limit` ngoài 1–10 hoặc `viewerKey` âm.

```json
{
  "items": [
    {
      "id": 31,
      "donorId": 7,
      "categoryId": 2,
      "areaId": 5,
      "offerType": "GIVE",
      "title": "Áo khoác gió",
      "status": "APPROVED",
      "images": ["/api/v1/media/120"],
      "expireAt": "2026-12-06T08:00:00Z",
      "boostedUntil": "2026-11-09T08:00:00Z",
      "boostStartedAt": null,
      "boostChainEndsAt": null,
      "boostCooldownUntil": null
    }
  ],
  "rotatedAt": "2026-11-06T08:00:00Z",
  "nextRotateAt": "2026-11-06T08:03:00Z"
}
```

(Ví dụ lược bớt các trường khác của `ItemResponse`.)

| Trường | Kiểu | Ý nghĩa |
|---|---|---|
| `items` | array[ItemResponse] | Tối đa `limit` bài của nhóm hiện tại, theo thứ tự xoay |
| `rotatedAt` | string | Lúc bắt đầu lượt hiện tại = `round * boost_rotate_seconds`, ISO-8601 UTC |
| `nextRotateAt` | string | Lúc đổi sang nhóm kế tiếp = `(round + 1) * boost_rotate_seconds`; FE hẹn giờ tải lại banner tới mốc này |

---

## 5. GET /api/v1/users/{userId}/items

Bài của một người cho trang hồ sơ công khai (xem `docs/api/user.md`, `GET /api/v1/users/{id}/profile`).

- **Xác thực:** bắt buộc (`401 UNAUTHORIZED` khi thiếu token).
- **Tham số:** `tab` (`ACTIVE` hoặc `DONE`, mặc định `ACTIVE`), `page` (từ 0, mặc định 0), `size` (1–50, mặc định 12).
- **Thành công:** `200 OK`, trang `{ items, page, size, totalElements, totalPages, rotationSeed }` (`rotationSeed` luôn
  `null` vì danh sách này không xoay bài đẩy), mỗi dòng là `ItemResponse`
  (cùng kiểu thẻ bài của `GET /api/v1/items`).
- **Lỗi:** `404 USER_NOT_FOUND` khi người dùng không tồn tại, chưa xác thực, bị khoá, bị cấm hoặc là Admin (cùng luật với hồ sơ);
  `400` khi `tab` sai giá trị hoặc phân trang sai.

Quy tắc:

- `ACTIVE`: bài của người đó đang `APPROVED`, `is_active = true`, chưa xoá, `expire_at` rỗng hoặc chưa tới (cùng điều
  kiện với trang Tìm đồ), mới tạo trước. Mỗi dòng `requestable = true`, `completedAt = null`.
- `DONE`: bài của người đó đang `TRADED`, `is_active = true`, chưa xoá. Mỗi dòng có `completedAt` (thời điểm Request
  `COMPLETED` của bài, kể cả khi bài là món đề nghị trong giao dịch trao đổi) và `requestable = false`. Sắp theo
  `updatedAt` giảm dần: bài `TRADED` không sửa được nữa nên lần cập nhật cuối là lúc chuyển sang `TRADED`.
- Không bao giờ trả bài `PENDING_REVIEW`, `REJECTED`, `RESERVED`, `EXPIRED`, bài hết hạn, bị ẩn hoặc đã gỡ. Chủ hồ sơ tự
  xem hồ sơ mình cũng theo luật công khai này (bài của tôi xem ở `GET /api/v1/me/items`).
- Các trường `donor*` để `null` như danh sách công khai; frontend lấy thông tin người từ API hồ sơ.

```json
{
  "items": [
    { "id": 51, "title": "Nồi cơm điện", "status": "TRADED", "images": ["/api/v1/media/90"],
      "completedAt": "2026-10-31T11:30:00Z", "requestable": false }
  ],
  "page": 0, "size": 12, "totalElements": 1, "totalPages": 1
}
```

(Ví dụ rút gọn, response thật có đủ các trường của `ItemResponse`.)

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
| `completedAt` | string? | Thời điểm giao dịch hoàn tất ISO-8601 UTC. Chỉ có khi bài `TRADED`, ở API chi tiết và `GET /users/{id}/items?tab=DONE`; nơi khác `null` |
| `requestable` | boolean? | Bài còn nhận yêu cầu không (`APPROVED`, đang hiển thị, chủ bài không bị khoá). Có ở API chi tiết và `GET /users/{id}/items`; các danh sách khác `null` |
| `boostStartedAt` | string? | Mốc bắt đầu đợt đẩy gần nhất (module listingfee). Chỉ có ở "Bài của tôi" và chi tiết bài khi **chủ bài** xem; nơi khác `null` |
| `boostChainEndsAt` | string? | Mốc đẩy muộn nhất của đợt = `boostStartedAt + boost_max_days`. Cùng phạm vi với `boostStartedAt` |
| `boostCooldownUntil` | string? | Mốc được mở đợt đẩy mới = `boostedUntil + boost_cooldown_days`. Cùng phạm vi với `boostStartedAt` |

`expireAt` và `boostedUntil` có trong mọi API trả `ItemResponse` (tạo, sửa, chi tiết, bài của tôi, danh sách công khai).
Ba mốc đợt đẩy (`boostStartedAt`, `boostChainEndsAt`, `boostCooldownUntil`) là thông tin mua gói của chủ bài: backend
tính sẵn để FE "Bài của tôi" chỉ hiển thị; mọi API trả bài cho người khác (danh sách Tìm đồ, Tin nổi bật, banner
`/items/featured`, chi tiết bài khi không phải chủ, hồ sơ công khai, tìm kiếm AI) đều trả `null`. Bài chưa từng đẩy thì
cả ba là `null`.
Với danh sách, hai trường này được đọc bằng một query cho cả trang qua `ListingFeeQueryService.findDisplayWindows`.

---

## Mã lỗi

| code | HTTP | Khi nào |
|---|---|---|
| `ITEM_NOT_FOUND` | 404 | Không tìm thấy bài đăng hoặc bài không ở `APPROVED`/`TRADED` mà người xem không phải chủ |
| `USER_NOT_FOUND` | 404 | `GET /users/{id}/items`: người dùng không tồn tại, chưa xác thực, bị khoá, bị cấm hoặc là Admin (mã của module user) |
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
