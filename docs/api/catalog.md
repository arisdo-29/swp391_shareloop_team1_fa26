# API module catalog

Quy ước chung theo Lộ trình mục 3.4: tiền tố `/api/v1`, thời gian ISO-8601 UTC, thành công trả thẳng DTO (không bọc),
lỗi theo định dạng thống nhất bên dưới. File này làm mẫu contract cho các module khác.

Người sở hữu: BE2. API công khai chỉ đọc; API Admin quản lý danh mục ở `/api/v1/admin/categories`.

## GET /api/v1/areas

Cây khu vực đang dùng (`is_active = true`, chưa xoá mềm), sắp theo id tăng dần ở mỗi cấp. Khu vực
cấp 1 chứa danh sách khu vực cấp 2 trong `children`; khu vực cấp 2 có `children` rỗng.

- Xác thực: **không cần** (GET công khai).
- Thành công: `200`, mảng `AreaResponse` (rỗng nếu chưa có khu vực).

```json
[
  {
    "id": 1,
    "name": "TP. Hồ Chí Minh",
    "parentId": null,
    "level": 1,
    "children": [
      { "id": 2, "name": "Quận 1", "parentId": 1, "level": 2, "children": [] }
    ]
  }
]
```

## AreaResponse

| Trường     | Kiểu           | Ý nghĩa                                      |
|------------|----------------|----------------------------------------------|
| `id`       | number         | Id khu vực                                   |
| `name`     | string         | Tên hiển thị                                 |
| `parentId` | number?        | Id khu vực cha, `null` ở cấp thành phố       |
| `level`    | number         | Cấp khu vực: `1` thành phố, `2` quận         |
| `children` | AreaResponse[] | Khu vực con đang dùng; rỗng ở cấp quận       |

## API Admin: `/api/v1/admin/areas`

Tất cả endpoint cần JWT có quyền Admin; người dùng thường nhận `403 FORBIDDEN`.

- `GET`: danh sách khu vực chưa xoá mềm, kể cả đang ẩn, sắp theo id; mỗi phần tử có `id`, `name`, `parentId`, `level`, `active`.
- `POST`: tạo khu vực, trả `201`. Body: `{ "name": "Quận mới", "parentId": 1 }`; `parentId: null` tạo cấp 1, cha cấp 1 đang hoạt động tạo cấp 2.
- `PUT /{id}`: sửa tên hoặc chuyển khu vực sang cha khác, trả `200`. Không thể chuyển khu vực có con thành cấp 2.
- `PATCH /{id}/active`: gửi `{ "active": false }` để ẩn hoặc `{ "active": true }` để hiện, trả `200`. Ẩn cấp 1 sẽ ẩn các con; hiện lại cấp 1 không tự hiện các con. Chỉ hiện con khi cha đang hoạt động. Không xoá cứng.
- `name` bắt buộc, tối đa 100 ký tự. Lỗi: `400 VALIDATION_FAILED`, `404 CATALOG_AREA_NOT_FOUND`, `422 CATALOG_INVALID_AREA_PARENT` (`field: parentId`), `409 CATALOG_AREA_HAS_CHILDREN`.

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

## GET /api/v1/categories/{categoryId}/attributes

Danh sách thuộc tính đang hoạt động của một danh mục đang hoạt động, sắp theo `sortOrder` rồi `id`; GET công khai. Trả `404 CATALOG_CATEGORY_NOT_FOUND` nếu danh mục không tồn tại hoặc bị ẩn. Thuộc tính `Trạng thái hoạt động` của danh mục đồ điện tử luôn bắt buộc.

```json
[
  { "id": 12, "categoryId": 4, "name": "Trạng thái hoạt động", "dataType": "SELECT", "options": ["NORMAL", "PARTIAL", "NOT_WORKING"], "required": true, "sortOrder": 1 }
]
```

## API Admin: `/api/v1/admin/categories/{categoryId}/attributes`

Tất cả endpoint cần JWT có quyền Admin; người dùng thường nhận `403 FORBIDDEN`.

