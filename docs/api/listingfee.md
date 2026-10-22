# API module listingfee

Quy ước chung theo Lộ trình mục 3.4: tiền tố `/api/v1`, thời gian ISO-8601 UTC, thành công trả thẳng DTO (không bọc),
lỗi theo định dạng thống nhất (xem `wallet.md`).

Người sở hữu: BE1. Module giữ, trừ, nhả phí đăng bài và phí sửa bài (C01, C02, C14, C14b), đặt thời hạn hiển thị
`expire_at` khi duyệt lần đầu, gia hạn bài (C16) và đẩy bài (C15). Mọi mức phí và số ngày đọc từ ConfigService
(`post_fee`, `edit_fee`, `renew_fee`, `post_display_days`, `post_display_days_gold`, `boost_fee`, `boost_days`,
`boost_max_days`), không viết cứng. Có một job nền chuyển bài
quá hạn sang `EXPIRED` (mục "Job nền"). Đổi tiền chỉ qua `wallet.CreditService`.

Phí đăng (`post_fee`) chỉ áp dụng cho bài Trao đổi (`SWAP`); bài Cho tặng (`GIVE`) miễn phí đăng, kể cả khi gửi lại
bài bị từ chối. Phí sửa bài sau duyệt (`edit_fee`) áp dụng cho cả hai loại. Không cho sửa bài `GIVE` thành `SWAP`
(`409 ITEM_OFFER_TYPE_CHANGE_NOT_ALLOWED`, xem `item.md`) để không né được phí đăng.

## GET /api/v1/listing-fees

Bảng phí và thời hạn hiện hành để FE hiển thị trước khi người dùng đăng, sửa, gia hạn hay đẩy bài. Mọi số đọc từ
ConfigService ở mỗi lần gọi, nên Admin đổi cấu hình (`PUT /api/v1/admin/configs/{key}`) thì lần gọi sau thấy số mới.

- Xác thực: **bắt buộc** Bearer Token (người dùng đã đăng nhập bất kỳ). Không có tham số.
- Thành công: `200`, một `ListingFeeConfigResponse`.
- Lỗi: `401 UNAUTHORIZED`.

```json
{
  "postFeeSwap": 5,
  "postFeeGive": 0,
  "editFee": 5,
  "renewFee": 5,
  "renewExpiredWindowDays": 7,
  "boostFee": 5,
  "boostDays": 3,
  "boostMaxDays": 14,
  "postDisplayDays": 30,
  "postDisplayDaysGold": 45
}
```

## ListingFeeConfigResponse

| Trường                   | Kiểu   | Khoá cấu hình               | Ý nghĩa                                                          |
|--------------------------|--------|-----------------------------|------------------------------------------------------------------|
| `postFeeSwap`            | number | `post_fee`                  | Phí đăng bài SWAP (Credit), giữ khi gửi bài, trừ khi được duyệt  |
| `postFeeGive`            | number | (không có)                  | Luôn `0`: bài GIVE đăng miễn phí, quy tắc cố định                |
| `editFee`                | number | `edit_fee`                  | Phí mỗi lượt sửa sau khi đã dùng lượt sửa miễn phí               |
| `renewFee`               | number | `renew_fee`                 | Phí gia hạn bài                                                  |
| `renewExpiredWindowDays` | number | `renew_expired_window_days` | Số ngày sau khi hết hạn vẫn được gia hạn (API gia hạn bài `EXPIRED` chưa hỗ trợ, xem mục renew) |
| `boostFee`               | number | `boost_fee`                 | Phí mỗi lần đẩy bài                                              |
| `boostDays`              | number | `boost_days`                | Số ngày được đẩy thêm sau mỗi lần đẩy                            |
| `boostMaxDays`           | number | `boost_max_days`            | `boostedUntil` không được vượt `now + boostMaxDays`              |
| `postDisplayDays`        | number | `post_display_days`         | Số ngày hiển thị sau lần duyệt đầu (cũng là số ngày cộng khi gia hạn) |
| `postDisplayDaysGold`    | number | `post_display_days_gold`    | Số ngày hiển thị sau lần duyệt đầu khi chủ bài từ hạng Vàng trở lên |

