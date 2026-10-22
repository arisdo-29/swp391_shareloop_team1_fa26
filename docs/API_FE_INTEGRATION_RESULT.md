# Kết quả hoàn thiện tích hợp API Frontend

Ngày thực hiện: **2026-10-22**  
Phạm vi: các API ưu tiên và các trạng thái thiếu trong `API_FE_INTEGRATION_AUDIT.md`. Không gọi mutation production.

## Tổng kết

| Method | Endpoint | FE File | UI/Page | Kết quả | Test |
|---|---|---|---|---|---|
| GET | `/api/v1/categories/{categoryId}/attributes` | `frontend/src/services/catalogApi.ts`; `pages/user/Post.tsx`; `pages/user/Activities.tsx` | Form đăng/sửa bài | `INTEGRATED` — tải khi có category, sắp theo `sortOrder`, reset khi đổi category; control `operatingStatus` dùng name/options/required từ response | API test pass; lint/build pass |
| GET | `/api/v1/requests/{requestId}/pinned-items` | `frontend/src/services/chatApi.ts`; `pages/user/Messages.tsx` | Đầu khung Chat | `INTEGRATED` — path canonical, JWT, không query user ID; loading/retry/empty; render image/title/type/status | API test pass; lint/build pass |
| GET | `/api/v1/requests/{requestId}/messages/pinned-items` | Không cần client riêng | Không gọi trùng | `SERVER_ONLY / ALIAS` — cùng controller method với path canonical | Test xác nhận FE chỉ gọi canonical path |
| POST | `/api/v1/admin/authenticity/{id}/grant` | `frontend/src/services/adminAuthenticityApi.ts:44-47`; `pages/admin/AdminAuthenticity.tsx:47-258` | Admin → Nhãn chính hãng (`/admin/authenticity`) | `INTEGRATED_WITH_LIMITATIONS` — nhập ID thủ công, modal xác nhận; Backend kiểm tra PENDING | Test pass: đúng path, không body, xử lý 204 |
| POST | `/api/v1/admin/authenticity/{id}/reject` | `frontend/src/services/adminAuthenticityApi.ts:48-50`; `pages/admin/AdminAuthenticity.tsx:47-258` | Admin → Nhãn chính hãng (`/admin/authenticity`) | `INTEGRATED_WITH_LIMITATIONS` — nhập ID và lý do bắt buộc; Backend kiểm tra PENDING | Test pass: chỉ `{ reason }`, validation và submit guard |
| POST | `/api/v1/admin/authenticity/{id}/revoke` | `frontend/src/services/adminAuthenticityApi.ts:51-53`; `pages/admin/AdminAuthenticity.tsx:47-258` | Admin → Nhãn chính hãng (`/admin/authenticity`) | `INTEGRATED_WITH_LIMITATIONS` — nhập ID và lý do bắt buộc; Backend kiểm tra VERIFIED | Test pass: chỉ `{ reason }`, validation và submit guard |

## Thay đổi đã thực hiện

- `backend/src/main/java/com/shareloop/chat/controller/ChatController.java`
  - Ẩn `@CurrentUserId` khỏi metadata OpenAPI bằng `@Parameter(hidden = true)`.
  - Không thay đổi resolver JWT hay contract runtime.
- `frontend/src/pages/user/Messages.tsx`
  - Giữ path canonical `/pinned-items`, bổ sung retry và empty state.
  - Không gửi `currentUserId` query; không gọi alias trùng.
- `frontend/src/pages/user/Post.tsx`
  - Reset `operatingStatus` và lỗi liên quan khi category thay đổi.
  - Sắp thuộc tính theo `sortOrder`; hiển thị type/options/required từ response.
  - Select trạng thái hoạt động dùng options từ backend, không hardcode danh sách option trong JSX.
- `frontend/src/pages/user/Activities.tsx`
  - Form sửa hiển thị metadata thuộc tính động và dùng options backend cho `operatingStatus`.
- `frontend/src/pages/admin/AdminAuthenticity.tsx`
  - Bổ sung màn hình thao tác thủ công theo ID bài đăng tại route Admin đã được bảo vệ.
  - Ba action đều yêu cầu modal xác nhận; Reject/Revoke bắt buộc lý do.
  - Có loading, khóa double-submit, thông báo thành công/thất bại và cảnh báo thiếu dữ liệu đọc trạng thái.
