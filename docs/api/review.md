# API module review

## GET /api/v1/admin/items/pending

Lấy hàng chờ duyệt. Chỉ Admin được truy cập. Kết quả được sắp theo thứ tự:

1. Bài có cờ kiểm duyệt `REVIEW` trước.
2. Người đăng có `trust_stars` thấp hơn trước.
3. Tài khoản người đăng mới hơn trước.
4. Bài chờ lâu hơn trước; `id` dùng để phá hòa ổn định.

Tham số phân trang: `page` mặc định `0`, `size` mặc định `20`, giới hạn `1–100`.

Mỗi phần tử gồm id bài, id người đăng, tiêu đề, `aiRisk`, `moderationFlag`, `flagReason`, `donorTrustStars` và `createdAt`. Cờ và lý do đọc từ `items.ai_result` (`flag`/`reason`, dự phòng `moderation`/`moderationReason`). Khi chưa có dữ liệu kiểm duyệt, cờ là `NONE` và lý do là `null`.
