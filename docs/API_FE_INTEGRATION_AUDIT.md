# Audit tích hợp API Backend ↔ Frontend

Ngày audit: **2026-10-22**  
Nguồn API: [`https://shareloop-api.onrender.com/v3/api-docs`](https://shareloop-api.onrender.com/v3/api-docs)  
Phạm vi: snapshot source hiện tại và OpenAPI công khai; **không gọi mutation thật**.

## A. Tổng quan

OpenAPI công khai có **95 path / 108 operation**. Mỗi `Method + Path` được tính là một API riêng.

| Trạng thái | Số lượng | Tỷ lệ |
|---|---:|---:|
| `INTEGRATED` | 102 | 94,44% |
| `INTEGRATED_WITH_LIMITATIONS` | 3 | 2,78% |
| `PARTIAL` | 0 | 0% |
| `NOT_INTEGRATED` | 0 | 0% |
| `UNUSED` | 0 | 0% |
| `MISMATCH` | 0 | 0% |
| `MOCK` | 0 | 0% |
| `UNVERIFIED` | 0 | 0% |
| `SERVER_ONLY / CALLBACK` | 2 | 1,85% |
| `SERVER_ONLY / ALIAS` | 1 | 0,93% |
| **Tổng** | **108** | **100%** |

Kết luận ngắn: các luồng người dùng, pinned items trong chat, thanh toán thủ công, ví, báo cáo và Admin đã có chuỗi UI → service → HTTP. Ba mutation **Admin Authenticity** đã có UI thao tác thủ công theo ID và được đánh dấu `INTEGRATED_WITH_LIMITATIONS` vì backend chưa có read contract trả `authStatus`/chứng từ hoặc danh sách PENDING, VERIFIED.

### Cách đọc và giới hạn

- `INTEGRATED` trong báo cáo này nghĩa là đã truy vết được chuỗi source và contract phù hợp. Nó **không có nghĩa** endpoint đã được smoke-test thành công với server thật.
- `Public`: không cần JWT; `JWT`: Bearer token; `ADMIN`: Bearer token + role Admin.
- Cấu hình HTTP chung gắn Bearer token tại `frontend/src/services/http.ts:64-67`, chuẩn hoá lỗi tại `frontend/src/services/http.ts:70-88`; route Admin được chặn tại `frontend/src/routes/Guards.tsx:41-45`.
- OpenAPI hiển thị `currentUserId` như query bắt buộc cho nhiều method dù backend dùng `@CurrentUserId` để lấy principal từ JWT (`backend/src/main/java/com/shareloop/chat/controller/ChatController.java:42-46`). Vì vậy cột contract dưới đây theo contract nghiệp vụ/implementation thật; đây là lỗi metadata OpenAPI cần sửa riêng.

## B. Bảng audit chi tiết

Ký hiệu contract: `Body → Response`; phần trong ngoặc là path/query quan trọng.
Trong các cột bằng chứng, tên file service được tính từ `frontend/src/services/`; tên page/component tính từ `frontend/src/pages/`, `frontend/src/components/`, `frontend/src/app/` hoặc `frontend/src/layouts/` theo tên file nêu trong dòng.

### Authentication

| # | Method | Endpoint | Contract/Auth | FE Service | UI/Page | Status | Vấn đề |
|---:|---|---|---|---|---|---|---|
| 1 | POST | `/api/v1/auth/register` | `RegisterRequest → OtpSentResponse`, Public | `authApi.ts:17` | `Register.tsx:48` | `INTEGRATED` | Form gửi dữ liệu, lưu OTP context và chuyển sang xác minh. |
| 2 | POST | `/api/v1/auth/verify-otp` | `VerifyOtpRequest → AuthResponse`, Public | `authApi.ts:21` | `VerifyEmail.tsx:102` | `INTEGRATED` | Token/phiên được khởi tạo sau xác minh. |
| 3 | POST | `/api/v1/auth/resend-otp` | `ResendOtpRequest → OtpSentResponse`, Public | `authApi.ts:27` | `VerifyEmail.tsx:59` | `INTEGRATED` | UI có cooldown, success/error. |
| 4 | POST | `/api/v1/auth/login` | `LoginRequest → AuthResponse`, Public | `authApi.ts:31` | `Login.tsx:40` | `INTEGRATED` | Token được lưu và `/me` được nạp qua `session.ts:8-11`. |
| 5 | POST | `/api/v1/auth/logout` | `— → 204`, JWT | `authApi.ts:52` | `AppShell.tsx:69` | `INTEGRATED` | Client luôn xoá token cục bộ kể cả khi request lỗi. |
| 6 | POST | `/api/v1/auth/change-password` | `ChangePasswordRequest → 204`, JWT | `authApi.ts:38` | `ChangePasswordForm.tsx:56` | `INTEGRATED` | Form có validation và error state. |
| 7 | POST | `/api/v1/auth/forgot-password` | `ForgotPasswordRequest → OtpSentResponse`, Public | `authApi.ts:42` | `ForgotPassword.tsx:62` | `INTEGRATED` | Luồng gửi OTP đã nối UI. |
| 8 | POST | `/api/v1/auth/reset-password` | `ResetPasswordRequest → 204`, Public | `authApi.ts:47` | `ForgotPassword.tsx:119` | `INTEGRATED` | Luồng OTP → mật khẩu mới đã nối UI. |

### User / Profile / Reputation

| # | Method | Endpoint | Contract/Auth | FE Service | UI/Page | Status | Vấn đề |
|---:|---|---|---|---|---|---|---|
| 9 | GET | `/api/v1/me` | `— → MeResponse`, JWT | `userApi.ts:39` | `Profile.tsx:192`; `session.ts:9` | `INTEGRATED` | Dùng cho profile và khôi phục phiên. |
| 10 | PUT | `/api/v1/me` | `UpdateProfileRequest → MeResponse`, JWT | `userApi.ts:43` | `Profile.tsx:406` | `INTEGRATED` | Response cập nhật state/profile. |
| 11 | POST | `/api/v1/me/phone/otp` | `SendChangePhoneOtpRequest → ChangePhoneOtpResponse`, JWT | `userApi.ts:47` | `Profile.tsx:297` | `INTEGRATED` | Có cooldown, success/error. |
| 12 | PUT | `/api/v1/me/phone` | `ChangePhoneRequest → MeResponse`, JWT | `userApi.ts:51` | `Profile.tsx:350` | `INTEGRATED` | UI gửi OTP và số điện thoại. |
| 13 | GET | `/api/v1/me/reputation` | `page,size → MyReputationResponse`, JWT | `reputationApi.ts:24` | `Profile.tsx:251` | `INTEGRATED` | Lịch sử uy tín có phân trang/error. |

### Items / Listing fees

| # | Method | Endpoint | Contract/Auth | FE Service | UI/Page | Status | Vấn đề |
|---:|---|---|---|---|---|---|---|
| 14 | GET | `/api/v1/items` | `categoryId,areaId,q,page,size → PageResponseItemResponse`, Public | `itemApi.ts:133` | `Browse.tsx:79`; `Home.tsx:290` | `INTEGRATED` | Response được map thành card; có loading/error/empty/paging. |
| 15 | POST | `/api/v1/items` | `CreateItemRequest → ItemResponse`, JWT | `itemApi.ts:137` | `Post.tsx:290` | `INTEGRATED` | Ảnh upload trước, body tạo bài gửi sau. |
| 16 | GET | `/api/v1/items/{id}` | `id → ItemResponse`, Public | `itemApi.ts:145` | `ProductDetail.tsx:56` | `INTEGRATED` | Chi tiết hiển thị từ response thật. |
| 17 | PUT | `/api/v1/items/{id}` | `UpdateItemRequest → ItemResponse`, JWT | `itemApi.ts:141` | `Activities.tsx:163` | `INTEGRATED` | UI lấy quote lại trước update và thay state bằng response. |
| 18 | DELETE | `/api/v1/items/{id}` | `id → 204`, JWT | `itemApi.ts:149` | `Activities.tsx:194` | `INTEGRATED` | Có confirm, success/error và reload danh sách. |
| 19 | GET | `/api/v1/me/items` | `— → ItemResponse[]`, JWT | `itemApi.ts:152` | `Activities.tsx:223` | `INTEGRATED` | Dùng cho trang bài của tôi và chọn món swap. |
| 20 | GET | `/api/v1/items/{id}/edit-quote` | `id → EditQuote`, JWT | `itemApi.ts:156` | `Activities.tsx:142,161` | `INTEGRATED` | Quote được hiển thị và tải lại trước submit. |
| 21 | POST | `/api/v1/items/{id}/renew` | `id → RenewResponse`, JWT | `itemApi.ts:160` | `Activities.tsx:306` | `INTEGRATED` | Response cập nhật hạn bài; có error. |
| 22 | POST | `/api/v1/items/{id}/boost` | `id → BoostResponse`, JWT | `itemApi.ts:164` | `Activities.tsx:316` | `INTEGRATED` | Response cập nhật boost/balance. |
| 23 | GET | `/api/v1/listing-fees` | `— → ListingFeeConfigResponse`, JWT | `itemApi.ts:129` | `Activities.tsx:116` | `INTEGRATED` | UI đọc phí từ backend, không hardcode rule. |
| 24 | GET | `/api/v1/listing-fees/held` | `— → HeldFeeItem[]`, JWT | `walletApi.ts:51` | `Credit.tsx:215` | `INTEGRATED` | Danh sách giữ phí có error/empty state. |

### Categories / Areas

| # | Method | Endpoint | Contract/Auth | FE Service | UI/Page | Status | Vấn đề |
|---:|---|---|---|---|---|---|---|
| 25 | GET | `/api/v1/categories` | `— → CategoryResponse[]`, Public | `catalogApi.ts:45` | `Browse.tsx:48`; `Post.tsx:97` | `INTEGRATED` | Dùng cho bộ lọc và form. |
| 26 | GET | `/api/v1/categories/{id}` | `id → CategoryResponse`, Public | `catalogApi.ts:49` | `ProductDetail.tsx:58` | `INTEGRATED` | Tên/metadata danh mục được hiển thị. |
| 27 | GET | `/api/v1/categories/{categoryId}/attributes` | `categoryId → CategoryAttributeResponse[]`, Public | `catalogApi.ts:53` | `Post.tsx:150`; `Activities.tsx:886` | `INTEGRATED` | Form tạo/sửa đọc thuộc tính động. |
| 28 | GET | `/api/v1/areas` | `— → AreaResponse[]`, Public | `catalogApi.ts:41` | `Browse.tsx:48`; `Profile.tsx:192` | `INTEGRATED` | Cây khu vực được map sang district options. |

### AI Search

| # | Method | Endpoint | Contract/Auth | FE Service | UI/Page | Status | Vấn đề |
|---:|---|---|---|---|---|---|---|
| 29 | GET | `/api/v1/ai/quota` | `— → AiQuotaResponse`, JWT | `aiApi.ts:33` | `AI.tsx:52,106,178` | `INTEGRATED` | Tải, retry và refresh quota sau search. |
| 30 | POST | `/api/v1/ai/search` | `AiSearchRequest + page,size → AiSearchResponse`, JWT | `aiApi.ts:37` | `AI.tsx:78` | `INTEGRATED` | Kết quả/quota/fallback đều được dùng. |

### Requests / Chat

| # | Method | Endpoint | Contract/Auth | FE Service | UI/Page | Status | Vấn đề |
|---:|---|---|---|---|---|---|---|
| 31 | POST | `/api/v1/items/{itemId}/requests` | `CreateRequest → RequestResponse`, JWT | `requestApi.ts:115` | `ProductDetail.tsx:217` | `INTEGRATED` | UI gửi yêu cầu GIVE/SWAP và dùng response. |
| 32 | GET | `/api/v1/items/{itemId}/requests` | `itemId → RequestResponse[]`, JWT | `requestApi.ts:121` | `Activities.tsx:234` | `INTEGRATED` | Chủ bài xem danh sách người yêu cầu. |
| 33 | GET | `/api/v1/me/requests` | `— → RequestResponse[]`, JWT | `requestApi.ts:127` | `Messages.tsx:164`; `Activities.tsx:223` | `INTEGRATED` | Dùng tạo danh sách activity/conversation. |
| 34 | GET | `/api/v1/requests/{id}` | `id → RequestResponse`, JWT | `requestApi.ts:133` | `Messages.tsx:175,259` | `INTEGRATED` | Poll kèm detail để cập nhật state nghiệp vụ. |
| 35 | POST | `/api/v1/requests/{id}/cancel` | `id → RequestResponse`, JWT | `requestApi.ts:139` | `ProductDetail.tsx:242`; `Activities.tsx:282` | `INTEGRATED` | Response cập nhật UI. |
| 36 | POST | `/api/v1/requests/{id}/select` | `id → RequestResponse`, JWT | `requestApi.ts:145` | `Activities.tsx:352` | `INTEGRATED` | Có confirm, error và reload. |
| 37 | POST | `/api/v1/requests/{id}/schedule` | `ScheduleRequest → RequestResponse`, JWT | `requestApi.ts:151` | `Messages.tsx:383` | `INTEGRATED` | UI ràng buộc state trước submit. |
| 38 | POST | `/api/v1/requests/{id}/confirm-logistics` | `ConfirmLogisticsRequest → RequestResponse`, JWT | `requestApi.ts:157` | `Messages.tsx:404` | `INTEGRATED` | Response thay request hiện hành. |
| 39 | GET | `/api/v1/requests/{id}/contact` | `id → RequestContactResponse`, JWT | `requestApi.ts:163` | `Messages.tsx:420` | `INTEGRATED` | Chỉ gọi từ action xem liên hệ. |
| 40 | POST | `/api/v1/requests/{id}/confirm-handover` | `HandoverConfirmRequest → RequestResponse`, JWT | `requestApi.ts:169` | `Messages.tsx:439` | `INTEGRATED` | Truyền `asDescribed`/lý do và cập nhật state. |
| 41 | GET | `/api/v1/requests/{id}/media` | `id → RequestMediaResponse[]`, JWT | `requestApi.ts:106` | `Messages.tsx:221` | `INTEGRATED` | Có loading/error/empty. |
| 42 | POST | `/api/v1/requests/{id}/media` | `AttachMediaRequest → RequestMediaResponse[]`, JWT | `requestApi.ts:110` | `Messages.tsx:580` | `INTEGRATED` | Upload media trước, attach sau, response thay gallery. |
| 43 | GET | `/api/v1/requests/{requestId}/messages` | `afterId → MessageResponse[]`, JWT | `chatApi.ts:35` | `Messages.tsx:258` | `INTEGRATED` | Polling dùng `afterId`; xem lưu ý test tự động ở mục D. |
| 44 | POST | `/api/v1/requests/{requestId}/messages` | `SendMessageRequest → MessageResponse`, JWT | `chatApi.ts:42` | `Messages.tsx:502` | `INTEGRATED` | UI dùng response để append tin nhắn. |
| 45 | GET | `/api/v1/requests/{requestId}/pinned-items` | `requestId → ItemCardDto[]`, JWT | `chatApi.ts:50` | `Messages.tsx:282-310,889-929` | `INTEGRATED` | Path canonical được gọi khi chọn chat; có loading, retry, empty, image/title/type/status và không gửi `currentUserId`. |
| 46 | GET | `/api/v1/requests/{requestId}/messages/pinned-items` | `requestId → ItemCardDto[]`, JWT | `NOT APPLICABLE` | `NOT APPLICABLE` | `SERVER_ONLY / ALIAS` | Cùng method backend với path canonical; FE cố ý không gọi trùng lặp. |

### Notifications

| # | Method | Endpoint | Contract/Auth | FE Service | UI/Page | Status | Vấn đề |
|---:|---|---|---|---|---|---|---|
| 47 | GET | `/api/v1/notifications` | `page,size → NotificationResponse[]`, JWT | `notificationApi.ts:24` | `NotificationMenu.tsx:74` | `INTEGRATED` | Menu có load-more, loading/error/empty. |
| 48 | GET | `/api/v1/notifications/unread-count` | `— → UnreadCountResponse`, JWT | `notificationApi.ts:28` | `NotificationMenu.tsx:108` | `INTEGRATED` | Badge được refresh sau mark-read. |
| 49 | POST | `/api/v1/notifications/{id}/read` | `id → NotificationResponse`, JWT | `notificationApi.ts:32` | `NotificationMenu.tsx:102` | `INTEGRATED` | Response thay item và cập nhật unread count. |

### Reports / Disputes / Media

| # | Method | Endpoint | Contract/Auth | FE Service | UI/Page | Status | Vấn đề |
|---:|---|---|---|---|---|---|---|
| 50 | POST | `/api/v1/reports` | `CreateReportRequest → ReportResponse`, JWT | `reportApi.ts:147` | `ReportDialog.tsx:52` | `INTEGRATED` | Upload evidence trước; có success/error. |
| 51 | POST | `/api/v1/reports/{reportId}/response` | `RespondReportRequest → 204`, JWT | `reportApi.ts:115` | `ReportResponse.tsx:48` | `INTEGRATED` | Trang route riêng có form/evidence/error. |
| 52 | POST | `/api/v1/requests/{requestId}/disputes` | `CreateDisputeRequest → DisputeResponse`, JWT | `reportApi.ts:106` | `Messages.tsx:537` | `INTEGRATED` | UI chỉ mở theo state hợp lệ và dùng deadline response. |
| 53 | POST | `/api/v1/disputes/{disputeId}/response` | `DisputeResponseRequest → 204`, JWT | `reportApi.ts:110` | `Messages.tsx:563` | `INTEGRATED` | Có evidence, submit state và error. |
| 54 | POST | `/api/v1/media` | `multipart/form-data(file) → MediaResponse`, JWT | `mediaApi.ts:18` | `Post.tsx:261`; `ReportDialog.tsx:50` | `INTEGRATED` | Dùng `FormData`, không tự ép sai content type. |
| 55 | GET | `/api/v1/media/{id}` | `id → binary`, Public | `mediaUrl.ts:7-8` | `Messages.tsx:942,948` | `INTEGRATED` | Browser `<img>`/link gọi URL trực tiếp; không cần Axios service. |

### Wallet / Top-up / VNPAY

| # | Method | Endpoint | Contract/Auth | FE Service | UI/Page | Status | Vấn đề |
|---:|---|---|---|---|---|---|---|
| 56 | GET | `/api/v1/wallet` | `— → WalletResponse`, JWT | `walletApi.ts:55` | `WalletContext.tsx:47` | `INTEGRATED` | Context chia sẻ wallet thật cho các trang. |
| 57 | GET | `/api/v1/wallet/transactions` | `from,to,page,size → PageResponseWalletTransactionResponse`, JWT | `walletApi.ts:59` | `Credit.tsx:242` | `INTEGRATED` | Có validate khoảng ngày, paging/error/empty. |
| 58 | POST | `/api/v1/wallet/topup-orders` | `CreateTopUpOrderRequest → TopUpOrderResponse`, JWT | `paymentApi.ts:59` | `Credit.tsx:363` | `INTEGRATED` | UI đi theo `redirectUrl/provider` từ response. |
| 59 | GET | `/api/v1/wallet/topup-orders/{orderCode}` | `orderCode → TopUpOrderDetailResponse`, JWT | `paymentApi.ts:47` | `ManualPaymentCheckout.tsx:58,70`; `Credit.tsx:287` | `INTEGRATED` | Poll/kiểm tra kết quả bằng dữ liệu server. |
| 60 | POST | `/api/v1/wallet/topup-orders/{orderCode}/mark-paid` | `orderCode → TopUpOrderDetailResponse`, JWT | `paymentApi.ts:53` | `ManualPaymentCheckout.tsx:58` | `INTEGRATED` | Submit thủ công và poll trạng thái. |
| 61 | GET | `/api/v1/payments/vnpay/ipn` | `VNPAY query → VnPayIpnResponse`, Public callback | `NOT APPLICABLE` | `VNPAY server` | `SERVER_ONLY / CALLBACK` | VNPAY gọi backend; FE không được gọi trực tiếp. |
| 62 | GET | `/api/v1/payments/vnpay/return` | `VNPAY query → redirect`, Public redirect | `NOT APPLICABLE` | `Browser redirect` | `SERVER_ONLY / CALLBACK` | Gateway/browser đi qua backend rồi trả về FE; không phải API client. |

### Admin — Areas / Categories / Attributes

| # | Method | Endpoint | Contract/Auth | FE Service | UI/Page | Status | Vấn đề |
|---:|---|---|---|---|---|---|---|
| 63 | GET | `/api/v1/admin/areas` | `— → AdminAreaResponse[]`, ADMIN | `adminAreaApi.ts:18` | `AdminPages.tsx:2286` | `INTEGRATED` | Có loading/error và table. |
| 64 | POST | `/api/v1/admin/areas` | `AdminAreaRequest → AdminAreaResponse`, ADMIN | `adminAreaApi.ts:22` | `AdminPages.tsx:2299` | `INTEGRATED` | Response append vào state. |
| 65 | PUT | `/api/v1/admin/areas/{id}` | `AdminAreaRequest → AdminAreaResponse`, ADMIN | `adminAreaApi.ts:26` | `AdminPages.tsx:2300` | `INTEGRATED` | Response thay dòng đã sửa. |
| 66 | PATCH | `/api/v1/admin/areas/{id}/active` | `AdminAreaActiveRequest → AdminAreaResponse`, ADMIN | `adminAreaApi.ts:30` | `AdminPages.tsx:2319` | `INTEGRATED` | Toggle dùng response server. |
| 67 | GET | `/api/v1/admin/categories` | `— → AdminCategoryResponse[]`, ADMIN | `adminCategoryApi.ts:43` | `AdminPages.tsx:2872` | `INTEGRATED` | Có loading/error/empty. |
| 68 | POST | `/api/v1/admin/categories` | `AdminCategoryRequest → AdminCategoryResponse`, ADMIN | `adminCategoryApi.ts:47` | `AdminPages.tsx:2884` | `INTEGRATED` | Response append vào state. |
| 69 | PUT | `/api/v1/admin/categories/{id}` | `AdminCategoryRequest → AdminCategoryResponse`, ADMIN | `adminCategoryApi.ts:51` | `AdminPages.tsx:2885` | `INTEGRATED` | Response thay dòng. |
| 70 | PATCH | `/api/v1/admin/categories/{id}/active` | `AdminCategoryActiveRequest → AdminCategoryResponse`, ADMIN | `adminCategoryApi.ts:55` | `AdminPages.tsx:2903` | `INTEGRATED` | Toggle dùng response. |
| 71 | GET | `/api/v1/admin/categories/{categoryId}/attributes` | `categoryId → AdminAttributeResponse[]`, ADMIN | `adminCategoryApi.ts:59` | `AdminPages.tsx:2960` | `INTEGRATED` | Reload theo category đang chọn. |
| 72 | POST | `/api/v1/admin/categories/{categoryId}/attributes` | `AdminAttributeRequest → AdminAttributeResponse`, ADMIN | `adminCategoryApi.ts:63` | `AdminPages.tsx:2973` | `INTEGRATED` | Response append vào state. |
| 73 | PUT | `/api/v1/admin/categories/{categoryId}/attributes/{attributeId}` | `AdminAttributeRequest → AdminAttributeResponse`, ADMIN | `adminCategoryApi.ts:67` | `AdminPages.tsx:2974` | `INTEGRATED` | Response thay dòng. |
| 74 | PATCH | `/api/v1/admin/categories/{categoryId}/attributes/{attributeId}/active` | `AdminAttributeActiveRequest → AdminAttributeResponse`, ADMIN | `adminCategoryApi.ts:71` | `AdminPages.tsx:2993` | `INTEGRATED` | Toggle dùng response. |

### Admin — Review / Authenticity

| # | Method | Endpoint | Contract/Auth | FE Service | UI/Page | Status | Vấn đề |
|---:|---|---|---|---|---|---|---|
| 75 | GET | `/api/v1/admin/reviews/pending` | `page,size → ReviewQueueResponse`, ADMIN | `adminModerationApi.ts:48` | `AdminPages.tsx:272` | `INTEGRATED` | Hàng chờ + checklist được dùng trong UI. |
| 76 | GET | `/api/v1/admin/reviews/reject-reasons` | `— → string[]`, ADMIN | `adminModerationApi.ts:52` | `AdminPages.tsx:273` | `INTEGRATED` | Dùng làm lý do mẫu. |
| 77 | POST | `/api/v1/admin/reviews/{itemId}/approve` | `ApproveReviewRequest → 204`, ADMIN | `adminModerationApi.ts:56` | `AdminPages.tsx:315` | `INTEGRATED` | Gửi checklist ID + answer; reload queue. |
| 78 | POST | `/api/v1/admin/reviews/{itemId}/reject` | `RejectReviewRequest → 204`, ADMIN | `adminModerationApi.ts:59` | `AdminPages.tsx:338` | `INTEGRATED` | Gửi reason, reload queue. |
| 79 | GET | `/api/v1/admin/items/pending` | `page,size → ReviewQueueItem[]`, ADMIN | `adminModerationApi.ts:44` | `AdminPages.tsx:286` | `INTEGRATED` | Chỉ dùng làm compatibility fallback khi endpoint review mới lỗi. |
| 80 | POST | `/api/v1/admin/authenticity/{id}/grant` | `id → 204`, ADMIN | `adminAuthenticityApi.ts:44` | `AdminAuthenticity.tsx:47-258` | `INTEGRATED_WITH_LIMITATIONS` | Nhập ID + modal xác nhận; không body; Backend kiểm tra PENDING. |
| 81 | POST | `/api/v1/admin/authenticity/{id}/reject` | `AuthenticityDecisionRequest → 204`, ADMIN | `adminAuthenticityApi.ts:48` | `AdminAuthenticity.tsx:47-258` | `INTEGRATED_WITH_LIMITATIONS` | Nhập ID + reason bắt buộc + modal; Backend kiểm tra PENDING. |
| 82 | POST | `/api/v1/admin/authenticity/{id}/revoke` | `AuthenticityDecisionRequest → 204`, ADMIN | `adminAuthenticityApi.ts:51` | `AdminAuthenticity.tsx:47-258` | `INTEGRATED_WITH_LIMITATIONS` | Nhập ID + reason bắt buộc + modal; Backend kiểm tra VERIFIED. |

### Admin — Config / Checklist / Lexicon

| # | Method | Endpoint | Contract/Auth | FE Service | UI/Page | Status | Vấn đề |
|---:|---|---|---|---|---|---|---|
| 83 | GET | `/api/v1/admin/configs` | `— → ConfigResponse[]`, ADMIN | `adminConfigApi.ts:20` | `AdminPages.tsx:1514` | `INTEGRATED` | Trang settings tải config thật. |
| 84 | PUT | `/api/v1/admin/configs` | `AdminUpdateConfigRequest → ConfigResponse`, ADMIN | `adminConfigApi.ts:31` | `AdminPages.tsx:1540` | `INTEGRATED` | Nhánh update theo body đã nối UI. |
| 85 | PUT | `/api/v1/admin/configs/{key}` | `UpdateConfigRequest → ConfigResponse`, ADMIN | `adminConfigApi.ts:24` | `AdminPages.tsx:1548` | `INTEGRATED` | Nhánh update theo key đã nối UI. |
| 86 | GET | `/api/v1/admin/checklist` | `— → AdminChecklistEntry[]`, ADMIN | `adminChecklistApi.ts:19` | `AdminPages.tsx:2753` | `INTEGRATED` | Có loading/error/table. |
| 87 | POST | `/api/v1/admin/checklist` | `AdminChecklistRequest → AdminChecklistEntry`, ADMIN | `adminChecklistApi.ts:23` | `AdminPages.tsx:2771` | `INTEGRATED` | Response append state. |
| 88 | PUT | `/api/v1/admin/checklist/{id}` | `AdminChecklistRequest → AdminChecklistEntry`, ADMIN | `adminChecklistApi.ts:27` | `AdminPages.tsx:2772` | `INTEGRATED` | Response thay dòng. |
| 89 | PATCH | `/api/v1/admin/checklist/{id}/active` | `AdminChecklistActiveRequest → AdminChecklistEntry`, ADMIN | `adminChecklistApi.ts:35` | `AdminPages.tsx:2790` | `INTEGRATED` | Toggle dùng response. |
| 90 | PUT | `/api/v1/admin/checklist/reorder` | `AdminChecklistReorderRequest → AdminChecklistEntry[]`, ADMIN | `adminChecklistApi.ts:31` | `AdminPages.tsx:2808` | `INTEGRATED` | Response thay toàn bộ thứ tự. |
| 91 | GET | `/api/v1/admin/lexicon` | `— → AdminLexiconEntry[]`, ADMIN | `adminLexiconApi.ts:33` | `AdminPages.tsx:2071` | `INTEGRATED` | Có loading/error/table. |
| 92 | POST | `/api/v1/admin/lexicon` | `AdminLexiconRequest → AdminLexiconEntry`, ADMIN | `adminLexiconApi.ts:37` | `AdminPages.tsx:2089` | `INTEGRATED` | Response append state. |
| 93 | PUT | `/api/v1/admin/lexicon/{id}` | `AdminLexiconRequest → AdminLexiconEntry`, ADMIN | `adminLexiconApi.ts:41` | `AdminPages.tsx:2090` | `INTEGRATED` | Response thay dòng. |
| 94 | PATCH | `/api/v1/admin/lexicon/{id}/active` | `AdminLexiconActiveRequest → AdminLexiconEntry`, ADMIN | `adminLexiconApi.ts:45` | `AdminPages.tsx:2108` | `INTEGRATED` | Toggle dùng response. |
| 95 | POST | `/api/v1/admin/lexicon/test` | `LexiconTestRequest → LexiconMatch[]`, ADMIN | `adminLexiconApi.ts:49` | `AdminPages.tsx:2123` | `INTEGRATED` | Kết quả test được render. |

### Admin — Reports / Finance / Users / Audit

| # | Method | Endpoint | Contract/Auth | FE Service | UI/Page | Status | Vấn đề |
|---:|---|---|---|---|---|---|---|
| 96 | GET | `/api/v1/admin/reports` | `status,targetType,page,size → AdminReportPage`, ADMIN | `reportApi.ts:133` | `AdminComplaints.tsx:139` | `INTEGRATED` | Filter/paging/loading/error đều có. |
| 97 | GET | `/api/v1/admin/reports/{reportId}` | `reportId → AdminReportDetail`, ADMIN | `reportApi.ts:137` | `AdminComplaints.tsx:170` | `INTEGRATED` | Detail/evidence/response/resolution được render. |
| 98 | GET | `/api/v1/admin/reports/{reportId}/evidence/{mediaId}` | `ids → binary`, ADMIN | `reportApi.ts:118` | `AdminComplaints.tsx:51` | `INTEGRATED` | Axios blob → object URL, có unavailable/error state. |
| 99 | POST | `/api/v1/admin/reports/{reportId}/conclude` | `ConcludeReportRequest → ReportConclusionResponse`, ADMIN | `reportApi.ts:143` | `AdminComplaints.tsx:197` | `INTEGRATED` | Có confirm, submit guard, 409/error và reload. |
| 100 | GET | `/api/v1/admin/finance/topup-orders` | `status,submittedOnly,page,size → PageResponseTopUpOrderDetailResponse`, ADMIN | `adminFinanceApi.ts:40` | `AdminTopUpOrders.tsx:53` | `INTEGRATED` | Có filter/paging/loading/error/empty. |
| 101 | POST | `/api/v1/admin/finance/topup-orders/{orderCode}/approve` | `ReviewTopUpRequest → TopUpOrderDetailResponse`, ADMIN | `adminFinanceApi.ts:47` | `AdminTopUpOrders.tsx:98` | `INTEGRATED` | Response được reload/hiển thị. |
| 102 | POST | `/api/v1/admin/finance/topup-orders/{orderCode}/reject` | `RejectTopUpRequest → TopUpOrderDetailResponse`, ADMIN | `adminFinanceApi.ts:54` | `AdminTopUpOrders.tsx:100` | `INTEGRATED` | Gửi `reason`, có submit/error. |
| 103 | GET | `/api/v1/admin/finance/report` | `from,to → FinanceSummaryResponse`, ADMIN | `adminFinanceApi.ts:61` | `AdminFinancePages.tsx:31,71` | `INTEGRATED` | Dashboard và bộ lọc ngày dùng response. |
| 104 | GET | `/api/v1/admin/finance/users/{userId}/history` | `from,to,page,size → PageResponseWalletTransactionResponse`, ADMIN | `adminFinanceApi.ts:65` | `AdminFinancePages.tsx:176` | `INTEGRATED` | Có paging/filter/error. |
| 105 | POST | `/api/v1/admin/users/{userId}/lock` | `LockUserRequest → 204`, ADMIN | `adminFinanceApi.ts:72` | `AdminFinancePages.tsx:131` | `INTEGRATED` | Gửi lý do và reload account. |
| 106 | POST | `/api/v1/admin/users/{userId}/unlock` | `LockUserRequest → 204`, ADMIN | `adminFinanceApi.ts:72` | `AdminFinancePages.tsx:131` | `INTEGRATED` | Dùng cùng UI action, URL đổi theo `locked`. |
| 107 | GET | `/api/v1/admin/activity-logs` | `filters,page,size → PageActivityLogDto`, ADMIN | `adminAuditApi.ts:47` | `AdminPages.tsx:1727`; `AdminFinancePages.tsx:31` | `INTEGRATED` | Mapper hỗ trợ cả page metadata mới/cũ. |
| 108 | GET | `/api/v1/admin/activity-logs/types` | `— → type/label[]`, ADMIN | `adminAuditApi.ts:61` | `AdminPages.tsx:1718` | `INTEGRATED` | Có fallback nhãn cục bộ nếu backend cũ lỗi. |

## C. API chưa tích hợp

Không còn endpoint mang trạng thái `NOT_INTEGRATED`. Alias `GET /api/v1/requests/{requestId}/messages/pinned-items` không cần FE gọi vì backend ánh xạ cùng method với path canonical `/pinned-items`.

## D. API tích hợp chưa hoàn chỉnh và sai lệch phụ trợ

### 1. Pinned items trong Chat đã hoàn thiện

- `GET /api/v1/requests/{requestId}/pinned-items` được gọi tại `frontend/src/pages/user/Messages.tsx:282-310`.
- UI render image, title, offer type, status; có loading, retry và empty state tại `Messages.tsx:889-929`.
- Alias `/messages/pinned-items` không được gọi, tránh request trùng.

### 2. Admin Authenticity đã tích hợp với giới hạn dữ liệu đọc

- Ba client `grant/reject/revoke` được tái sử dụng tại `frontend/src/services/adminAuthenticityApi.ts:44-53`.
- Route Admin có thật tại `frontend/src/app/router.tsx:105`; UI nhập ID, modal xác nhận, reason bắt buộc, loading/success/error và submit lock nằm tại `frontend/src/pages/admin/AdminAuthenticity.tsx:47-258`.
- Không có endpoint GET danh sách/chi tiết Authenticity, `authStatus` hoặc chứng từ. FE vì vậy không tạo dữ liệu giả, không suy diễn trạng thái và để Backend xác thực PENDING/VERIFIED.

### 3. OpenAPI làm lộ `currentUserId` như query parameter — đã sửa cho Chat trong source

- Ví dụ Swagger ghi `currentUserId` required cho Chat, nhưng controller dùng `@CurrentUserId` và contract module ghi JWT là nguồn danh tính.
- FE Request vẫn gửi `currentUserId` query (`frontend/src/services/requestApi.ts:113-171`), trong khi Chat đã bỏ query này (`frontend/src/services/chatApi.ts:34-52`). Hai module không nhất quán dù JWT interceptor đã có.
- Các tham số `@CurrentUserId` trong `ChatController` đã được đánh dấu `@Parameter(hidden = true)`; service Chat không gửi ID danh tính từ client. Swagger production chỉ thay đổi sau khi deploy backend mới.

### 4. Test API frontend đã khớp signature Chat

- `frontend/scripts/verify-api.mjs` đã gọi `chatApi.list(101, 25)`, kiểm tra chỉ có `afterId`, kiểm tra path pinned canonical và body `refItemId`.
- `npm run test:api`: **18/18 pass**.

### 5. Client dev-only không có trong Swagger production — MEDIUM

- FE có `POST /api/v1/wallet/topup-orders/{orderCode}/mock-confirm` tại `frontend/src/services/paymentApi.ts:65-70`, được `DemoPaymentCheckout.tsx:51` gọi qua `paymentFlow.ts:22-29`.
- Endpoint không xuất hiện trong 108 operation production do controller có `@ConditionalOnProperty(app.payment.mock.enabled=true)` (`backend/src/main/java/com/shareloop/wallet/controller/MockTopUpController.java:20-35`).
- Không tính endpoint này vào thống kê Swagger; route demo phải tiếp tục bị khoá khi production tắt property.

### 6. Dữ liệu mock còn tồn tại nhưng không thay thế 108 Swagger API đã đánh dấu `INTEGRATED`

- Store vẫn có nhánh demo cho tài khoản không phải API, ví dụ `Credit.tsx:200,329`, `Messages.tsx:1161-1233`, nhiều trang Admin cũ trong `AdminPages.tsx`.
- Các route/API-account đã được truy vết trong bảng không fallback sang mock khi API lỗi; chúng hiển thị error/empty. Vì vậy không gán `MOCK` cho endpoint Swagger nào.
- Tuy nhiên dashboard và một số màn Admin không có endpoint tương ứng trong Swagger vẫn hiển thị dữ liệu demo; đó là nợ UI ngoài inventory 108 API, không nên nhầm là dữ liệu production.

## E. Thứ tự hoàn thiện đề xuất

1. **Bổ sung read contract cho Admin Authenticity**: danh sách/chi tiết phải trả item ID, chứng từ và `authStatus`; sau đó mới nối grant/reject/revoke và bộ trạng thái UI mà không suy đoán.
2. **Deploy sửa OpenAPI Chat** và xác nhận `currentUserId` không còn xuất hiện trong Swagger production.
3. **Giữ test Chat/Category/Authenticity** trong `npm run test:api` để chặn regression contract.
5. **Giữ dev-only payment tách khỏi production** và ghi rõ route demo; không coi `mock-confirm` là contract production.
6. **Tách/ghi nhãn các màn Admin demo cũ** để tránh người dùng hiểu nhầm chúng đang hiển thị dữ liệu backend.

## Checklist bàn giao

- [x] Nối `chatApi.pinnedItems()` vào `Messages.tsx` bằng path canonical.
- [x] Không gọi alias `/messages/pinned-items` trùng lặp.
- [ ] Xây UI Admin Authenticity cho grant/reject/revoke.
- [ ] Chốt endpoint/nguồn danh sách item cho Admin Authenticity.
- [x] Ẩn `@CurrentUserId` khỏi các operation Chat trong source OpenAPI.
- [ ] Bỏ `currentUserId` query khỏi các Request service sau khi contract JWT được chốt và test.
- [x] Cập nhật `frontend/scripts/verify-api.mjs` theo signature Chat hiện tại.
- [ ] Xác minh production không expose `mock-confirm` và route demo bị disable.
- [ ] Smoke-test read-only trên môi trường tích hợp, sau đó test mutation trên staging với dữ liệu chuyên dụng.
- [ ] Chạy lại `npm run test:api`, `npm run lint`, `npm run build` sau khi sửa; mục tiêu 100% pass.

## Bằng chứng kiểm tra

- OpenAPI đã tải được ngày 2026-10-22: 95 path, 108 operation.
- Đã quét `frontend/src/services`, `pages`, `components`, `app`, `layouts`, `routes`, các contract `docs/api/*.md` và controller backend liên quan.
- `npm run test:api`: 18/18 pass; `npm run lint`: pass; `npm run build` (gồm TypeScript): pass.
- Backend test chưa chạy được trên máy audit do Java hiện tại là 19 trong khi Maven compile với release 21.
- Không gọi mutation thật.