- `frontend/src/services/adminAuthenticityApi.ts`
  - Giữ nguyên ba API client hiện có; bổ sung validation, submit lock và ánh xạ lỗi HTTP cho UI.
- `frontend/scripts/verify-api.mjs`
  - Sửa test Chat stale.
  - Thêm test path/params cho Category Attributes và Pinned Items.
  - Thêm test body chính xác, 204 không body, reason validation, lỗi 400/401/403/404/409/500, xác nhận và double-submit cho ba mutation Authenticity.
- `docs/API_FE_INTEGRATION_AUDIT.md`
  - Cập nhật trạng thái, bằng chứng và kết quả test.
- `docs/API_FE_INTEGRATION_RESULT.md`
  - Báo cáo kết quả này.

## API đã tồn tại và không cần tạo lại

- `catalogApi.categoryAttributes(categoryId)` đã tồn tại và đúng path.
- `chatApi.pinnedItems(requestId)` đã tồn tại và dùng path canonical.
- `adminAuthenticityApi.grant/reject/revoke` đã khớp method, path và body Swagger; không tạo service trùng.
- Hai pinned path ánh xạ cùng `ChatController.getPinnedItems`; FE chỉ cần canonical path.

## Giới hạn contract còn lại

### Category Attributes

Backend `CreateItemRequest` và `UpdateItemRequest` hiện chỉ nhận thuộc tính động đã chốt là `operatingStatus`; không có `attributes`, `attributeValues` hoặc map tổng quát. FE vì thế:

- Chỉ gửi `operatingStatus` khi response category có SELECT phù hợp.
- Không tự thêm field cho TEXT/NUMBER/BOOLEAN/SELECT khác.
- Hiển thị metadata các thuộc tính còn lại để người dùng biết, nhưng không giả vờ lưu giá trị mà backend chưa nhận.

### Admin Authenticity

Ba mutation đã được gắn vào UI thao tác thủ công theo ID. Giới hạn còn lại:

- `ReviewQueueItem` không có `authStatus` hoặc thông tin chứng từ.
- `ItemResponse` không có `authStatus`.
- Không có GET Admin Authenticity list/detail trong Swagger.
- Create/update item không có trường yêu cầu nhãn/chứng từ, nên FE cũng không có luồng tạo `PENDING`.

FE không tự coi item là `PENDING`/`VERIFIED`, không tự cập nhật `authStatus` sau 204 và không dựng danh sách giả. UI luôn cảnh báo Admin kiểm tra bài đăng trước khi thao tác; Backend là nguồn xác thực điều kiện cuối cùng. Vì chưa có GET trạng thái/chứng từ, ba API được đánh dấu `INTEGRATED_WITH_LIMITATIONS`.

## Kết quả kiểm thử

| Kiểm tra | Kết quả |
|---|---|
| TypeScript typecheck | `PASS` qua `npm run build` |
| ESLint | `PASS` |
| Frontend production build | `PASS`; Vite cảnh báo chunk JS 925,88 kB, không làm build fail |
| API tests | `PASS` — 21/21 |
| Backend targeted tests | `NOT RUN` — Maven dừng trước compile vì máy dùng Java 19, dự án yêu cầu Java 21 |
| Runtime/E2E với API production | `NOT RUN` — không có tài khoản staging và không gọi mutation production |

## Rủi ro và việc tiếp theo

1. Khi Backend có GET `authStatus` và chứng từ, thay thao tác nhập ID thủ công bằng danh sách/chi tiết có trạng thái xác thực.
2. Chạy `./mvnw verify` bằng JDK 21.
3. Deploy backend rồi xác nhận Swagger Chat không còn `currentUserId` query.
4. Smoke-test Chat pinned items với cả GIVE, SWAP và danh sách rỗng trên staging.
5. Nếu backend mở schema giá trị thuộc tính tổng quát, bổ sung DTO FE và control TEXT/NUMBER/SELECT/BOOLEAN sau khi Swagger được chốt.
