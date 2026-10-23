# Báo cáo đối chiếu tích hợp API Backend ↔ Frontend ShareLoop

Ngày audit: **23/10/2026**  
Phạm vi: toàn bộ controller Swagger trong `backend/src/main/java`, contract trong `docs/api`, và toàn bộ `frontend/src` (service, component, page, router).

## 1. Phương pháp và quy ước

- Đơn vị đối chiếu là cặp **HTTP method + path**, không suy luận chỉ từ tên hàm.
- Một API chỉ được xếp `INTEGRATED` khi có đủ chuỗi: contract/service hoặc URL gọi trực tiếp → component/page gọi ở runtime → giao diện dùng kết quả hoặc cung cấp thao tác.
- Khai báo trong service hoặc chỉ xuất hiện trong test không đủ để được xem là đã tích hợp.
- `PARTIAL` dùng khi FE đã khai báo nhưng chưa gọi, hoặc đã gọi nhưng UI chưa đủ dữ liệu/ngữ cảnh để vận hành an toàn.
- `BLOCKED` dùng cho endpoint được yêu cầu kiểm tra nhưng không tồn tại trong Swagger/controller hiện tại; không tính vào tổng API Backend.
- `BACKEND_ONLY` dùng cho callback/IPN do cổng thanh toán gọi trực tiếp, không phải API mà SPA chủ động gọi.
- Hai alias tương thích được gộp thành một chức năng và chỉ tính một lần:
  - `GET /api/v1/admin/items/pending` là alias cũ/fallback của `GET /api/v1/admin/reviews/pending`.
  - `GET /api/v1/requests/{requestId}/messages/pinned-items` là alias của `GET /api/v1/requests/{requestId}/pinned-items`.
- Mức ưu tiên: **P0** chặn luồng cốt lõi/an toàn; **P1** chức năng nghiệp vụ đã có Backend nhưng UI chưa dùng được đầy đủ; **P2** tính năng bổ trợ.

Các điểm vào FE chính được kiểm tra gồm `frontend/src/services/*`, `frontend/src/components/*`, `frontend/src/pages/**/*`, `frontend/src/app/router.tsx`, `frontend/src/app/WalletContext.tsx` và `frontend/src/layouts/AppShell.tsx`.

## 2. Kết luận nhanh

| Phân loại | Số API logic | Tỷ lệ |
|---|---:|---:|
| `INTEGRATED` | 104 | 95,4% |
| `PARTIAL` | 3 | 2,8% |
| `NOT_INTEGRATED` | 0 | 0% |
| `BACKEND_ONLY` | 2 | 1,8% |
| **Tổng** | **109** | **100%** |

Không còn API Swagger nào thuộc `NOT_INTEGRATED`. Ba API `PARTIAL` đều thuộc xác minh hàng chính hãng và bị giới hạn bởi dữ liệu đọc mà Backend hiện cung cấp. Hai endpoint được yêu cầu kiểm tra thêm nhưng không tồn tại trong Swagger/controller được ghi riêng là `BLOCKED`, không đưa vào mẫu số 109.

## 3. Ma trận đối chiếu chi tiết

### 3.1. Auth — 8/8 `INTEGRATED`

| Method | Endpoint | Chức năng | Bằng chứng sử dụng ở FE | Phân loại |
|---|---|---|---|---|
| POST | `/api/v1/auth/register` | Đăng ký và gửi OTP | `services/authApi.ts` → `pages/auth/Register.tsx` | INTEGRATED |
| POST | `/api/v1/auth/verify-otp` | Xác thực OTP đăng ký | `services/authApi.ts` → `pages/auth/VerifyEmail.tsx` | INTEGRATED |
| POST | `/api/v1/auth/resend-otp` | Gửi lại OTP | `services/authApi.ts` → `pages/auth/VerifyEmail.tsx` | INTEGRATED |
| POST | `/api/v1/auth/login` | Đăng nhập | `services/authApi.ts` → `pages/auth/Login.tsx` | INTEGRATED |
| POST | `/api/v1/auth/logout` | Đăng xuất | `services/authApi.ts` → `layouts/AppShell.tsx` | INTEGRATED |
| POST | `/api/v1/auth/change-password` | Đổi mật khẩu | `services/authApi.ts` → `components/ChangePasswordForm.tsx` → route `/change-password` | INTEGRATED |
| POST | `/api/v1/auth/forgot-password` | Gửi OTP quên mật khẩu | `services/authApi.ts` → `pages/auth/ForgotPassword.tsx` | INTEGRATED |
| POST | `/api/v1/auth/reset-password` | Đặt lại mật khẩu | `services/authApi.ts` → `pages/auth/ForgotPassword.tsx` | INTEGRATED |

