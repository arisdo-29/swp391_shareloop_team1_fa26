# Trợ lý tìm đồ ShareLoop

## POST `/api/v1/ai/search`

Cần JWT của Member. Mỗi request là một cuộc tìm độc lập; backend và Gemini không dùng lịch sử của lần trước. Body:

```json
{ "query": "Mình muốn một món giúp vẻ ngoài có phong cách hơn" }
```

`query` dài 1–500 ký tự và không được chứa số điện thoại, email hoặc URL. Trợ lý chỉ phục vụ nhu cầu tìm đồ trong ShareLoop, kể cả khi người dùng mô tả công dụng hoặc cảm giác mong muốn thay vì tên món. Sau khi qua kiểm tra hạn mức và số dư, yêu cầu ngoài phạm vi nhận một lời từ chối ngắn, không tiêu lượt và không trừ Credit.

Response `200`:

```json
{
  "message": "Mình đã xem nhu cầu của bạn và tìm được vài món đồ đang có trên ShareLoop có thể phù hợp. Bạn nhấn vào từng bài đăng để xem chi tiết nhé!",
  "results": { "items": [], "page": 0, "size": 0, "totalElements": 0 },
  "quota": { "freeUsesRemaining": 5, "feeCredits": 2, "hasToppedUp": true },
  "chargedCredits": 0,
  "fallback": false,
  "outOfScope": false
}
```

Backend đọc toàn bộ bài `APPROVED`, còn hoạt động, chưa xoá và chưa hết hạn từ ItemService. Trạng thái `RESERVED`, `TRADED` và các trạng thái khác không vào danh sách ứng viên. Backend gửi ID và nội dung của **món đang được đăng** theo từng lô cho Gemini `gemini-3.5-flash-lite` qua Gemini Interactions API, với `store=false`. Với bài trao đổi, trường `desiredItem` (món chủ bài muốn nhận) không được gửi cho AI và không dùng để chọn bài. AI chọn tối đa 6 ID từ các ứng viên đã nhận. Khi kho bài lớn, mỗi lời gọi xét lô tiếp theo cùng danh sách rút gọn trước đó. Backend không gửi dữ liệu tài khoản, ví, giao dịch hoặc ảnh riêng tư. Trước khi trả kết quả, backend đọc lại từng bài bằng ItemService để loại bài đã bị giữ chỗ, hoàn tất, ẩn, khoá chủ bài hoặc hết hạn trong lúc AI xử lý. Mỗi ID kết quả luôn có bài đăng thật để người dùng mở xem.

`message` là một tin nhắn ngắn do Gemini soạn cho lượt tìm có kết quả; backend chuẩn hoá và dùng lời nhắn an toàn mặc định nếu nội dung không hợp lệ. Không có endpoint nhắn tiếp hoặc lịch sử hội thoại. Trường hợp không có kết quả, ngoài phạm vi hoặc AI không khả dụng đều trả `items: []`, `chargedCredits: 0`; `fallback: true` chỉ khi AI không khả dụng hoặc phản hồi lỗi và không tự chuyển sang tìm từ khóa thường. Nếu AI trả ID không có trong danh sách đã cung cấp hoặc JSON sai schema, backend không trả ID đó và không tính phí.

Với yêu cầu tìm **áo** hoặc **giày** được nêu rõ, backend kiểm tra loại món chính trong tiêu đề và loại bài đăng khác loại dù AI chọn nhầm ID; chỉ tiêu lượt khi còn kết quả hợp lệ. Yêu cầu rộng như “muốn trông phong cách hơn” vẫn để AI chọn món theo nhu cầu.

Backend kiểm tra lượt miễn phí hoặc Credit khả dụng qua `AiQuotaService` **trước khi đọc danh sách bài và gọi Gemini**. Nếu đã hết lượt miễn phí và không đủ Credit, API trả `402 WALLET_INSUFFICIENT_BALANCE`; Gemini không được gọi. Hệ thống chỉ tiêu một lượt miễn phí hoặc trừ phí sau khi Gemini thành công **và** còn ít nhất một bài hợp lệ được trả về. Phí và hạn mức đọc từ cấu hình hiện có qua `AiQuotaService`; tiền chỉ đổi qua `CreditService`. Kết quả ghi activity log gồm model, trạng thái, thời gian và loại tính phí, không ghi nội dung người dùng hoặc API key.