Hạn hiển thị (`expireAt`) và hạn đẩy (`boostedUntil`) của từng bài được trả kèm trong `ItemResponse` (xem `item.md`).

## GET /api/v1/listing-fees/held

Danh sách bài của người đang đăng nhập đang giữ phí (FR-15), để màn Ví hiện cạnh số "đang giữ". Gồm bài chờ duyệt
đang giữ phí đăng (`POST`) và bài đã duyệt đang chờ duyệt bản sửa giữ phí sửa (`EDIT`). Bài GIVE (không giữ gì), bài
đã được duyệt xong (phí đã trừ) và bài bị từ chối hoặc bị gỡ (phí đã nhả) không có trong danh sách. Tổng `pendingFee`
của các dòng bằng `heldCredit` trong `GET /api/v1/wallet`. Xếp bài mới cập nhật trước.

- Xác thực: **bắt buộc** Bearer Token. Chỉ trả bài của chính người gọi. Không có tham số.
- Thành công: `200`, mảng `HeldFeeItem` (rỗng nếu không giữ phí nào).
- Lỗi: `401 UNAUTHORIZED`.

```json
[
  {
    "itemId": 42,
    "title": "Xe đạp mini",
    "offerType": "SWAP",
    "status": "APPROVED",
    "pendingFee": 5,
    "feeType": "EDIT"
  },
  {
    "itemId": 40,
    "title": "Bàn học gỗ",
    "offerType": "SWAP",
    "status": "PENDING_REVIEW",
    "pendingFee": 5,
    "feeType": "POST"
  }
]
```

## HeldFeeItem

| Trường       | Kiểu   | Ý nghĩa                                                       |
|--------------|--------|---------------------------------------------------------------|
| `itemId`     | number | Id bài                                                        |
| `title`      | string | Tên bài                                                       |
| `offerType`  | string | `GIVE` hoặc `SWAP`                                            |
| `status`     | string | Trạng thái bài hiện tại (`PENDING_REVIEW`, `APPROVED`, ...)   |
| `pendingFee` | number | Số Credit đang giữ cho bài này                                |
| `feeType`    | string | `POST` (phí đăng) hoặc `EDIT` (phí sửa)                       |

## GET /api/v1/items/{id}/edit-quote

Báo trước lượt sửa tới của bài là miễn phí hay phải giữ bao nhiêu Credit (F07 bước 2, FR-26). Chỉ đọc, không giữ
tiền, không đổi dữ liệu. Số `amount` trả về đúng bằng số `PUT /items/{id}` sẽ giữ khi gửi bản sửa.

- Xác thực: **bắt buộc** Bearer Token. Id người dùng lấy từ token, không nhận từ client.
- Tham số đường dẫn: `id` — id bài đăng.
- Thành công: `200`, một `EditQuote`.
- Lỗi: `401 UNAUTHORIZED`; `403 LISTINGFEE_NOT_OWNER` khi không phải chủ bài; `404 LISTINGFEE_ITEM_NOT_FOUND` khi không
  có bài hoặc bài đã xoá.

```json
{ "free": false, "amount": 5, "reason": "PAID_EDIT" }
```

```json
{ "free": true, "amount": 0, "reason": "FIRST_FREE_EDIT" }
```

## EditQuote

| Trường   | Kiểu    | Ý nghĩa                                                                 |
|----------|---------|-------------------------------------------------------------------------|
| `free`   | boolean | `true` khi lượt sửa không giữ Credit nào (`amount == 0`)                |
| `amount` | number  | Số Credit sẽ giữ khi gửi bản sửa; `0` khi miễn phí                      |
| `reason` | string  | Lý do (enum `Reason`), xem bảng luật bên dưới                           |

Swagger hiển thị mô tả từng giá trị của `reason` (ghi trong `@Schema` của `EditQuote.Reason`):