### 3.2. Hồ sơ người dùng — 4/4 `INTEGRATED`

| Method | Endpoint | Chức năng | Bằng chứng sử dụng ở FE | Phân loại |
|---|---|---|---|---|
| GET | `/api/v1/me` | Đọc hồ sơ hiện tại | `services/userApi.ts` → `services/session.ts`, `pages/user/Profile.tsx` | INTEGRATED |
| PUT | `/api/v1/me` | Cập nhật hồ sơ/avatar | `services/userApi.ts` → `pages/user/Profile.tsx` | INTEGRATED |
| POST | `/api/v1/me/phone/otp` | Gửi OTP đổi số điện thoại | `services/userApi.ts` → `pages/user/Profile.tsx` | INTEGRATED |
| PUT | `/api/v1/me/phone` | Xác nhận đổi số điện thoại | `services/userApi.ts` → `pages/user/Profile.tsx` | INTEGRATED |

### 3.3. Catalog công khai và quản trị — 16/16 `INTEGRATED`

| Method | Endpoint | Chức năng | Bằng chứng sử dụng ở FE | Phân loại |
|---|---|---|---|---|
| GET | `/api/v1/areas` | Danh sách khu vực | `services/catalogApi.ts` → session, Home, Browse, Post, Profile, Activities, AI | INTEGRATED |
| GET | `/api/v1/categories` | Danh sách danh mục | `services/catalogApi.ts` → Home, Browse, Post, Activities, AI, moderation | INTEGRATED |
| GET | `/api/v1/categories/{id}` | Chi tiết danh mục | `services/catalogApi.ts` → `pages/public/ProductDetail.tsx` | INTEGRATED |
| GET | `/api/v1/categories/{categoryId}/attributes` | Thuộc tính động của danh mục | `services/catalogApi.ts` → Post và form sửa trong Activities | INTEGRATED |
| GET | `/api/v1/admin/areas` | Danh sách khu vực cho Admin | `services/adminAreaApi.ts` → trang `/admin/districts` trong `AdminPages.tsx` | INTEGRATED |
| POST | `/api/v1/admin/areas` | Tạo khu vực | `services/adminAreaApi.ts` → UI quản lý khu vực | INTEGRATED |
| PUT | `/api/v1/admin/areas/{id}` | Sửa khu vực | `services/adminAreaApi.ts` → UI quản lý khu vực | INTEGRATED |
| PATCH | `/api/v1/admin/areas/{id}/active` | Ẩn/hiện khu vực | `services/adminAreaApi.ts` → UI quản lý khu vực | INTEGRATED |
| GET | `/api/v1/admin/categories` | Danh sách danh mục cho Admin | `services/adminCategoryApi.ts` → trang `/admin/categories` | INTEGRATED |
| POST | `/api/v1/admin/categories` | Tạo danh mục | `services/adminCategoryApi.ts` → UI quản lý danh mục | INTEGRATED |
| PUT | `/api/v1/admin/categories/{id}` | Sửa danh mục | `services/adminCategoryApi.ts` → UI quản lý danh mục | INTEGRATED |
| PATCH | `/api/v1/admin/categories/{id}/active` | Ẩn/hiện danh mục | `services/adminCategoryApi.ts` → UI quản lý danh mục | INTEGRATED |
| GET | `/api/v1/admin/categories/{categoryId}/attributes` | Danh sách thuộc tính cho Admin | `services/adminCategoryApi.ts` → UI thuộc tính danh mục | INTEGRATED |
| POST | `/api/v1/admin/categories/{categoryId}/attributes` | Tạo thuộc tính | `services/adminCategoryApi.ts` → UI thuộc tính danh mục | INTEGRATED |
| PUT | `/api/v1/admin/categories/{categoryId}/attributes/{attributeId}` | Sửa thuộc tính | `services/adminCategoryApi.ts` → UI thuộc tính danh mục | INTEGRATED |
| PATCH | `/api/v1/admin/categories/{categoryId}/attributes/{attributeId}/active` | Ẩn/hiện thuộc tính | `services/adminCategoryApi.ts` → UI thuộc tính danh mục | INTEGRATED |

