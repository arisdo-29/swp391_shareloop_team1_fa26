# API module listingfee

Quy ước chung theo Lộ trình mục 3.4: tiền tố `/api/v1`, thời gian ISO-8601 UTC, thành công trả thẳng DTO (không bọc),
lỗi theo định dạng thống nhất (xem `wallet.md`).

Người sở hữu: BE1. Module giữ, trừ, nhả phí đăng bài và phí sửa bài (C01, C02, C14, C14b). Mọi mức phí đọc từ
ConfigService (`post_fee`, `edit_fee`), không viết cứng. Đổi tiền chỉ qua `wallet.CreditService`.

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

## Mã lỗi

| code                          | HTTP | Khi nào                                                      | Lộ ra qua API                 |
|-------------------------------|------|--------------------------------------------------------------|-------------------------------|
| `LISTINGFEE_ITEM_NOT_FOUND`   | 404  | Không có bài hoặc bài đã xoá mềm                             | `GET /items/{id}/edit-quote`  |
| `LISTINGFEE_NOT_OWNER`        | 403  | Người gọi không phải chủ bài                                 | `GET /items/{id}/edit-quote`  |
| `LISTINGFEE_ALREADY_HELD`     | 409  | Tạo bài mà bài đã có khoản phí đang giữ                      | qua API của module gọi        |
| `LISTINGFEE_NOTHING_HELD`     | 409  | Duyệt / từ chối / xoá mà bài không có khoản phí đang giữ     | qua API của module gọi        |
| `LISTINGFEE_EDIT_NOT_ALLOWED` | 409  | Dự phòng; hiện chưa dùng (kiểm status bài là việc của item)  | -                             |

Không đủ Credit khi giữ phí: `402 WALLET_INSUFFICIENT_BALANCE` (xem `wallet.md`).

## Hàm nội bộ `PostFeeService` (cho module khác, không phải endpoint)

Mọi hàm chạy trong transaction của người gọi (REQUIRED). Khoá luôn theo thứ tự dòng `users` rồi dòng `items`.

| Hàm                               | Người gọi                         | Việc làm                                                                 |
|-----------------------------------|-----------------------------------|--------------------------------------------------------------------------|
| `quoteEdit(itemId, userId)`       | item (PUT), endpoint ở trên       | Trả `EditQuote`; chỉ đọc, không khoá                                     |
| `holdPostFee(itemId, userId)`     | item (tạo bài)                    | Giữ `post_fee`, `fee_state = HELD`, loại POST                            |
| `holdEditFee(itemId, userId)`     | item (sửa bài)                    | Giữ đúng `amount` của `quoteEdit`; đang HELD thì không làm gì            |
| `chargeHeldFee(itemId)`           | review (duyệt)                    | Trừ khoản đang giữ, `fee_state = CHARGED`                                |
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