- `GET`: danh sách mọi thuộc tính chưa xoá mềm của danh mục, kể cả đang ẩn; mỗi phần tử thêm `active`.
- `POST`: tạo thuộc tính cho danh mục đang hoạt động, trả `201`.
- `PUT /{attributeId}`: sửa tên, kiểu, tùy chọn, cờ bắt buộc và thứ tự, trả `200`.
- `PATCH /{attributeId}/active`: ẩn hoặc hiện thuộc tính, trả `200`.
- Body POST/PUT: `{ "name": "Màu sắc", "dataType": "SELECT", "options": ["Đỏ", "Xanh"], "required": false, "sortOrder": 2 }`. `name` bắt buộc, tối đa 100 ký tự; `sortOrder` không âm. `SELECT` cần `options` không rỗng, không trùng; các kiểu `TEXT`, `NUMBER`, `BOOLEAN` không nhận options.
- Không thể đổi tên, kiểu, options, cờ bắt buộc hoặc ẩn thuộc tính `Trạng thái hoạt động` bắt buộc của đồ điện tử. Có thể đổi `sortOrder`.
- Lỗi: `400 VALIDATION_FAILED`, `404 CATALOG_CATEGORY_NOT_FOUND`/`CATALOG_ATTRIBUTE_NOT_FOUND`, `422 CATALOG_INVALID_ATTRIBUTE_OPTIONS` (`field: options`), `409 CATALOG_REQUIRED_ATTRIBUTE`.

## API Admin: `/api/v1/admin/categories`

Tất cả endpoint cần JWT có quyền Admin; người dùng thường nhận `403 FORBIDDEN`.

- `GET`: danh sách mọi danh mục chưa xoá mềm, kể cả đang ẩn, theo `sortOrder` rồi `id`. Response có các trường như `CategoryResponse` và thêm `active`.
- `POST`: tạo danh mục, trả `201` và `AdminCategoryResponse`.
- `PUT /{id}`: sửa tên, danh mục cha, cờ `restricted`/`boostable` và `sortOrder`; trả `200`.
- `PATCH /{id}/active`: gửi `{ "active": false }` để ẩn, `{ "active": true }` để hiện lại; trả `200`. Ẩn danh mục cha cũng ẩn các danh mục con đang hoạt động. Không xoá cứng.
- Body của POST/PUT: `{ "name": "Sách", "parentId": null, "restricted": false, "boostable": true, "sortOrder": 1 }`. `name` bắt buộc, tối đa 100 ký tự; `sortOrder` không âm. Danh mục cha phải đang hoạt động và không được tạo vòng lặp.
- Lỗi: `400 VALIDATION_FAILED`, `404 CATALOG_CATEGORY_NOT_FOUND`, `422 CATALOG_INVALID_PARENT` (`field: parentId`).

## Mã lỗi

| code                        | HTTP | Khi nào                                        |
|-----------------------------|------|------------------------------------------------|
| `CATALOG_CATEGORY_NOT_FOUND`| 404  | Không có danh mục đang dùng với id đã cho      |
| `CATALOG_INVALID_PARENT`    | 422  | Danh mục cha không tồn tại, đang ẩn hoặc tạo vòng lặp |
| `CATALOG_AREA_NOT_FOUND`    | 404  | Khu vực không tồn tại hoặc đã xoá mềm |
| `CATALOG_INVALID_AREA_PARENT` | 422 | Cha không phải khu vực cấp 1 đang hoạt động hoặc trỏ về chính nó |
| `CATALOG_AREA_HAS_CHILDREN` | 409 | Không thể chuyển khu vực có con thành cấp 2 |
| `CATALOG_ATTRIBUTE_NOT_FOUND` | 404 | Thuộc tính không tồn tại hoặc không thuộc danh mục |
| `CATALOG_INVALID_ATTRIBUTE_OPTIONS` | 422 | Options không phù hợp với kiểu thuộc tính |
| `CATALOG_REQUIRED_ATTRIBUTE` | 409 | Cố sửa hoặc ẩn thuộc tính trạng thái hoạt động bắt buộc |
| `VALIDATION_FAILED`         | 400  | Tham số đường dẫn hoặc request body không hợp lệ |

Định dạng lỗi (`field` và `details` chỉ có khi liên quan):

```json
{ "code": "CATALOG_CATEGORY_NOT_FOUND", "message": "Không tìm thấy danh mục.", "traceId": "a1b2c3d4" }
```