### 3.4. Media — 2/2 `INTEGRATED`

| Method | Endpoint | Chức năng | Bằng chứng sử dụng ở FE | Phân loại |
|---|---|---|---|---|
| POST | `/api/v1/media` | Upload ảnh | `services/mediaApi.ts` → Post, Profile, Messages, ReportDialog, ReportResponse, Activities | INTEGRATED |
| GET | `/api/v1/media/{id}` | Đọc ảnh công khai | `services/mediaUrl.ts` tạo URL; ảnh được render ở `components/ui.tsx`, Messages và moderation | INTEGRATED |

`GET /media/{id}` được trình duyệt gọi qua `src`/`href`, không đi qua Axios; đây vẫn là tích hợp UI thực tế chứ không chỉ là khai báo.

### 3.5. Bài đăng — 6/6 `INTEGRATED`

| Method | Endpoint | Chức năng | Bằng chứng sử dụng ở FE | Phân loại |
|---|---|---|---|---|
| POST | `/api/v1/items` | Tạo bài | `services/itemApi.ts` → `pages/user/Post.tsx` | INTEGRATED |
| GET | `/api/v1/items/{id}` | Chi tiết bài | ProductDetail, Activities, Messages và modal moderation | INTEGRATED |
| PUT | `/api/v1/items/{id}` | Sửa bài | `services/itemApi.ts` → `pages/user/Activities.tsx` | INTEGRATED |
| DELETE | `/api/v1/items/{id}` | Xóa mềm bài | `services/itemApi.ts` → `pages/user/Activities.tsx` | INTEGRATED |
| GET | `/api/v1/me/items` | Bài của tôi | ProductDetail, Activities, Messages | INTEGRATED |
| GET | `/api/v1/items` | Danh sách/tìm kiếm bài | Home và Browse qua `itemApi.list`/`listItems` | INTEGRATED |

### 3.6. Listing fees — 5/5 `INTEGRATED`

| Method | Endpoint | Chức năng | Bằng chứng sử dụng ở FE | Phân loại |
|---|---|---|---|---|
| GET | `/api/v1/listing-fees` | Đọc phí đăng/sửa/gia hạn/đẩy tin | `services/itemApi.ts` → Post và Activities | INTEGRATED |
| GET | `/api/v1/listing-fees/held` | Danh sách Credit đang giữ theo bài | `services/walletApi.ts` → `pages/user/Credit.tsx` trên `/credit` và `/wallet` | INTEGRATED |
| GET | `/api/v1/items/{id}/edit-quote` | Báo phí trước khi sửa | `services/itemApi.ts` → modal sửa trong Activities | INTEGRATED |
| POST | `/api/v1/items/{id}/renew` | Gia hạn bài | `services/itemApi.ts` → Activities | INTEGRATED |
| POST | `/api/v1/items/{id}/boost` | Đẩy bài | `services/itemApi.ts` → Activities | INTEGRATED |

Phần `held` có loading, lỗi/retry, empty state sau tải thành công và hiển thị đủ contract. Không thấy dữ liệu giả thay thế response API trong luồng tài khoản Backend.

### 3.7. Ví, nạp Credit và VNPAY — 11 API logic

