SHARELOOP · SWP391 · NHÓM 1
LUỒNG HOẠT ĐỘNG & ERD
Hệ thống ShareLoop v10 — đi kèm SRS v10
17 luồng · 2 sơ đồ trạng thái · 17 bảng dữ liệu
PostgreSQL 16 · cấu trúc mapping theo hình minh hoạ của giảng viên
Ngày: 29/09/2026 — ERD 17 bảng đã chốt (C33), bổ sung cột chuẩn cho bảng chính (C34)

ShareLoop — Luồng hoạt động & ERD v10
MỤC LỤC
1. Giới thiệu................................................................................................................................................3
1.1. Mục tiêu thiết kế.............................................................................................................................3
1.2. Chú thích sơ đồ..............................................................................................................................3
2. Kiến trúc và phân chia module...............................................................................................................4
3. Luồng hoạt động tổng thể......................................................................................................................6
3.1. Một vòng đời điển hình...................................................................................................................6
4. Luồng chi tiết..........................................................................................................................................8
4.1. F01 — Đăng ký và xác thực email.................................................................................................8
4.2. F02 — Đăng nhập và quên mật khẩu............................................................................................9
4.3. F03 — Đổi số điện thoại...............................................................................................................10
4.4. F04 — Nạp Credit.........................................................................................................................11
4.5. F05 — Đăng bài, giữ phí và kiểm duyệt ba tầng..........................................................................12
4.6. F06 — Admin duyệt bài................................................................................................................13
4.7. F07 — Sửa bài.............................................................................................................................15
4.8. F08 — Gia hạn và đẩy bài............................................................................................................16
4.9. F09 — Tìm kiếm và AI trợ lý........................................................................................................17
4.10. F10 — Gửi yêu cầu và chọn người............................................................................................18
4.11. F11 — Chat có kiểm duyệt.........................................................................................................19
4.12. F12 — Thẻ bài đăng và đổi món đề nghị...................................................................................20
4.13. F13 — Chuyển bài Cho thành Trao đổi.....................................................................................21
4.14. F14 — Chốt lịch và trao thông tin liên lạc..................................................................................22
4.15. F15 — Giao nhận và hoàn tất....................................................................................................23
4.16. F16 — Khiếu nại và tranh chấp..................................................................................................24
4.17. F17 — Uy tín: trừ sao, hồi sao, ân hạn......................................................................................25
5. Sơ đồ trạng thái....................................................................................................................................26
5.1. Request........................................................................................................................................26
5.2. Bài đăng.......................................................................................................................................27
5.3. Các trạng thái khác.......................................................................................................................27
6. Tác vụ nền............................................................................................................................................29
7. Thiết kế dữ liệu (ERD)..........................................................................................................................30
7.1. Tổng quan 17 bảng......................................................................................................................30
7.2. Cấu trúc mapping theo giảng viên................................................................................................32
7.3. Module Người dùng & hệ thống...................................................................................................33
7.4. Module Bài đăng...........................................................................................................................38
7.5. Module Giao dịch..........................................................................................................................43
7.6. Module Tài chính..........................................................................................................................47
7.7. Ràng buộc và index quan trọng...................................................................................................48
7.8. So với ERD v9..............................................................................................................................49
7.9. Ánh xạ luồng ↔ bảng...................................................................................................................50
7.10. Giá trị cấu hình khởi tạo (website_attributes, nhóm CONFIG)..................................................50
7.11. Cột chuẩn của bảng chính (C34)...............................................................................................52
8. Gợi ý để code đơn giản........................................................................................................................55
8.1. Ranh giới transaction...................................................................................................................55
8.2. Những chỗ nên làm đơn giản.......................................................................................................55
Nếu mở bằng Microsoft Word mà mục lục chưa hiện số trang: bấm Ctrl+A rồi F9, chọn "Update entire table".
Trang 2 / 55

ShareLoop — Luồng hoạt động & ERD v10
1. Giới thiệu
Tài liệu này trình bày luồng hoạt động của toàn bộ hệ thống ShareLoop v10 theo góc nhìn kỹ thuật
và thiết kế dữ liệu (ERD) đi kèm. Mỗi luồng có sơ đồ, các bước xử lý ở backend, các bảng được đọc
hoặc ghi, và danh sách API gợi ý. Tài liệu đi cùng SRS v10: SRS trả lời "hệ thống làm gì", tài liệu này
trả lời "hệ thống chạy như thế nào và dữ liệu nằm ở đâu".
1.1. Mục tiêu thiết kế
• Tối đa 20 bảng — thiết kế thực tế 17 bảng.
• Giữ cấu trúc mapping của giảng viên: Pictures + ItemPictureMappings, ItemAttributes +
ItemAttributeMappings, WebsiteAttributes.
• Tối ưu code, giảm độ phức tạp: ít entity và repository; mỗi nhóm cột có đúng một service được
ghi; không có bảng chỉ phục vụ một tính năng nhỏ; dùng ràng buộc của cơ sở dữ liệu thay cho
code kiểm tra ở nhiều nơi.
ERD đã chốt (C33) — 28/09/2026
Nhóm đã review và chốt 17 bảng đúng như tài liệu này. Từ mốc này:
• V1__init.sql tạo đúng 17 bảng theo từ điển dữ liệu ở chương 7, kèm ràng buộc và index ở mục 7.7.
• Mọi thay đổi schema sau đó đi bằng một migration Flyway mới (quy tắc đặt tên trong tài liệu Lộ trình),
PR phải được người sở hữu bảng (mục 2) duyệt.
• Thay đổi nào làm lệch tài liệu thì ghi thêm một dòng vào mục 7.8 kèm lý do.
• Các quyết định ngày 28/09 không đổi cấu trúc bảng, chỉ đổi giá trị cấu hình (mục 7.10): phí sửa từ lần
thứ hai 5 Credit, AI trợ lý 2 Credit/lượt, gia hạn 5 Credit/30 ngày, đẩy bài 5 Credit/3 ngày.
• Bổ sung 29/09 (C34), theo yêu cầu giảng viên: các bảng chính có đủ 7 cột chuẩn created_at,
updated_at, created_by, updated_by, is_active, is_deleted, status. Vẫn 17 bảng, không thêm
quan hệ. Quy ước, lý do chọn bảng và cách code ở mục 7.11.
1.2. Chú thích sơ đồ
Hình 1 — Chú thích màu dùng cho mọi sơ đồ luồng
Trang 3 / 55

ShareLoop — Luồng hoạt động & ERD v10
2. Kiến trúc và phân chia module
Hình 2 — Kiến trúc tổng thể
| Module  | Service chính | Bảng sở hữu (được ghi) | Người phụ trách |
| ------- | ------------- | ---------------------- | --------------- |
(package)
auth, user AuthService, OtpService,  users — nhóm tài khoản: mật khẩu, trạng  BE1
|     | JwtService, UserService | thái, otp_*, hồ sơ, phone, pending_phone |     |
| --- | ----------------------- | ---------------------------------------- | --- |
wallet CreditService, PaymentService payment_orders, credit_ledger; users —  BE1
nhóm tiền: credit_balance, held_credit,
has_topped_up
listingfee PostFeeService, RenewService,  items — nhóm phí: post_fee_paid,  BE1
|     | BoostService | pending_fee*, fee_state, free_edit_used,  |     |
| --- | ------------ | ----------------------------------------- | --- |
approved_edit_count, expire_at,
boosted_until
catalog, media CategoryService, AreaService,  item_categories, item_attributes, areas,  BE2
|     | MediaService | media_files |     |
| --- | ------------ | ----------- | --- |
item ItemService, ItemQueryService items — nội dung và status;  BE2
item_media_mappings,
item_attribute_mappings
Trang 4 / 55

ShareLoop — Luồng hoạt động & ERD v10
| Module  | Service chính | Bảng sở hữu (được ghi) | Người phụ trách |
| ------- | ------------- | ---------------------- | --------------- |
(package)
moderation,  ContentModerationService,  items — ai_risk, ai_result; users —  BE2
| search | ItemScreeningService,  | free_ai_used, free_ai_date |     |
| ------ | ---------------------- | -------------------------- | --- |
SearchService, AiAssistantService
request, chat,  RequestService, LogisticsService,  requests, request_media_mappings,  BE3
| notification | HandoverService, ChatService,  | messages, notifications |     |
| ------------ | ------------------------------ | ----------------------- | --- |
NotificationService
setting, review ConfigService, LexiconService,  website_attributes; items — review_snapshot,  BE4
|     | ChecklistService, ReviewService | reject_reason, approved_at, auth_status |     |
| --- | ------------------------------- | --------------------------------------- | --- |
report, reputation,  ReportService, ReputationService,  reports, activity_logs; users — trust_stars,  BE4
admin, audit AdminUserService,  total_points, rank_level, points_*,
|     | FinanceReportService,  | clean_streak, grace_star_used |     |
| --- | ---------------------- | ----------------------------- | --- |
ActivityLogService
common, config,  Lớp nền, SecurityConfig,  — (không sở hữu bảng) BE1; ai, storage:
| integration | PaymentGatewayClient, AiClient,  |     | BE2 |
| ----------- | -------------------------------- | --- | --- |
EmailSender, StorageService
Quy tắc "một nhóm cột — một service ghi"
Bảng users và items gộp nhiều nhóm cột để giảm số bảng. Để không bị sửa chồng, mỗi nhóm cột chỉ một
service được ghi. Ví dụ: chỉ CreditService đổi credit_balance, held_credit; chỉ
ReputationService đổi trust_stars, total_points. Review từ chối mọi PR vi phạm quy tắc này.
Trong mã, mỗi nhóm cột được ánh xạ bằng một entity "lát cắt" riêng thuộc module sở hữu (ví dụ
WalletAccount chỉ map id và các cột tiền của users), nên không ai phải sửa chung một file entity. items.status
chỉ đổi qua ItemService; module khác gọi hàm của ItemService. Ngoại lệ duy nhất: cột chuẩn updated_at,
updated_by được mọi entity lát cắt cùng cập nhật (mục 7.11).
Trang 5 / 55

ShareLoop — Luồng hoạt động & ERD v10
3. Luồng hoạt động tổng thể
Hình 3 — Luồng hoạt động tổng thể
3.1. Một vòng đời điển hình
An và Bình đều là sinh viên. Ví dụ dưới đây đi qua gần hết hệ thống; mỗi bước ghi bảng bị tác động.
# Diễn biến Bảng bị tác động
1 An đăng ký bằng email, nhập OTP; tài khoản ACTIVE. users
2 An nạp 20.000đ qua VNPay, nhận 20 Credit. payment_orders, credit_ledger,
users
Trang 6 / 55

ShareLoop — Luồng hoạt động & ERD v10
# Diễn biến Bảng bị tác động
3 An đăng bài Cho "Nồi cơm điện"; lần đầu bị chặn vì mô tả có số điện thoại, items, media_files,
sửa ngay trên form (không mất phí); gửi lại, bị giữ 5 Credit. item_media_mappings,
item_attribute_mappings, users
4 AI gắn cờ LOW; Admin duyệt; 5 Credit bị trừ thật. items, users, credit_ledger,
activity_logs, notifications
5 Bình tìm "noi com dien" (không dấu), thấy bài, gửi yêu cầu. items (đọc), requests, notifications
6 An chọn Bình; bài RESERVED; chat mở với thẻ sản phẩm. requests, items, messages
7 Bình viết "zalo mình 09…" → bị chặn ở tầng 1, được giải thích. messages, requests
8 Hai bên chọn giao trực tiếp, xác nhận lịch; hệ thống gửi email chứa liên lạc của requests, messages, notifications
nhau.
9 Gặp nhau; An xác nhận đã giao; Bình xác nhận đã nhận và chọn "đúng mô tả". requests
10 Giao dịch COMPLETED; An +10 điểm, Bình +2 điểm; bài TRADED. requests, users, items, activity_logs
Trang 7 / 55

