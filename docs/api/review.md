# API module review

## GET /api/v1/admin/items/pending

Lấy hàng chờ duyệt. Chỉ Admin được truy cập. Kết quả được sắp theo thứ tự:

1. Bài có cờ kiểm duyệt `REVIEW` trước.
2. Người đăng có `trust_stars` thấp hơn trước.
3. Bài chờ lâu hơn trước; `id` dùng để phá hòa ổn định.

Tham số phân trang: `page` mặc định `0`, `size` mặc định `20`, giới hạn `1–100`.

Mỗi phần tử gồm id bài, id người đăng, tiêu đề, `aiRisk`, `moderationFlag`, `flagReason`, `donorTrustStars` và `createdAt`. `items.ai_result` dùng hai khóa `flag` và `reason`; khi chưa có cờ, `moderationFlag` là `NONE` và `flagReason` là `null`.

## POST /api/v1/admin/items/{itemId}/auth-verify

Admin đối chiếu chứng từ và cấp hoặc từ chối nhãn chính hãng. Bài phải ở `auth_status = PENDING`; để cấp nhãn, bài cần ít nhất một chứng từ private đang `ATTACHED`. Admin vẫn có thể từ chối khi thiếu chứng từ và ghi lý do.

```json
{ "approved": true, "reason": "Hóa đơn khớp với thương hiệu và sản phẩm" }
```

`approved: true` chuyển sang `VERIFIED`; `false` chuyển sang `REJECTED`. `reason` bắt buộc cho cả hai kết quả. Mỗi quyết định cập nhật `items.auth_status` và ghi một dòng `activity_logs` với Admin, bài, trạng thái cũ/mới và lý do.

## POST /api/v1/admin/items/{itemId}/auth-revoke

Thu hồi nhãn đã xác minh khi phát hiện chứng từ giả. Chỉ trạng thái `VERIFIED` được chuyển sang `REVOKED`; body bắt buộc có lý do.

```json
{ "reason": "Chứng từ được xác minh là giả" }
```

Thao tác ghi một dòng `activity_logs` loại `AUTH_REVOKED`. Lỗi trả `404 REVIEW_ITEM_NOT_FOUND`, `409 REVIEW_AUTH_INVALID_STATE_TRANSITION` hoặc `422 REVIEW_AUTH_PROOF_NOT_FOUND`.