| Method | Endpoint | Chức năng | Bằng chứng sử dụng ở FE | Phân loại |
|---|---|---|---|---|
| GET | `/api/v1/wallet` | Số dư ví | `services/walletApi.ts` → `app/WalletContext.tsx` → AppShell, Credit, Profile, Post | INTEGRATED |
| GET | `/api/v1/wallet/transactions` | Lịch sử Credit | `services/walletApi.ts` → Credit | INTEGRATED |
| POST | `/api/v1/wallet/topup-orders` | Tạo đơn nạp | `services/paymentApi.ts` → Credit | INTEGRATED |
| GET | `/api/v1/wallet/topup-orders/{orderCode}` | Đọc đơn MANUAL | `services/paymentApi.ts` → ManualPaymentCheckout và kiểm tra trạng thái trả về | INTEGRATED |
| POST | `/api/v1/wallet/topup-orders/{orderCode}/mark-paid` | Người dùng báo đã chuyển khoản | `services/paymentApi.ts` → ManualPaymentCheckout | INTEGRATED |
| POST | `/api/v1/wallet/topup-orders/{orderCode}/mock-confirm` | Xác nhận thanh toán mock | `services/paymentApi.ts`/`paymentFlow.ts` → DemoPaymentCheckout | INTEGRATED |
| GET | `/api/v1/admin/finance/topup-orders` | Danh sách đơn nạp MANUAL | `services/adminFinanceApi.ts` → AdminTopUpOrders | INTEGRATED |
| POST | `/api/v1/admin/finance/topup-orders/{orderCode}/approve` | Admin duyệt đơn nạp | `services/adminFinanceApi.ts` → AdminTopUpOrders | INTEGRATED |
| POST | `/api/v1/admin/finance/topup-orders/{orderCode}/reject` | Admin từ chối đơn nạp | `services/adminFinanceApi.ts` → AdminTopUpOrders | INTEGRATED |
| GET | `/api/v1/payments/vnpay/ipn` | VNPAY server callback | Không nên được SPA gọi; VNPAY gọi thẳng Backend | BACKEND_ONLY |
| GET | `/api/v1/payments/vnpay/return` | Điểm trả về từ VNPAY, Backend xác minh rồi điều hướng | Không cần service FE gọi trực tiếp; FE xử lý kết quả redirect tại Credit | BACKEND_ONLY |

### 3.8. AI search — 2/2 `INTEGRATED`

| Method | Endpoint | Chức năng | Bằng chứng sử dụng ở FE | Phân loại |
|---|---|---|---|---|
| GET | `/api/v1/ai/quota` | Đọc hạn mức tìm kiếm AI | `services/aiApi.ts` → `pages/public/AI.tsx` với loading/lỗi/retry | INTEGRATED |
| POST | `/api/v1/ai/search` | Tìm kiếm bằng AI | `services/aiApi.ts` → AI, có kết quả và phân trang | INTEGRATED |

### 3.9. Request và media giao nhận — 12/12 `INTEGRATED`

| Method | Endpoint | Chức năng | Bằng chứng sử dụng ở FE | Phân loại |
|---|---|---|---|---|
| POST | `/api/v1/items/{itemId}/requests` | Gửi yêu cầu nhận/trao đổi | `services/requestApi.ts` → ProductDetail | INTEGRATED |
| GET | `/api/v1/items/{itemId}/requests` | Yêu cầu theo bài của chủ bài | Messages và Activities | INTEGRATED |
| GET | `/api/v1/me/requests` | Yêu cầu của tôi | ProductDetail, Messages, Activities | INTEGRATED |
| POST | `/api/v1/requests/{id}/cancel` | Hủy yêu cầu | ProductDetail, Messages, Activities | INTEGRATED |
| POST | `/api/v1/requests/{id}/select` | Chọn người giao dịch | Messages và Activities | INTEGRATED |
| GET | `/api/v1/requests/{id}` | Chi tiết yêu cầu | Messages | INTEGRATED |
| POST | `/api/v1/requests/{id}/schedule` | Đề xuất/cập nhật lịch | Messages | INTEGRATED |
| POST | `/api/v1/requests/{id}/confirm-logistics` | Xác nhận logistics | Messages | INTEGRATED |
| GET | `/api/v1/requests/{id}/contact` | Mở thông tin liên hệ đúng trạng thái | Messages | INTEGRATED |
| POST | `/api/v1/requests/{id}/confirm-handover` | Xác nhận giao nhận | Messages | INTEGRATED |
| POST | `/api/v1/requests/{id}/media` | Gắn ảnh bằng chứng vào request | `services/requestApi.ts` → Messages | INTEGRATED |
| GET | `/api/v1/requests/{id}/media` | Đọc ảnh bằng chứng của request | `services/requestApi.ts` → Messages | INTEGRATED |

