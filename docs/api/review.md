# Review API

Tất cả các endpoint trong module này yêu cầu quyền **ADMIN** (Bearer JWT token của Admin). Người dùng chưa đăng nhập nhận `401 UNAUTHORIZED`, người dùng không có quyền Admin nhận `403 FORBIDDEN`.

---

## 1. GET /api/v1/admin/reviews/pending

Lấy trang bài đăng đang chờ duyệt (`PENDING_REVIEW`) kèm danh sách câu hỏi `checklist` kiểm duyệt hiện hành.

### Thứ tự ưu tiên sắp xếp:
1. Bài có cờ kiểm duyệt `moderationFlag = 'REVIEW'` đứng trước bài không có cờ.
2. Người đăng có uy tín (`trust_stars`) thấp hơn đứng trước (ưu tiên soát tài khoản rủi ro cao).
3. Tài khoản người đăng mới hơn đứng trước.
4. Bài đăng chờ lâu hơn đứng trước; trường `id` dùng để phá hòa ổn định.

### Query Parameters
| Tham số | Kiểu | Mặc định | Giới hạn | Mô tả |
|---------|------|----------|----------|-------|
| `page` | `int` | `0` | $\ge 0$ | Số trang (bắt đầu từ 0) |
| `size` | `int` | `20` | `1–100` | Số lượng bản ghi mỗi trang |

### Response 200 OK
```json
{
  "items": [
    {
      "id": 42,
      "donorId": 9,
      "title": "Áo khoác gió nam",
      "aiRisk": "LOW",
      "moderationFlag": "REVIEW",
      "flagReason": "Có thể có thông tin liên hệ",
      "donorTrustStars": 3,
      "createdAt": "2026-10-14T10:00:00Z"
    }
  ],
  "checklist": [
    "Ảnh là ảnh thật của đúng món đồ?",
    "Món đồ không thuộc danh mục hàng cấm hoặc vi phạm chính sách?"
  ],
  "page": 0,
  "size": 20
}
```

| Mã HTTP | Ý nghĩa |
|---------|---------|
| 200 | Trả về danh sách hàng chờ và checklist kiểm duyệt |
| 401 | Chưa đăng nhập |
| 403 | Không có quyền Admin |

---

## 2. POST /api/v1/admin/reviews/{itemId}/approve

Duyệt bài đăng đang chờ kiểm duyệt. Admin bắt buộc phải trả lời đủ tất cả các câu hỏi trong `checklist` (lấy từ endpoint pending) và tất cả câu trả lời phải là `true` (đạt).

### Path Parameters
- `itemId`: ID bài đăng cần duyệt.

### Request Body (`ApproveReviewRequest`)
```json
{
  "answers": [true, true]
}
```

### Xử lý hệ thống:
- Trừ khoản phí đăng bài đang tạm giữ (`CreditService.deductHeldFee`).
- Chuyển trạng thái bài đăng sang `APPROVED` (hiển thị công khai trên feed).
- Lưu snapshot kết quả checklist kiểm duyệt (`ReviewItem`) cùng thời điểm duyệt `approved_at` và ID Admin duyệt.
- Ghi log hoạt động hệ thống (`ActivityLogService.log`).

### Response & Mã lỗi
| Mã HTTP | Mã lỗi / Chi tiết | Ý nghĩa |
|---------|-------------------|---------|
| 204 | - | Duyệt thành công (No Content) |
| 400 | `VALIDATION_FAILED` | Danh sách `answers` rỗng hoặc chứa giá trị null |
| 400 | `REVIEW_CHECKLIST_INCOMPLETE` | Trả lời thiếu số lượng câu checklist hoặc có câu trả lời là `false` |
| 401 | `UNAUTHORIZED` | Chưa đăng nhập |
| 403 | `FORBIDDEN` | Không có quyền Admin |
| 404 | `REVIEW_ITEM_NOT_FOUND` / `LISTINGFEE_ITEM_NOT_FOUND` | Không tìm thấy bài đăng |
| 409 | `ITEM_INVALID_STATUS_TRANSITION` | Bài đăng không ở trạng thái `PENDING_REVIEW` (đã được xử lý trước đó) |
| 409 | `LISTINGFEE_NOTHING_HELD` | Không tìm thấy khoản phí đang giữ để trừ |

