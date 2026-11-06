# API module listingfee

Quy ước chung theo Lộ trình mục 3.4: tiền tố `/api/v1`, thời gian ISO-8601 UTC, thành công trả thẳng DTO (không bọc),
lỗi theo định dạng thống nhất (xem `wallet.md`).

Người sở hữu: BE1. Module giữ, trừ, nhả phí đăng bài và phí sửa bài (C01, C02, C14, C14b), đặt thời hạn hiển thị
`expire_at` khi duyệt lần đầu, gia hạn bài (C16) và đẩy bài (C15). Mọi mức phí và số ngày đọc từ ConfigService
(`post_fee`, `post_fee_over_quota`, `edit_fee`, `renew_fee`, `post_display_days`, `post_display_days_gold`, `boost_fee`, `boost_days`,
`boost_max_days`, `boost_cooldown_days`, `boost_max_per_user`, `boost_max_active`, `boost_ending_notice_hours`), không viết
cứng. Có hai job nền: chuyển bài quá hạn sang `EXPIRED` và thông báo cho chủ bài về đợt đẩy (mục "Job nền"). Đổi tiền
chỉ qua `wallet.CreditService`.

Phí đăng (`post_fee` hoặc `post_fee_over_quota`) chỉ áp dụng cho bài Trao đổi (`SWAP`); bài Cho tặng (`GIVE`) miễn phí đăng, kể cả khi gửi lại
bài bị từ chối. Bài Trao đổi vượt ngưỡng phí thường theo hạng vẫn đăng được với phí cao hơn. Khi đổi hình thức, bài đã trả phí đăng Trao đổi không được hoàn và không phải trả lại; bài Cho chưa trả phí chuyển sang Trao đổi giữ 5 hoặc 10 Credit theo ngưỡng hiện tại.

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
  "boostFee": 5,
  "boostDays": 3,
  "boostMaxDays": 6,
  "postDisplayDays": 30,
  "postDisplayDaysGold": 45
}
```

## ListingFeeConfigResponse

| Trường                   | Kiểu   | Khoá cấu hình               | Ý nghĩa                                                          |
|--------------------------|--------|-----------------------------|------------------------------------------------------------------|
| `postFeeSwap`            | number | `post_fee`                  | Phí thường của bài SWAP; phí bài kế tiếp theo tài khoản ở `GET /me/posting-quota` |
| `postFeeGive`            | number | (không có)                  | Luôn `0`: bài GIVE đăng miễn phí, quy tắc cố định                |
| `editFee`                | number | `edit_fee`                  | Phí mỗi lượt sửa sau khi đã dùng lượt sửa miễn phí               |
| `renewFee`               | number | `renew_fee`                 | Phí gia hạn mọi bài, mặc định 5 Credit                            |
| `boostFee`               | number | `boost_fee`                 | Phí mỗi lần đẩy bài                                              |
| `boostDays`              | number | `boost_days`                | Số ngày được đẩy thêm sau mỗi lần đẩy                            |
| `boostMaxDays`           | number | `boost_max_days`            | Số ngày đẩy liên tục tối đa trong một đợt, tính từ lúc bắt đầu đợt (mặc định 6) |
| `postDisplayDays`        | number | `post_display_days`         | Số ngày hiển thị sau lần duyệt đầu (cũng là số ngày cộng khi gia hạn) |
| `postDisplayDaysGold`    | number | `post_display_days_gold`    | Số ngày hiển thị sau lần duyệt đầu khi chủ bài từ hạng Vàng trở lên |

Hạn hiển thị (`expireAt`) và hạn đẩy (`boostedUntil`) của từng bài được trả kèm trong `ItemResponse` (xem `item.md`).

`ListingFeeQueryService.findDisplayWindows` (hàm nội bộ cho module item) còn trả sẵn ba mốc của đợt đẩy để FE chỉ
hiển thị, không tự tính: `boostStartedAt` (mốc bắt đầu đợt gần nhất), `boostChainEndsAt` (`boostStartedAt +
boost_max_days`) và `boostCooldownUntil` (`boostedUntil + boost_cooldown_days`); bài chưa từng đẩy thì cả ba là `null`.
Module item map ba trường này vào `ItemResponse`, nhưng chỉ trả ở "Bài của tôi" và chi tiết bài khi chủ bài xem; các
API công khai trả `null` (xem `item.md`, mục `ItemResponse`).

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
- Tham số đường dẫn: `id` — id bài đăng. Không nhận body hay query `offerType` (hình thức là thuộc tính cố định theo BD-10).
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
| `REPOST_FEE`               | Bài chưa từng được duyệt và phí đăng đã được nhả: gửi lại tính phí đăng theo hình thức và số bài đang hoạt động (Trao đổi 5 hoặc 10; Cho 0). |
| `PENDING_REVIEW_OVERWRITE` | Bài đã duyệt và đang có một bản sửa chờ duyệt: sửa đè, không thêm phí, không thêm lượt.    |
| `FIRST_FREE_EDIT`          | Bài đã duyệt nhưng chưa dùng lượt sửa miễn phí: miễn phí một lần.                           |
| `PAID_EDIT`                | Đã dùng lượt sửa miễn phí sau duyệt: tính phí sửa (`edit_fee`).                             |

### Luật báo giá (xét từ trên xuống, gặp dòng đúng thì dừng)

"Đã từng được duyệt" xét bằng `approved_at`, không xét status hiện tại (R36): bản sửa bị từ chối không làm mất lượt
miễn phí.

| `reason`                   | Điều kiện                                                       | `free` | `amount`   |
|----------------------------|-----------------------------------------------------------------|--------|------------|
| `NOT_YET_APPROVED`         | Chưa từng duyệt, phí đăng đang giữ (bài chờ duyệt lần đầu)      | true   | 0          |
| `REPOST_FEE`               | Chưa từng duyệt, phí đăng đã nhả (bị từ chối): giữ lại phí đăng | Theo mức phí | `post_fee` hoặc `post_fee_over_quota` (bài `SWAP`); bài `GIVE`: `free = true`, 0 |
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
- Phí: mọi bài `GIVE` và `SWAP` đều trừ `renew_fee` (mặc định 5 Credit) từ số dư khả dụng (`credit_balance - held_credit`) và ghi `credit_ledger` loại `RENEW_FEE`. Phí cấu hình bằng 0 thì không đụng ví.
- Hạn mới: `expire_at = max(now, expire_at cũ) + post_display_days` ngày (mặc định 30), áp dụng như nhau cho mọi hạng.
  Bài còn hạn thì cộng nối tiếp; bài đã quá hạn nhưng job chưa chuyển `EXPIRED`, hoặc chưa có `expire_at`, thì tính từ
  lúc gia hạn.
- Gọi nhiều lần (kể cả cùng lúc): mỗi lần thành công trừ phí một lần và cộng thêm một khoảng.
- Thành công: `200`, một `RenewResponse`.
- Lỗi: `401 UNAUTHORIZED`; `402 WALLET_INSUFFICIENT_BALANCE` khi không đủ Credit khả dụng; `403 LISTINGFEE_NOT_OWNER`;
  `404 LISTINGFEE_ITEM_NOT_FOUND` khi không có bài hoặc bài đã xoá; `409 LISTINGFEE_RENEW_NOT_ALLOWED` khi bài không ở
  `APPROVED` (đang chờ duyệt, bị từ chối, đã giữ chỗ, đã trao, đã hết hạn).
- Chưa hỗ trợ: gia hạn bài đã `EXPIRED` (SRS F08, C27). Khoá `renew_expired_window_days` đã xoá mềm ở L13 vì chưa có luồng nào đọc; làm luồng này thì thêm lại khoá bằng migration mới. Phần này cần module item
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
- Thời gian: `boosted_until = max(now, boosted_until cũ) + boost_days` ngày (mặc định 3). Bài đang đẩy thì cộng nối
  từ mốc cũ (mua thêm); chưa đẩy hoặc đã hết đẩy thì tính từ `now` và mở **đợt mới**.
- Phí: `boost_fee` (mặc định 5 Credit), trừ từ số dư khả dụng và ghi `credit_ledger` loại `BOOST_FEE` gắn `item_id`.
  Phí cấu hình bằng 0 thì không đụng ví. Không hoàn phí khi bài bị gỡ, bị ẩn hay hết hạn (SRS 4.6).
- Thành công: `200`, một `BoostResponse`.
- Lỗi: `401 UNAUTHORIZED`; `402 WALLET_INSUFFICIENT_BALANCE`; `403 LISTINGFEE_NOT_OWNER`; `404 LISTINGFEE_ITEM_NOT_FOUND`;
  `404 CATALOG_CATEGORY_NOT_FOUND` khi danh mục của bài đã bị ẩn; `409 LISTINGFEE_BOOST_NOT_ALLOWED`,
  `409 LISTINGFEE_CATEGORY_NOT_BOOSTABLE`, `409 LISTINGFEE_BOOST_COOLDOWN`, `409 LISTINGFEE_BOOST_CHAIN_LIMIT`,
  `409 LISTINGFEE_BOOST_EXCEEDS_EXPIRY`, `409 LISTINGFEE_BOOST_USER_LIMIT`, `409 LISTINGFEE_BOOST_SOLD_OUT`.
- Xếp bài đang đẩy lên đầu kết quả tìm kiếm thuộc module item/search, chưa làm ở endpoint này.

### Luật mua gói đẩy

"Bài đang đẩy" = `boosted_until > now`, `status = APPROVED`, `is_active = true`, chưa xoá mềm và (`expire_at` trống
hoặc `expire_at > now`). Bài bị ẩn hay hết hạn hiển thị không còn tính là đang đẩy nên trả suất ngay.

| Luật | Mặc định | Khoá cấu hình |
|------|----------|---------------|
| Mỗi gói | 3 ngày, 5 Credit | `boost_days`, `boost_fee` |
| Một đợt đẩy liên tục tối đa, tính từ lúc **bắt đầu đợt** (`boost_started_at`), không tính từ lúc mua thêm | 6 ngày (2 gói) | `boost_max_days` |
| Hết đợt phải nghỉ trước khi mở đợt mới, tính từ `boosted_until` cũ | 3 ngày | `boost_cooldown_days` |
| Số bài đang đẩy cùng lúc của một người | 3 bài | `boost_max_per_user` |
| Số bài đang đẩy cùng lúc trên toàn hệ thống | 30 bài | `boost_max_active` |

- Mua thêm cho bài **đang đẩy** giữ nguyên `boost_started_at` và **không chiếm suất mới**: bỏ qua hai giới hạn số bài.
- Thứ tự kiểm (gặp lỗi đầu tiên thì dừng), tất cả chạy **trước** khi trừ tiền nên bị từ chối thì không mất Credit:
  1. bài `APPROVED`, không ẩn (`LISTINGFEE_BOOST_NOT_ALLOWED`); danh mục cho phép đẩy (`LISTINGFEE_CATEGORY_NOT_BOOSTABLE`);
  2. không đang nghỉ: `boosted_until ≤ now < boosted_until + boost_cooldown_days` thì `LISTINGFEE_BOOST_COOLDOWN`;
  3. mốc mới `≤ boost_started_at + boost_max_days` (đợt mới thì `boost_started_at = now`), vượt thì
     `LISTINGFEE_BOOST_CHAIN_LIMIT`;
  4. mốc mới `≤ expire_at` (`LISTINGFEE_BOOST_EXCEEDS_EXPIRY`);
  5. chỉ khi bài chưa đang đẩy: số bài đang đẩy của chủ bài `< boost_max_per_user` (`LISTINGFEE_BOOST_USER_LIMIT`),
     rồi số bài đang đẩy toàn hệ thống `< boost_max_active` (`LISTINGFEE_BOOST_SOLD_OUT`);
  6. trừ `boost_fee` (`402 WALLET_INSUFFICIENT_BALANCE` nếu thiếu).
- Đồng thời: khoá dòng `users` của chủ bài, rồi dòng `items`, rồi một advisory lock chung cho suất đẩy
  (`pg_advisory_xact_lock`). Hai người cùng mua suất cuối thì chạy lần lượt, người sau đếm lại và nhận
  `LISTINGFEE_BOOST_SOLD_OUT`.
- Bài đang đẩy bị ẩn: job thông báo cắt `boosted_until` về lúc dừng, không hoàn phí; thời gian nghỉ tính từ mốc dừng
  này (mục "Job nền").

```json
{ "itemId": 31, "boostedUntil": "2026-10-20T08:00:00Z", "creditBalance": 15 }
```

## BoostResponse

| Trường          | Kiểu   | Ý nghĩa                                                 |
|-----------------|--------|---------------------------------------------------------|
| `itemId`        | number | Bài vừa đẩy                                             |
| `boostedUntil`  | string | Thời điểm hết đẩy mới, ISO-8601 UTC                     |
| `creditBalance` | number | Số dư Credit (`credit_balance`) của chủ bài sau khi trừ |

## GET /api/v1/items/{id}/boost-quote

Báo trước một lần đẩy bài để FE hiện phí, các mốc của đợt đẩy, số suất còn trống và lý do nếu chưa đẩy được. Chỉ đọc:
không khoá, không trừ tiền, không đổi dữ liệu. Tính bằng đúng các hàm của `POST /items/{id}/boost` (cùng công thức mốc
mới, cùng thứ tự kiểm, cùng query đếm suất, cùng `boost_fee`), nên số báo giá khớp số thật tại thời điểm gọi. Mọi mốc
thời gian do backend tính sẵn; FE chỉ hiển thị. Không trả số dư ví.

- Xác thực: **bắt buộc** Bearer Token. Id người dùng lấy từ token, không nhận từ client.
- Tham số đường dẫn: `id` là id bài đăng.
- Thành công: `200`, một `BoostQuote`. Bài không đẩy được vẫn trả `200` với `allowed = false`, `reason` và `reasonCode`.
- Lỗi: `401 UNAUTHORIZED`; `403 LISTINGFEE_NOT_OWNER`; `404 LISTINGFEE_ITEM_NOT_FOUND`.

Mở đợt mới, gọi lúc `2026-11-06T08:00:00Z`:

```json
{
  "fee": 5,
  "newBoostedUntil": "2026-11-09T08:00:00Z",
  "allowed": true,
  "reason": null,
  "reasonCode": null,
  "boostStartedAt": "2026-11-06T08:00:00Z",
  "chainEndsAt": "2026-11-12T08:00:00Z",
  "cooldownUntil": "2026-11-12T08:00:00Z",
  "extending": false,
  "slotsLeft": 12,
  "earliestSlotAt": null,
  "userActiveBoosts": 1,
  "userEarliestBoostEndAt": null,
  "expireAt": "2026-12-06T08:00:00Z"
}
```

Mua thêm khi đang đẩy (đợt bắt đầu `2026-11-06T08:00:00Z`, đang đẩy tới `2026-11-09T08:00:00Z`), gọi lúc
`2026-11-08T10:00:00Z`:

```json
{
  "fee": 5,
  "newBoostedUntil": "2026-11-12T08:00:00Z",
  "allowed": true,
  "reason": null,
  "reasonCode": null,
  "boostStartedAt": "2026-11-06T08:00:00Z",
  "chainEndsAt": "2026-11-12T08:00:00Z",
  "cooldownUntil": "2026-11-15T08:00:00Z",
  "extending": true,
  "slotsLeft": 0,
  "earliestSlotAt": "2026-11-08T15:30:00Z",
  "userActiveBoosts": 3,
  "userEarliestBoostEndAt": "2026-11-09T08:00:00Z",
  "expireAt": "2026-12-06T08:00:00Z"
}
```

Hết suất toàn hệ thống, gọi lúc `2026-11-06T08:00:00Z`:

```json
{
  "fee": 5,
  "newBoostedUntil": "2026-11-09T08:00:00Z",
  "allowed": false,
  "reason": "Đã hết suất đẩy bài, vui lòng thử lại sau.",
  "reasonCode": "LISTINGFEE_BOOST_SOLD_OUT",
  "boostStartedAt": "2026-11-06T08:00:00Z",
  "chainEndsAt": "2026-11-12T08:00:00Z",
  "cooldownUntil": null,
  "extending": false,
  "slotsLeft": 0,
  "earliestSlotAt": "2026-11-06T15:30:00Z",
  "userActiveBoosts": 1,
  "userEarliestBoostEndAt": null,
  "expireAt": "2026-12-06T08:00:00Z"
}
```

## BoostQuote

| Trường                   | Kiểu           | Ý nghĩa                                                                              |
|--------------------------|----------------|--------------------------------------------------------------------------------------|
| `fee`                    | number         | Số Credit sẽ bị trừ khi đẩy (`boost_fee`)                                            |
| `newBoostedUntil`        | string         | Mốc hết đẩy mới nếu đẩy ngay: `max(now, boosted_until cũ) + boost_days`               |
| `allowed`                | boolean        | `true` khi đẩy ngay được (không tính số dư: thiếu Credit chỉ lộ ra ở `POST` bằng `402`) |
| `reason`                 | string \| null | Lý do không đẩy được, tiếng Việt, hiển thị thẳng; `null` khi `allowed = true`        |
| `reasonCode`             | string \| null | Mã lỗi mà `POST /boost` sẽ trả, để FE chọn popup; `null` khi `allowed = true`        |
| `boostStartedAt`         | string         | Mốc bắt đầu đợt: của đợt đang chạy nếu bài đang đẩy, hoặc `now` nếu là đợt mới       |
| `chainEndsAt`            | string         | Mốc đẩy muộn nhất của đợt: `boostStartedAt + boost_max_days`                         |
| `cooldownUntil`          | string \| null | Mốc được mở đợt mới, xem bảng dưới                                                   |
| `extending`              | boolean        | Bài đang được đẩy (mua thêm, không chiếm suất mới)                                   |
| `slotsLeft`              | number         | Số suất còn trống toàn hệ thống: `boost_max_active - số bài đang đẩy`, không âm      |
| `earliestSlotAt`         | string \| null | Lúc sớm nhất có thêm suất (bài đang đẩy hết sớm nhất); chỉ có khi `slotsLeft = 0`    |
| `userActiveBoosts`       | number         | Số bài của người gọi đang được đẩy                                                   |
| `userEarliestBoostEndAt` | string \| null | Lúc sớm nhất một bài của người gọi hết đẩy; chỉ có khi đã đủ `boost_max_per_user`     |
| `expireAt`               | string \| null | Hạn hiển thị của bài, để popup "vượt hạn hiển thị" ghi được ngày                     |

Mọi mốc là ISO-8601 UTC. Cách tính `cooldownUntil` (cộng `boost_cooldown_days`):

| Trường hợp | `cooldownUntil` |
|------------|-----------------|
| Được đẩy (`allowed = true`) | `newBoostedUntil + boost_cooldown_days` (nếu mua gói này rồi không mua thêm) |
| Không được đẩy, bài đang đẩy hoặc đang nghỉ | `boosted_until hiện tại + boost_cooldown_days` |
| Bài chưa từng đẩy, hoặc đã nghỉ đủ | `null` |

`reasonCode` và dữ liệu FE nên dùng cho từng popup:

| `reasonCode` | Khi nào | Trường nên hiển thị |
|--------------|---------|---------------------|
| `LISTINGFEE_BOOST_NOT_ALLOWED` | Bài không `APPROVED` hoặc đang bị ẩn | `reason` |
| `LISTINGFEE_CATEGORY_NOT_BOOSTABLE` | Danh mục không `boostable` | `reason` |
| `CATALOG_CATEGORY_NOT_FOUND` | Danh mục của bài đã bị ẩn | `reason` |
| `LISTINGFEE_BOOST_COOLDOWN` | Đang trong thời gian nghỉ | `cooldownUntil` |
| `LISTINGFEE_BOOST_CHAIN_LIMIT` | Mua thêm sẽ vượt mốc cuối đợt | `chainEndsAt`, `cooldownUntil` |
| `LISTINGFEE_BOOST_EXCEEDS_EXPIRY` | Mốc mới vượt hạn hiển thị | `expireAt` |
| `LISTINGFEE_BOOST_USER_LIMIT` | Đã đủ số bài đang đẩy của một người | `userActiveBoosts`, `userEarliestBoostEndAt` |
| `LISTINGFEE_BOOST_SOLD_OUT` | Hết suất toàn hệ thống | `earliestSlotAt` |

## GET /api/v1/items/{id}/renew-quote

Báo trước một lần gia hạn: phí, hạn mới và lý do nếu chưa gia hạn được. Chỉ đọc, dùng chung hàm tính phí và hàm tính
hạn mới với `POST /items/{id}/renew`.

- Xác thực: **bắt buộc** Bearer Token. Id người dùng lấy từ token.
- Tham số đường dẫn: `id` là id bài đăng.
- Thành công: `200`, một `RenewQuote`. Bài không gia hạn được vẫn trả `200` với `allowed = false` và `reason`.
- Lỗi: `401 UNAUTHORIZED`; `403 LISTINGFEE_NOT_OWNER`; `404 LISTINGFEE_ITEM_NOT_FOUND`.

```json
{ "fee": 5, "newExpireAt": "2026-11-28T08:00:00Z", "allowed": true, "reason": null }
```

## RenewQuote

| Trường        | Kiểu           | Ý nghĩa                                                                          |
|---------------|----------------|----------------------------------------------------------------------------------|
| `fee`         | number         | Số Credit sẽ bị trừ khi gia hạn; `0` là miễn phí                                 |
| `newExpireAt` | string         | Hạn mới nếu gia hạn ngay: `max(now, expire_at cũ) + post_display_days`, ISO-8601 UTC |
| `allowed`     | boolean        | `true` khi bài đang `APPROVED`                                                   |
| `reason`      | string \| null | Lý do không gia hạn được, tiếng Việt; `null` khi `allowed = true`                |

`fee` là `renew_fee` cho mọi bài, kể cả bài `GIVE`. Báo giá và gia hạn thật dùng chung một hàm tính phí; FE chỉ hiện
"Miễn phí" khi Admin cấu hình `renew_fee = 0`.

## Mã lỗi

| code                          | HTTP | Khi nào                                                      | Lộ ra qua API                 |
|-------------------------------|------|--------------------------------------------------------------|-------------------------------|
| `LISTINGFEE_ITEM_NOT_FOUND`   | 404  | Không có bài hoặc bài đã xoá mềm                             | `GET /items/{id}/edit-quote`, `POST /items/{id}/renew`, `POST /items/{id}/boost` |
| `LISTINGFEE_NOT_OWNER`        | 403  | Người gọi không phải chủ bài                                 | `GET /items/{id}/edit-quote`, `POST /items/{id}/renew`, `POST /items/{id}/boost` |
| `LISTINGFEE_RENEW_NOT_ALLOWED`| 409  | Gia hạn bài không ở trạng thái `APPROVED`                    | `POST /items/{id}/renew`      |
| `LISTINGFEE_BOOST_NOT_ALLOWED`| 409  | Đẩy bài không `APPROVED` hoặc đang bị ẩn                     | `POST /items/{id}/boost`      |
| `LISTINGFEE_CATEGORY_NOT_BOOSTABLE` | 409 | Danh mục của bài có `boostable = false`                | `POST /items/{id}/boost`      |
| `LISTINGFEE_BOOST_COOLDOWN`         | 409 | Đợt đẩy đã hết chưa quá `boost_cooldown_days` ngày     | `POST /items/{id}/boost`      |
| `LISTINGFEE_BOOST_CHAIN_LIMIT`      | 409 | `boosted_until` mới vượt `boost_started_at + boost_max_days` | `POST /items/{id}/boost` |
| `LISTINGFEE_BOOST_EXCEEDS_EXPIRY`   | 409 | `boosted_until` mới vượt `expire_at` của bài           | `POST /items/{id}/boost`      |
| `LISTINGFEE_BOOST_USER_LIMIT`       | 409 | Mở đợt mới khi chủ bài đã có `boost_max_per_user` bài đang đẩy | `POST /items/{id}/boost` |
| `LISTINGFEE_BOOST_SOLD_OUT`         | 409 | Mở đợt mới khi toàn hệ thống đã có `boost_max_active` bài đang đẩy | `POST /items/{id}/boost` |
| `LISTINGFEE_ALREADY_HELD`     | 409  | Tạo bài mà bài đã có khoản phí đang giữ                      | qua API của module gọi        |
| `LISTINGFEE_NOTHING_HELD`     | 409  | Duyệt / từ chối / xoá mà bài không có khoản phí đang giữ     | qua API của module gọi        |
| `LISTINGFEE_EDIT_NOT_ALLOWED` | 409  | Dự phòng; hiện chưa dùng (kiểm status bài là việc của item)  | -                             |

Không đủ Credit khi giữ phí, gia hạn hoặc đẩy bài: `402 WALLET_INSUFFICIENT_BALANCE` (xem `wallet.md`).

Các mã `409` của đẩy bài cũng xuất hiện trong `reasonCode` của `GET /items/{id}/boost-quote` (trả `200`, không ném lỗi).
Thông điệp của bốn mã `COOLDOWN`, `CHAIN_LIMIT`, `USER_LIMIT`, `SOLD_OUT` không chứa ngày giờ; mốc cụ thể lấy từ báo giá.

## Hàm nội bộ `PostFeeService` (cho module khác, không phải endpoint)

Mọi hàm chạy trong transaction của người gọi (REQUIRED). Khoá luôn theo thứ tự dòng `users` rồi dòng `items`.

| Hàm                               | Người gọi                         | Việc làm                                                                 |
|-----------------------------------|-----------------------------------|--------------------------------------------------------------------------|
| `quoteEdit(itemId, userId)`       | item (PUT), endpoint ở trên       | Trả `EditQuote`; chỉ đọc, không khoá                                     |
| `holdPostFee(itemId, userId)`     | item (tạo bài)                    | Giữ `post_fee` hoặc `post_fee_over_quota` (bài `SWAP`) hoặc 0 (bài `GIVE`), `fee_state = HELD`, loại POST |
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
| `BoostNotificationJob` | `0 0 * * * *` (phút 0 mỗi giờ) | `BoostNotificationService.stopHiddenBoosts`, `notifyEndingSoon`, `notifyEnded` (theo thứ tự này) | Dừng đẩy bài bị ẩn; báo chủ bài sắp hết đẩy, đã hết đẩy |

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

### Thông báo đợt đẩy (C15, SRS 4.6)

Gửi qua `NotificationService.notify` tới chủ bài, `link = /items/{id}`. Không thêm bảng hay cột.

| `type` | Chọn bài | Tiêu đề | Nội dung |
|--------|----------|---------|----------|
| `BOOST_STOPPED` | Còn hạn đẩy (`boosted_until > now`), `is_active = false`, chưa xoá mềm | Bài đã dừng đẩy | Bài «tên» bị ẩn nên đã dừng đẩy; thời gian đẩy còn lại không được hoàn phí. |
| `BOOST_ENDING_SOON` | Bài đang đẩy, hết đẩy trong `boost_ending_notice_hours` giờ tới (mặc định 24) | Bài sắp hết thời gian đẩy | Bài «tên» hết đẩy lúc HH:mm dd/MM. Bạn còn mua thêm được tới HH:mm dd/MM. |
| `BOOST_ENDED` | Bài `APPROVED`, đang hiển thị, còn hạn hiển thị, hết đẩy trong `boost_cooldown_days` ngày qua | Bài đã hết thời gian đẩy | Bài «tên» đã hết thời gian đẩy. Có thể đẩy lại từ HH:mm dd/MM. |

- Giờ theo `Asia/Ho_Chi_Minh`, định dạng `HH:mm dd/MM` như các thông báo khác. Bài không có tên thì ghi "Bài của bạn".
- `BOOST_ENDING_SOON`: câu "Bạn còn mua thêm được tới [boost_started_at + boost_max_days]" chỉ có khi thêm một gói
  (`boosted_until + boost_days`) vẫn không vượt mốc cuối đợt.
- `BOOST_ENDED`: mốc đẩy lại = `boosted_until + boost_cooldown_days`.
- `BOOST_STOPPED`: trong cùng transaction, trước tiên gán `boosted_until = now` bằng một câu `UPDATE` có điều kiện (còn
  đẩy, đang ẩn, chưa xoá), rồi mới báo. Bài mất phần thời gian đẩy còn lại (Admin bỏ ẩn cũng không đẩy tiếp), thời
  gian nghỉ tính từ mốc dừng. Bài vừa được bỏ ẩn trước câu `UPDATE` thì không bị cắt. Bài chủ tự gỡ không được báo.
- **Chống gửi trùng** (job idempotent): trước khi gửi, hỏi `NotificationService.hasNotificationSince(userId, type,
  link, since)`; đã có thông báo cùng loại, cùng bài, tạo từ mốc `since` trở đi thì bỏ qua.

  | `type` | `since` |
  |--------|---------|
  | `BOOST_ENDING_SOON` | `boosted_until - boost_ending_notice_hours` |
  | `BOOST_ENDED` | `boosted_until` |
  | `BOOST_STOPPED` | `last_boosted_at` (lần mua gần nhất) |

  Mua thêm gói thì `boosted_until` lùi ra sau nên `since` cũng lùi theo, đợt mới vẫn được báo.
- **Lỗi**: mỗi bài một transaction; bài lỗi chỉ rollback chính nó, ghi log WARN, lượt sau thử lại.