Hai API request media mới đã có upload media trước, attach ID, tải danh sách và gallery thực tế trong giao diện Messages.

### 3.10. Chat — 3/3 `INTEGRATED`

| Method | Endpoint | Chức năng | Bằng chứng sử dụng ở FE | Phân loại |
|---|---|---|---|---|
| POST | `/api/v1/requests/{requestId}/messages` | Gửi tin nhắn/text/media/item card | `services/chatApi.ts` → Messages | INTEGRATED |
| GET | `/api/v1/requests/{requestId}/messages` | Đọc/poll tin nhắn theo `afterId` | `services/chatApi.ts` → Messages | INTEGRATED |
| GET | `/api/v1/requests/{requestId}/pinned-items` | Danh sách thẻ bài ghim | `services/chatApi.ts` → khu vực pinned items trong Messages | INTEGRATED |

Alias `/messages/pinned-items` được Backend giữ tương thích nhưng FE dùng canonical `/pinned-items`; chỉ tính một chức năng.

### 3.11. Thông báo — 3/3 `INTEGRATED`

| Method | Endpoint | Chức năng | Bằng chứng sử dụng ở FE | Phân loại |
|---|---|---|---|---|
| GET | `/api/v1/notifications` | Danh sách thông báo | `services/notificationApi.ts` → `components/NotificationMenu.tsx` | INTEGRATED |
| POST | `/api/v1/notifications/{id}/read` | Đánh dấu đã đọc | NotificationMenu | INTEGRATED |
| GET | `/api/v1/notifications/unread-count` | Đếm chưa đọc | NotificationMenu | INTEGRATED |

### 3.12. Uy tín — 2/2 `INTEGRATED`

| Method | Endpoint | Chức năng | Bằng chứng sử dụng ở FE | Phân loại | Ưu tiên/vị trí nên tích hợp |
|---|---|---|---|---|---|
| GET | `/api/v1/me/reputation` | Tổng quan, hạng và lịch sử uy tín cá nhân | `services/reputationApi.ts` → tab uy tín trong Profile | INTEGRATED | — |
| POST | `/api/v1/admin/users/{userId}/grace-star` | Admin cấp sao hỗ trợ có lý do | `services/adminReputationApi.ts` → `pages/admin/AdminReputation.tsx` → route `/admin/reputation`; có kiểm tra input, xác nhận, khóa khi gửi, kết quả và ánh xạ lỗi 401/403/404/409 | INTEGRATED | — |

### 3.13. Kiểm duyệt bài — 4/4 chức năng canonical `INTEGRATED`

| Method | Endpoint | Chức năng | Bằng chứng sử dụng ở FE | Phân loại |
|---|---|---|---|---|
| GET | `/api/v1/admin/reviews/pending` | Hàng chờ, risk/flag và checklist | `services/adminModerationApi.ts` → AdminModeration | INTEGRATED |
| POST | `/api/v1/admin/reviews/{itemId}/approve` | Duyệt với checklist ID | AdminModeration gửi `{answers: [{id, passed}]}` | INTEGRATED |
| GET | `/api/v1/admin/reviews/reject-reasons` | Lý do từ chối mẫu | AdminModeration | INTEGRATED |
| POST | `/api/v1/admin/reviews/{itemId}/reject` | Từ chối bài có lý do | AdminModeration | INTEGRATED |

`GET /api/v1/admin/items/pending` là endpoint tương thích cũ. FE chỉ gọi làm fallback khi endpoint canonical thất bại; không tính thành API logic thứ hai và không xem đây là một chức năng thiếu.

