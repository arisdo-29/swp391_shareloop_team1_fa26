# Phụ lục AI-B — Gợi ý mô tả từ tên món đồ

Phụ lục này bổ sung luồng gợi ý mô tả cho form đăng và sửa bài. Nội dung dưới đây là bản đã điều chỉnh của đề xuất AI-B: Gemini chỉ nhận tên món đồ, không nhận hoặc đọc ảnh. Quy định số ảnh tối thiểu khi **đăng bài** vẫn áp dụng độc lập.

## I. Quy tắc nghiệp vụ

### TT-08 — Gợi ý mô tả

- Thành viên có thể bấm **Gợi ý mô tả bằng AI** khi đã nhập tên món đồ. Nút không phụ thuộc số ảnh đã tải lên.
- Server chỉ gửi tên món đồ đến Gemini. Tên được xử lý như dữ liệu không đáng tin. Không gửi ảnh, dữ liệu ví, thông tin liên hệ hoặc lịch sử các lượt tìm kiếm.
- Kết quả gồm bản nháp mô tả tiếng Việt và các thuộc tính có `name`, `value`, `requiresUserInput`. Những chi tiết không xuất hiện trong tên món phải để trống bằng dấu `…`; kích thước, khối lượng, dung tích, thời gian đã dùng và tình trạng hoạt động luôn để người đăng tự điền. Gợi ý tình trạng không tự chọn ô **Tình trạng**.
- Server lọc thông tin liên hệ và từ thuộc lexicon kiểm duyệt khỏi từng giá trị trước khi trả về. Người đăng xem trước, bấm **Dùng gợi ý** mới chép bản nháp vào ô mô tả, có thể sửa tiếp và tự gửi bài. Bài vẫn qua kiểm tra nội dung tự động và Admin duyệt.
- Gemini lỗi, trả sai cấu trúc hoặc quá thời gian chờ thì trả lời “Chưa gợi ý được, bạn tự viết mô tả nhé.”, không chặn đăng hoặc sửa bài.

### TT-09 — Hạn mức gợi ý mô tả

- Gợi ý mô tả miễn phí, tối đa `ai_describe_per_day` lượt thành công/người/ngày; mặc định 5. Ngày tính theo `Asia/Ho_Chi_Minh`, làm mới lúc 00:00 giờ Việt Nam.
- Server kiểm hạn mức trước khi gọi Gemini. Các lượt thành công được đếm từ `activity_logs` với `type=AI_USAGE`, `target_type=AI_DESCRIBE`. Mỗi lời gọi lỗi có nhật ký `AI_DESCRIBE_ERROR` nhưng không tiêu lượt. Không thêm bảng và không trừ Credit.
- `ai_describe_timeout_seconds` mặc định 20 giây. Cả hai giá trị đọc qua `ConfigService`.

## Hợp đồng API

`POST /api/v1/ai/describe-item` nhận `{ "title": "..." }` (1–150 ký tự) và trả bản nháp, các thuộc tính, số lượt còn lại, lời nhắn. Chi tiết lỗi và ví dụ response nằm tại [API AI](../api/ai.md).