ShareLoop — Luồng hoạt động & ERD v10
4. Luồng chi tiết
4.1. F01 — Đăng ký và xác thực email
Hình 4 — F01 — Đăng ký và xác thực email
Xử lý ở backend
1. POST /auth/register: kiểm tra định dạng email, tên miền dùng một lần (website_attributes
nhóm DISPOSABLE_DOMAIN), định dạng số điện thoại.
Trang 8 / 55

ShareLoop — Luồng hoạt động & ERD v10
2. Kiểm tra trùng: email trong mọi tài khoản chưa xoá; số điện thoại chỉ trong tài khoản ACTIVE. Nếu
có tài khoản PENDING cùng email thì ghi đè (người dùng đăng ký lại).
3. Đếm tài khoản tạo từ cùng registration_ip trong 24 giờ; ≥ 3 thì ghi activity_logs SECURITY
để Admin xem.
4. Tạo users status PENDING_VERIFICATION, băm mật khẩu (BCrypt), sinh OTP 6 số, lưu
otp_code_hash, otp_purpose = REGISTER, otp_expires_at = now + 5 phút,
otp_failed_count = 0.
5. Gửi email qua NotificationSender (sau khi commit transaction, để email không đi khi ghi DB
lỗi).
6. POST /auth/verify-otp: so bản băm; sai thì tăng otp_failed_count, đủ 5 thì xoá mã. Đúng thì
status = ACTIVE, xoá các cột OTP, trả JWT.
Bảng liên quan: users (ghi), website_attributes (đọc), activity_logs (ghi).
API gợi ý: POST /api/v1/auth/register · POST /api/v1/auth/verify-otp · POST
/api/v1/auth/resend-otp
4.2. F02 — Đăng nhập và quên mật khẩu
Hình 5 — F02 — Đăng nhập và quên mật khẩu
Xử lý ở backend
1. POST /auth/login: tìm theo email, so BCrypt; trả cùng một thông báo lỗi cho "sai email" và "sai
mật khẩu".
2. Kiểm tra tài khoản: chỉ status = ACTIVE, is_active = true (không bị khoá) và chưa xoá mới được
cấp JWT (chứa userId, isAdmin, hạn 120 phút).
3. POST /auth/forgot-password: luôn trả 200; nếu email tồn tại thì sinh OTP RESET_PASSWORD và
gửi email.
Trang 9 / 55

ShareLoop — Luồng hoạt động & ERD v10
4. POST /auth/reset-password: kiểm OTP, lưu mật khẩu mới, xoá OTP, ghi activity_logs
SECURITY.
Bảng liên quan: users (đọc/ghi), activity_logs (ghi).
API gợi ý: POST /api/v1/auth/login · POST /api/v1/auth/forgot-password · POST
/api/v1/auth/reset-password · PUT /api/v1/me/password
4.3. F03 — Đổi số điện thoại
Hình 6 — F03 — Đổi số điện thoại
Xử lý ở backend
1. Kiểm tra phone_changed_at (≥ 30 ngày) và không có requests của người này ở trạng thái
LOGISTICS_CONFIRMED hoặc AWAITING_HANDOVER.
Trang 10 / 55

ShareLoop — Luồng hoạt động & ERD v10
2. Kiểm tra số mới chưa thuộc tài khoản ACTIVE khác; lưu pending_phone, sinh OTP
CHANGE_PHONE, gửi về email.
3. Xác nhận OTP: phone = pending_phone, phone_changed_at = now, xoá pending_phone, ghi
log.
Bảng liên quan: users, requests (đọc), activity_logs.
API gợi ý: POST /api/v1/me/phone-change · POST /api/v1/me/phone-change/confirm
4.4. F04 — Nạp Credit
Hình 7 — F04 — Nạp Credit
Xử lý ở backend
1. POST /wallet/topups: kiểm tra số tiền (≥ 10.000đ, bội số 1.000đ), tạo payment_orders
PENDING với order_code duy nhất, credits, vnd_per_credit = 1000; trả URL thanh toán
VNPay.
2. GET /payments/vnpay/ipn (VNPay gọi): kiểm chữ ký; tìm đơn theo order_code; so số tiền.
Trang 11 / 55

ShareLoop — Luồng hoạt động & ERD v10
3. Một transaction: khoá dòng users (SELECT … FOR UPDATE), đơn → SUCCESS, lưu
callback_data, cộng credit_balance, ghi credit_ledger TOP_UP với payment_order_id
(unique) và balance_after, đặt has_topped_up = true.
4. Trang trả về của trình duyệt chỉ đọc trạng thái đơn (GET /wallet/topups/{code}).
Bảng liên quan: payment_orders, users, credit_ledger, notifications.
API gợi ý: POST /api/v1/wallet/topups · GET /api/v1/payments/vnpay/ipn · GET
/api/v1/wallet/topups/{orderCode} · GET /api/v1/wallet · GET /api/v1/wallet/ledger
4.5. F05 — Đăng bài, giữ phí và kiểm duyệt ba tầng
Hình 8 — F05 — Đăng bài, giữ phí và kiểm duyệt ba tầng
Xử lý ở backend
1. Tải ảnh trước: POST /media trả mediaId (lưu media_files, file vào StorageService). Form
đăng bài gửi danh sách mediaId.
2. POST /items: ContentModerationService.checkTier1() trên tiêu đề, mô tả, món mong muốn;
kiểm danh mục, số ảnh, tình trạng, thuộc tính bắt buộc. Lỗi thì trả 422 với code và field.
Trang 12 / 55

ShareLoop — Luồng hoạt động & ERD v10
3. Một transaction: khoá dòng users; kiểm tra credit_balance − held_credit ≥ 5; held_credit
+= 5; tạo items (PENDING_REVIEW, fee_state = HELD, pending_fee = 5,
pending_fee_type = POST); tạo item_media_mappings, item_attribute_mappings; gọi
MediaService.attach(mediaIds, userId): kiểm created_by là người gọi, đổi media_files.status
UPLOADED → ATTACHED.
4. Sau commit: phát sự kiện ItemSubmitted → xử lý @Async gọi AI tầng 2, lưu ai_result, ai_risk
(lỗi: UNCHECKED).
5. Admin duyệt ở F06.
Bảng liên quan: media_files, items, item_media_mappings, item_attribute_mappings, users,
website_attributes, activity_logs.
API gợi ý: POST /api/v1/media · POST /api/v1/items · GET /api/v1/items/{id} · GET
/api/v1/me/items · DELETE /api/v1/items/{id} (rút / gỡ bài: is_deleted = true, nhaD
phí đang giữ)
4.6. F06 — Admin duyệt bài
Hình 9 — F06 — Admin duyệt bài
Trang 13 / 55

ShareLoop — Luồng hoạt động & ERD v10
Xử lý ở backend
1. GET /admin/items/pending: sắp xếp theo ai_risk (HIGH trước), rồi người từng bị trừ sao
(trust_stars < 5), người mới, rồi created_at.
2. POST /admin/items/{id}/review với câu trả lời checklist, quyết định, lý do.
3. Duyệt: một transaction khoá users của người đăng: held_credit −= pending_fee,
credit_balance −= pending_fee, ghi credit_ledger (POST_FEE hoặc EDIT_FEE, item_id),
fee_state = CHARGED; nếu là POST thì post_fee_paid = true và đặt expire_at (lần duyệt
đầu); nếu là lượt sửa miễn phí thì free_edit_used = true; status = APPROVED, approved_at
(nếu null).
4. Từ chối: held_credit −= pending_fee, fee_state = RELEASED, status = REJECTED,
reject_reason.
5. Luôn lưu review_snapshot và ghi activity_logs REVIEW; tạo notifications.
Bảng liên quan: items, users, credit_ledger, item_media_mappings, media_files, activity_logs,
notifications.
API gợi ý: GET /api/v1/admin/items/pending · GET /api/v1/admin/items/{id} · POST
/api/v1/admin/items/{id}/review · POST /api/v1/admin/items/{id}/auth-verify
Trang 14 / 55

ShareLoop — Luồng hoạt động & ERD v10
4.7. F07 — Sửa bài
Hình 10 — F07 — Sửa bài
Xử lý ở backend
1. PUT /items/{id}: chặn nếu có requests của bài ở RESERVED trở lên.
2. Xác định loại lần sửa: approved_at null → miễn phí; approved_at có và free_edit_used =
false → lượt miễn phí; còn lại → có phí (website_attributes key edit_fee = 5 Credit, C14b).
3. Tầng 1 như F05. Bị chặn thì trả 422, không đổi gì trong DB.
Trang 15 / 55

ShareLoop — Luồng hoạt động & ERD v10
4. Lượt có phí: khoá users, kiểm số dư, held_credit += edit_fee, pending_fee_type = EDIT.
Bài chưa từng trả phí đăng bài và chưa đang giữ: giữ 5 Credit loại POST.
5. Ghi đè nội dung, status = PENDING_REVIEW (ẩn tạm tới khi duyệt xong — C10 đã chốt),
ai_risk = UNCHECKED; phát ItemSubmitted để AI sàng lọc lại. Admin xử lý phí ở F06.
Bảng liên quan: items, item_media_mappings, item_attribute_mappings, users, requests (đọc).
API gợi ý: PUT /api/v1/items/{id} · GET /api/v1/items/{id}/edit-quote (traD loại lượt và
phí dự kiếnP)
4.8. F08 — Gia hạn và đẩy bài
Hình 11 — F08 — Gia hạn và đẩy bài
Xử lý ở backend
1. POST /items/{id}/renew: bài APPROVED hoặc EXPIRED ≤ 7 ngày; trừ renew_fee = 5 Credit
ngay (ghi RENEW_FEE); expire_at = max(now, expire_at) + 30 ngày; EXPIRED →
APPROVED.
2. POST /items/{id}/boost với số gói: kiểm bài APPROVED, còn hạn, danh mục is_boostable;
tính boosted_until mới ≤ min(now + 14 ngày, expire_at); trừ boost_fee = 5 Credit mỗi gói 3 ngày
(BOOST_FEE).
3. Truy vấn tìm kiếm sắp xếp boosted_until > now lên trước, giới hạn 3 bài mỗi trang.
Bảng liên quan: items, users, credit_ledger, item_categories (đọc).
API gợi ý: POST /api/v1/items/{id}/renew · POST /api/v1/items/{id}/boost
Trang 16 / 55

ShareLoop — Luồng hoạt động & ERD v10
4.9. F09 — Tìm kiếm và AI trợ lý
Hình 12 — F09 — Tìm kiếm và AI trợ lý
Xử lý ở backend
1. GET /items?q=&categoryId=&areaId=&condition=&offerType=&page=: WHERE status =
APPROVED AND is_active AND NOT is_deleted và f_unaccent(lower(title ||
description)) % f_unaccent(lower(:q)) hoặc ILIKE; index GIN trigram.
2. POST /ai/search: khoá users; kiểm hạn mức (free_ai_used, free_ai_date, has_topped_up);
dùng lượt miễn phí hoặc trừ AI_SEARCH_FEE (ai_search_fee = 2 Credit, C17).
3. Gọi LLM lần 1 (tách bộ lọc); kiểm schema; chạy truy vấn như trên; gọi LLM lần 2 (tuỳ chọn) với ≤ 5
bài; lọc itemId lạ.
4. Lỗi hoặc quá 15 giây: hoàn lượt (giảm free_ai_used) hoặc cộng lại Credit bằng dòng
ADMIN_ADJUST loại hoàn phí AI — hoặc đơn giản hơn: chỉ trừ phí sau khi LLM thành công
(khuyến nghị, xem 8.2).
5. Ghi activity_logs AI_CALL.
Bảng liên quan: items, item_media_mappings, media_files, users, credit_ledger, activity_logs.
API gợi ý: GET /api/v1/items · POST /api/v1/ai/search · GET /api/v1/ai/quota
Trang 17 / 55