Không tồn tại endpoint Swagger chuyên biệt kiểu `GET /api/v1/admin/reviews/{itemId}` trong Backend hiện tại. UI chi tiết moderation dùng `GET /api/v1/items/{id}` sau khi chọn một dòng từ hàng chờ. Vì vậy không có “admin review detail endpoint” nào để đánh dấu thiếu; dữ liệu hàng chờ/checklist và chi tiết bài đều đã được dùng.

### 3.14. Xác minh chính hãng — 3/3 `PARTIAL`

| Method | Endpoint | Chức năng | Hiện trạng FE | Phân loại | Ưu tiên/vị trí nên hoàn thiện |
|---|---|---|---|---|---|
| POST | `/api/v1/admin/authenticity/{id}/grant` | Cấp nhãn chính hãng | `AdminAuthenticity.tsx` bắt buộc tải bài thật qua `GET /items/{id}`, hiển thị tiêu đề/chủ bài/loại/trạng thái bài rồi mới bật confirm và mutation; Backend vẫn không trả auth status/chứng từ | PARTIAL | **P1** — cần Backend cung cấp hàng chờ/detail authenticity và trạng thái `PENDING` trước khi có thể hoàn thiện |
| POST | `/api/v1/admin/authenticity/{id}/reject` | Từ chối xác minh | Có bài thật, confirm, lý do, loading/error và khóa double-submit; thiếu auth status/chứng từ từ Backend | PARTIAL | **P1** — cần cùng API đọc authenticity nêu trên |
| POST | `/api/v1/admin/authenticity/{id}/revoke` | Thu hồi nhãn | Có bài thật, confirm, lý do, loading/error; không thể xác định `VERIFIED` trước mutation vì Backend không trả trường này | PARTIAL | **P1** — cần API đọc trạng thái/list bài đã xác minh |

Lưu ý: Backend hiện cũng chưa có API list/detail riêng cho hàng chờ authenticity. Để hoàn thiện UI đúng nghĩa cần bổ sung nguồn đọc ở Backend hoặc mở rộng response bài đăng một cách có contract; không nên suy đoán trạng thái trên FE.

### 3.15. Report, dispute và bằng chứng — 8/8 `INTEGRATED`

| Method | Endpoint | Chức năng | Bằng chứng sử dụng ở FE | Phân loại |
|---|---|---|---|---|
| POST | `/api/v1/reports` | Gửi báo cáo bài/người dùng/nội dung | `services/reportApi.ts` → `components/ReportDialog.tsx` | INTEGRATED |
| POST | `/api/v1/reports/{reportId}/response` | Người bị báo cáo gửi giải trình | `pages/user/ReportResponse.tsx` | INTEGRATED |
| POST | `/api/v1/requests/{requestId}/disputes` | Mở khiếu nại giao dịch | `openDispute` → Messages | INTEGRATED |
| POST | `/api/v1/disputes/{disputeId}/response` | Gửi giải trình khiếu nại | `respondDispute` → Messages | INTEGRATED |
| GET | `/api/v1/admin/reports` | Danh sách hồ sơ có lọc/phân trang | `services/reportApi.ts` → AdminComplaints | INTEGRATED |
| GET | `/api/v1/admin/reports/{reportId}` | Chi tiết hồ sơ | AdminComplaints | INTEGRATED |
| GET | `/api/v1/admin/reports/{reportId}/evidence/{mediaId}` | Đọc bằng chứng theo quyền Admin | AdminComplaints tạo object URL và render gallery | INTEGRATED |
| POST | `/api/v1/admin/reports/{reportId}/conclude` | Kết luận `UPHOLD`/`DISMISS` | AdminComplaints có confirm, chống double-submit và xử lý xung đột 409 | INTEGRATED |

### 3.16. Cấu hình, từ khóa và checklist — 13/13 `INTEGRATED`

