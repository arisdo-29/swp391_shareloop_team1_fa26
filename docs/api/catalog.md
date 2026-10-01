# API module catalog

Quy ước chung theo Lộ trình mục 3.4: tiền tố `/api/v1`, thời gian ISO-8601 UTC, thành công trả thẳng DTO (không bọc),
lỗi theo định dạng thống nhất bên dưới. File này làm mẫu contract cho các module khác.

Người sở hữu: BE2. Danh mục chỉ có endpoint đọc; ghi danh mục thuộc API Admin (`/api/v1/admin/...`, chưa có).

## GET /api/v1/categories

Danh sách danh mục đang dùng (`is_active = true`, chưa xoá mềm), sắp theo `sortOrder` tăng dần.

- Xác thực: **không cần** (GET công khai).
- Thành công: `200`, mảng `CategoryResponse` (rỗng nếu chưa có danh mục).

```json
[
  { "id": 1, "name": "Sách", "parentId": null, "restricted": false, "boostable": true, "sortOrder": 1 },
  { "id": 7, "name": "Truyện tranh", "parentId": 1, "restricted": false, "boostable": true, "sortOrder": 2 }
]
```

## GET /api/v1/categories/{id}

Chi tiết một danh mục đang dùng.

- Xác thực: **không cần**.
- Thành công: `200`, một `CategoryResponse`.
- Lỗi: `404 CATALOG_CATEGORY_NOT_FOUND` khi id không tồn tại, đã bị ẩn hoặc đã xoá mềm.
- Lỗi: `400 VALIDATION_FAILED` (`field` = `id`) khi `id` không phải số, ví dụ `GET /api/v1/categories/abc`.

```json
{ "id": 1, "name": "Sách", "parentId": null, "restricted": false, "boostable": true, "sortOrder": 1 }
```

## CategoryResponse

| Trường       | Kiểu    | Ý nghĩa                                              |
|--------------|---------|------------------------------------------------------|
| `id`         | number  | Id danh mục                                          |
| `name`       | string  | Tên hiển thị (tối đa 100 ký tự)                      |
| `parentId`   | number? | Id danh mục cha, `null` nếu là danh mục gốc          |
| `restricted` | boolean | Danh mục hạn chế (cần kiểm duyệt thêm)               |
| `boostable`  | boolean | Cho phép đẩy tin (boost) bài đăng thuộc danh mục này |
| `sortOrder`  | number  | Thứ tự hiển thị, nhỏ đứng trước                      |

## Mã lỗi

| code                        | HTTP | Khi nào                                        |
|-----------------------------|------|------------------------------------------------|
| `CATALOG_CATEGORY_NOT_FOUND`| 404  | Không có danh mục đang dùng với id đã cho      |
| `VALIDATION_FAILED`         | 400  | `id` trên đường dẫn không phải số              |

Định dạng lỗi (`field` và `details` chỉ có khi liên quan):

```json
{ "code": "CATALOG_CATEGORY_NOT_FOUND", "message": "Không tìm thấy danh mục.", "traceId": "a1b2c3d4" }
```