ShareLoop — Luồng hoạt động & ERD v10
4.10. F10 — Gửi yêu cầu và chọn người
Hình 13 — F10 — Gửi yêu cầu và chọn người
Xử lý ở backend
1. POST /items/{id}/requests (kèm offeredItemId nếu SWAP): kiểm BR-R01, BR-R02, BR-
U02, BR-U05, BR-S01, BR-S03. Tạo requests PENDING; thông báo chủ bài.
2. GET /items/{id}/requests: danh sách kèm sao, hạng, tỉ lệ đúng mô tả (tính từ
requests.as_described của người đó).
Trang 18 / 55

ShareLoop — Luồng hoạt động & ERD v10
3. POST /requests/{id}/select: một transaction khoá dòng items (bài và món đề nghị): request
→ RESERVED; bài → RESERVED; món đề nghị → RESERVED; các request khác của bài →
REJECTED; request PENDING khác dùng cùng món đề nghị → CANCELLED; tin SYSTEM đầu
tiên trong messages.
Bảng liên quan: requests, items, users (đọc), messages, notifications.
API gợi ý: POST /api/v1/items/{id}/requests · GET /api/v1/items/{id}/requests · POST
/api/v1/requests/{id}/select · POST /api/v1/requests/{id}/cancel · GET
/api/v1/me/requests
4.11. F11 — Chat có kiểm duyệt
Hình 14 — F11 — Chat có kiểm duyệt
Xử lý ở backend
1. POST /requests/{id}/messages: chỉ hai bên của request; request ở RESERVED đến
AWAITING_HANDOVER.
2. Chưa có contact_revealed_at: chặn ảnh; chạy tầng 1; Ambiguous thì gọi LLM (timeout 5 giây).
3. Lưu tin với moderation, moderation_tier, blocked_reason, ai_confidence. Tin BLOCKED chỉ
trả về cho người gửi. Tăng requests.chat_violations; đủ 3 thì tạo cảnh báo Admin
(activity_logs + notifications cho Admin).
4. FE lấy tin mới bằng GET /requests/{id}/messages?after={lastId} mỗi 3–5 giây (polling).
Bảng liên quan: messages, requests, media_files, website_attributes (lexicon), activity_logs.
API gợi ý: GET /api/v1/requests/{id}/messages · POST /api/v1/requests/{id}/messages
Trang 19 / 55

ShareLoop — Luồng hoạt động & ERD v10
4.12. F12 — Thẻ bài đăng và đổi món đề nghị
Hình 15 — F12 — Thẻ bài đăng và đổi món đề nghị
Xử lý ở backend
1. Tin ITEM_CARD: lưu ref_item_id; khi đọc, backend ghép dữ liệu hiện tại của bài (tiêu đề, ảnh
đầu, trạng thái).
2. Tin OFFER_CHANGE: ref_item_id là món mới, offer_status = PENDING. Chủ bài gọi POST
/messages/{id}/accept: một transaction đổi requests.offered_item_id, món cũ →
APPROVED, món mới → RESERVED, offer_status = ACCEPTED.
Bảng liên quan: messages, requests, items.
API gợi ý: POST /api/v1/requests/{id}/messages (kind = ITEM_CARD | OFFER_CHANGE) · POST
/api/v1/messages/{id}/accept · POST /api/v1/messages/{id}/decline
Trang 20 / 55

ShareLoop — Luồng hoạt động & ERD v10
4.13. F13 — Chuyển bài Cho thành Trao đổi
Hình 16 — F13 — Chuyển bài Cho thành Trao đổi
Xử lý ở backend
1. POST /requests/{id}/convert-to-swap với desiredItem: chỉ chủ bài; request ở RESERVED
hoặc AWAITING_LOGISTICS.
2. Tầng 1 trên desiredItem; cập nhật items.offer_type = SWAP, desired_item; requests.type
= SWAP; tin SYSTEM; phát sự kiện AI sàng lọc.
3. Không có thay đổi nào về Credit.
Bảng liên quan: items, requests, messages.
API gợi ý: POST /api/v1/requests/{id}/convert-to-swap
Trang 21 / 55

ShareLoop — Luồng hoạt động & ERD v10
4.14. F14 — Chốt lịch và trao thông tin liên lạc
Hình 17 — F14 — Chốt lịch và trao thông tin liên lạc
Xử lý ở backend
1. PUT /requests/{id}/logistics: delivery_method, meeting_place, meeting_time →
AWAITING_LOGISTICS; ghi *_waiver_at nếu SHIPPING.
2. POST /requests/{id}/confirm-schedule: ghi donor_schedule_ok_at hoặc
receiver_schedule_ok_at. Khi đủ hai mốc (và SWAP có offered_item_id):
LOGISTICS_CONFIRMED, contact_revealed_at = now.
3. Sau commit: gửi email thông tin liên lạc cho hai bên; tin SYSTEM trong chat. API chi tiết request
chỉ trả email, SĐT đối phương khi contact_revealed_at có giá trị.
Bảng liên quan: requests, users (đọc), messages, notifications.
API gợi ý: PUT /api/v1/requests/{id}/logistics · POST /api/v1/requests/{id}/confirm-
schedule · GET /api/v1/requests/{id}
Trang 22 / 55

ShareLoop — Luồng hoạt động & ERD v10
4.15. F15 — Giao nhận và hoàn tất
Hình 18 — F15 — Giao nhận và hoàn tất
Xử lý ở backend
1. POST /requests/{id}/handover (kèm asDescribed, note khi là bên nhận; mediaIds minh
chứng tuỳ chọn → request_media_mappings stage HANDOVER).
2. asDescribed = false: tạo reports NOT_AS_DESCRIBED stage BEFORE, request →
DISPUTED.
3. Đủ hai mốc *_handover_at: COMPLETED, completed_at; cộng điểm và cập nhật uy tín qua
ReputationService (ghi activity_logs POINTS); bài (và món đề nghị) → TRADED.
Bảng liên quan: requests, request_media_mappings, media_files, reports, users, items, activity_logs.
API gợi ý: POST /api/v1/requests/{id}/handover
Trang 23 / 55

ShareLoop — Luồng hoạt động & ERD v10
4.16. F16 — Khiếu nại và tranh chấp
Hình 19 — F16 — Khiếu nại và tranh chấp
Xử lý ở backend
1. POST /reports: bài/người/giao dịch; với giao dịch kiểm khung thời gian; request → DISPUTED;
report → AWAITING_RESPONSE.
2. Report bài: đếm report OPEN của bài; đủ 3 → items.is_active = false (ẩn bài, giữ nguyên status).
3. POST /reports/{id}/response: bên bị khiếu nại giải trình (≤ 3 ngày).
Trang 24 / 55

ShareLoop — Luồng hoạt động & ERD v10
4. POST /admin/reports/{id}/resolve: kết luận; có cơ sở thì gọi
ReputationService.deductStar() (ghi stars_deducted, activity_logs STARS); hàng cấm
thì bắt buộc rà soát bài khác; request về COMPLETED hoặc CANCELLED.
Bảng liên quan: reports, requests, items, users, request_media_mappings, activity_logs, notifications.
API gợi ý: POST /api/v1/reports · POST /api/v1/reports/{id}/response · GET
/api/v1/admin/reports · POST /api/v1/admin/reports/{id}/resolve
4.17. F17 — Uy tín: trừ sao, hồi sao, ân hạn
Hình 20 — F17 — Uy tín: trừ sao, hồi sao, ân hạn
Xử lý ở backend
1. Mọi thay đổi trust_stars, total_points, rank_level, clean_streak, points_today chỉ đi qua
ReputationService, mỗi thay đổi ghi một dòng activity_logs (POINTS hoặc STARS) có lý do.
2. Sao về 0: ẩn mọi bài APPROVED của người đó (is_active = false) và chặn tạo bài, tạo request ở
tầng service.
3. POST /admin/users/{id}/grace-star: chỉ khi grace_star_used = false; bắt buộc lý do.
Bảng liên quan: users, items, activity_logs.
API gợi ý: GET /api/v1/me/reputation · POST /api/v1/admin/users/{id}/grace-star
Trang 25 / 55

ShareLoop — Luồng hoạt động & ERD v10
5. Sơ đồ trạng thái
5.1. Request
Hình 21 — Trạng thái của requests.status
Trang 26 / 55

ShareLoop — Luồng hoạt động & ERD v10
5.2. Bài đăng
Hình 22 — Trạng thái của items.status
5.3. Các trạng thái khác
Cột Giá trị và chuyển đổi hợp lệ
users.status PENDING_VERIFICATION → ACTIVE (xác thực OTP) · ACTIVE → BANNED (về 0 sao lần
hai hoặc Admin). Khoá / mở khoá tạm thời dùng is_active, không đổi status.
items.fee_state NONE → HELD (gửi duyệt / gửi bản sửa có phí) → CHARGED (duyệt) hoặc RELEASED (từ
chối, rút bài)
items.auth_status NONE → PENDING (bật nhãn) → VERIFIED / REJECTED (Admin) · VERIFIED → REVOKED
(chứng từ giả)
payment_orders.statu PENDING → SUCCESS (IPN hợp lệ) / FAILED (IPN báo lỗi) / EXPIRED (job 30 phút)
s
reports.status OPEN → AWAITING_RESPONSE (khiếu nại giao dịch) → OPEN (hết hạn giải trình hoặc đã
giải trình) → RESOLVED / DISMISSED
messages.offer_statu PENDING → ACCEPTED / DECLINED
s
media_files.status UPLOADED → ATTACHED khi luồng gắn tệp gọi MediaService.attach() (bài đăng,
sửa bài, ảnh chat, minh chứng, ảnh đại diện). Tệp UPLOADED quá 24 giờ bị job dọn
(is_deleted = true).
is_active, Hai cờ của mọi bảng chính, độc lập với status: tắt / bật lại không làm mất status cũ; xoá mềm
is_deleted không đổi status. Xem mục 7.11.
Trang 27 / 55

ShareLoop — Luồng hoạt động & ERD v10
Mỗi cột trạng thái có một enum Java và một hàm canTransition(from, to) duy nhất; mọi chỗ đổi
trạng thái đều gọi hàm này, chuyển sai thì ném lỗi 409.
Trang 28 / 55

ShareLoop — Luồng hoạt động & ERD v10
6. Tác vụ nền
Job Tần suất Điều kiện chọn Hành động Bảng
Xoá tài khoản chưa Mỗi giờ users PENDING_VERIFICATION, is_deleted = true (xoá mềm) users
xác thực created_at < now − 24h
Hết hạn đơn nạp 5 phút payment_orders PENDING quá 30 phút EXPIRED payment_orders
Dọn tệp chưa gắn Mỗi giờ media_files UPLOADED, created_at < is_deleted = true; xoá tệp media_files
now − 24h trong StorageService
Huỷ yêu cầu treo Mỗi giờ requests PENDING quá 3 ngày CANCELLED, thông báo requests,
notifications
Huỷ giao dịch im lặng Mỗi giờ requests RESERVED / CANCELLED; bài và món đề requests, items
AWAITING_LOGISTICS, reserved_at < nghị về APPROVED
now − 10 ngày
Tự hoàn tất Mỗi giờ requests AWAITING_HANDOVER, một COMPLETED, requests, users,
bên xác nhận quá 3 ngày, không có auto_confirmed = true, cộng items, activity_logs
report mở điểm
Hết hạn bài Mỗi giờ items APPROVED, expire_at < now EXPIRED, xoá boosted_until items
Hết đẩy bài Không Truy vấn dùng điều kiện boosted_until > — items
cần job now
Hết hạn giải trình Mỗi giờ reports AWAITING_RESPONSE quá 3 Chuyển OPEN để Admin reports
ngày quyết
Cảnh báo SLA duyệt Mỗi giờ items PENDING_REVIEW quá 24 giờ Thông báo Admin notifications
Nhắc gia hạn (Could) Mỗi ngày items APPROVED, còn ≤ 3 ngày Email + thông báo notifications
• Mọi job idempotent: điều kiện chọn bao gồm trạng thái hiện tại, nên chạy lại không có tác dụng
kép.
• Mỗi job xử lý theo lô nhỏ (vd 100 bản ghi) và mỗi bản ghi trong transaction riêng, để một bản ghi
lỗi không làm hỏng cả lô.
Trang 29 / 55