| Method | Endpoint | Chức năng | Bằng chứng sử dụng ở FE | Phân loại |
|---|---|---|---|---|
| GET | `/api/v1/admin/configs` | Danh sách cấu hình | `services/adminConfigApi.ts` → AdminSettings | INTEGRATED |
| PUT | `/api/v1/admin/configs/{key}` | Cập nhật theo key | AdminSettings dùng làm fallback tương thích | INTEGRATED |
| PUT | `/api/v1/admin/configs` | Cập nhật với key trong body | AdminSettings dùng làm đường chính | INTEGRATED |
| GET | `/api/v1/admin/lexicon` | Danh sách từ khóa | `services/adminLexiconApi.ts` → `/admin/keywords` | INTEGRATED |
| POST | `/api/v1/admin/lexicon` | Tạo từ khóa | UI quản lý từ khóa | INTEGRATED |
| PUT | `/api/v1/admin/lexicon/{id}` | Sửa từ khóa | UI quản lý từ khóa | INTEGRATED |
| PATCH | `/api/v1/admin/lexicon/{id}/active` | Ẩn/hiện từ khóa | UI quản lý từ khóa | INTEGRATED |
| POST | `/api/v1/admin/lexicon/test` | Thử khớp nội dung | UI kiểm tra từ khóa | INTEGRATED |
| GET | `/api/v1/admin/checklist` | Danh sách checklist | `services/adminChecklistApi.ts` → `/admin/checklist` | INTEGRATED |
| POST | `/api/v1/admin/checklist` | Tạo mục checklist | UI checklist | INTEGRATED |
| PUT | `/api/v1/admin/checklist/{id}` | Sửa mục checklist | UI checklist | INTEGRATED |
| PATCH | `/api/v1/admin/checklist/{id}/active` | Ẩn/hiện mục checklist | UI checklist | INTEGRATED |
| PUT | `/api/v1/admin/checklist/reorder` | Sắp xếp checklist | UI checklist | INTEGRATED |

### 3.17. Audit log — 2/2 `INTEGRATED`

| Method | Endpoint | Chức năng | Bằng chứng sử dụng ở FE | Phân loại |
|---|---|---|---|---|
| GET | `/api/v1/admin/activity-logs` | Nhật ký có lọc/phân trang | `services/adminAuditApi.ts` → dashboard và AdminAuditLogs | INTEGRATED |
| GET | `/api/v1/admin/activity-logs/types` | Danh sách loại hành động | AdminAuditLogs dùng cho bộ lọc, có fallback nhãn cũ | INTEGRATED |

### 3.18. Tài chính và quản lý tài khoản — 5 API logic

| Method | Endpoint | Chức năng | Bằng chứng sử dụng ở FE | Phân loại | Ưu tiên/vị trí nên tích hợp |
|---|---|---|---|---|---|
| GET | `/api/v1/admin/finance/report` | Tổng hợp tiền/Credit/đối soát | `services/adminFinanceApi.ts` → dashboard và `/admin/finance` | INTEGRATED | — |
| GET | `/api/v1/admin/finance/report.csv` | Tải báo cáo tài chính CSV theo khoảng ngày | `adminFinanceApi.reportCsv` gọi với `responseType: 'blob'`; nút “Xuất báo cáo CSV” tại `AdminFinanceReport` dùng đúng bộ lọc đã áp dụng, giữ filename từ `Content-Disposition`, có loading/thành công/lỗi | INTEGRATED | — |
| GET | `/api/v1/admin/finance/users/{userId}/history` | Lịch sử Credit của một người | `services/adminFinanceApi.ts` → `/admin/finance/users/:userId` | INTEGRATED | — |
| POST | `/api/v1/admin/users/{userId}/lock` | Khóa tài khoản có lý do | `adminFinanceApi.setLocked` → `/admin/users` | INTEGRATED | — |
| POST | `/api/v1/admin/users/{userId}/unlock` | Mở khóa tài khoản có lý do | `adminFinanceApi.setLocked` → `/admin/users` | INTEGRATED | — |

Hai endpoint lock/unlock được một hàm FE chọn path động theo boolean nhưng vẫn là hai cặp method/path đã được UI gọi thực tế.

## 4. Kiểm tra riêng các nhóm API mới được yêu cầu