| Giá trị                    | Ý nghĩa                                                                                     |
|----------------------------|---------------------------------------------------------------------------------------------|
| `NOT_YET_APPROVED`         | Bài chưa từng được duyệt và phí đăng đang được giữ (bài chờ duyệt lần đầu): sửa miễn phí.   |
| `REPOST_FEE`               | Bài chưa từng được duyệt và phí đăng đã được nhả (bị từ chối): gửi lại phải giữ lại `post_fee`. |
| `PENDING_REVIEW_OVERWRITE` | Bài đã duyệt và đang có một bản sửa chờ duyệt: sửa đè, không thêm phí, không thêm lượt.    |
| `FIRST_FREE_EDIT`          | Bài đã duyệt nhưng chưa dùng lượt sửa miễn phí: miễn phí một lần.                           |
| `PAID_EDIT`                | Đã dùng lượt sửa miễn phí sau duyệt: tính phí sửa (`edit_fee`).                             |

### Luật báo giá (xét từ trên xuống, gặp dòng đúng thì dừng)

"Đã từng được duyệt" xét bằng `approved_at`, không xét status hiện tại (R36): bản sửa bị từ chối không làm mất lượt
miễn phí.

| `reason`                   | Điều kiện                                                       | `free` | `amount`   |
|----------------------------|-----------------------------------------------------------------|--------|------------|
| `NOT_YET_APPROVED`         | Chưa từng duyệt, phí đăng đang giữ (bài chờ duyệt lần đầu)      | true   | 0          |
| `REPOST_FEE`               | Chưa từng duyệt, phí đăng đã nhả (bị từ chối): giữ lại phí đăng | false  | `post_fee` (bài `SWAP`); bài `GIVE`: `free = true`, 0 |
| `PENDING_REVIEW_OVERWRITE` | Đã duyệt, đang có bản sửa chờ duyệt: sửa đè (UC-14 2a)          | true   | 0          |
| `FIRST_FREE_EDIT`          | Đã duyệt, chưa dùng lượt sửa miễn phí (C14)                     | true   | 0          |
| `PAID_EDIT`                | Đã duyệt, đã dùng lượt miễn phí (C14b)                          | false  | `edit_fee` |

Khi Admin cấu hình `post_fee` hoặc `edit_fee` bằng 0 thì `REPOST_FEE` / `PAID_EDIT` trả `free = true`, `amount = 0`.

## POST /api/v1/items/{id}/renew

Gia hạn bài của chính mình (F08 bước 1, FR-28, BR-D03, BR-P12). Trừ phí gia hạn ngay (không giữ chỗ), kéo dài
`expire_at`. Nội dung không đổi nên **không** qua kiểm duyệt lại và **không** đổi status.

- Xác thực: **bắt buộc** Bearer Token. Id người dùng lấy từ token, không nhận từ client.
- Tham số đường dẫn: `id` là id bài đăng. Không có body.
- Điều kiện: người gọi là chủ bài; bài đang `APPROVED`. Gia hạn lúc nào cũng được, không cần chờ sắp hết hạn.
- Phí: `renew_fee` (mặc định 5 Credit), trừ từ số dư khả dụng (`credit_balance - held_credit`) và ghi `credit_ledger`
  loại `RENEW_FEE`. Phí cấu hình bằng 0 thì không đụng ví, không ghi `credit_ledger`.
- Hạn mới: `expire_at = max(now, expire_at cũ) + post_display_days` ngày (mặc định 30), áp dụng như nhau cho mọi hạng.
  Bài còn hạn thì cộng nối tiếp; bài đã quá hạn nhưng job chưa chuyển `EXPIRED`, hoặc chưa có `expire_at`, thì tính từ
  lúc gia hạn.
- Gọi nhiều lần (kể cả cùng lúc): mỗi lần thành công trừ phí một lần và cộng thêm một khoảng.
- Thành công: `200`, một `RenewResponse`.
- Lỗi: `401 UNAUTHORIZED`; `402 WALLET_INSUFFICIENT_BALANCE` khi không đủ Credit khả dụng; `403 LISTINGFEE_NOT_OWNER`;
  `404 LISTINGFEE_ITEM_NOT_FOUND` khi không có bài hoặc bài đã xoá; `409 LISTINGFEE_RENEW_NOT_ALLOWED` khi bài không ở
  `APPROVED` (đang chờ duyệt, bị từ chối, đã giữ chỗ, đã trao, đã hết hạn).