ShareLoop — Luồng hoạt động & ERD v10
7. Thiết kế dữ liệu (ERD)
7.1. Tổng quan 17 bảng
Nhóm Bảng Vai trò
Người dùng & hệ users Tài khoản Member/Admin; gộp luôn OTP, ví Credit, hạn mức
thống AI, uy tín và điểm.
Người dùng & hệ areas Cây khu vực (tỉnh → quận/huyện).
thống
Người dùng & hệ website_attributes (mẫu Cấu hình dạng khoá–giá trị: phí, hạn mức, mốc thời gian,
thống WebsiteAttributes) lexicon, checklist, lý do từ chối mẫu, tên miền email dùng một
lần.
Người dùng & hệ activity_logs Nhật ký chung: hành động Admin, lượt gọi AI, thay đổi điểm và
thống sao, quyết định kiểm duyệt, sự kiện bảo mật.
Người dùng & hệ notifications Thông báo trong ứng dụng (chuông thông báo).
thống
Bài đăng item_categories Danh mục (có danh mục cha).
Bài đăng item_attributes (mẫu Định nghĩa thuộc tính động theo danh mục (vd: trạng thái hoạt
ItemAttributes) động).
Bài đăng items Bài đăng; gộp trạng thái phí đang giữ, kết quả AI sàng lọc, bản
chụp checklist lần duyệt gần nhất, đẩy bài.
Bài đăng item_attribute_mappings Giá trị thuộc tính động của từng bài.
(mẫu ItemAttributeMappings)
Bài đăng media_files (mẫu Pictures) Mọi tệp đã tải lên (ảnh bài, ảnh lỗi, chứng từ, ảnh chat, minh
chứng, ảnh đại diện).
Bài đăng item_media_mappings (mẫu Gắn tệp vào bài đăng theo vai trò và thứ tự.
ItemPictureMappings)
Giao dịch requests Một yêu cầu xin đồ hoặc đề nghị trao đổi; toàn bộ vòng đời giao
dịch, lịch hẹn, xác nhận hai chiều.
Giao dịch request_media_mappings (mẫu Ảnh/video minh chứng gắn với một giao dịch (khi giao nhận
ItemPictureMappings) hoặc khi tranh chấp).
Giao dịch messages Tin nhắn trong khung chat, gồm thẻ bài đăng và đề xuất đổi
món; lưu cả kết quả kiểm duyệt.
Giao dịch reports Báo cáo bài/người và khiếu nại giao dịch; kết quả xử lý và số
sao bị trừ.
Tài chính payment_orders Đơn nạp Credit; đơn SUCCESS chính là sổ tiền mặt thực thu.
Tài chính credit_ledger Sổ cái Credit: mọi thay đổi thật của số dư.
Năm bảng chính users, items, requests, reports, media_files mang đủ 7 cột chuẩn; bốn bảng
danh mục, cấu hình mang 6 cột (không có status). Chi tiết ở mục 7.11.
Trang 30 / 55

ShareLoop — Luồng hoạt động & ERD v10
Hình 23 — ERD tổng quan (khoá chính, khoá ngoại)
Trang 31 / 55

ShareLoop — Luồng hoạt động & ERD v10
7.2. Cấu trúc mapping theo giảng viên
Mẫu Bảng ShareLoop Ghi chú
Pictures media_files Một bảng cho mọi tệp: ảnh bài, ảnh lỗi, chứng từ,
ảnh chat, minh chứng, ảnh đại diện. Cột
visibility quyết định công khai hay private.
ItemPictureMappings item_media_mappings, Khoá chính ghép (chủ thể, tệp). role phân biệt ảnh
request_media_mappings thường, ảnh lỗi, chứng từ.
ItemAttributes / item_attributes, Thuộc tính động theo danh mục; thêm thuộc tính
ItemAttributeMappings item_attribute_mappings không cần sửa schema.
WebsiteAttributes website_attributes attr_group tách loại cấu hình; extra (JSONB)
chứa dữ liệu phụ như mức độ và ngoại lệ của từ
khoá cấm.
Trang 32 / 55

ShareLoop — Luồng hoạt động & ERD v10
7.3. Module Người dùng & hệ thống
Hình 24 — ERD chi tiết — Người dùng & hệ thống
Bảng users
Tài khoản Member/Admin; gộp luôn OTP, ví Credit, hạn mức AI, uy tín và điểm.
Trang 33 / 55

ShareLoop — Luồng hoạt động & ERD v10
| Cột | Kiểu   | Ràng buộc | Ghi chú  |
| --- | ------ | --------- | -------- |
| id  | BIGINT | PK        | Identity |
email VARCHAR(255) NN, UQ* Định danh đăng nhập, đã xác thực OTP. *Duy
nhất trong bản ghi chưa xoá
| password_hash | VARCHAR(100) | NN  | BCrypt |
| ------------- | ------------ | --- | ------ |
| full_name     | VARCHAR(100) | NN  |        |
phone VARCHAR(15) NN, UQ* *Duy nhất trong tài khoản ACTIVE chưa xoá;
không xác thực OTP
| area_id | BIGINT | FK→areas | Khu vực mặc định |
| ------- | ------ | -------- | ---------------- |
avatar_media_id BIGINT FK→media_files Ảnh đại diện, có thể null
| is_admin | BOOLEAN     | NN     | Phân quyền Admin                 |
| -------- | ----------- | ------ | -------------------------------- |
| status   | VARCHAR(20) | NN, CK | PENDING_VERIFICATION / ACTIVE /  |
BANNED
otp_code_hash VARCHAR(100) OTP đang hiệu lực (băm), một mã tại một
thời điểm
| otp_purpose | VARCHAR(20) | CK  | REGISTER / RESET_PASSWORD /  |
| ----------- | ----------- | --- | ---------------------------- |
CHANGE_PHONE
| otp_expires_at   | TIMESTAMPTZ |            | Hạn 5 phút                         |
| ---------------- | ----------- | ---------- | ---------------------------------- |
| otp_failed_count | SMALLINT    | NN         | Tối đa 5 lần sai                   |
| pending_phone    | VARCHAR(15) |            | Số mới đang chờ xác nhận OTP email |
| phone_changed_at | TIMESTAMPTZ |            | Chặn đổi số quá 1 lần/30 ngày      |
| credit_balance   | BIGINT      | NN, CK ≥ 0 | Số dư Credit                       |
held_credit BIGINT NN, CK ≥ 0 Tổng phí đang giữ cho bài chờ duyệt
has_topped_up BOOLEAN NN Đã từng nạp → mở 5 lượt AI miễn phí/ngày
| free_ai_used | SMALLINT | NN  | Số lượt AI miễn phí đã dùng trong  |
| ------------ | -------- | --- | ---------------------------------- |
free_ai_date
| free_ai_date    | DATE        |            |                                   |
| --------------- | ----------- | ---------- | --------------------------------- |
| trust_stars     | SMALLINT    | NN, CK 0–5 | Mặc định 5                        |
| total_points    | INT         | NN         |                                   |
| rank_level      | VARCHAR(10) | NN         | BRONZE / SILVER / GOLD / DIAMOND  |
| points_today    | INT         | NN         | Trần 30 điểm/ngày (BR-U11)        |
| points_date     | DATE        |            |                                   |
| clean_streak    | INT         | NN         | Giao dịch sạch liên tiếp (BR-U09) |
| grace_star_used | BOOLEAN     | NN         | Chỉ đổi false → true              |
| registration_ip | VARCHAR(45) |            | Giới hạn 3 tài khoản/IP/24 giờ    |
Cột chuẩn (mục 7.11) — cùng status ở trên là đủ 7 cột
Trang 34 / 55

ShareLoop — Luồng hoạt động & ERD v10
| Cột        | Kiểu        | Ràng buộc | Ghi chú                                 |
| ---------- | ----------- | --------- | --------------------------------------- |
| created_at | TIMESTAMPTZ | NN        | Mặc định now()                          |
| updated_at | TIMESTAMPTZ | NN        | Trigger + JPA tự cập nhật               |
| created_by | BIGINT      |           | Null khi tự đăng ký                     |
| updated_by | BIGINT      |           | Id người sửa gần nhất; job không ghi đè |
is_active BOOLEAN NN false = Admin khoá tài khoản (thay trạng thái
LOCKED cũ)
is_deleted BOOLEAN NN true = tài khoản chưa xác thực quá 24 giờ bị
dọn
Bảng areas
Cây khu vực (tỉnh → quận/huyện).
| Cột       | Kiểu         | Ràng buộc | Ghi chú                        |
| --------- | ------------ | --------- | ------------------------------ |
| id        | BIGINT       | PK        |                                |
| name      | VARCHAR(100) | NN        |                                |
| parent_id | BIGINT       | FK→areas  | Tự tham chiếu                  |
| level     | SMALLINT     | NN        | 1 = tỉnh/thành, 2 = quận/huyện |
Cột chuẩn (mục 7.11) — bảng danh mục, cấu hình không có status
| created_at | TIMESTAMPTZ | NN  | Mặc định now()            |
| ---------- | ----------- | --- | ------------------------- |
| updated_at | TIMESTAMPTZ | NN  | Trigger + JPA tự cập nhật |
created_by BIGINT Id người tạo; null khi chưa đăng nhập hoặc
do job
| updated_by | BIGINT  |     | Id người sửa gần nhất; job không ghi đè |
| ---------- | ------- | --- | --------------------------------------- |
| is_active  | BOOLEAN | NN  | false = không cho chọn khi đăng bài     |
| is_deleted | BOOLEAN | NN  | Mặc định false; xoá mềm                 |
Bảng website_attributes
Cấu hình dạng khoá–giá trị: phí, hạn mức, mốc thời gian, lexicon, checklist, lý do từ chối mẫu, tên miền
email dùng một lần. Theo mẫu WebsiteAttributes của giảng viên.
| Cột        | Kiểu        | Ràng buộc | Ghi chú                    |
| ---------- | ----------- | --------- | -------------------------- |
| id         | BIGINT      | PK        |                            |
| attr_group | VARCHAR(30) | NN, CK    | CONFIG / BANNED_KEYWORD /  |
CHECKLIST / REJECT_REASON /
DISPOSABLE_DOMAIN
attr_key VARCHAR(100) NN UQ (attr_group, attr_key) trong bản ghi chưa
xoá. Vd: post_fee, "súng"
Trang 35 / 55