| Nhóm | Kết quả |
|---|---|
| Listing fees | 5/5 đã tích hợp, gồm phí cấu hình, held, edit quote, renew, boost. |
| Admin review detail | Không có endpoint detail riêng trong Swagger hiện tại. UI dùng queue response + `GET /items/{id}`; không ghi nhận API bị bỏ sót. |
| Request media | Cả POST attach và GET list đều đã tích hợp trong Messages. |
| Disputes | Mở khiếu nại, gửi giải trình, admin list/detail/evidence/conclude đều đã tích hợp. |
| Reputation | API cá nhân và API admin `grace-star` đều đã tích hợp. |
| Anomalies | `GET /api/v1/admin/anomalies` không có trong controller/Swagger hiện tại: **BLOCKED**. `/admin/alerts` đã ngừng hiển thị dữ liệu local và báo rõ chưa có nguồn Backend. |
| Finance | Báo cáo JSON, CSV, đơn nạp, lịch sử người dùng và lock/unlock đều đã tích hợp. |

## 5. Trạng thái các API trong phạm vi triển khai lần này

### `INTEGRATED`

1. `POST /api/v1/admin/users/{userId}/grace-star` — form thật tại `/admin/reputation`, đúng body `{reason}`, có xác nhận, loading, kết quả và xử lý lỗi.
2. `GET /api/v1/admin/finance/report.csv` — xuất blob theo đúng `from`/`to` đã áp dụng, dùng filename Backend và có trạng thái loading/lỗi.

### `PARTIAL` — P1

1. `POST /api/v1/admin/authenticity/{id}/grant` — đã tải bài thật và gọi mutation đúng contract; thiếu auth status/chứng từ từ Backend.
2. `POST /api/v1/admin/authenticity/{id}/reject` — đã có ngữ cảnh bài, xác nhận, lý do, loading/lỗi; thiếu auth status/chứng từ từ Backend.
3. `POST /api/v1/admin/authenticity/{id}/revoke` — đã có ngữ cảnh bài, xác nhận, lý do, loading/lỗi; không thể biết bài đang `VERIFIED` trước khi gọi.

### `BLOCKED` — không tồn tại trong Swagger/controller Backend hiện tại

1. `GET /api/v1/admin/anomalies` — không thể tích hợp `/admin/alerts` bằng dữ liệu thật. Cần Backend bổ sung contract đọc cảnh báo; ưu tiên đề xuất **P1**.
2. `GET /api/v1/admin/reviews/{itemId}` — không thể gọi endpoint này. Moderation tiếp tục dùng response hàng chờ và `GET /api/v1/items/{id}` hiện có; không tự đổi endpoint hay dựng detail giả.

## 6. Ghi chú ngoài mẫu số API

- **Cảnh báo bất thường:** route và UI có tồn tại nhưng chỉ hiển thị trạng thái `BLOCKED`, không còn dựng cảnh báo từ dữ liệu local/mock. Khi Backend bổ sung contract, vị trí tích hợp là `/admin/alerts`.
- **Xác minh chính hãng:** FE nay bắt buộc tải bài thật trước khi mở thao tác. Ba mutation vẫn là `PARTIAL` vì API bài đăng không có auth status/chứng từ và Backend không có API đọc hàng chờ/detail authenticity.
- **Alias:** không tạo hai mục thiếu cho endpoint tương thích cũ. FE ưu tiên canonical và chỉ dùng alias review cũ làm fallback.
- Các mutation mới chỉ được kiểm tra bằng mock/unit test; không gọi môi trường production.

## 7. Tổng hợp cuối

- **Tổng số API logic:** 109
- **Đã tích hợp (`INTEGRATED`):** 104
- **Tích hợp một phần (`PARTIAL`):** 3
- **Chưa tích hợp (`NOT_INTEGRATED`):** 0
- **Chỉ dành cho Backend/cổng thanh toán (`BACKEND_ONLY`):** 2
- **API Swagger còn `PARTIAL`, theo ưu tiên:**
  1. P1 — `POST /api/v1/admin/authenticity/{id}/grant`
  2. P1 — `POST /api/v1/admin/authenticity/{id}/reject`
  3. P1 — `POST /api/v1/admin/authenticity/{id}/revoke`
- **Endpoint yêu cầu kiểm tra nhưng `BLOCKED` vì Backend chưa có:**
  1. `GET /api/v1/admin/anomalies`
  2. `GET /api/v1/admin/reviews/{itemId}`

