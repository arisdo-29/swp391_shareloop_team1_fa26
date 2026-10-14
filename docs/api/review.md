# API module review

Các endpoint quản lý hàng chờ duyệt chỉ dành cho `ADMIN`. Người dùng đã đăng nhập nhưng không phải Admin nhận `403 FORBIDDEN`.

## GET /api/v1/admin/reviews/pending

Lấy trang bài đang `PENDING_REVIEW` cùng checklist hiện hành. Thứ tự ưu tiên:

1. Bài có cờ kiểm duyệt `REVIEW` trước.
2. Người đăng có `trust_stars` thấp hơn trước.
3. Tài khoản người đăng mới hơn trước.
4. Bài chờ lâu hơn trước; `id` dùng để phá hòa ổn định.

Tham số phân trang: `page` mặc định `0`, `size` mặc định `20`, giới hạn `1–100`.

```json
{
  "items": [
    {
      "id": 42,
      "donorId": 9,
      "title": "Áo khoác",
      "aiRisk": "LOW",
      "moderationFlag": "REVIEW",
      "flagReason": "Có thể có thông tin liên hệ",
      "donorTrustStars": 3,
      "createdAt": "2026-10-14T10:00:00Z"
    }
  ],
  "checklist": ["Ảnh là ảnh thật của đúng món đồ?"],
  "page": 0,
  "size": 20
}
```

## POST /api/v1/admin/reviews/{itemId}/approve

Duyệt bài. `answers` là danh sách câu trả lời boolean theo đúng thứ tự `checklist` trả về ở endpoint hàng chờ. Phải có đủ câu trả lời và tất cả đều `true` mới duyệt được.

```json
{ "answers": [true, true, true] }
```

Thành công trả `204 No Content`. Hệ thống trừ phí đang giữ, chuyển trạng thái bài sang `APPROVED`, rồi lưu snapshot gồm từng câu hỏi và câu trả lời cùng `approved_at` trong cùng transaction.

## POST /api/v1/admin/reviews/{itemId}/reject

Từ chối bài. `reason` bắt buộc và không được để trống. Hệ thống nhả phí đang giữ, chuyển trạng thái bài sang `REJECTED`, rồi lưu `reject_reason` trong cùng transaction.

```json
{ "reason": "Ảnh không đúng món đồ được đăng" }
```

Thành công trả `204 No Content`.

## Mã lỗi

- `401 UNAUTHORIZED`: chưa đăng nhập.
- `403 FORBIDDEN`: không có quyền Admin.
- `400 VALIDATION_FAILED`: thiếu lý do từ chối hoặc body sai định dạng.
- `400 REVIEW_CHECKLIST_INCOMPLETE`: câu trả lời checklist thiếu, sai số lượng hoặc có mục chưa đạt.
- `404 REVIEW_ITEM_NOT_FOUND` hoặc `LISTINGFEE_ITEM_NOT_FOUND`: không tìm thấy bài.
- `409 ITEM_INVALID_STATUS_TRANSITION`: bài không còn ở trạng thái `PENDING_REVIEW`.
- `409 LISTINGFEE_NOTHING_HELD`: bài không có khoản phí đang giữ để trừ/nhả.

## Tương thích endpoint cũ

`GET /api/v1/admin/items/pending` vẫn được giữ và trả danh sách bài như trước, chưa kèm checklist.