ShareLoop — Luồng hoạt động & ERD v10
| Cột        | Kiểu  | Ràng buộc | Ghi chú                                   |
| ---------- | ----- | --------- | ----------------------------------------- |
| attr_value | TEXT  | NN        | Vd: "5"; nội dung câu hỏi checklist       |
| extra      | JSONB |           | Lexicon: {category, severity, matchMode,  |
exceptions[], scope}
| sort_order | INT | NN  | Thứ tự checklist |
| ---------- | --- | --- | ---------------- |
Cột chuẩn (mục 7.11) — bảng danh mục, cấu hình không có status
| created_at | TIMESTAMPTZ | NN  | Mặc định now()            |
| ---------- | ----------- | --- | ------------------------- |
| updated_at | TIMESTAMPTZ | NN  | Trigger + JPA tự cập nhật |
created_by BIGINT Id người tạo; null khi chưa đăng nhập hoặc
do job
| updated_by | BIGINT |     | Admin sửa gần nhất |
| ---------- | ------ | --- | ------------------ |
is_active BOOLEAN NN false = tạm tắt (từ khoá, câu hỏi checklist)
| is_deleted | BOOLEAN | NN  | Mặc định false; xoá mềm |
| ---------- | ------- | --- | ----------------------- |
Bảng activity_logs
Nhật ký chung: hành động Admin, lượt gọi AI, thay đổi điểm và sao, quyết định kiểm duyệt, sự kiện bảo
mật.
| Cột      | Kiểu        | Ràng buộc | Ghi chú                            |
| -------- | ----------- | --------- | ---------------------------------- |
| id       | BIGINT      | PK        |                                    |
| actor_id | BIGINT      | FK→users  | Null nếu là hệ thống               |
| type     | VARCHAR(40) | NN        | ADMIN_ACTION / AI_CALL / POINTS /  |
STARS / REVIEW / SECURITY …
| target_type | VARCHAR(20) |     | USER / ITEM / REQUEST / REPORT /  |
| ----------- | ----------- | --- | --------------------------------- |
CONFIG
| target_id | BIGINT |     | Không đặt FK (bảng log)                      |
| --------- | ------ | --- | -------------------------------------------- |
| data      | JSONB  |     | Chi tiết: điểm cộng, sao trừ, model AI, bản  |
chụp checklist…
| reason     | TEXT        |     | Bắt buộc với hành động Admin |
| ---------- | ----------- | --- | ---------------------------- |
| ip         | VARCHAR(45) |     |                              |
| created_at | TIMESTAMPTZ | NN  |                              |
Bảng notifications
Thông báo trong ứng dụng (chuông thông báo).
| Cột     | Kiểu   | Ràng buộc    | Ghi chú |
| ------- | ------ | ------------ | ------- |
| id      | BIGINT | PK           |         |
| user_id | BIGINT | FK→users, NN |         |
Trang 36 / 55

ShareLoop — Luồng hoạt động & ERD v10
| Cột  | Kiểu        | Ràng buộc | Ghi chú                        |
| ---- | ----------- | --------- | ------------------------------ |
| type | VARCHAR(40) | NN        | POST_APPROVED / NEW_REQUEST /  |
SELECTED / …
| title      | VARCHAR(150) | NN  |                    |
| ---------- | ------------ | --- | ------------------ |
| body       | TEXT         |     |                    |
| link       | VARCHAR(255) |     | Đường dẫn trong FE |
| is_read    | BOOLEAN      | NN  |                    |
| created_at | TIMESTAMPTZ  | NN  |                    |
Trang 37 / 55

ShareLoop — Luồng hoạt động & ERD v10
7.4. Module Bài đăng
Hình 25 — ERD chi tiết — Bài đăng
Bảng item_categories
Danh mục (có danh mục cha).
Trang 38 / 55

ShareLoop — Luồng hoạt động & ERD v10
| Cột           | Kiểu         | Ràng buộc          | Ghi chú                     |
| ------------- | ------------ | ------------------ | --------------------------- |
| id            | BIGINT       | PK                 |                             |
| name          | VARCHAR(100) | NN                 |                             |
| parent_id     | BIGINT       | FK→item_categories | Tự tham chiếu               |
| is_restricted | BOOLEAN      | NN                 | Không cho chọn khi đăng bài |
| is_boostable  | BOOLEAN      | NN                 | Danh mục "Khác" = false     |
| sort_order    | INT          | NN                 |                             |
Cột chuẩn (mục 7.11) — bảng danh mục, cấu hình không có status
| created_at | TIMESTAMPTZ | NN  | Mặc định now()            |
| ---------- | ----------- | --- | ------------------------- |
| updated_at | TIMESTAMPTZ | NN  | Trigger + JPA tự cập nhật |
created_by BIGINT Id người tạo; null khi chưa đăng nhập hoặc
do job
| updated_by | BIGINT |     | Id người sửa gần nhất; job không ghi đè |
| ---------- | ------ | --- | --------------------------------------- |
is_active BOOLEAN NN false = ẩn khỏi form đăng bài, bài cũ giữ
nguyên
| is_deleted | BOOLEAN | NN  | Chỉ xoá khi chưa có bài nào dùng |
| ---------- | ------- | --- | -------------------------------- |
Bảng item_attributes
Định nghĩa thuộc tính động theo danh mục (vd: trạng thái hoạt động). Theo mẫu ItemAttributes của
giảng viên.
| Cột         | Kiểu   | Ràng buộc            | Ghi chú |
| ----------- | ------ | -------------------- | ------- |
| id          | BIGINT | PK                   |         |
| category_id | BIGINT | FK→item_categories,  |         |
NN
| name | VARCHAR(100) | NN  |     |
| ---- | ------------ | --- | --- |
data_type VARCHAR(10) NN, CK TEXT / NUMBER / SELECT / BOOLEAN
| options     | JSONB   |     | Danh sách lựa chọn khi SELECT |
| ----------- | ------- | --- | ----------------------------- |
| is_required | BOOLEAN | NN  |                               |
| sort_order  | INT     | NN  |                               |
Cột chuẩn (mục 7.11) — bảng danh mục, cấu hình không có status
| created_at | TIMESTAMPTZ | NN  | Mặc định now()            |
| ---------- | ----------- | --- | ------------------------- |
| updated_at | TIMESTAMPTZ | NN  | Trigger + JPA tự cập nhật |
created_by BIGINT Id người tạo; null khi chưa đăng nhập hoặc
do job
| updated_by | BIGINT  |     | Id người sửa gần nhất; job không ghi đè |
| ---------- | ------- | --- | --------------------------------------- |
| is_active  | BOOLEAN | NN  | false = không hỏi ở form đăng bài mới   |
Trang 39 / 55

ShareLoop — Luồng hoạt động & ERD v10
| Cột        | Kiểu    | Ràng buộc | Ghi chú                 |
| ---------- | ------- | --------- | ----------------------- |
| is_deleted | BOOLEAN | NN        | Mặc định false; xoá mềm |
Bảng items
Bài đăng; gộp trạng thái phí đang giữ, kết quả AI sàng lọc, bản chụp checklist lần duyệt gần nhất, đẩy
bài.
| Cột         | Kiểu   | Ràng buộc            | Ghi chú    |
| ----------- | ------ | -------------------- | ---------- |
| id          | BIGINT | PK                   |            |
| donor_id    | BIGINT | FK→users, NN         | Người đăng |
| category_id | BIGINT | FK→item_categories,  |            |
NN
| area_id      | BIGINT       | FK→areas, NN |                   |
| ------------ | ------------ | ------------ | ----------------- |
| offer_type   | VARCHAR(10)  | NN, CK       | GIVE / SWAP       |
| title        | VARCHAR(150) | NN           |                   |
| description  | TEXT         | NN           |                   |
| desired_item | TEXT         | CK           | Bắt buộc khi SWAP |
condition VARCHAR(12) NN, CK NEW / LIKE_NEW / GOOD / DEFECTIVE
| defect_note | TEXT        | CK  | Bắt buộc khi DEFECTIVE |
| ----------- | ----------- | --- | ---------------------- |
| brand       | VARCHAR(80) |     |                        |
auth_status VARCHAR(10) NN, CK NONE / PENDING / VERIFIED / REJECTED /
REVOKED
| status | VARCHAR(16) | NN, CK | PENDING_REVIEW / APPROVED /  |
| ------ | ----------- | ------ | ---------------------------- |
REJECTED / RESERVED / TRADED /
EXPIRED
| reject_reason | TEXT |     | Lý do Admin từ chối gần nhất |
| ------------- | ---- | --- | ---------------------------- |
post_fee_paid BOOLEAN NN Đã trừ phí đăng bài (5 Credit) → gửi lại không
thu nữa
pending_fee_type VARCHAR(10) CK POST / EDIT — khoản phí đang giữ
| pending_fee | INT | NN  | Số Credit đang giữ cho bài này |
| ----------- | --- | --- | ------------------------------ |
fee_state VARCHAR(10) NN, CK NONE / HELD / CHARGED / RELEASED
free_edit_used BOOLEAN NN Đã dùng lượt sửa miễn phí sau duyệt
approved_edit_count SMALLINT NN Số lần sửa sau duyệt đã được duyệt
| ai_risk   | VARCHAR(10) | NN, CK | LOW / MEDIUM / HIGH / UNCHECKED |
| --------- | ----------- | ------ | ------------------------------- |
| ai_result | JSONB       |        | JSON cờ AI + model + thời điểm  |
review_snapshot JSONB Bộ câu hỏi + câu trả lời checklist lần duyệt
gần nhất
| approved_at | TIMESTAMPTZ |     |     |
| ----------- | ----------- | --- | --- |
Trang 40 / 55

ShareLoop — Luồng hoạt động & ERD v10
| Cột           | Kiểu        | Ràng buộc | Ghi chú                   |
| ------------- | ----------- | --------- | ------------------------- |
| expire_at     | TIMESTAMPTZ |           | 30 ngày (45 từ hạng Vàng) |
| boosted_until | TIMESTAMPTZ |           | Đẩy bài                   |
| view_count    | INT         | NN        |                           |
Cột chuẩn (mục 7.11) — cùng status ở trên là đủ 7 cột
| created_at | TIMESTAMPTZ | NN  | Mặc định now()                          |
| ---------- | ----------- | --- | --------------------------------------- |
| updated_at | TIMESTAMPTZ | NN  | Trigger + JPA tự cập nhật               |
| created_by | BIGINT      |     | Bằng donor_id (BaseEntity tự điền)      |
| updated_by | BIGINT      |     | Id người sửa gần nhất; job không ghi đè |
is_active BOOLEAN NN false = bài bị ẩn (3 report, người đăng về 0
sao, Admin ẩn); bật lại thì về đúng status cũ
is_deleted BOOLEAN NN true = bài bị gỡ (người đăng rút / gỡ, Admin
gỡ)
Bảng item_attribute_mappings
Giá trị thuộc tính động của từng bài. Theo mẫu ItemAttributeMappings của giảng viên.
| Cột          | Kiểu         | Ràng buộc              | Ghi chú |
| ------------ | ------------ | ---------------------- | ------- |
| item_id      | BIGINT       | PK, FK→items           |         |
| attribute_id | BIGINT       | PK, FK→item_attributes |         |
| value        | VARCHAR(255) | NN                     |         |
Bảng media_files
Mọi tệp đã tải lên (ảnh bài, ảnh lỗi, chứng từ, ảnh chat, minh chứng, ảnh đại diện). Theo mẫu Pictures
của giảng viên.
| Cột | Kiểu   | Ràng buộc | Ghi chú |
| --- | ------ | --------- | ------- |
| id  | BIGINT | PK        |         |
storage_key VARCHAR(300) UQ, NN Khoá trong StorageService, không lưu URL
tuyệt đối
| mime_type | VARCHAR(50) | NN  | image/jpeg, image/png, image/webp,  |
| --------- | ----------- | --- | ----------------------------------- |
video/mp4
| size_bytes | BIGINT | NN  | ≤ 5 MB với ảnh |
| ---------- | ------ | --- | -------------- |
| width      | INT    |     |                |
| height     | INT    |     |                |
visibility VARCHAR(10) NN, CK PUBLIC / PRIVATE (chứng từ, minh chứng)
| status | VARCHAR(10) | NN, CK | UPLOADED (chưa gắn vào đâu) /  |
| ------ | ----------- | ------ | ------------------------------ |
ATTACHED
Trang 41 / 55

