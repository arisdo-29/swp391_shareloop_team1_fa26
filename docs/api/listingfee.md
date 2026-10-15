# API module listingfee

Quy ước chung theo Lộ trình mục 3.4: tiền tố `/api/v1`, thời gian ISO-8601 UTC, thành công trả thẳng DTO (không bọc),
lỗi theo định dạng thống nhất (xem `wallet.md`).

Người sở hữu: BE1. Module giữ, trừ, nhả phí đăng bài và phí sửa bài (C01, C02, C14, C14b), đặt thời hạn hiển thị
`expire_at` khi duyệt lần đầu, và gia hạn bài (C16). Mọi mức phí và số ngày đọc từ ConfigService (`post_fee`,
`edit_fee`, `renew_fee`, `post_display_days`), không viết cứng. Đổi tiền chỉ qua `wallet.CreditService`.

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
| `reason` | string  | Lý do, xem bảng luật bên dưới                                           |

### Luật báo giá (xét từ trên xuống, gặp dòng đúng thì dừng)

"Đã từng được duyệt" xét bằng `approved_at`, không xét status hiện tại (R36): bản sửa bị từ chối không làm mất lượt
miễn phí.

| `reason`                   | Điều kiện                                                       | `free` | `amount`   |
|----------------------------|-----------------------------------------------------------------|--------|------------|
| `NOT_YET_APPROVED`         | Chưa từng duyệt, phí đăng đang giữ (bài chờ duyệt lần đầu)      | true   | 0          |
| `REPOST_FEE`               | Chưa từng duyệt, phí đăng đã nhả (bị từ chối): giữ lại phí đăng | false  | `post_fee` |
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

## Mã lỗi

| code                          | HTTP | Khi nào                                                      | Lộ ra qua API                 |
|-------------------------------|------|--------------------------------------------------------------|-------------------------------|
| `LISTINGFEE_ITEM_NOT_FOUND`   | 404  | Không có bài hoặc bài đã xoá mềm                             | `GET /items/{id}/edit-quote`, `POST /items/{id}/renew` |
| `LISTINGFEE_NOT_OWNER`        | 403  | Người gọi không phải chủ bài                                 | `GET /items/{id}/edit-quote`, `POST /items/{id}/renew` |
| `LISTINGFEE_RENEW_NOT_ALLOWED`| 409  | Gia hạn bài không ở trạng thái `APPROVED`                    | `POST /items/{id}/renew`      |
| `LISTINGFEE_ALREADY_HELD`     | 409  | Tạo bài mà bài đã có khoản phí đang giữ                      | qua API của module gọi        |
| `LISTINGFEE_NOTHING_HELD`     | 409  | Duyệt / từ chối / xoá mà bài không có khoản phí đang giữ     | qua API của module gọi        |
| `LISTINGFEE_EDIT_NOT_ALLOWED` | 409  | Dự phòng; hiện chưa dùng (kiểm status bài là việc của item)  | -                             |

Không đủ Credit khi giữ phí hoặc khi gia hạn: `402 WALLET_INSUFFICIENT_BALANCE` (xem `wallet.md`).

## Hàm nội bộ `PostFeeService` (cho module khác, không phải endpoint)

Mọi hàm chạy trong transaction của người gọi (REQUIRED). Khoá luôn theo thứ tự dòng `users` rồi dòng `items`.

| Hàm                               | Người gọi                         | Việc làm                                                                 |
|-----------------------------------|-----------------------------------|--------------------------------------------------------------------------|
| `quoteEdit(itemId, userId)`       | item (PUT), endpoint ở trên       | Trả `EditQuote`; chỉ đọc, không khoá                                     |
| `holdPostFee(itemId, userId)`     | item (tạo bài)                    | Giữ `post_fee`, `fee_state = HELD`, loại POST                            |
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
- Phí 0 (Admin cấu hình `post_fee` hoặc `edit_fee` = 0): vẫn ghi `fee_state = HELD` với `pending_fee = 0`, không gọi ví.
- **Thời hạn hiển thị**: khi duyệt khoản giữ loại POST (chỉ có ở lần duyệt đầu, kể cả bài gửi lại sau khi bị từ chối
  lần đầu), `chargeHeldFee` đặt `expire_at = lúc duyệt + post_display_days` ngày. Duyệt bản sửa (loại EDIT) không đổi
  `expire_at`. Tạm thời mọi hạng đều dùng `post_display_days`; khi có `ReputationService.getRank` (#96) thì hạng Vàng
  trở lên sẽ dùng `post_display_days_gold`.
