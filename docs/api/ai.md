# AI trợ lý tìm đồ — issue #124

## POST `/api/v1/ai/search`

Yêu cầu JWT của Member. Body:

```json
{ "query": "Tìm sách tiếng Anh cũ ở quận 1" }
```

`query` là câu tự nhiên không rỗng, tối đa 500 ký tự; từ chối nội dung có số điện thoại, email hoặc URL trước khi gọi AI. Không nhập tên riêng hoặc dữ liệu cá nhân khác trong nhu cầu tìm đồ. Query parameters `page` (mặc định `0`) và `size` (mặc định `20`, từ `1` đến `100`) phân trang kết quả. Mỗi request gọi AI một lần, kể cả khi đổi trang. FE gọi qua `frontend/src/services/aiApi.ts`.

Response `200`:

```json
{
  "filters": {
    "keywords": "sách tiếng Anh",
    "categoryId": 4,
    "areaId": 12,
    "condition": null,
    "offerType": null
  },
  "results": {
    "items": [],
    "page": 0,
    "size": 20,
    "totalElements": 0
  },
  "quota": { "freeUsesRemaining": 5, "feeCredits": 2, "hasToppedUp": true },
  "chargedCredits": 0,
  "fallback": false,
  "broadened": false
}
```

`filters` là bộ lọc do OpenRouter dùng `cohere/north-mini-code:free` trích xuất qua lời gọi hàm với schema riêng và được backend kiểm tra lại trước khi truy vấn. Model này không hỗ trợ `response_format`; backend nhận đối số JSON của hàm đúng tên hoặc JSON hợp lệ trong nội dung phản hồi. `categoryId` phải thuộc danh mục đang hoạt động; `areaId` phải là ID quận/huyện đang hoạt động. `condition` thuộc `NEW`, `LIKE_NEW`, `GOOD`, `DEFECTIVE`; `offerType` thuộc `GIVE`, `SWAP`. Mỗi trường có thể là `null`. `results.items` là `ItemResponse` từ cơ sở dữ liệu, chỉ gồm bài `APPROVED`, còn hoạt động và chưa xoá. LLM không tạo bài, diễn giải kết quả hay quyết định bài nào được duyệt.

Backend truy vấn bằng đầy đủ bộ lọc trước. Nếu không có bài và AI đã trả từ khóa cùng ít nhất một bộ lọc khác, backend thử tìm theo từ khóa, không giới hạn danh mục, khu vực, tình trạng hoặc hình thức. Nếu lượt tìm thứ hai có bài, `broadened: true`; `filters` vẫn là bộ lọc AI đã hiểu để FE giải thích vì sao kết quả được mở rộng. Đây vẫn là một lượt gọi AI và không có phí bổ sung. Nếu cả hai lượt truy vấn không có bài, không trừ Credit hoặc lượt miễn phí, `chargedCredits: 0`. `fallback: true` chỉ dành cho trường hợp AI lỗi hoặc thiếu key; kết quả mở rộng sau lời gọi AI thành công có `fallback: false`.

Nội dung `query` được gửi riêng ở vai trò `user`; chỉ thị hệ thống chứa schema và ID danh mục/quận huyện hợp lệ. Backend không đính kèm hồ sơ, tên, email, số điện thoại hoặc dữ liệu ví từ tài khoản người dùng tới OpenRouter. Bộ lọc tầng 1 từ chối thông tin liên hệ rõ ràng trong câu nhập; người dùng vẫn cần tránh nhập tên hoặc dữ liệu cá nhân khác. Backend ghi `AiUsage` cho từng lượt gọi với vai trò `SEARCH`, model, kết quả và thời gian; không ghi câu người dùng hoặc API key.

| HTTP | Code | Ý nghĩa |
| --- | --- | --- |
| 400 | `SEARCH_QUERY_INVALID` hoặc lỗi validation chung | Query hoặc phân trang không hợp lệ |
| 401 | `UNAUTHORIZED` | Chưa đăng nhập |
| 402 | `WALLET_INSUFFICIENT_BALANCE` | Hết lượt miễn phí và không đủ Credit khả dụng |
| 404 | `SEARCH_ACCOUNT_NOT_FOUND` | Không tìm thấy tài khoản |

Lỗi OpenRouter, phản hồi sai schema, thiếu key và quá thời gian `ai_search_timeout_seconds` được xử lý bằng kết quả tìm thường `200` có `fallback: true`.

## Hạn mức và phí — issue #125

`GET /api/v1/ai/quota` cần JWT, không tiêu lượt. Response:

```json
{ "freeUsesRemaining": 5, "feeCredits": 2, "hasToppedUp": true }
```

Tài khoản đã từng nạp thành công có 5 lượt miễn phí mỗi ngày theo ngày của máy chủ. Tài khoản chưa nạp có 1 lượt dùng thử duy nhất. Mức lượt và phí lấy từ cấu hình `ai_free_per_day`, `ai_trial_uses`, `ai_search_fee`. Khi hết lượt miễn phí, backend kiểm tra đủ Credit khả dụng trước khi gọi AI; nếu không đủ, API trả `402 WALLET_INSUFFICIENT_BALANCE`. Chỉ khi tìm được ít nhất một bài hợp lệ mới trừ Credit qua CreditService hoặc tiêu lượt miễn phí. Mỗi request đổi trang cũng là một lượt gọi AI mới và chỉ tính phí khi có kết quả.

`chargedCredits` là phí của lần gọi có kết quả, bằng 0 khi dùng lượt miễn phí hoặc không có kết quả. Nếu OpenRouter lỗi, trả JSON sai hoặc quá thời gian chờ, backend không trừ lượt hay Credit, tìm thường bằng chính câu nhập, trả `fallback: true`, `chargedCredits: 0`. Khi chưa cấu hình `OPENROUTER_API_KEY`, API cũng tìm thường và không trừ lượt. Tìm thường chỉ so khớp từ khoá với bài đã duyệt; nó không hiểu category, địa bàn hoặc ý định như mô hình AI. Có thể nhận kết quả rỗng dù AI sẽ hiểu câu nhập tốt hơn.

FE trang trợ lý sẽ nối API ở issue #130. `frontend/src/services/aiApi.ts` đã có phương thức `search`; Miều cập nhật type theo các trường bổ sung và gọi endpoint quota để hiển thị lượt/phí trước khi bấm.
