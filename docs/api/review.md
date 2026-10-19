# Review API

Tất cả các endpoint trong module này yêu cầu quyền **ADMIN** (Bearer JWT token của Admin). Người dùng chưa đăng nhập nhận `401 UNAUTHORIZED`, người dùng không có quyền Admin nhận `403 FORBIDDEN`.

---

## 1. GET /api/v1/admin/reviews/pending

Lấy trang bài đăng đang chờ duyệt (`PENDING_REVIEW`) kèm danh sách điều kiện đạt `checklist` hiện hành, chỉ gồm mục active theo `sort_order`, rồi `id`.

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
      "flagReasonCode": "LEXICON_REVIEW",
      "flagReason": "Có từ khoá cần xem xét: zalo",
      "matchedTerms": ["zalo"],
      "donorTrustStars": 3,
      "createdAt": "2026-10-14T10:00:00Z"
    }
  ],
  "checklist": [
    {"id": 11, "key": "real_photos", "statement": "Ảnh là ảnh thật của đúng món đồ, đủ số lượng, không lấy trên mạng."},
    {"id": 12, "key": "category_match", "statement": "Danh mục đúng với món đồ."}
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

Duyệt bài đăng đang chờ kiểm duyệt. Gửi đáp án theo `id` của từng điều kiện trong `checklist`, không ghép theo vị trí. Phải đủ ID đang active, không trùng và tất cả `passed = true` (đạt).

### Path Parameters
- `itemId`: ID bài đăng cần duyệt.

### Request Body (`ApproveReviewRequest`)
```json
{
  "answers": [{"id": 11, "passed": true}, {"id": 12, "passed": true}]
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
| 400 | `VALIDATION_FAILED` | Danh sách `answers` rỗng, phần tử null hoặc thiếu `id`/`passed` |
| 400 | `REVIEW_CHECKLIST_INCOMPLETE` | Thiếu ID đang active, trùng ID hoặc có `passed = false` |
| 409 | `REVIEW_CHECKLIST_OUTDATED` | Có ID không thuộc checklist đang active; tải lại trang |
| 409 | `REVIEW_CHECKLIST_NOT_CONFIGURED` | Chưa có mục checklist đang active |
| 401 | `UNAUTHORIZED` | Chưa đăng nhập |
| 403 | `FORBIDDEN` | Không có quyền Admin |
| 404 | `REVIEW_ITEM_NOT_FOUND` / `LISTINGFEE_ITEM_NOT_FOUND` | Không tìm thấy bài đăng |
| 409 | `ITEM_INVALID_STATUS_TRANSITION` | Bài đăng không ở trạng thái `PENDING_REVIEW` (đã được xử lý trước đó) |
| 409 | `LISTINGFEE_NOTHING_HELD` | Không tìm thấy khoản phí đang giữ để trừ |

---

Snapshot và log `REVIEW_APPROVE` lưu `{"checks":[{"id":11,"key":"real_photos","statement":"Ảnh là ảnh thật của đúng món đồ, đủ số lượng, không lấy trên mạng.","passed":true}]}` theo thứ tự cấu hình. Snapshot cũ được chuyển từ câu seed có `key`, `statement`, `passed`, không bổ sung `id` hồi tố.

`flagReasonCode` giữ mã gốc hoặc lý do tự do; `flagReason` là câu tiếng Việt, nối từ khớp khi mã là `LEXICON_REVIEW`. `matchedTerms` là mảng chuỗi, rỗng nếu dữ liệu cũ không có. Các trường này cũng áp dụng endpoint tương thích cũ.

Khi duyệt lưu `approved_by`; khi từ chối lưu `rejected_by`, `rejected_at`. Các cột này thuộc module review, độc lập với JPA Auditing.

FE cần cập nhật `frontend/src/services/adminModerationApi.ts` và `frontend/src/pages/admin/AdminPages.tsx`:

- Đổi `checklist: string[]` sang `{id: number, key: string, statement: string}[]`; hiển thị `statement`.
- Gửi `answers: {id: number, passed: boolean}[]`, dùng ID của mục tương ứng; không gửi mảng boolean.
- Thêm `flagReasonCode`, `matchedTerms`; hiển thị trực tiếp `flagReason` tiếng Việt.
- Khi nhận `409 REVIEW_CHECKLIST_OUTDATED`, tải lại checklist và yêu cầu tích lại trước khi duyệt.

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
    "flagReasonCode": "LEXICON_REVIEW",
    "flagReason": "Có từ khoá cần xem xét: zalo",
    "matchedTerms": ["zalo"],
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

---

## 6. POST /api/v1/admin/authenticity/{id}/grant

Cấp nhãn hàng chính hãng sau khi Admin đối chiếu chứng từ đạt. Chuyển `auth_status` từ `PENDING` sang `VERIFIED`.

### Path Parameters
- `id`: ID bài đăng cần cấp nhãn.

### Response & Mã lỗi
| Mã HTTP | Mã lỗi / Chi tiết | Ý nghĩa |
|---------|-------------------|---------|
| 204 | - | Cấp nhãn thành công (No Content) |
| 401 | `UNAUTHORIZED` | Chưa đăng nhập |
| 403 | `FORBIDDEN` | Không có quyền Admin |
| 404 | `AUTHENTICITY_ITEM_NOT_FOUND` | Không tìm thấy bài đăng |
| 409 | `AUTHENTICITY_INVALID_STATUS` | Bài đăng không ở trạng thái chờ xét duyệt chứng từ (`PENDING`) |

---

## 7. POST /api/v1/admin/authenticity/{id}/reject

Từ chối cấp nhãn hàng chính hãng kèm lý do bắt buộc. Chuyển `auth_status` từ `PENDING` sang `REJECTED`.

### Path Parameters
- `id`: ID bài đăng cần từ chối cấp nhãn.

### Request Body (`AuthenticityDecisionRequest`)
```json
{
  "reason": "Chứng từ mờ, không rõ số serial hoặc hoá đơn không khớp thông tin sản phẩm."
}
```

### Response & Mã lỗi
| Mã HTTP | Mã lỗi / Chi tiết | Ý nghĩa |
|---------|-------------------|---------|
| 204 | - | Từ chối nhãn thành công (No Content) |
| 400 | `VALIDATION_FAILED` / `AUTHENTICITY_REASON_REQUIRED` | Thiếu lý do hoặc lý do chỉ toàn khoảng trắng |
| 401 | `UNAUTHORIZED` | Chưa đăng nhập |
| 403 | `FORBIDDEN` | Không có quyền Admin |
| 404 | `AUTHENTICITY_ITEM_NOT_FOUND` | Không tìm thấy bài đăng |
| 409 | `AUTHENTICITY_INVALID_STATUS` | Bài đăng không ở trạng thái chờ xét duyệt chứng từ (`PENDING`) |

---

## 8. POST /api/v1/admin/authenticity/{id}/revoke

Thu hồi nhãn hàng chính hãng khi phát hiện chứng từ giả mạo. Chỉ áp dụng cho bài đăng đang có nhãn chính hãng (`VERIFIED`). Chuyển `auth_status` sang `REVOKED`.

### Path Parameters
- `id`: ID bài đăng cần thu hồi nhãn.

### Request Body (`AuthenticityDecisionRequest`)
```json
{
  "reason": "Xác minh phát hiện hoá đơn/chứng từ giả mạo sau khi nhận báo cáo vi phạm."
}
```

### Response & Mã lỗi
| Mã HTTP | Mã lỗi / Chi tiết | Ý nghĩa |
|---------|-------------------|---------|
| 204 | - | Thu hồi nhãn thành công (No Content) |
| 400 | `VALIDATION_FAILED` / `AUTHENTICITY_REASON_REQUIRED` | Thiếu lý do hoặc lý do chỉ toàn khoảng trắng |
| 401 | `UNAUTHORIZED` | Chưa đăng nhập |
| 403 | `FORBIDDEN` | Không có quyền Admin |
| 404 | `AUTHENTICITY_ITEM_NOT_FOUND` | Không tìm thấy bài đăng |
| 409 | `AUTHENTICITY_NOT_VERIFIED` | Bài đăng chưa được cấp nhãn chính hãng (không ở trạng thái `VERIFIED`) |