- Chưa hỗ trợ: gia hạn bài đã `EXPIRED` trong `renew_expired_window_days` (SRS F08, C27). Phần này cần module item
  chuyển `EXPIRED → APPROVED`, sẽ làm ở issue sau.

```json
{ "itemId": 31, "fee": 5, "expireAt": "2026-11-14T08:00:00Z" }
```

```json
{ "code": "LISTINGFEE_RENEW_NOT_ALLOWED", "message": "Chỉ gia hạn được bài đang hiển thị.", "traceId": "a1b2c3d4" }
```

## RenewResponse

| Trường     | Kiểu   | Ý nghĩa                                                       |
|------------|--------|---------------------------------------------------------------|
| `itemId`   | number | Bài vừa gia hạn                                               |
| `fee`      | number | Số Credit vừa trừ; `0` khi Admin cấu hình `renew_fee = 0`     |
| `expireAt` | string | Hạn hiển thị mới, ISO-8601 UTC                                |

## POST /api/v1/items/{id}/boost

Đẩy bài của chính mình thêm một gói (C15, FR-36, BR-P08; SRS 4.6, UC-16). Trừ phí ngay (không giữ chỗ), kéo dài
`boosted_until`. Không đổi status, không qua kiểm duyệt lại.

- Xác thực: **bắt buộc** Bearer Token. Id người dùng lấy từ token, không nhận từ client.
- Tham số đường dẫn: `id` là id bài đăng. Không có body; mỗi lần gọi là **một gói**, muốn thêm thì gọi tiếp.
- Điều kiện: người gọi là chủ bài; bài `APPROVED` và không bị ẩn (`is_active = true`); danh mục có `boostable = true`.
- Thời gian: `boosted_until = max(now, boosted_until cũ) + boost_days` ngày (mặc định 3). Kết quả phải
  `≤ now + boost_max_days` (mặc định 14) và `≤ expire_at`; vượt thì từ chối và **không** trừ tiền.
- Phí: `boost_fee` (mặc định 5 Credit), trừ từ số dư khả dụng và ghi `credit_ledger` loại `BOOST_FEE` gắn `item_id`.
  Phí cấu hình bằng 0 thì không đụng ví. Không hoàn phí khi bài bị gỡ, bị ẩn hay hết hạn (SRS 4.6).
- Thành công: `200`, một `BoostResponse`.
- Lỗi: `401 UNAUTHORIZED`; `402 WALLET_INSUFFICIENT_BALANCE`; `403 LISTINGFEE_NOT_OWNER`; `404 LISTINGFEE_ITEM_NOT_FOUND`;
  `404 CATALOG_CATEGORY_NOT_FOUND` khi danh mục của bài đã bị ẩn; `409 LISTINGFEE_BOOST_NOT_ALLOWED`,
  `409 LISTINGFEE_CATEGORY_NOT_BOOSTABLE`, `409 LISTINGFEE_BOOST_LIMIT_EXCEEDED`, `409 LISTINGFEE_BOOST_EXCEEDS_EXPIRY`.
- Xếp bài đang đẩy lên đầu kết quả tìm kiếm thuộc module item/search, chưa làm ở endpoint này.

```json
{ "itemId": 31, "boostedUntil": "2026-10-20T08:00:00Z", "creditBalance": 15 }
```

## BoostResponse

| Trường          | Kiểu   | Ý nghĩa                                                 |
|-----------------|--------|---------------------------------------------------------|
| `itemId`        | number | Bài vừa đẩy                                             |
| `boostedUntil`  | string | Thời điểm hết đẩy mới, ISO-8601 UTC                     |
| `creditBalance` | number | Số dư Credit (`credit_balance`) của chủ bài sau khi trừ |

## Mã lỗi