ShareLoop — Luồng hoạt động & ERD v10
| Cột | Kiểu | Ràng buộc | Ghi chú |
| --- | ---- | --------- | ------- |
Cột chuẩn (mục 7.11) — cùng status ở trên là đủ 7 cột
| created_at | TIMESTAMPTZ | NN  | Mặc định now()                          |
| ---------- | ----------- | --- | --------------------------------------- |
| updated_at | TIMESTAMPTZ | NN  | Trigger + JPA tự cập nhật               |
| created_by | BIGINT      |     | Người tải lên (thay cột uploaded_by cũ) |
| updated_by | BIGINT      |     | Id người sửa gần nhất; job không ghi đè |
is_active BOOLEAN NN false = Admin gỡ tệp vi phạm, vẫn giữ làm
bằng chứng
is_deleted BOOLEAN NN true = người dùng xoá, hoặc job dọn tệp
UPLOADED quá 24 giờ
Bảng item_media_mappings
Gắn tệp vào bài đăng theo vai trò và thứ tự. Theo mẫu ItemPictureMappings của giảng viên.
| Cột        | Kiểu        | Ràng buộc          | Ghi chú                     |
| ---------- | ----------- | ------------------ | --------------------------- |
| item_id    | BIGINT      | PK, FK→items       |                             |
| media_id   | BIGINT      | PK, FK→media_files |                             |
| role       | VARCHAR(12) | NN, CK             | PHOTO / DEFECT / AUTH_PROOF |
| sort_order | SMALLINT    | NN                 | Ảnh 0 là ảnh đại diện       |
Trang 42 / 55

ShareLoop — Luồng hoạt động & ERD v10
7.5. Module Giao dịch
Hình 26 — ERD chi tiết — Giao dịch
Bảng requests
Một yêu cầu xin đồ hoặc đề nghị trao đổi; toàn bộ vòng đời giao dịch, lịch hẹn, xác nhận hai chiều.
Trang 43 / 55

ShareLoop — Luồng hoạt động & ERD v10
| Cột     | Kiểu   | Ràng buộc    | Ghi chú                         |
| ------- | ------ | ------------ | ------------------------------- |
| id      | BIGINT | PK           |                                 |
| item_id | BIGINT | FK→items, NN | Bài được xin / được đề nghị đổi |
receiver_id BIGINT FK→users, NN Người gửi yêu cầu; CK ≠ donor (kiểm ở
service)
| offered_item_id | BIGINT      | FK→items | Món đề nghị (SWAP)    |
| --------------- | ----------- | -------- | --------------------- |
| type            | VARCHAR(10) | NN, CK   | GIVE / SWAP           |
| status          | VARCHAR(30) | NN, CK   | PENDING / RESERVED /  |
AWAITING_LOGISTICS /
LOGISTICS_CONFIRMED /
AWAITING_HANDOVER / COMPLETED /
DISPUTED / REJECTED / CANCELLED
| delivery_method         | VARCHAR(12)  | CK  | IN_PERSON / SHIPPING        |
| ----------------------- | ------------ | --- | --------------------------- |
| meeting_place           | VARCHAR(255) |     |                             |
| meeting_time            | TIMESTAMPTZ  |     |                             |
| donor_waiver_at         | TIMESTAMPTZ  |     | Cam kết khi ship            |
| receiver_waiver_at      | TIMESTAMPTZ  |     |                             |
| donor_schedule_ok_at    | TIMESTAMPTZ  |     | Xác nhận lịch               |
| receiver_schedule_ok_at | TIMESTAMPTZ  |     |                             |
| contact_revealed_at     | TIMESTAMPTZ  |     | Mốc trao thông tin liên lạc |
| donor_handover_at       | TIMESTAMPTZ  |     | Xác nhận đã giao            |
| receiver_handover_at    | TIMESTAMPTZ  |     | Xác nhận đã nhận            |
| as_described            | BOOLEAN      |     | Câu trả lời "đúng mô tả?"   |
| auto_confirmed          | BOOLEAN      | NN  |                             |
| chat_violations         | SMALLINT     | NN  | ≥ 3 thì cảnh báo Admin      |
| reserved_at             | TIMESTAMPTZ  |     | Mốc 10 ngày                 |
| completed_at            | TIMESTAMPTZ  |     | Mốc 7 ngày khiếu nại        |
| cancel_reason           | VARCHAR(100) |     |                             |
Cột chuẩn (mục 7.11) — cùng status ở trên là đủ 7 cột
| created_at | TIMESTAMPTZ | NN  | Mặc định now()                          |
| ---------- | ----------- | --- | --------------------------------------- |
| updated_at | TIMESTAMPTZ | NN  | Trigger + JPA tự cập nhật               |
| created_by | BIGINT      |     | Bằng receiver_id                        |
| updated_by | BIGINT      |     | Id người sửa gần nhất; job không ghi đè |
| is_active  | BOOLEAN     | NN  | Luôn true ở v10 (giữ theo chuẩn chung)  |
is_deleted BOOLEAN NN Luôn false ở v10: giao dịch không xoá, huỷ
bằng status CANCELLED
Trang 44 / 55

ShareLoop — Luồng hoạt động & ERD v10
Bảng request_media_mappings
Ảnh/video minh chứng gắn với một giao dịch (khi giao nhận hoặc khi tranh chấp). Theo mẫu
ItemPictureMappings của giảng viên.
| Cột         | Kiểu        | Ràng buộc          | Ghi chú            |
| ----------- | ----------- | ------------------ | ------------------ |
| request_id  | BIGINT      | PK, FK→requests    |                    |
| media_id    | BIGINT      | PK, FK→media_files |                    |
| stage       | VARCHAR(10) | NN, CK             | HANDOVER / DISPUTE |
| uploaded_by | BIGINT      | FK→users, NN       |                    |
| created_at  | TIMESTAMPTZ | NN                 |                    |
Bảng messages
Tin nhắn trong khung chat, gồm thẻ bài đăng và đề xuất đổi món; lưu cả kết quả kiểm duyệt.
| Cột        | Kiểu        | Ràng buộc       | Ghi chú                     |
| ---------- | ----------- | --------------- | --------------------------- |
| id         | BIGINT      | PK              |                             |
| request_id | BIGINT      | FK→requests, NN |                             |
| sender_id  | BIGINT      | FK→users        | Null = tin hệ thống         |
| kind       | VARCHAR(12) | NN, CK          | TEXT / IMAGE / ITEM_CARD /  |
OFFER_CHANGE / SYSTEM
| content         | TEXT         |                |                               |
| --------------- | ------------ | -------------- | ----------------------------- |
| media_id        | BIGINT       | FK→media_files | Khi IMAGE                     |
| ref_item_id     | BIGINT       | FK→items       | Khi ITEM_CARD / OFFER_CHANGE  |
| offer_status    | VARCHAR(10)  | CK             | PENDING / ACCEPTED / DECLINED |
| moderation      | VARCHAR(8)   | NN, CK         | PASS / BLOCKED                |
| moderation_tier | SMALLINT     |                | 1 hoặc 2                      |
| blocked_reason  | VARCHAR(100) |                | Hiển thị cho người gửi        |
| ai_confidence   | NUMERIC(3,2) |                | Khi đi qua tầng 2             |
| created_at      | TIMESTAMPTZ  | NN             |                               |
Bảng reports
Báo cáo bài/người và khiếu nại giao dịch; kết quả xử lý và số sao bị trừ.
| Cột         | Kiểu        | Ràng buộc    | Ghi chú               |
| ----------- | ----------- | ------------ | --------------------- |
| id          | BIGINT      | PK           |                       |
| reporter_id | BIGINT      | FK→users, NN |                       |
| target_type | VARCHAR(10) | NN, CK       | ITEM / USER / REQUEST |
| item_id     | BIGINT      | FK→items     |                       |
Trang 45 / 55

ShareLoop — Luồng hoạt động & ERD v10
| Cột            | Kiểu        | Ràng buộc   | Ghi chú                           |
| -------------- | ----------- | ----------- | --------------------------------- |
| target_user_id | BIGINT      | FK→users    |                                   |
| request_id     | BIGINT      | FK→requests |                                   |
| reason         | VARCHAR(30) | NN, CK      | NOT_AS_DESCRIBED / COUNTERFEIT /  |
PROHIBITED_ITEM / NO_SHOW /
WRONG_CONTACT_INFO /
HARASSMENT / OTHER
| description | TEXT        | NN     |                                   |
| ----------- | ----------- | ------ | --------------------------------- |
| stage       | VARCHAR(8)  | NN, CK | BEFORE / AFTER (so với Completed) |
| status      | VARCHAR(20) | NN, CK | OPEN / AWAITING_RESPONSE /        |
RESOLVED / DISMISSED
| response       | TEXT        |          | Giải trình của bên bị khiếu nại |
| -------------- | ----------- | -------- | ------------------------------- |
| responded_at   | TIMESTAMPTZ |          |                                 |
| resolved_by    | BIGINT      | FK→users | Admin                           |
| resolution     | TEXT        |          |                                 |
| stars_deducted | SMALLINT    | NN       |                                 |
| resolved_at    | TIMESTAMPTZ |          |                                 |
Cột chuẩn (mục 7.11) — cùng status ở trên là đủ 7 cột
| created_at | TIMESTAMPTZ | NN  | Mặc định now()                          |
| ---------- | ----------- | --- | --------------------------------------- |
| updated_at | TIMESTAMPTZ | NN  | Trigger + JPA tự cập nhật               |
| created_by | BIGINT      |     | Bằng reporter_id                        |
| updated_by | BIGINT      |     | Id người sửa gần nhất; job không ghi đè |
| is_active  | BOOLEAN     | NN  | Luôn true ở v10 (giữ theo chuẩn chung)  |
| is_deleted | BOOLEAN     | NN  | true = người báo cáo rút khi còn OPEN   |
Trang 46 / 55

ShareLoop — Luồng hoạt động & ERD v10
7.6. Module Tài chính
Hình 27 — ERD chi tiết — Tài chính
Bảng payment_orders
Đơn nạp Credit; đơn SUCCESS chính là sổ tiền mặt thực thu.
| Cột             | Kiểu        | Ràng buộc    | Ghi chú                     |
| --------------- | ----------- | ------------ | --------------------------- |
| id              | BIGINT      | PK           |                             |
| user_id         | BIGINT      | FK→users, NN |                             |
| order_code      | VARCHAR(40) | UQ, NN       | Mã gửi sang cổng            |
| amount_vnd      | BIGINT      | NN, CK       | Bội số 1.000, ≥ 10.000      |
| credits         | INT         | NN           | amount_vnd / vnd_per_credit |
| vnd_per_credit  | INT         | NN           | 1000 tại thời điểm nạp      |
| provider        | VARCHAR(20) | NN           | VNPAY / MOCK                |
| provider_txn_id | VARCHAR(60) |              |                             |
status VARCHAR(10) NN, CK PENDING / SUCCESS / FAILED / EXPIRED
| signature_verified | BOOLEAN     | NN  |                             |
| ------------------ | ----------- | --- | --------------------------- |
| callback_data      | JSONB       |     | Dữ liệu IPN gốc để đối soát |
| created_at         | TIMESTAMPTZ | NN  |                             |
updated_at TIMESTAMPTZ NN Mới (29/09): đổi status thì cập nhật
Trang 47 / 55

