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
  }
}
```

`filters` là bộ lọc được Gemini trích xuất và backend kiểm tra. `categoryId` phải thuộc danh mục đang hoạt động; `areaId` phải là ID quận/huyện đang hoạt động. `condition` thuộc `NEW`, `LIKE_NEW`, `GOOD`, `DEFECTIVE`; `offerType` thuộc `GIVE`, `SWAP`. Mỗi trường có thể là `null`. `results.items` là `ItemResponse` từ cơ sở dữ liệu, chỉ gồm bài `APPROVED`, còn hoạt động và chưa xoá. LLM không tạo bài, diễn giải kết quả hay quyết định bài nào được duyệt.

Nội dung `query` được gửi riêng ở vai trò `user`; chỉ thị hệ thống chứa schema và ID danh mục/quận huyện hợp lệ. Backend không đính kèm hồ sơ, tên, email, số điện thoại hoặc dữ liệu ví từ tài khoản người dùng tới Gemini. Bộ lọc tầng 1 từ chối thông tin liên hệ rõ ràng trong câu nhập; người dùng vẫn cần tránh nhập tên hoặc dữ liệu cá nhân khác. Backend ghi `AiUsage` cho từng lượt gọi với vai trò `SEARCH`, model, kết quả và thời gian; không ghi câu người dùng hoặc API key.

| HTTP | Code | Ý nghĩa |
| --- | --- | --- |
| 400 | `SEARCH_QUERY_INVALID` hoặc lỗi validation chung | Query hoặc phân trang không hợp lệ |
| 401 | `UNAUTHORIZED` | Chưa đăng nhập |
| 502 | `SEARCH_AI_FAILED` | Không gọi được Gemini |
| 502 | `SEARCH_AI_INVALID_RESPONSE` | JSON AI sai schema hoặc bộ lọc ngoài danh mục hợp lệ |
| 503 | `SEARCH_AI_UNAVAILABLE` | Backend chưa có `AI_API_KEY` |
| 504 | `SEARCH_AI_TIMEOUT` | Gemini quá thời gian `ai_search_timeout_seconds` |

Issue #124 chưa tính lượt miễn phí hay trừ Credit. Issue #125 sẽ thêm hạn mức, hoàn lượt khi AI lỗi và kết quả tìm thường khi lỗi. FE trang trợ lý sẽ nối API ở issue #130; file service `aiApi.ts` được tạo tại đây để bàn giao contract.