| code                          | HTTP | Khi nào                                                      | Lộ ra qua API                 |
|-------------------------------|------|--------------------------------------------------------------|-------------------------------|
| `LISTINGFEE_ITEM_NOT_FOUND`   | 404  | Không có bài hoặc bài đã xoá mềm                             | `GET /items/{id}/edit-quote`, `POST /items/{id}/renew`, `POST /items/{id}/boost` |
| `LISTINGFEE_NOT_OWNER`        | 403  | Người gọi không phải chủ bài                                 | `GET /items/{id}/edit-quote`, `POST /items/{id}/renew`, `POST /items/{id}/boost` |
| `LISTINGFEE_RENEW_NOT_ALLOWED`| 409  | Gia hạn bài không ở trạng thái `APPROVED`                    | `POST /items/{id}/renew`      |
| `LISTINGFEE_BOOST_NOT_ALLOWED`| 409  | Đẩy bài không `APPROVED` hoặc đang bị ẩn                     | `POST /items/{id}/boost`      |
| `LISTINGFEE_CATEGORY_NOT_BOOSTABLE` | 409 | Danh mục của bài có `boostable = false`                | `POST /items/{id}/boost`      |
| `LISTINGFEE_BOOST_LIMIT_EXCEEDED`   | 409 | `boosted_until` mới vượt `now + boost_max_days`        | `POST /items/{id}/boost`      |
| `LISTINGFEE_BOOST_EXCEEDS_EXPIRY`   | 409 | `boosted_until` mới vượt `expire_at` của bài           | `POST /items/{id}/boost`      |
| `LISTINGFEE_ALREADY_HELD`     | 409  | Tạo bài mà bài đã có khoản phí đang giữ                      | qua API của module gọi        |
| `LISTINGFEE_NOTHING_HELD`     | 409  | Duyệt / từ chối / xoá mà bài không có khoản phí đang giữ     | qua API của module gọi        |
| `LISTINGFEE_EDIT_NOT_ALLOWED` | 409  | Dự phòng; hiện chưa dùng (kiểm status bài là việc của item)  | -                             |

Không đủ Credit khi giữ phí, gia hạn hoặc đẩy bài: `402 WALLET_INSUFFICIENT_BALANCE` (xem `wallet.md`).

## Hàm nội bộ `PostFeeService` (cho module khác, không phải endpoint)

Mọi hàm chạy trong transaction của người gọi (REQUIRED). Khoá luôn theo thứ tự dòng `users` rồi dòng `items`.

| Hàm                               | Người gọi                         | Việc làm                                                                 |
|-----------------------------------|-----------------------------------|--------------------------------------------------------------------------|
| `quoteEdit(itemId, userId)`       | item (PUT), endpoint ở trên       | Trả `EditQuote`; chỉ đọc, không khoá                                     |
| `holdPostFee(itemId, userId)`     | item (tạo bài)                    | Giữ `post_fee` (bài `SWAP`) hoặc 0 (bài `GIVE`), `fee_state = HELD`, loại POST |
| `holdEditFee(itemId, userId)`     | item (sửa bài)                    | Giữ đúng `amount` của `quoteEdit`; đang HELD thì không làm gì            |
| `chargeHeldFee(itemId)`           | review (duyệt)                    | Trừ khoản đang giữ, `fee_state = CHARGED`; loại POST thì đặt `expire_at` |
| `releaseHeldFee(itemId, reason)`  | review (từ chối), item (xoá bài)  | Nhả khoản đang giữ, `fee_state = RELEASED`                               |

Ghi chú cho người gọi:

- **ItemService (PUT /items/{id})**: gọi `holdEditFee` cho **mọi** lần gửi bản sửa (không chỉ khi có phí), sau khi đã
  qua tầng 1. Hàm tự quyết giữ bao nhiêu: lượt miễn phí vẫn ghi `fee_state = HELD, pending_fee = 0,
  pending_fee_type = EDIT` (không đụng ví) để lúc duyệt biết đây là bản sửa. Bài đang chờ duyệt (đang HELD) thì sửa
  đè, không giữ thêm, không lỗi. `REPOST_FEE` giữ loại POST, các lượt sửa sau duyệt giữ loại EDIT.