ShareLoop — Luồng hoạt động & ERD v10
| Cột     | Kiểu        | Ràng buộc | Ghi chú |
| ------- | ----------- | --------- | ------- |
| paid_at | TIMESTAMPTZ |           |         |
Bảng credit_ledger
Sổ cái Credit: mọi thay đổi thật của số dư.
| Cột     | Kiểu        | Ràng buộc    | Ghi chú                         |
| ------- | ----------- | ------------ | ------------------------------- |
| id      | BIGINT      | PK           |                                 |
| user_id | BIGINT      | FK→users, NN |                                 |
| amount  | INT         | NN           | Dương = cộng, âm = trừ          |
| type    | VARCHAR(16) | NN, CK       | TOP_UP / POST_FEE / EDIT_FEE /  |
RENEW_FEE / BOOST_FEE /
AI_SEARCH_FEE / ADMIN_ADJUST
| balance_after | BIGINT | NN       | Truy vết số dư            |
| ------------- | ------ | -------- | ------------------------- |
| item_id       | BIGINT | FK→items | Với các loại phí bài đăng |
payment_order_id BIGINT FK→payment_orders,  Với TOP_UP; UQ chống cộng hai lần
UQ
| note       | VARCHAR(255) |          | Bắt buộc với ADMIN_ADJUST |
| ---------- | ------------ | -------- | ------------------------- |
| created_by | BIGINT       | FK→users | Admin khi ADMIN_ADJUST    |
| created_at | TIMESTAMPTZ  | NN       |                           |
7.7. Ràng buộc và index quan trọng
-- tiếnY không âm
ALTER TABLE users ADD CHECK (credit_balance >= 0 AND held_credit >= 0);
-- sôP điện thoại duy nhâtP trong tài khoaDn đang hoạt động
CREATE UNIQUE INDEX ux_users_phone_active ON users(phone)
  WHERE status = 'ACTIVE' AND NOT is_deleted;
-- email duy nhâPt trong các baDn ghi chưa xoá mếmY (C34)
CREATE UNIQUE INDEX ux_users_email ON users(email) WHERE NOT is_deleted;
-- môei người chỉD một yếu câuY đang hoạt động cho một bài
CREATE UNIQUE INDEX ux_requests_active ON requests(item_id, receiver_id)
  WHERE status IN ('PENDING','RESERVED','AWAITING_LOGISTICS',
                   'LOGISTICS_CONFIRMED','AWAITING_HANDOVER','DISPUTED');
-- một bài chỉD một giao dịch từ RESERVED trDở lến
CREATE UNIQUE INDEX ux_requests_one_reserved ON requests(item_id)
  WHERE status IN ('RESERVED','AWAITING_LOGISTICS','LOGISTICS_CONFIRMED',
                   'AWAITING_HANDOVER','DISPUTED');
-- chônPg cộng Credit hai lânY cho một đởn nạp
CREATE UNIQUE INDEX ux_ledger_order ON credit_ledger(payment_order_id)
  WHERE payment_order_id IS NOT NULL;
-- bài Trao đôiD bắtP buộc có món mong muônP; bài lôie bắPt buộc mô taD lôei
ALTER TABLE items ADD CHECK (offer_type <> 'SWAP' OR desired_item IS NOT NULL);
ALTER TABLE items ADD CHECK (condition <> 'DEFECTIVE' OR defect_note IS NOT NULL);
-- câPu hình không trùng khoá
CREATE UNIQUE INDEX ux_attr ON website_attributes(attr_group, attr_key)
Trang 48 / 55

ShareLoop — Luồng hoạt động & ERD v10
WHERE NOT is_deleted;
-- tìm kiếPm không dâuP
CREATE INDEX ix_items_search ON items
USING gin (f_unaccent(lower(title || ' ' || description)) gin_trgm_ops);
CREATE INDEX ix_items_list ON items(status, category_id, area_id, created_at DESC)
WHERE is_active AND NOT is_deleted;
CREATE INDEX ix_items_boost ON items(boosted_until) WHERE boosted_until IS NOT NULL;
CREATE INDEX ix_messages_req ON messages(request_id, id);
CREATE INDEX ix_ledger_user ON credit_ledger(user_id, created_at DESC);
CREATE INDEX ix_logs_target ON activity_logs(target_type, target_id);
-- trigger updated_at cho mọi baDng có cột này: xem mục 7.11
7.8. So với ERD v9
Thay đổi Lý do
Bỏ requests.*FeeState Giao dịch không còn thu phí.
Thêm vào items: post_fee_paid, pending_fee_type, Phí đăng bài, luật sửa bài, AI sàng lọc, đẩy bài.
pending_fee, fee_state, free_edit_used,
approved_edit_count, ai_risk, ai_result,
review_snapshot, boosted_until
Thêm bảng request_media_mappings Minh chứng giao nhận và tranh chấp theo đúng mẫu
mapping, thay vì nhét vào bảng khác.
Thêm lại bảng notifications Thông báo trong app đơn giản hơn nhiều so với suy ra từ
các bảng khác.
Bỏ các cột OTP số điện thoại; thêm pending_phone, Bỏ xác thực SMS; đổi số bằng OTP email.
phone_changed_at
Thêm vào messages: kind ITEM_CARD / Thẻ bài trong chat, đổi món đề nghị.
OFFER_CHANGE, ref_item_id, offer_status
credit_ledger.type đổi bộ giá trị POST_FEE, EDIT_FEE, RENEW_FEE, BOOST_FEE thay
SwapFee, ReceiveFee.
Chuyển sang PostgreSQL, tên snake_case Chương 2.5 của SRS.
[29/09] Thêm 7 cột chuẩn cho users, items, requests, Yêu cầu giảng viên (C34), mục 7.11.
reports, media_files; 6 cột (không status) cho
item_categories, item_attributes, areas, website_attributes;
payment_orders thêm updated_at
[29/09] Bỏ LOCKED khỏi users.status; bỏ HIDDEN, Thay bằng is_active và is_deleted để không có hai cột cùng
REMOVED khỏi items.status nghĩa; ẩn rồi bật lại không mất status cũ.
[29/09] media_files.uploaded_by đổi thành created_by; thêm Bỏ cột trùng nghĩa; job dọn tệp tải lên nhưng không dùng tới.
media_files.status
[29/09] Bỏ khoá ngoại của website_attributes.updated_by; Cột chuẩn chỉ ghi id người thao tác; không vẽ thêm quan hệ,
cột created_by, updated_by mới không đặt khoá ngoại ERD gọn.
Trang 49 / 55

ShareLoop — Luồng hoạt động & ERD v10
7.9. Ánh xạ luồng ↔ bảng
| Luồng | Bảng đọc / ghi |     |     |
| ----- | -------------- | --- | --- |
F01 Đăng ký và xác thực email users (ghi), website_attributes (đọc), activity_logs (ghi)
| F02 Đăng nhập và quên mật khẩu | users (đọc/ghi), activity_logs (ghi) |     |     |
| ------------------------------ | ------------------------------------ | --- | --- |
| F03 Đổi số điện thoại          | users, requests (đọc), activity_logs |     |     |
F04 Nạp Credit payment_orders, users, credit_ledger, notifications
F05 Đăng bài, giữ phí và kiểm duyệt ba  media_files, items, item_media_mappings, item_attribute_mappings, users,
| tầng | website_attributes, activity_logs |     |     |
| ---- | --------------------------------- | --- | --- |
F06 Admin duyệt bài items, users, credit_ledger, item_media_mappings, media_files, activity_logs,
notifications
F07 Sửa bài items, item_media_mappings, item_attribute_mappings, users, requests (đọc)
F08 Gia hạn và đẩy bài items, users, credit_ledger, item_categories (đọc)
F09 Tìm kiếm và AI trợ lý items, item_media_mappings, media_files, users, credit_ledger, activity_logs
F10 Gửi yêu cầu và chọn người requests, items, users (đọc), messages, notifications
F11 Chat có kiểm duyệt messages, requests, media_files, website_attributes (lexicon), activity_logs
| F12 Thẻ bài đăng và đổi món đề nghị | messages, requests, items |     |     |
| ----------------------------------- | ------------------------- | --- | --- |
| F13 Chuyển bài Cho thành Trao đổi   | items, requests, messages |     |     |
F14 Chốt lịch và trao thông tin liên lạc requests, users (đọc), messages, notifications
F15 Giao nhận và hoàn tất requests, request_media_mappings, media_files, reports, users, items,
activity_logs
F16 Khiếu nại và tranh chấp reports, requests, items, users, request_media_mappings, activity_logs,
notifications
| F17 Uy tín: trừ sao, hồi sao, ân hạn | users, items, activity_logs |     |     |
| ------------------------------------ | --------------------------- | --- | --- |
7.10. Giá trị cấu hình khởi tạo (website_attributes, nhóm CONFIG)
Bảng dưới là dữ liệu của migration V2__seed_config.sql (người sở hữu: BE4). Mỗi dòng là một bản
ghi attr_group = CONFIG, attr_key = cột Khoá, attr_value = cột Giá trị. Mã nguồn đọc qua
ConfigService bằng enum ConfigKey, không viết cứng con số nào. Admin đổi giá trị trên trang cấu
hình mà không cần deploy lại.
| Khoá (attr_key) | Giá trị | Ý nghĩa | Nguồn |
| --------------- | ------- | ------- | ----- |
Phí & Credit
|     | 1000 | Tỉ lệ quy đổi: 1.000đ = 1 Credit | BR-P02 |
| --- | ---- | -------------------------------- | ------ |
vnd_per_credit
| topup_min_credits | 10  | Nạp tối thiểu (bội số 1.000đ)         | BR-P02   |
| ----------------- | --- | ------------------------------------- | -------- |
| post_fee          | 5   | Phí đăng bài, mọi bài Cho và Trao đổi | C01, C02 |
post_display_days 30 Số ngày hiển thị sau khi duyệt / gia hạn C02
| post_display_days_gold | 45  | Số ngày hiển thị từ hạng Vàng | C28 |
| ---------------------- | --- | ----------------------------- | --- |
Trang 50 / 55

ShareLoop — Luồng hoạt động & ERD v10
| Khoá (attr_key) | Giá trị | Ý nghĩa | Nguồn |
| --------------- | ------- | ------- | ----- |
edit_fee 5 Phí mỗi lần sửa, từ lượt thứ hai sau khi bài đã  C14b
duyệt
| renew_fee | 5   | Phí gia hạn thêm 30 ngày | C16 |
| --------- | --- | ------------------------ | --- |
renew_expired_window_days 7 Bài EXPIRED còn gia hạn được trong số ngày  C27
này
| boost_fee      | 5   | Phí một gói đẩy bài               | C15 |
| -------------- | --- | --------------------------------- | --- |
| boost_days     | 3   | Số ngày của một gói               | C15 |
| boost_max_days | 14  | Cộng dồn tối đa, tính từ hiện tại | C15 |
boost_max_per_page 3 Số bài được đẩy tối đa ở đầu mỗi trang C15
|     | 2   | Phí mỗi lượt AI trợ lý sau khi hết lượt miễn phí | C17 |
| --- | --- | ------------------------------------------------ | --- |
ai_search_fee
ai_free_per_day 5 Lượt miễn phí/ngày cho tài khoản đã từng nạp C17, C18
| ai_trial_uses | 1   | Lượt thử cho tài khoản chưa nạp | C18 |
| ------------- | --- | ------------------------------- | --- |
ai_search_timeout_seconds 15 Quá thời gian thì hoàn lượt, chạy tìm kiếm  BR-P11
thường
ai_daily_budget chưa đặt Trần ngân sách AI mỗi ngày C31 (chờ)
Kiểm duyệt
chat_ai_threshold 0.7 Độ tin cậy tối thiểu để chặn tin nhắn ở tầng 2 C21
|     | 5   | Timeout AI kiểm duyệt chat; lỗi thì chặn, không  | C21 |
| --- | --- | ------------------------------------------------ | --- |
chat_ai_timeout_seconds
tính vi phạm
| item_ai_timeout_seconds | 20  | Timeout AI sàng lọc bài            | C22 |
| ----------------------- | --- | ---------------------------------- | --- |
|                         | 1   | Số lần thử lại khi AI sàng lọc lỗi | C22 |
item_ai_retries
chat_violation_alert 3 Số vi phạm trong một giao dịch thì cảnh báo  BR-C04
Admin
| item_photos_min /  | 3 / 8 | Số ảnh mỗi bài | C23 |
| ------------------ | ----- | -------------- | --- |
item_photos_max
| image_max_mb | 5   | Dung lượng tối đa mỗi ảnh | C23 |
| ------------ | --- | ------------------------- | --- |
media_orphan_hours 24 Tệp UPLOADED chưa gắn vào đâu quá mốc này  C34
thì bị dọn
review_sla_hours 24 Bài chờ duyệt quá mốc này thì cảnh báo Admin C27
|     | 3   | Số report đang mở thì tự ẩn bài | C27 |
| --- | --- | ------------------------------- | --- |
report_hide_threshold
Tài khoản
| otp_ttl_minutes | 5   | Hạn của mã OTP email   | 5.2 SRS |
| --------------- | --- | ---------------------- | ------- |
| otp_max_fails   | 5   | Số lần nhập sai tối đa | 5.2 SRS |
otp_resend_seconds 60 Khoảng cách tối thiểu giữa hai lần gửi lại 5.2 SRS
|     | 24  | Tài khoản chưa xác thực bị xoá sau mốc này | C27 |
| --- | --- | ------------------------------------------ | --- |
unverified_account_hours
accounts_per_ip_24h 3 Số tài khoản mới mỗi IP trong 24 giờ C30
Trang 51 / 55