Backend dùng `GEMINI_API_KEY` từ cấu hình `app.ai.api-key`. Không cấu hình key thì trả lời AI không khả dụng mà không trừ lượt hoặc Credit.

## GET `/api/v1/ai/quota`

Cần JWT. Không tiêu lượt. Response:

```json
{ "freeUsesRemaining": 5, "feeCredits": 2, "hasToppedUp": true }
```

Giá trị lượt miễn phí, lượt thử và phí lấy từ `ai_free_per_day`, `ai_trial_uses`, `ai_search_fee`. Lượt miễn phí hằng ngày được tính theo `Asia/Ho_Chi_Minh` và làm mới lúc 00:00 giờ Việt Nam.

## POST `/api/v1/ai/describe-item`

Cần JWT của Member. Tạo bản nháp mô tả chỉ từ tên món đồ, không nhận `mediaIds`, không đọc hoặc gửi ảnh. Body:

```json
{ "title": "Đồng hồ Casio màu đen" }
```

`title` bắt buộc, tối đa 150 ký tự, không chứa thông tin liên hệ hoặc từ bị chặn. Backend kiểm tra tối đa `ai_describe_per_day` (mặc định 5) lượt thành công/người/ngày theo `Asia/Ho_Chi_Minh`, dựa trên `activity_logs` loại `AI_USAGE` và `target_type=AI_DESCRIBE`. Quá hạn mức trả `429 AI_DESCRIPTION_LIMIT_REACHED`; không gọi Gemini. Thời gian chờ lấy từ `ai_describe_timeout_seconds` (mặc định 20 giây).

Response thành công:

```json
{
  "description": "Món đồ: Đồng hồ Casio màu đen.\nLoại: Đồng hồ\nThương hiệu: Casio\nMàu: đen\nMặt đồng hồ: …\nDây đeo: …",
  "attributes": [
    { "name": "Thương hiệu", "value": "Casio", "requiresUserInput": false, "prompt": null },
    { "name": "Mặt đồng hồ", "value": "…", "requiresUserInput": true, "prompt": "Mặt chiếc đồng hồ Casio này có kiểu dáng và kích cỡ ra sao?" },
    { "name": "Dây đeo", "value": "…", "requiresUserInput": true, "prompt": "Dây đeo hiện làm bằng chất liệu gì?" }
  ],
  "remainingUses": 4,
  "message": "Đây là bản nháp từ tên món đồ. Bạn hãy kiểm tra và sửa lại trước khi đăng nhé."
}
```

Gemini tự viết 2–4 câu hỏi riêng cho tên món; tên mục và câu hỏi không lấy từ danh sách thuộc tính cố định. Backend chỉ đưa vào bản nháp những thông tin AI trích ra được nguyên văn từ tên, cùng các mục cần người đăng tự điền. Câu hỏi hiển thị bên dưới bản nháp để hướng dẫn người đăng sửa mô tả. Backend kiểm tra độ dài, thông tin liên hệ, lexicon, câu hỏi trùng lặp, con số không có trong tên và trường hợp sai ngữ cảnh rõ ràng như dung tích của đồng hồ. Nếu không còn câu hỏi hợp lệ, lượt đó không bị tính. Giá trị chưa biết để dấu `…`, người đăng tự xác nhận. Nút “Dùng gợi ý” ở form chỉ chép bản nháp vào ô Mô tả để người đăng sửa; không tự chọn ô Tình trạng hay tự gửi bài. Nếu Gemini lỗi hoặc quá giờ, trả `description: ""`, `attributes: []`, thông báo tự viết mô tả và không tính lượt. Bài đăng vẫn qua kiểm duyệt nội dung và Admin như bình thường.