---

## 3. POST /api/v1/admin/reviews/{itemId}/reject

Từ chối bài đăng đang chờ kiểm duyệt kèm lý do bắt buộc (có thể chọn từ danh sách mẫu `reject-reasons` hoặc tự nhập).

### Path Parameters
- `itemId`: ID bài đăng cần từ chối.

### Request Body (`RejectReviewRequest`)
```json
{
  "reason": "Ảnh không phải ảnh thật của món đồ hoặc lấy từ mạng."
}
```

### Xử lý hệ thống:
- Hoàn trả khoản phí đăng bài đang tạm giữ cho chủ bài (`CreditService.releaseHeldFee`).
- Chuyển trạng thái bài sang `REJECTED`, lưu lý do từ chối `reject_reason` cùng ID Admin xử lý.
- Gửi thông báo trong ứng dụng (`NotificationService.notify`) loại `POST_REJECTED` kèm nội dung lý do tới người đăng.
- Ghi log hoạt động hệ thống (`ActivityLogService.log`).

### Response & Mã lỗi
| Mã HTTP | Mã lỗi / Chi tiết | Ý nghĩa |
|---------|-------------------|---------|
| 204 | - | Từ chối thành công (No Content) |
| 400 | `VALIDATION_FAILED` | Thiếu lý do hoặc lý do chỉ toàn khoảng trắng |
| 401 | `UNAUTHORIZED` | Chưa đăng nhập |
| 403 | `FORBIDDEN` | Không có quyền Admin |
| 404 | `REVIEW_ITEM_NOT_FOUND` / `LISTINGFEE_ITEM_NOT_FOUND` | Không tìm thấy bài đăng |
| 409 | `ITEM_INVALID_STATUS_TRANSITION` | Bài đăng không ở trạng thái `PENDING_REVIEW` (đã được xử lý trước đó) |

---

## 4. GET /api/v1/admin/reviews/reject-reasons

Lấy danh sách các lý do từ chối mẫu soạn sẵn (nhóm `REJECT_REASON`, đang kích hoạt `is_active = true`, sắp xếp theo `sort_order`) để Admin lựa chọn nhanh trên giao diện kiểm duyệt.

### Response 200 OK
```json
[
  "Ảnh không phải ảnh thật của món đồ hoặc lấy từ mạng.",
  "Bài đăng chứa thông tin liên hệ (số điện thoại, email, link liên kết).",
  "Chọn sai danh mục món đồ.",
  "Món đồ thuộc danh mục hàng cấm hoặc hạn chế chia sẻ.",
  "Nội dung hoặc hình ảnh không phù hợp với tiêu chuẩn cộng đồng.",
  "Đồ lỗi nhưng chưa mô tả chi tiết lỗi hoặc thiếu ảnh chụp chỗ lỗi."
]
```

| Mã HTTP | Ý nghĩa |
|---------|---------|
| 200 | Trả về danh sách lý do mẫu thành công |
| 401 | Chưa đăng nhập |
| 403 | Không có quyền Admin |

---

## 5. GET /api/v1/admin/items/pending (Endpoint tương thích cũ)

Endpoint kế thừa dùng để lấy danh sách bài đăng chờ duyệt mà không kèm danh sách câu hỏi checklist.

### Response 200 OK
```json
[
  {
    "id": 42,
    "donorId": 9,
    "title": "Áo khoác gió nam",
    "aiRisk": "LOW",
    "moderationFlag": "REVIEW",
    "flagReason": "Có thể có thông tin liên hệ",
    "donorTrustStars": 3,
    "createdAt": "2026-10-14T10:00:00Z"
  }
]
```

| Mã HTTP | Ý nghĩa |
|---------|---------|
| 200 | Trả về danh sách bài đăng chờ duyệt |
| 401 | Chưa đăng nhập |
| 403 | Không có quyền Admin |