ShareLoop — Luồng hoạt động & ERD v10
| Khoá (attr_key) | Giá trị | Ý nghĩa |     | Nguồn |
| --------------- | ------- | ------- | --- | ----- |
phone_change_interval_days 30 Khoảng cách tối thiểu giữa hai lần đổi số C05
Giao dịch & uy tín
max_pending_requests 5 Số Request Pending đồng thời của một người C19
|     | 3   | Request Pending quá hạn thì tự huỷ |     | C27 |
| --- | --- | ---------------------------------- | --- | --- |
request_pending_days
chat_open_days 10 Từ Reserved chưa chốt lịch thì huỷ, đóng chat C27
auto_confirm_days 3 Một bên xác nhận, bên kia im lặng thì tự hoàn tất C27
| complaint_window_days | 7   | Thời hạn khiếu nại sau Completed |     | C27 |
| --------------------- | --- | -------------------------------- | --- | --- |
response_window_days 3 Thời hạn bên bị khiếu nại giải trình C27
|     | 30  | Trần điểm cộng mỗi ngày |     | C20 |
| --- | --- | ----------------------- | --- | --- |
daily_points_cap
payment_order_expire_minute 30 Đơn nạp PENDING quá hạn thì EXPIRED Job F04
s
7.11. Cột chuẩn của bảng chính (C34)
Giảng viên yêu cầu mỗi bảng chính có đủ bảy thuộc tính: ngày tạo, ngày sửa, người tạo, người sửa,
isActive, isDelete và status. Trong ShareLoop chúng mang tên created_at, updated_at, created_by,
updated_by, is_active, is_deleted, status. Yêu cầu chỉ áp dụng cho bảng chính, nên nhóm chia
17 bảng thành năm nhóm và chỉ thêm cột ở nơi cột đó có nghĩa. Số bảng giữ nguyên 17.
| Nhóm | Bảng | Cột chuẩn có | Vì sao |     |
| ---- | ---- | ------------ | ------ | --- |
A — Bảng chính users, items, requests,  Đủ 7 cột Thực thể nghiệp vụ có vòng
|     | reports, media_files |     | đời riêng. |     |
| --- | -------------------- | --- | ---------- | --- |
B — Danh mục, cấu  item_categories,  6 cột, không có status Admin quản lý, không có
| hình | item_attributes, areas,  |     | vòng đời; tắt bằng     |     |
| ---- | ------------------------ | --- | ---------------------- | --- |
|      | website_attributes       |     | is_active, xoá mềm để  |     |
bài cũ vẫn tham chiếu được.
C — Tài chính payment_orders,  created_at;  Dữ liệu tiền không được xoá
credit_ledger payment_orders thêm  hay tắt; sửa sai bằng một
|     |     | updated_at, có status;  | dòng ADMIN_ADJUST mới. |     |
| --- | --- | ----------------------- | ---------------------- | --- |
credit_ledger có
created_by
D — Sự kiện, nhật ký messages, notifications,  created_at; người tạo là  Chỉ ghi thêm, không sửa,
|     | activity_logs | sender_id, user_id,  | không xoá. |     |
| --- | ------------- | -------------------- | ---------- | --- |
actor_id
E — Mapping item_attribute_mappings,  Giữ nguyên Bảng nối theo mẫu giảng
|     | item_media_mappings,   |     | viên; sống và mất theo bản  |     |
| --- | ---------------------- | --- | --------------------------- | --- |
|     | request_media_mappings |     | ghi cha.                    |     |
Trang 52 / 55

ShareLoop — Luồng hoạt động & ERD v10
Ý nghĩa và ai ghi
Cột Kiểu Ý nghĩa Ai ghi
created_at TIMESTAMPTZ NN Thời điểm tạo bản ghi Database, JPA @CreatedDate
DEFAULT now()
updated_at TIMESTAMPTZ NN Lần sửa gần nhất JPA @LastModifiedDate;
DEFAULT now() trigger set_updated_at bắt cả
câu UPDATE viết tay
created_by BIGINT, không đặt FK Id người tạo; null khi tạo lúc chưa JPA @CreatedBy, lấy id từ
đăng nhập (tự đăng ký) hoặc do job JWT
updated_by BIGINT, không đặt FK Id người sửa gần nhất; lần sửa do JPA @LastModifiedBy
job không ghi đè (JPA Auditing bỏ
qua khi không có người dùng; job
ghi lý do vào activity_logs)
is_active BOOLEAN NN DEFAULT Công tắc tạm tắt / ẩn, không đổi Admin, hoặc luật nghiệp vụ (ẩn
true vòng đời bài khi đủ 3 report)
is_deleted BOOLEAN NN DEFAULT Xoá mềm: bản ghi không còn hiện Service gọi markDeleted();
false ở đâu, dữ liệu vẫn giữ để đối soát không dùng câu DELETE
status VARCHAR + CHECK Bước hiện tại trong vòng đời nghiệp Chỉ service sở hữu, luôn qua
vụ; mỗi bảng một enum riêng canTransition()
Ba cột trạng thái không được chồng nghĩa
• status trả lời "đang ở bước nào của nghiệp vụ"; is_active trả lời "đang bật hay bị tạm tắt";
is_deleted trả lời "còn hay đã xoá".
• Vì vậy bỏ giá trị LOCKED của users.status (dùng is_active), bỏ HIDDEN (dùng is_active) và
REMOVED (dùng is_deleted) của items.status. Bài bị ẩn rồi bật lại tự về đúng status cũ, không phải
nhớ trạng thái trước đó.
• Người dùng thường chỉ thấy bản ghi có status phù hợp, is_active = true và is_deleted = false.
Admin thấy cả bản ghi đang tắt; bản ghi đã xoá chỉ xem qua nhật ký.
Bảng is_active = false khi is_deleted = true khi
users Admin khoá tài khoản (FR-107) Tài khoản chưa xác thực quá 24 giờ bị job dọn
items Đủ 3 report, người đăng về 0 sao, hoặc Người đăng rút / gỡ bài, Admin gỡ, bài hết
Admin ẩn hạn quá lâu
media_files Admin gỡ tệp vi phạm (vẫn giữ làm bằng Người dùng xoá tệp; job dọn tệp chưa gắn
chứng) quá 24 giờ
reports Không dùng ở v10 (luôn true) Người báo cáo rút khi báo cáo còn OPEN
requests Không dùng ở v10 (luôn true) Không dùng: giao dịch không xoá, huỷ bằng
status CANCELLED
Trigger và ràng buộc trong V1__init.sql
CREATE OR REPLACE FUNCTION set_updated_at() RETURNS trigger
LANGUAGE plpgsql AS $$
Trang 53 / 55

ShareLoop — Luồng hoạt động & ERD v10
BEGIN
NEW.updated_at := now();
RETURN NEW;
END $$;
-- gắPn cho 10 baDng có updated_at: 9 baDng nhóm A, B và payment_orders
CREATE TRIGGER trg_items_updated_at BEFORE UPDATE ON items
FOR EACH ROW EXECUTE FUNCTION set_updated_at();
-- ràng buộc duy nhâtP chỉD tính baDn ghi chưa xoá mếYm (mục 7.7)
CREATE UNIQUE INDEX ux_users_email ON users(email) WHERE NOT is_deleted;
Trong mã nguồn
• Một lớp BaseEntity (@MappedSuperclass) chứa id và 6 cột chuẩn; bật @EnableJpaAuditing với
bean AuditorAware<Long> đọc id người dùng từ JWT (trả rỗng khi job chạy). Chín entity nhóm A,
B kế thừa lớp này; status khai báo trong từng entity với enum riêng.
• Mỗi entity nhóm A, B gắn @SQLRestriction("is_deleted = false"): mọi truy vấn JPA tự bỏ
bản ghi đã xoá. Câu SQL viết tay (native query) phải tự thêm điều kiện này.
• Không gọi repository.delete(); gọi entity.markDeleted() rồi lưu, để updated_by ghi đúng
người xoá.
• Cờ của bảng nào do service sở hữu bảng đó đổi: UserService.lock / unlock (Admin gọi),
ItemService.hide / unhide / remove, MediaService.attach / deactivate / remove,
ReportService.withdraw. Module khác gọi các hàm này, không tự gán cột.
• Entity lát cắt (WalletAccount, AiQuota, ReputationProfile, ItemFee, ItemAiFlag,
ItemReview) kế thừa lớp nhỏ SliceAudit chỉ map updated_at, updated_by, cũng gắn
@SQLRestriction. Đây là ngoại lệ duy nhất của luật "không map một cột ở hai entity".
Trang 54 / 55

ShareLoop — Luồng hoạt động & ERD v10
8. Gợi ý để code đơn giản
8.1. Ranh giới transaction
Thao tác Khoá gì Ghi gì trong cùng transaction
Gửi bài / gửi bản sửa có phí users của người đăng users.held_credit, items
Admin duyệt / từ chối users của người đăng, items users, items, credit_ledger,
activity_logs
IPN nạp tiền payment_orders, users payment_orders, users, credit_ledger
Đẩy bài, gia hạn, AI trả phí users users, items hoặc không, credit_ledger
Chọn người items (bài và món đề nghị) requests (nhiều dòng), items, messages
Xác nhận lịch, giao nhận requests requests, items, users (điểm)
8.2. Những chỗ nên làm đơn giản
• AI trợ lý: gọi LLM trước, chỉ trừ phí khi đã có kết quả — không cần luồng hoàn phí.
• Email và AI chạy sau commit (@TransactionalEventListener(phase = AFTER_COMMIT) +
@Async): transaction ngắn, lỗi bên ngoài không làm hỏng dữ liệu.
• Một `ConfigService` đọc website_attributes và cache trong bộ nhớ, làm mới khi Admin lưu.
• Một `ContentModerationService` cho cả bài đăng và tin nhắn; bộ test chung.
• Chat bằng polling ở bản đầu.
• Không dùng bảng trung gian cho thông báo đã đọc/chưa đọc theo nhóm: mỗi người một
dòng notifications.
• Báo cáo tài chính là truy vấn trên credit_ledger và payment_orders, không có bảng tổng
hợp.
Trang 55 / 55