- **ReviewService**: mọi bài đang chờ duyệt đều ở `fee_state = HELD`, nên luôn gọi được `chargeHeldFee` /
  `releaseHeldFee`. Khoản giữ bằng 0 thì không đụng ví và không ghi `credit_ledger`.
- **Lượt sửa miễn phí** chỉ bị tính (`free_edit_used = true`) khi Admin **duyệt** một bản sửa loại EDIT. Từ chối thì
  nhả phí và không tính lượt (BR-D07 (4), R36).
- Phí 0 (bài `GIVE` khi đăng hoặc gửi lại sau từ chối, hoặc Admin cấu hình `post_fee` / `edit_fee` = 0): vẫn ghi
  `fee_state = HELD` với `pending_fee = 0`, không gọi ví. Nhờ vậy bài `GIVE` vẫn qua `chargeHeldFee` lúc duyệt: được
  đặt `expire_at`, `post_fee_paid = true`, không có dòng `credit_ledger`.
- **Thời hạn hiển thị**: khi duyệt khoản giữ loại POST (chỉ có ở lần duyệt đầu, kể cả bài gửi lại sau khi bị từ chối
  lần đầu), `chargeHeldFee` đặt `expire_at = lúc duyệt + N` ngày, với N = `post_display_days_gold` (45) khi
  `ReputationService.getRank(donorId)` là `GOLD` hoặc `DIAMOND`, còn lại `post_display_days` (30) (BR-D03, FR-29).
  Hạng xét tại lúc duyệt; hạng đổi sau đó không tính lại `expire_at`. Duyệt bản sửa (loại EDIT) không đổi `expire_at`.
  Gia hạn (`renew`) luôn cộng `post_display_days`, không phụ thuộc hạng (BR-P12).

## Job nền

Không phải endpoint. Profile `test` đặt `app.jobs.enabled=false` nên bean job không được tạo; test gọi thẳng service.

| Job | Lịch | Service | Việc làm |
|-----|------|---------|----------|
| `ItemExpiryJob` (#89) | `0 0 * * * *` (phút 0 mỗi giờ) | `ItemExpiryService.expireOverdueItems(now)` | Bài hết hạn → `EXPIRED` |

### Hết hạn bài (FR-29, BR-D03)

- **Chọn**: `status = APPROVED`, `expire_at < now`, `is_deleted = false` (bài đang ẩn `is_active = false` vẫn hết hạn).
  Đọc theo lô 100 id, phân trang keyset (`id > id cuối lô trước`).
- **Không chọn**: `RESERVED` (đang giao dịch; `RESERVED → EXPIRED` không hợp lệ), `PENDING_REVIEW` (bản sửa chờ duyệt
  lại), `EXPIRED`, `TRADED`, `REJECTED`. Bài quay lại `APPROVED` (huỷ giao dịch, duyệt bản sửa) mà đã quá `expire_at`
  thì lượt kế tiếp xử lý.
- **Mỗi bài một transaction**: khoá dòng `items` (`lockById`), kiểm lại `status = APPROVED` và `expire_at < now`, rồi
  gọi `ItemService.markExpired(itemId)`; không đổi thẳng cột `status`. Khoá để không hết hạn bài vừa được gia hạn
  cùng lúc (`renew` cũng khoá dòng này). Không khoá `users` nên không deadlock với các hàm phí.
- **Lỗi**: một bài lỗi chỉ rollback chính nó, ghi log WARN, các bài khác vẫn chạy; bài đó vẫn `APPROVED` nên lượt sau
  thử lại. Hàm trả số bài đã chuyển; job ghi log INFO số này mỗi lượt.
- **Idempotent**: điều kiện chọn gồm trạng thái hiện tại, chạy lại không có tác dụng kép.
- Không xoá `boosted_until`: luật đẩy bài bảo đảm `boosted_until ≤ expire_at`, nên lúc hết hạn mốc đẩy đã qua.
