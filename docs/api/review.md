# API module review

## GET /api/v1/admin/items/pending

Lấy hàng chờ duyệt. Chỉ Admin được truy cập. Kết quả được sắp theo thứ tự:

1. Bài có cờ kiểm duyệt `REVIEW` trước.
2. Người đăng có `trust_stars` thấp hơn trước.
3. Bài chờ lâu hơn trước; `id` dùng để phá hòa ổn định.

Tham số phân trang: `page` mặc định `0`, `size` mặc định `20`, giới hạn `1–100`.

Mỗi phần tử gồm id bài, id người đăng, tiêu đề, `aiRisk`, `moderationFlag`, `flagReason`, `donorTrustStars` và `createdAt`. `items.ai_result` dùng hai khóa `flag` và `reason`; khi chưa có cờ, `moderationFlag` là `NONE` và `flagReason` là `null`.
