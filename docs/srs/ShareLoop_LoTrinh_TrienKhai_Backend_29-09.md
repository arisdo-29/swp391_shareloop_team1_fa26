SHARELOOP · SWP391 · NHÓM 1
LỘ TRÌNH TRIỂN KHAI
Tuần 4 → tuần 9: từ các quyết định đã chốt tới khi code backend và các mốc tới khi
kết thúc
Việc cần làm · Cấu trúc thư mục · Công cụ · Kiến thức · Lưu ảnh
khi deploy
Dành cho: 4 thành viên Backend (Java 21 · Spring Boot 4.1.1) và 1 thành viên Frontend
Đi kèm: SRS ShareLoop v10 (cập nhật 29/09) và Luồng hoạt động & ERD v10 (ERD đã chốt, có cột
chuẩn)
Ngày: 29/09/2026 — đầu tuần 4 (cập nhật cột chuẩn, Spring Boot 4.1.1)

ShareLoop — Lộ trình tới khi code backend
MỤC LỤC
1. Mục đích của tài liệu...............................................................................................................................3
1.1. Thế nào là "sẵn sàng code"...........................................................................................................3
1.2. Phân vai trong nhóm......................................................................................................................3
2. Tổng quan các bước..............................................................................................................................5
3. Chi tiết từng bước..................................................................................................................................6
3.1. Bước 1 — Các quyết định đã chốt và việc tương ứng trong code................................................6
3.2. Bước 2 và 3 — Môi trường, công cụ và sơ đồ...............................................................................7
3.3. Bước 4 — Migration từ ERD đã chốt.............................................................................................7
3.4. Bước 5 — API contract..................................................................................................................9
3.5. Bước 6 — Dựng skeleton backend................................................................................................9
3.6. Bước 7 — Quy ước làm việc.......................................................................................................13
3.7. Bước 8 — Thử nghiệm kỹ thuật (spike).......................................................................................14
3.8. Bước 9 — Backlog theo tuần.......................................................................................................14
4. Cấu trúc thư mục và cách 4 người code song song............................................................................16
4.1. Năm nguyên tắc...........................................................................................................................16
4.2. Cấu trúc repo................................................................................................................................16
4.3. Cấu trúc thư mục backend...........................................................................................................16
4.4. Bên trong một module..................................................................................................................18
4.5. Module nào cần khi nào...............................................................................................................19
4.6. Module — người sở hữu — dữ liệu được ghi..............................................................................19
4.7. Gọi chéo module: service cửa ngõ...............................................................................................20
4.8. Bảng users và items: entity "lát cắt".............................................................................................21
4.9. Migration Flyway khi 4 người cùng sửa schema.........................................................................23
4.10. Thư mục resources và test.........................................................................................................23
4.11. CODEOWNERS và những file "nóng".......................................................................................23
5. Công cụ phát triển: IDE và trợ lý AI.....................................................................................................26
5.1. Cài đặt chung (cả 4 BE)...............................................................................................................26
5.2. Thiết lập từng IDE........................................................................................................................26
5.3. Giữ mã đồng nhất giữa các IDE...................................................................................................27
5.4. Trợ lý AI: một bộ hướng dẫn cho mọi công cụ.............................................................................27
6. Kiến thức cần học................................................................................................................................30
6.1. Phần cả nhóm backend đều phải nắm.........................................................................................30
6.2. Phần học thêm theo module........................................................................................................30
6.3. Cách học hiệu quả trong thời gian ngắn......................................................................................31
7. Những gì còn phải tìm hiểu..................................................................................................................32
8. Phương án lưu hình ảnh khi deploy (C12)...........................................................................................33
8.1. Ước lượng dung lượng................................................................................................................33
8.2. So sánh các phương án...............................................................................................................33
8.3. Thiết kế StorageService...............................................................................................................34
8.4. Các bước triển khai lưu ảnh.........................................................................................................35
9. Lịch làm việc từ tuần 4 đến tuần 9.......................................................................................................36
9.1. Tuần 4 theo từng ngày.................................................................................................................36
9.2. Các tuần tiếp theo........................................................................................................................36
10. Checklist sẵn sàng code....................................................................................................................38
11. Những lỗi hay gặp khi bắt đầu và cách tránh.....................................................................................39
Nguồn tham khảo.....................................................................................................................................40
Nếu mở bằng Microsoft Word mà mục lục chưa hiện số trang: bấm Ctrl+A rồi F9, chọn "Update entire table".
Trang 2 / 40

ShareLoop — Lộ trình tới khi code backend
1. Mục đích của tài liệu
SRS v10 trả lời câu hỏi "hệ thống làm gì", tài liệu Luồng hoạt động & ERD trả lời "dữ liệu nằm ở đâu".
Tài liệu này trả lời câu hỏi "4 bạn backend bắt đầu code thế nào, để trong thư mục nào, dùng
công cụ gì, học gì". Dự án đang ở đầu tuần 4 và kết thúc ở tuần 9. Sáng 28/9 nhóm đã chốt các
điểm nhóm 1 (chương 15 SRS) và chốt ERD 17 bảng, nên phần chuẩn bị còn lại gói trong 3 ngày
28/9 – 30/9; code tính năng từ thứ Năm 1/10.
So với bản 27/09, bản này thay đổi bốn điểm: backend dùng Java 21 + Spring Boot 4.1.1 (nhánh đang
được hỗ trợ, trùng môi trường bài thi thực hành môn Java); bảng chính có 7 cột chuẩn theo yêu cầu
giảng viên (C34); thêm chương 4 — cấu trúc thư mục để 4 người code song song ít đụng nhau; thêm
chương 5 — công cụ cho nhóm dùng nhiều IDE (IntelliJ IDEA, Antigravity, VS Code) và nhiều trợ lý AI
(Claude Code, Codex, Gemini…); nơi lưu ảnh khi deploy (C12) lùi tới khi web chạy ổn định.
1.1. Thế nào là "sẵn sàng code"
Mỗi điều kiện là đầu ra của một bước ở chương 3. Điều kiện 1–7 phải xong trước tối thứ Năm 1/10;
điều kiện 8 xong trước khi module cần tới (tuần 4–5).
1. Đã xong 28/9: các điểm nhóm 1 và ERD 17 bảng đã chốt, ghi vào biên bản docs/decisions/ và
SRS đã sửa theo.
2. Cả 4 bạn backend đã cài JDK 21, IDE và Docker theo chương 5; mở được project và chạy test
trên máy mình.
3. V1__init.sql (17 bảng) và các migration dữ liệu khởi tạo V2–V4 chạy sạch trên database trống.
4. Skeleton backend theo đúng cây thư mục ở chương 4: đủ package của 18 module,
CODEOWNERS, AGENTS.md; chạy bằng một lệnh; CI build, test và kiểm định dạng mã đều
xanh.
5. API contract cho các module của tuần 4–5 đã viết trong docs/api/; FE nối hoặc mock theo đúng
contract.
6. Các service "cửa ngõ" giữa module (mục 4.7) đã có chữ ký hàm và bản rỗng được merge, để
người gọi code trước mà không phải chờ.
7. Quy ước làm việc (nhánh, commit, PR, review, định dạng lỗi API, định dạng mã, dùng trợ lý AI) đã
ghi trong README.
8. Các thử nghiệm kỹ thuật rủi ro cao (thanh toán, email, khoá dòng ví, AI) đã chạy thử thành công ít
nhất một lần.
1.2. Phân vai trong nhóm
Vai GitHub Package phụ trách (chương 4) Nghiệp vụ
BE1 — nhóm arisdo-29 auth, user, wallet, listingfee; Đăng ký, OTP email, đăng nhập, hồ sơ,
trưởng lớp nền common, config; đổi số; ví, nạp VNPay; phí đăng bài (giữ /
integration/payment, trừ / nhả), phí sửa, gia hạn, đẩy bài; dựng
integration/mail skeleton.
BE2 Nguyentri2531 catalog, media, item, Danh mục, thuộc tính, khu vực; ảnh; bài
moderation, search; đăng; kiểm duyệt nội dung (lexicon, regex,
integration/ai, AI sàng lọc); tìm kiếm; AI trợ lý.
integration/storage
Trang 3 / 40

ShareLoop — Lộ trình tới khi code backend
Vai GitHub Package phụ trách (chương 4) Nghiệp vụ
BE3 dwargon73- request, chat, notification Request và state machine, chat (gọi bộ
sketch lọc của BE2), chốt lịch, trao liên lạc, giao
nhận, thông báo; quy ước job nền.
BE4 kopslngbtram2 setting, review, report, Cấu hình, lexicon, checklist; hàng chờ
110 reputation, admin, audit duyệt; khiếu nại; TrustStars, điểm, hạng;
khoá tài khoản, báo cáo tài chính; nhật ký.
FE My-Mieu frontend/ Màn hình theo screen flow, mock theo API
contract, nối API khi có.
Mỗi package có đúng một người chịu trách nhiệm
Người sở hữu package là người viết chính và là người duyệt mọi PR chạm vào package đó (tự động qua
CODEOWNERS, mục 4.11). Nhóm đổi phân công tuỳ ý, chỉ cần sửa bảng này và file CODEOWNERS cùng
lúc.
Trang 4 / 40

ShareLoop — Lộ trình tới khi code backend
2. Tổng quan các bước
| Bước Việc | Ai làm | Đầu ra | Thời gian |
| --------- | ------ | ------ | --------- |
1 Chốt quyết định nhóm 1 và ERD  Cả nhóm; BE1 chủ trì Biên bản, SRS cập nhật Xong 28/9
17 bảng
2 Cài môi trường và công cụ  Từng người Mở được project, chạy  T2 28/9
| (chương 5) |     | test |     |
| ---------- | --- | ---- | --- |
3 Cập nhật screen flow theo các  FE + BE1 Screen flow mới 28/9 – 2/10
mức phí đã chốt
4 Viết migration từ ERD đã chốt  BE1 (V1), BE4 (V2,  Migration chạy sạch T4 30/9
| (có cột chuẩn C34) | V4), BE2 (V3) |     |     |
| ------------------ | ------------- | --- | --- |
5 API contract phần tài khoản, bài  Mỗi BE phần module  Contract sprint tuần 4–5 28/9 – 30/9
| đăng, ví | mình; FE review |     |     |
| -------- | --------------- | --- | --- |
6 Dựng skeleton theo cây thư  BE1 (cặp cùng một BE  Repo chạy được, CI xanh 29/9 – 1/10
| mục chương 4 (Hướng dẫn  | khác) |     |     |
| ------------------------ | ----- | --- | --- |
BE1)
7 Thống nhất quy ước làm việc BE1 soạn, cả nhóm  Mục "Quy ước" trong  T3 29/9
|     | duyệt | README |     |
| --- | ----- | ------ | --- |
8 Thử nghiệm kỹ thuật (spike) Theo module, giới hạn  Đoạn mã chạy được + ghi  30/9 – 9/10, xen
|     | thời gian | chú | kẽ code |
| --- | --------- | --- | ------- |
9 Backlog sprint tuần 4, chia việc BE1 Issue trên GitHub Projects T4 30/9
→ Bắt đầu code tính năng 4 BE + FE Mốc tuần 4: đăng ký →  từ T5 1/10
đăng nhập → tạo bài
Các bước 2, 4, 5, 6, 7 chạy song song trong ba ngày đầu tuần. Việc học (chương 6) diễn ra xuyên
suốt, mỗi người học trước phần cần cho việc của tuần đó.
Trang 5 / 40

ShareLoop — Lộ trình tới khi code backend
3. Chi tiết từng bước
3.1. Bước 1 — Các quyết định đã chốt và việc tương ứng trong code
Buổi chốt sáng 28/9 đã khép gần hết chương 15 của SRS. Bảng dưới chuyển từng quyết định thành
việc cụ thể trong mã, để người phụ trách biết mình phải làm gì.
Mã Quyết định Việc trong code Ai
C01, C02 Mọi bài 5 Credit / 30 ngày; giữ khi gửi, PostFeeService.holdPostFee / BE1
trừ khi duyệt lần đầu chargeHeldFee / releaseHeldFee; khoá
post_fee
C14, Sửa: miễn phí khi bị chặn hoặc chưa GET /items/{id}/edit-quote; BE1 + BE2
C14b duyệt; 1 lần miễn phí sau duyệt; từ lần holdEditFee; khoá edit_fee = 5
sau 5 Credit/lần
C10 Bài đang hiển thị khi sửa thì ẩn tạm tới ItemService.submitEdit đưa bài về BE2
khi duyệt xong PENDING_REVIEW; Request Pending giữ
nguyên
C16 Gia hạn 5 Credit / 30 ngày, không RenewService; khoá renew_fee BE1
duyệt lại
C15 Đẩy bài 5 Credit / 3 ngày, cộng dồn ≤ BoostService; truy vấn tìm kiếm xếp bài đang BE1 + BE2
14 ngày, ≤ 3 bài/trang đẩy lên trước
C17, C18 AI trợ lý 2 Credit/lượt; 5 lượt miễn AiAssistantService kiểm hạn mức; khoá BE2
phí/ngày khi đã nạp; chưa nạp 1 lượt ai_search_fee = 2
thử
C04 Vẫn chặn liên hệ trong chat ChatService gọi BE3 + BE2
ContentModerationService trước khi lưu tin
C05 SĐT duy nhất trong tài khoản ACTIVE; Partial unique index trong V1; BE1
đổi số bằng OTP email, 1 lần/30 ngày UserService.changePhone
C08 AI sàng lọc bài chỉ gắn cờ ItemScreeningService chỉ ghi ai_risk, BE2
ai_result; không đổi status
C09 Danh mục theo SRS 6.17; cấm động Dữ liệu V3__seed_catalog.sql, cờ BE2
vật sống is_restricted
C11 VNPay sandbox; dự phòng cổng giả PaymentGatewayClient hai bản cài đặt: BE1
lập VnPayClient, MockPaymentClient
C06 PostgreSQL managed có gói miễn phí Chọn dịch vụ cụ thể ở spike S11, trước deploy BE4
hoặc cùng nhà cung cấp với nơi lưu thử tuần 7
ảnh
C33 ERD 17 bảng đã chốt V1__init.sql; thay đổi sau đó chỉ bằng BE1
migration mới (mục 4.9)
C34 Bảng chính có 7 cột chuẩn; bảng danh BaseEntity + JPA Auditing; BE1
mục 6 cột (ERD mục 7.11) @SQLRestriction xoá mềm; trigger
set_updated_at trong V1
C13 Không cần hỏi lại giảng viên Đóng —
Trang 6 / 40

ShareLoop — Lộ trình tới khi code backend
Còn mở
C07 nhà cung cấp LLM và C31 trần ngân sách AI: chốt tuần 5 theo spike S5 (BE2). C12 nơi lưu ảnh khi
deploy: chốt sau khi web chạy ổn định, trước deploy thử tuần 7 (BE2, chương 8). C26 thời điểm đối tác xem
chứng từ: chốt trước khi làm xác thực hàng chính hãng ở tuần 8 (BE4 + nhóm). Mọi con số nghiệp vụ nằm
trong website_attributes, nên đổi giá trị sau này không phải sửa mã.
3.2. Bước 2 và 3 — Môi trường, công cụ và sơ đồ
• Môi trường: mỗi người làm theo chương 5 trong buổi sáng 28/9. Người xong trước hỗ trợ người
sau; cuối ngày cả 4 người chạy được ./mvnw verify.
• Use case: bốn sơ đồ v10 đã có trong SRS chương 17. Nếu nhóm vẫn nộp file drawio thì sửa theo
đúng các hình đó.
• Screen flow: popup phí 5 Credit và chính sách hoàn phí khi đăng bài; trước khi sửa hiện loại lượt
(miễn phí / 5 Credit); màn hình gia hạn (5 Credit / 30 ngày); màn hình đẩy bài (5 Credit / 3 ngày);
trang AI trợ lý hiện lượt miễn phí còn lại và giá 2 Credit khi hết lượt; nút chia sẻ thẻ bài trong chat;
câu hỏi "đúng mô tả?" khi xác nhận nhận hàng; màn hình quản lý từ khoá cấm của Admin; bỏ
bước OTP số điện thoại khỏi đăng ký.
• FE đối chiếu các trang đã làm trong frontend/src/pages với screen flow mới, liệt kê trang cần
sửa. Ví dụ tiện ích aiMatching.ts thuộc tính năng AI ghép đôi đã bỏ từ v9.
3.3. Bước 4 — Migration từ ERD đã chốt
ERD không còn phải review: việc của bước này là chép đúng từ điển dữ liệu (chương 7 tài liệu ERD)
thành SQL. Bốn file khởi tạo, mỗi file một người:
File Nội dung Người viết
V1__init.sql Extension unaccent, pg_trgm; hàm f_unaccent; 17 bảng; BE1
ràng buộc và index ở mục 7.7 ERD
V2__seed_config.sql Toàn bộ giá trị cấu hình ở mục 7.10 ERD (attr_group = BE4
CONFIG)
V3__seed_catalog.sql Danh mục, thuộc tính động, khu vực (tỉnh → quận/huyện) BE2
V4__seed_lexicon_checklist.sq Từ khoá cấm khởi tạo, 10 mục checklist, lý do từ chối mẫu, BE4
l tên miền email dùng một lần
Quy ước khi viết (giữ nguyên với mọi migration sau này):
• Tên bảng và cột snake_case chữ thường, bảng số nhiều (users, items, credit_ledger).
• Khoá chính id BIGINT GENERATED ALWAYS AS IDENTITY; khoá ngoại đặt tên <ba>ng số @ít>_id.
• Cột chuẩn theo nhóm bảng (ERD mục 7.11): 5 bảng chính đủ created_at, updated_at,
created_by, updated_by, is_active, is_deleted, status; 4 bảng danh mục, cấu hình có 6 cột
(không status); bảng tài chính, nhật ký, mapping giữ gọn. created_by, updated_by không đặt
khoá ngoại.
• Không xoá cứng bản ghi nghiệp vụ; ràng buộc duy nhất chỉ tính bản ghi chưa xoá (WHERE NOT
is_deleted). Trigger set_updated_at gắn cho mọi bảng có updated_at.
• Tiền và Credit dùng BIGINT, có CHECK (credit_balance >= 0), CHECK (held_credit >= 0).
Trang 7 / 40

ShareLoop — Lộ trình tới khi code backend
• Enum lưu VARCHAR + CHECK (status IN (...)); dữ liệu linh hoạt (kết quả AI, bản chụp
checklist) dùng JSONB.
• Ràng buộc nghiệp vụ nào đưa được xuống DB thì đưa: SĐT duy nhất trong tài khoản ACTIVE
(partial index), một Request đang hoạt động cho mỗi cặp (item, receiver), payment_order_id duy
nhất trong sổ Credit.
-- V1__init.sql (phầnI đầuI)
CREATE EXTENSION IF NOT EXISTS unaccent;
CREATE EXTENSION IF NOT EXISTS pg_trgm;
-- unaccent() khống pha>i IMMUTABLE nên khống dùng trực tiêp@ trong index; bọc lại:
CREATE OR REPLACE FUNCTION f_unaccent(text) RETURNS text
LANGUAGE sql IMMUTABLE PARALLEL SAFE STRICT
AS $$ SELECT public.unaccent('public.unaccent', $1) $$;
CREATE OR REPLACE FUNCTION set_updated_at() RETURNS trigger
LANGUAGE plpgsql AS $$ BEGIN NEW.updated_at := now(); RETURN NEW; END $$;
CREATE TABLE users (
id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
email VARCHAR(255) NOT NULL,
phone VARCHAR(15) NOT NULL,
password_hash VARCHAR(100) NOT NULL,
credit_balance BIGINT NOT NULL DEFAULT 0 CHECK (credit_balance >= 0),
held_credit BIGINT NOT NULL DEFAULT 0 CHECK (held_credit >= 0),
status VARCHAR(20) NOT NULL DEFAULT 'PENDING_VERIFICATION'
CHECK (status IN ('PENDING_VERIFICATION','ACTIVE','BANNED')),
is_admin BOOLEAN NOT NULL DEFAULT FALSE,
-- ... các cột còn lại theo từ điê>n dữ liệu mục 7.3 ERD
-- 6 cột chuần> (ERD 7.11), cùng status >ở trên là đu> 7
created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
created_by BIGINT,
updated_by BIGINT,
is_active BOOLEAN NOT NULL DEFAULT TRUE,
is_deleted BOOLEAN NOT NULL DEFAULT FALSE
);
CREATE UNIQUE INDEX ux_users_email ON users(email) WHERE NOT is_deleted;
CREATE UNIQUE INDEX ux_users_phone_active ON users(phone)
WHERE status = 'ACTIVE' AND NOT is_deleted;
CREATE TRIGGER trg_users_updated_at BEFORE UPDATE ON users
FOR EACH ROW EXECUTE FUNCTION set_updated_at();
-- V2__seed_config.sql (ví dụ vài dòng)
INSERT INTO website_attributes
(attr_group, attr_key, attr_value, sort_order) VALUES
('CONFIG', 'post_fee', '5', 0), -- cột chuần> lầ@y giá trị mặc định
('CONFIG', 'edit_fee', '5', 0),
('CONFIG', 'renew_fee', '5', 0),
('CONFIG', 'ai_search_fee', '2', 0);
Trang 8 / 40

ShareLoop — Lộ trình tới khi code backend
3.4. Bước 5 — API contract
Contract là thứ FE và BE cùng dựa vào để làm song song. Viết trước khi code, mỗi module một file
markdown trong docs/api/ (ví dụ docs/api/item.md). Khi backend đã chạy, springdoc sinh Swagger
từ mã; nhóm đối chiếu hai bản để phát hiện chỗ lệch.
Quy ước chung
Nội dung Quy ước
Tiền tố /api/v1/...
Tên tài nguyên Danh từ số nhiều: /items, /requests/{id}; API của Admin: /api/v1/admin/...
Hành động nghiệp vụ Động từ con: POST /requests/{id}/select, POST /items/{id}/boost
Xác thực Header Authorization: Bearer <JWT>
Phân trang ?page=0&size=20&sort=createdAt,desc; trả { items, page, size,
totalElements }
Thời gian ISO-8601 UTC, ví dụ 2026-10-10T03:00:00Z; FE tự đổi sang giờ Việt Nam
Mã HTTP 200/201 thành công; 400 sai dữ liệu; 401 chưa đăng nhập; 403 không có quyền; 404
không thấy; 409 xung đột trạng thái (vd bài đã Reserved); 422 vi phạm business rule
Định dạng lỗi thống nhất
{
"code": "ITEM_CONTAINS_CONTACT",
"message": "Mố ta> có số@ điện thoại. Vui lòng xoá đê> tiêp@ tục.",
"field": "description",
"details": { "match": "09xx xxx xxx" },
"traceId": "a1b2c3"
}
code là hằng số trong enum mã lỗi của từng module (ItemErrorCode, WalletErrorCode…, tiền tố
theo module), FE dựa vào code để hiển thị, không dựa vào message. Danh sách mã lỗi là một phần
của contract.
Thứ tự viết contract
1. Auth (đăng ký, OTP, đăng nhập, đổi mật khẩu) — FE đã có trang, cần nối sớm nhất.
2. Danh mục, khu vực (đọc), tải ảnh và bài đăng (tạo, xem, tìm kiếm).
3. Ví, nạp tiền, phí đăng bài, báo giá sửa bài (edit-quote).
4. Request và chat.
5. Admin: hàng chờ duyệt, cấu hình, lexicon.
3.5. Bước 6 — Dựng skeleton backend
3.5.1. Tạo project
Dùng Spring Initializr (start.spring.io hoặc trình tạo project có sẵn trong IntelliJ / VS Code): Maven,
Java 21, Spring Boot 4.1.1, group com.shareloop, artifact backend, package com.shareloop,
packaging jar. Sau khi tạo, kiểm tra thẻ parent của pom.xml đúng 4.1.1 và commit ngay, để cả 4 người
build ra cùng một bộ thư viện:
Trang 9 / 40

ShareLoop — Lộ trình tới khi code backend
<parent>
<groupId>org.springframework.boot</groupId>
<artifactId>spring-boot-starter-parent</artifactId>
<version>4.1.1</version>
</parent>
<properties>
<java.version>21</java.version>
</properties>
Dependency Để làm gì
Spring Web REST API (Spring MVC) — artifact spring-boot-starter-webmvc (Spring Boot 4
đổi tên từ -web)
Spring Security Xác thực, phân quyền
OAuth2 Resource Server Tạo và kiểm JWT bằng JwtEncoder / JwtDecoder có sẵn — spring-boot-
starter-security-oauth2-resource-server (tên mới ở Spring Boot 4)
Spring Data JPA Truy cập PostgreSQL
PostgreSQL Driver JDBC driver
Flyway Migration Quản lý schema — spring-boot-starter-flyway + flyway-database-
postgresql (Spring Boot 4 bắt buộc có starter)
Validation @Valid, @NotBlank… cho DTO
Java Mail Sender Gửi OTP và thông báo
Spring Boot Actuator Endpoint /actuator/health cho deploy
Lombok Bớt code getter/constructor (IDE phải bật annotation processing)
Testcontainers Test trên PostgreSQL thật — spring-boot-testcontainers, Testcontainers 2.x:
testcontainers-postgresql, testcontainers-junit-jupiter
Thêm tay trong pom.xml springdoc-openapi-starter-webmvc-ui dòng 3.x (dòng dành cho Spring Boot
4) để có Swagger UI; test starter theo công nghệ: spring-boot-starter-webmvc-
test, spring-boot-starter-data-jpa-test; plugin Spotless (mục 5.3); AWS
SDK v2 S3 khi làm bản lưu ảnh S3
Vì sao Spring Boot 4.1.1, và lưu ý khi đọc hướng dẫn cũ
Nhánh 3.5 đã hết hỗ trợ miễn phí từ 30/06/2026; 4.1.1 (20/08/2026) là bản vá mới nhất của nhánh đang được
hỗ trợ, và bài thi thực hành môn Java cũng dùng bản này. Không đổi phiên bản khi IDE, Dependabot hay trợ
lý AI gợi ý; đổi bản Spring Boot phải cả nhóm đồng ý.
Phần lớn hướng dẫn trên mạng vẫn viết cho Spring Boot 3. Những chỗ khác khi làm với Spring Boot 4:
• Starter đổi tên, tách nhỏ: spring-boot-starter-webmvc thay -web; Flyway cần spring-boot-
starter-flyway; test có starter riêng cho từng công nghệ (-webmvc-test, -data-jpa-test). Không
dùng starter "classic" (chỉ để chuyển dự án cũ).
• Jackson 3: ObjectMapper/JsonMapper nằm ở package tools.jackson.databind; annotation như
@JsonProperty vẫn ở com.fasterxml.jackson.annotation.
• Test: dùng @MockitoBean; @MockBean đã bị gỡ khỏi Spring Boot 4.
• Testcontainers 2: artifact có tiền tố testcontainers- (vd testcontainers-postgresql); dùng lớp
org.testcontainers.postgresql.PostgreSQLContainer, lớp cũ trong
org.testcontainers.containers đã deprecated.
Trang 10 / 40

ShareLoop — Lộ trình tới khi code backend
• Spring Security 7: cấu hình bằng bean SecurityFilterChain; hướng dẫn cũ dùng
WebSecurityConfigurerAdapter không dùng được.
3.5.2. Cấu trúc thư mục
Skeleton tạo đủ cây thư mục của chương 4 ngay từ đầu, kể cả package chưa có mã (đặt một file
package-info.java ghi người sở hữu). Như vậy ngày đầu code không ai phải tạo package mới, và
mỗi người chỉ làm trong thư mục của mình.
3.5.3. Môi trường dev bằng Docker Compose
Mỗi người chỉ cần Docker Desktop và chạy docker compose up -d trong thư mục backend/ là có
PostgreSQL, hộp thư giả để xem email OTP, và MinIO để thử lưu ảnh kiểu S3.
# backend/docker-compose.yml
services:
db:
image: postgres:16
environment:
POSTGRES_DB: shareloop
POSTGRES_USER: shareloop
POSTGRES_PASSWORD: shareloop
ports: ["5432:5432"]
volumes: [pgdata:/var/lib/postgresql/data]
mail: # SMTP gia>: xem email tại http://localhost:8025
image: axllent/mailpit
ports: ["1025:1025", "8025:8025"]
minio: # tuỳ chọn, thử >StorageService kiêu> S3
image: minio/minio
command: server /data --console-address ":9001"
ports: ["9000:9000", "9001:9001"]
volumes:
pgdata:
# src/main/resources/application-dev.yml
spring:
datasource:
url: jdbc:postgresql://localhost:5432/shareloop
username: shareloop
password: shareloop
jpa:
open-in-view: false
hibernate.ddl-auto: validate # schema do Flyway qua>n lý
properties.hibernate.jdbc.time_zone: UTC
flyway:
locations: classpath:db/migration,classpath:db/dev-data
out-of-order: true # chỉ> >ở dev, xem mục 4.9
mail: { host: localhost, port: 1025 }
app:
jwt:
secret: ${JWT_SECRET:dev-only-secret-change-me-32-bytes-min}
ttl-minutes: 120
storage:
type: local # local | s3
ai:
enabled: false # bật khi đã có API key
Trang 11 / 40

ShareLoop — Lộ trình tới khi code backend
timeout-seconds: 20
Ba profile: dev (máy cá nhân), test (Testcontainers), prod (đọc mọi bí mật từ biến môi trường). Không
commit API key, mật khẩu SMTP, secret JWT; file .env nằm trong .gitignore, chỉ commit
.env.example.
3.5.4. Những lớp nền cần có ngay (BE1)
• PageResponse, ErrorResponse và định dạng lỗi ở mục 3.4; API thành công trả thẳng DTO, không
bọc.
• Interface ErrorCode + BusinessException + GlobalExceptionHandler
(@RestControllerAdvice) biến mọi lỗi thành JSON thống nhất. Mỗi module tự khai báo enum
<Module>ErrorCode implements ErrorCode.
• BaseEntity có id và 6 cột chuẩn (createdAt, updatedAt, createdBy, updatedBy, isActive,
isDeleted), điền bằng JPA Auditing với AuditorAware<Long> đọc id từ JWT; hàm
markDeleted(); lớp SliceAudit cho entity lát cắt (ERD mục 7.11). Bean Clock để test chỉnh
được thời gian cho job.
• SecurityConfig: mở /api/v1/auth/**, /actuator/health, Swagger; /api/v1/admin/** yêu
cầu quyền ADMIN; còn lại cần JWT. Nhờ quy ước URL này, thêm API mới không phải sửa
SecurityConfig.
• @CurrentUserId để controller lấy id người đang đăng nhập mà không đụng tới Spring Security.
• Một endpoint mẫu đủ các tầng (GET /api/v1/categories trong package catalog) kèm một unit
test và một integration test, để mọi người copy theo đúng khuôn.
• Các service "cửa ngõ" ở mục 4.7 với chữ ký hàm và thân rỗng, merge trong tuần 4.
3.5.5. CI trên GitHub Actions
# .github/workflows/backend.yml
# Build + kiem dinh dang + test backend. Chay tren moi PR de dung lam
# "required check"; PR khong dong vao backend/ thi bo qua cac buoc nang.
name: backend
on:
pull_request:
push:
branches: [develop, main]
jobs:
backend-build:
name: backend-build
runs-on: ubuntu-latest
steps:
- uses: actions/checkout@v4
with:
fetch-depth: 0
- name: PR co sua backend khong
id: changes
env:
EVENT: ${{ github.event_name }}
BASE: ${{ github.event.pull_request.base.sha }}
run: |
if [ "$EVENT" != "pull_request" ]; then
echo "run=true" >> "$GITHUB_OUTPUT"
elif git diff --name-only "$BASE...HEAD" \
| grep -qE '^(backend/|\.github/workflows/backend\.yml)'; then
echo "run=true" >> "$GITHUB_OUTPUT"
Trang 12 / 40

ShareLoop — Lộ trình tới khi code backend
else
echo "run=false" >> "$GITHUB_OUTPUT"
fi
- uses: actions/setup-java@v4
if: steps.changes.outputs.run == 'true'
with:
distribution: temurin
java-version: "21"
cache: maven
- name: Kiem tra dinh dang (Spotless)
if: steps.changes.outputs.run == 'true'
working-directory: backend
run: ./mvnw -B spotless:check
- name: Build va test (ca Testcontainers)
if: steps.changes.outputs.run == 'true'
working-directory: backend
run: ./mvnw -B verify
Workflow chạy trên mọi PR để dùng làm check bắt buộc; PR không đụng backend/ thì bỏ qua các
bước nặng và vẫn xanh, nên PR của FE không bị kẹt. Repo có thêm workflow no-ai-attribution
chặn dòng ghi công AI (Hướng dẫn BE1, bước B2).
Bật luật bảo vệ nhánh develop và main: bắt buộc PR, bắt buộc CI xanh, bắt buộc review của code
owner; mã trong wallet/, listingfee/ và request/ cần 2 người duyệt.
3.6. Bước 7 — Quy ước làm việc
Nội dung Quy ước
Nhánh main (bản nộp / demo), develop (tích hợp), feature/<module>-<mố-ta>> (vd
feature/wallet-topup-vnpay), fix/<module>-<mố-ta>>
Commit Dạng feat(item): thêm API tạo bài, fix(wallet): ..., test(request): ...,
docs: ...; phạm vi trong ngoặc là tên package
Pull request Nhỏ (dưới ~400 dòng), mỗi PR chủ yếu trong một module; dùng template trong .github/,
ghi rõ FR/BR liên quan
Review Code owner của package được hỏi tự động; mã chạm tiền, trạng thái, bảo mật cần 2
người
Định dạng mã Spotless quyết định, không phải IDE: chạy ./mvnw spotless:apply trước khi commit
(mục 5.3)
Test Mỗi business rule có ít nhất một test; tên test mô tả hành vi, vd
holdFee_whenBalanceInsufficient_throws
DTO Không trả entity ra API; request/response là record; kiểm tra đầu vào bằng annotation
Validation
Cấu hình Mọi con số nghiệp vụ (phí, hạn mức, mốc thời gian) đọc qua ConfigService từ
website_attributes, không viết cứng
Mã do AI sinh Được dùng theo mục 5.4; người mở PR phải hiểu và giải thích được từng dòng; commit
dưới tên người làm
Trang 13 / 40

ShareLoop — Lộ trình tới khi code backend
3.7. Bước 8 — Thử nghiệm kỹ thuật (spike)
Spike là đoạn mã nhỏ, làm nhanh, để chắc một kỹ thuật rủi ro chạy được trước khi đưa vào kế hoạch.
Mỗi spike giới hạn thời gian; hết giờ mà chưa xong thì báo nhóm để đổi hướng sớm. Mã spike để trong
nhánh spike/<tên>, không merge thẳng vào develop.
Spike Mục tiêu "xong" Ai Giới hạn
S1 — VNPay sandbox Tạo URL thanh toán, trả về, nhận IPN, kiểm chữ ký HMAC- BE1 1,5 ngày
SHA512 thành công ở localhost (dùng ngrok hoặc tương tự để
nhận IPN).
S2 — Email OTP Gửi OTP qua Mailpit ở dev và qua Gmail App Password thật một BE1 0,5 ngày
lần.
S3 — Khoá dòng ví Test 2 luồng đồng thời cùng giữ phí trên một tài khoản chỉ đủ tiền BE1 1 ngày
một lần: đúng một luồng thành công. Dùng entity lát cắt
WalletAccount (mục 4.8).
S4 — Chuẩn hoá Hàm chuẩn hoá và regex số điện thoại bắt được 20 mẫu lách BE2 1 ngày
tiếng Việt + regex luật, không bắt nhầm 10 mẫu hợp lệ.
S5 — Gọi LLM trả Gọi 2–3 nhà cung cấp với cùng 20 mẫu bài đăng, nhận JSON BE2 1,5 ngày
JSON đúng schema, đo thời gian và ước chi phí. Căn cứ chốt C07,
C31.
S6 — Tìm kiếm không Truy vấn "ao khoac" ra bài "Áo khoác" dùng f_unaccent + BE2 0,5 ngày
dấu pg_trgm, có dùng index.
S7 — Lưu ảnh Bản Local chạy thật (nén ảnh, xoá EXIF); bản S3 chạy qua cùng BE2 1 ngày
interface với MinIO. Thử với nơi lưu thật khi chốt C12, sau khi
web ổn định.
S8 — State machine Trạng thái Request bằng enum + bảng chuyển hợp lệ; chuyển sai BE3 1 ngày
thì ném lỗi 409; có test.
S9 — Chat Chọn polling hay WebSocket (STOMP); làm bản đơn giản nhất BE3 1 ngày
chạy được với FE.
S10 — Job nền @Scheduled chạy lặp 2 lần không gây tác dụng kép. BE3 0,5 ngày
S11 — Hosting Tạo thử database trên 1–2 dịch vụ managed theo nguyên tắc BE4 0,5 ngày
PostgreSQL C06, kiểm tra bật được unaccent, pg_trgm; ghi giới hạn gói
miễn phí.
Gợi ý cho S9 — chat
Với 6 tuần còn lại, bản đầu nên dùng polling (FE gọi API lấy tin mới mỗi 3–5 giây): đơn giản, dễ test, đủ cho
demo. WebSocket là nâng cấp khi còn thời gian. Bộ lọc kiểm duyệt nằm ở service nên đổi cách truyền tin sau
này không ảnh hưởng.
3.8. Bước 9 — Backlog theo tuần
• Mỗi FR mức Must của sprint tuần đó thành một issue trên GitHub Projects, ghi mã FR, BR liên
quan và tiêu chí nghiệm thu (lấy từ luồng thay thế của use case).
• Issue đủ nhỏ để làm trong 1–2 ngày; lớn hơn thì tách. Issue chạm hai module thì tách thành hai
issue, nối bằng service cửa ngõ (mục 4.7).
Trang 14 / 40

ShareLoop — Lộ trình tới khi code backend
• Gắn nhãn module (trùng tên package) và người phụ trách; cột Todo / In progress / Review / Done.
• Mỗi tuần là một sprint (thứ Hai lập kế hoạch, Chủ nhật demo nội bộ). Sprint tuần 4: đăng ký + OTP
+ đăng nhập; danh mục, khu vực; tải ảnh và tạo bài (chưa kiểm duyệt); gửi yêu cầu; cấu hình
trong website_attributes. Các sprint sau theo SRS chương 16.
Trang 15 / 40

ShareLoop — Lộ trình tới khi code backend
4. Cấu trúc thư mục và cách 4 người code song song
Mục tiêu của chương này: mỗi người làm trong thư mục của mình, gọi sang phần của người khác
qua một số ít hàm đã thống nhất trước, và những file dùng chung được giữ ở mức tối thiểu. Làm
đúng thì phần lớn PR chỉ chạm một thư mục, merge hằng ngày gần như không có xung đột.
4.1. Năm nguyên tắc
1. Chia theo module nghiệp vụ, không chia theo tầng. Không có thư mục controllers/ chung
cho cả dự án; mỗi module có controller, service, repository, entity, dto của riêng nó.
2. Một module — một người sở hữu (bảng mục 4.6). Người khác sửa được, nhưng PR phải qua
người sở hữu duyệt.
3. Gọi chéo module chỉ qua service công khai hoặc sự kiện. Không inject repository của module
khác, không đọc entity của module khác để sửa.
4. Giữa các module chỉ lưu id. Item lưu donorId kiểu Long, không @ManyToOne User. Quan hệ
JPA chỉ dùng bên trong một module (vd Item ↔ ItemMediaMapping).
5. Dùng chung càng ít càng tốt. common/ chỉ chứa thứ không thuộc nghiệp vụ nào; mọi thứ khác
nằm trong module. Khi phân vân, đặt vào module.
4.2. Cấu trúc repo
swp391_shareloop_team1_fa26/
├── .github/
│ ├── workflows/
│ │ ├── backend.yml # build + spotless:check + test khi backend/** đố>i
│ │ └── frontend.yml
│ ├── CODEOWNERS # ai duyệt thử mục nào (mục 4.11)
│ └── pull_request_template.md
├── backend/ # Spring Boot — 4 BE (mục 4.3)
├── frontend/ # React + Vite — FE
├── docs/
│ ├── srs/ # SRS, LuốnIg hoạt động & ERD, Lộ trình (docx/pdf)
│ ├── api/ # contract theo module: auth.md, item.md ...
│ └── decisions/ # biên ba>n chố@t: mã C, nội dung, ngày
├── AGENTS.md # hửớng dần‚ chung cho mọi trợ lý AI (mục 5.4)
├── CLAUDE.md # một dòng: @AGENTS.md
├── .gemini/settings.json # cho Gemini CLI đọc AGENTS.md
├── .editorconfig # thụt lêI, mã hoá, xuốn@g dòng cho mọi IDE
├── .gitattributes # xuốn@g dòng LF (quan trọng với Windows)
├── .gitignore
└── README.md # cách chạy + mục "Quy ửớc"
4.3. Cấu trúc thư mục backend
backend/
├── pom.xml # BE1 giữ; thêm dependency bằng PR riêng
├── mvnw, mvnw.cmd, .mvn/ # Maven Wrapper: mọi máy build cùng một ba>n Maven
├── docker-compose.yml # PostgreSQL 16, Mailpit, MinIO
├── .env.example # mầu‚ biê@n mối trửờng; .env thật khống commit
├── http/ # file .http gọi thử> API, mối‚ module một file
│ ├── auth.http item.http wallet.http request.http admin.http
└── src/
├── main/
Trang 16 / 40

ShareLoop — Lộ trình tới khi code backend
│ ├── java/com/shareloop/
│ │ ├── ShareLoopApplication.java
│ │ │
│ │ ├── common/ # nênI dùng chung, khống có nghiệp vụ (BE1)
│ │ ├── config/ # cầu@ hình Spring (BE1)
│ │ ├── integration/ # gọi hệ thố@ng bên ngoài (theo thử mục con)
│ │ │
│ │ ├── auth/ # đăng ký, OTP email, đăng nhập, JWT (BE1)
│ │ ├── user/ # hố Isở, đối> số @điện thoại (BE1)
│ │ ├── wallet/ # ví Credit, nạp tiêIn, số >Credit (BE1)
│ │ ├── listingfee/ # phí đăng bài, phí sửa>, gia hạn, đầy> bài (BE1)
│ │ │
│ │ ├── catalog/ # danh mục, thuộc tính động, khu vực (BE2)
│ │ ├── media/ # ta>i a>nh, media_files (BE2)
│ │ ├── item/ # bài đăng (BE2)
│ │ ├── moderation/ # lexicon, regex, AI sàng lọc / chat (BE2)
│ │ ├── search/ # tìm kiê@m, AI trợ lý tìm đố I (BE2)
│ │ │
│ │ ├── request/ # Request, state machine, lịch, giao nhận (BE3)
│ │ ├── chat/ # tin nhă@n, the> bài, đố>i món đê Inghị (BE3)
│ │ ├── notification/ # thống báo trong app + email giao dịch (BE3)
│ │ │
│ │ ├── setting/ # cầu@ hình, lexicon, checklist (BE4)
│ │ ├── review/ # hàng chờ duyệt, duyệt / từ chố@i (BE4)
│ │ ├── report/ # báo cáo, khiêu@ nại, tranh chầp@ (BE4)
│ │ ├── reputation/ # TrustStars, điê>m, hạng (BE4)
│ │ ├── admin/ # khoá tài khoa>n, báo cáo tài chính (BE4)
│ │ └── audit/ # activity_logs (BE4)
│ └── resources/ # mục 4.10
└── test/ # cùng cầy package với main (mục 4.10)
Ba thư mục nền được chia nhỏ như sau:
common/
├── api/ PageResponse, ErrorResponse
├── exception/ ErrorCode (interface), CommonErrorCode, BusinessException,
│ GlobalExceptionHandler
├── entity/ BaseEntity (id + 6 cột chuần>), SliceAudit
└── security/ CurrentUserId (annotation), CurrentUserArgumentResolver
config/
├── SecurityConfig.java # quy tăc@ URL theo quy ửớc, khống sử>a khi thêm API
├── JwtConfig.java # JwtEncoder / JwtDecoder
├── OpenApiConfig.java # Swagger
├── AsyncConfig.java # @Async cho AI và email sau commit
├── SchedulingConfig.java # bật @Scheduled
├── JpaAuditingConfig.java
└── ClockConfig.java # bean Clock, test chỉ>nh đửợc giờ
integration/
├── payment/ (BE1) PaymentGatewayClient, VnPayClient, MockPaymentClient
├── mail/ (BE1) EmailSender, SmtpEmailSender
├── ai/ (BE2) AiClient, <Provider>AiClient, AiProperties
└── storage/ (BE2) StorageService, LocalStorageService, S3StorageService
Trang 17 / 40

ShareLoop — Lộ trình tới khi code backend
4.4. Bên trong một module
Mọi module dùng cùng một khuôn. Thư mục nào module không cần thì không tạo. Ví dụ module item:
item/
├── controller/
│ ├── ItemController.java # /api/v1/items/**
│ └── MyItemController.java # /api/v1/me/items
├── service/
│ ├── ItemService.java # ghi: tạo, sửa>, đối> status (cửa> ngõ)
│ └── ItemQueryService.java # đọc: chi tiêt@, danh sách theo bộ lọc
├── repository/
│ ├── ItemRepository.java
│ ├── ItemMediaMappingRepository.java
│ └── ItemAttributeMappingRepository.java
├── entity/
│ ├── Item.java # entity chính cu>a ba>ng items
│ ├── ItemMediaMapping.java
│ ├── ItemAttributeMapping.java
│ ├── ItemStatus.java # enum + canTransition(from, to)
│ ├── OfferType.java
│ └── ItemCondition.java
├── dto/
│ ├── CreateItemRequest.java # record + @Valid
│ ├── UpdateItemRequest.java
│ ├── ItemResponse.java
│ └── ItemSummaryResponse.java
├── mapper/
│ └── ItemMapper.java # entity ↔ DTO, viêt@ tay
├── event/
│ └── ItemSubmittedEvent.java # moderation nghe sự kiện này đê >gọi AI
├── job/
│ └── ItemExpiryJob.java # APPROVED quá expire_at → EXPIRED
└── ItemErrorCode.java # ITEM_NOT_FOUND, ITEM_NOT_EDITABLE, ...
Loại Quy ước đặt tên Ví dụ
Controller <Tên>Controller; API của Admin tách riêng AdminCategoryController trong
Admin<Tên>Controller nhưng vẫn nằm trong catalog/, path
module sở hữu dữ liệu /api/v1/admin/categories
Service <Tên>Service; hàm public là API cho module PostFeeService.chargeHeldFee(ite
khác, hàm nội bộ để package-private mId)
DTO record, hậu tố Request / Response CreateItemRequest, WalletResponse
Enum trạng thái <Tên>Status kèm canTransition(from, to) RequestStatus, ItemStatus
Sự kiện Thì quá khứ, hậu tố Event; người nghe hậu tố ItemSubmittedEvent,
Listener ItemScreeningListener
Job Hậu tố Job, nằm trong module sở hữu dữ liệu PaymentOrderExpiryJob trong
wallet/job/
Mã lỗi Enum <Module>ErrorCode implements WALLET_INSUFFICIENT_BALANCE
ErrorCode, tiền tố tên module
Test Unit ...Test; test controller ...ControllerTest; CreditServiceIT
test có DB thật ...IT
Trang 18 / 40

ShareLoop — Lộ trình tới khi code backend
| Loại | Quy ước đặt tên |     | Ví dụ |     |
| ---- | --------------- | --- | ----- | --- |
Migration V<yyyyMMddHHmm>__<module>_<mố_ta>>.sql  V202610061530__item_add_view_ind
(mục 4.9)
ex.sql
4.5. Module nào cần khi nào
Không phải module nào cũng bắt đầu ngay. Thứ tự dưới đây bám lịch SRS chương 16, để mỗi tuần
mọi người đều có việc trong thư mục của mình:
| Tuần BE1 |     | BE2 | BE3 | BE4 |
| -------- | --- | --- | --- | --- |
4 common, config, auth,  catalog, media, item  request (enum, gửi yêu  setting, khung admin,
| user                  |     | (tạo bài)         | cầu)                   | audit  |
| --------------------- | --- | ----------------- | ---------------------- | ------ |
| 5 wallet,             |     | moderation tầng 1 | request (chọn người),  | review |
| integration/payment,  |     |                   | chat (polling)         |        |
listingfee (giữ / trừ /
nhả)
6 listingfee (sửa, gia  search, moderation  request (lịch, liên lạc,  report, reputation
| hạn) |     | tầng 2 (AI sàng lọc) | giao nhận),  |     |
| ---- | --- | -------------------- | ------------ | --- |
notification, job
7 listingfee (đẩy bài),  search (AI trợ lý), AI  chat (thẻ bài, đổi món) admin (báo cáo tài
| deploy |     | chat tầng 2 |     | chính, khoá tài khoản) |
| ------ | --- | ----------- | --- | ---------------------- |
4.6. Module — người sở hữu — dữ liệu được ghi
Bảng này khớp với mục 2 của tài liệu Luồng hoạt động & ERD. Cột "Dữ liệu được ghi" là quy tắc một
nhóm cột — một module ghi: module khác muốn đổi các cột đó phải gọi service của module sở hữu.
| Package | Ngườ | Service chính | Dữ liệu được ghi |     |
| ------- | ---- | ------------- | ---------------- | --- |
i
auth, user BE1 AuthService, OtpService,  users: mật khẩu, trạng thái, otp_*, hồ sơ,
|     |     | JwtService, UserService | phone, pending_phone |     |
| --- | --- | ----------------------- | -------------------- | --- |
wallet BE1 CreditService, PaymentService payment_orders, credit_ledger; users:
credit_balance, held_credit,
has_topped_up
listingfee BE1 PostFeeService, RenewService,  items: post_fee_paid, pending_fee*,
|     |     | BoostService | fee_state, free_edit_used,  |     |
| --- | --- | ------------ | --------------------------- | --- |
approved_edit_count, expire_at,
boosted_until
catalog, media BE2 CategoryService, AreaService,  item_categories, item_attributes,
|     |     | MediaService | areas, media_files |     |
| --- | --- | ------------ | ------------------ | --- |
item BE2 ItemService, ItemQueryService items: nội dung, status;
item_media_mappings,
item_attribute_mappings
Trang 19 / 40

ShareLoop — Lộ trình tới khi code backend
| Package | Ngườ | Service chính | Dữ liệu được ghi |     |
| ------- | ---- | ------------- | ---------------- | --- |
i
moderation,  BE2 ContentModerationService,  items: ai_risk, ai_result; users:
| search |     | ItemScreeningService,  | free_ai_used, free_ai_date |     |
| ------ | --- | ---------------------- | -------------------------- | --- |
SearchService,
AiAssistantService
request, chat,  BE3 RequestService,  requests, request_media_mappings,
| notification |     | LogisticsService,  | messages, notifications |     |
| ------------ | --- | ------------------ | ----------------------- | --- |
HandoverService, ChatService,
NotificationService
setting, review BE4 ConfigService, LexiconService,  website_attributes; items:
|     |     | ChecklistService, ReviewService | review_snapshot, reject_reason,  |     |
| --- | --- | ------------------------------- | -------------------------------- | --- |
approved_at, auth_status
| report,  | BE4 | ReportService,  | reports, activity_logs; users:  |     |
| -------- | --- | --------------- | ------------------------------- | --- |
reputation, admin,  ReputationService,  trust_stars, total_points,
audit AdminUserService,  rank_level, points_*, clean_streak,
|     |     | FinanceReportService,  | grace_star_used |     |
| --- | --- | ---------------------- | --------------- | --- |
ActivityLogService
4.7. Gọi chéo module: service cửa ngõ
Được làm Không được làm
Inject và gọi hàm public của <Tên>Service module  Inject repository của module khác
khác
Nghe sự kiện của module khác  Sửa trực tiếp entity lấy từ module khác
(@TransactionalEventListener) để làm việc sau
commit
Dùng enum và DTO công khai của module khác  @ManyToOne / @OneToMany sang entity của module khác
(ItemStatus, ModerationResult)
Lưu id của bản ghi module khác (Long donorId) Đặt class nghiệp vụ vào common/ cho "tiện dùng chung"
Những hàm dưới đây là điểm nối giữa người này với người kia. Người sở hữu viết chữ ký hàm và
thân rỗng (ném UnsupportedOperationException hoặc trả giá trị giả), merge ngay trong tuần 4.
Người gọi code tiếp được mà không phải chờ; người sở hữu làm phần thân sau, không ai sửa file của
ai.
| Service cửa ngõ |     | Chủ Hàm chính |     | Ai gọi |
| --------------- | --- | ------------- | --- | ------ |
wallet.CreditService BE1 hold, release, chargeHeld, spend(userId,  listingfee, search
amount, type, itemId), topUp
listingfee.PostFeeService BE1 quoteEdit, holdPostFee, holdEditFee,  item, review
chargeHeldFee, releaseHeldFee
|     |     | BE2 markApproved, markRejected, reserve,  |     | review, request,  |
| --- | --- | ----------------------------------------- | --- | ----------------- |
item.ItemService
|     |     | release, markTraded, hide, unhide, remove,  |     | reputation, report,  |
| --- | --- | ------------------------------------------- | --- | -------------------- |
|     |     | hideAllOfUser                               |     | admin                |
Trang 20 / 40

ShareLoop — Lộ trình tới khi code backend
Service cửa ngõ Chủ Hàm chính Ai gọi
media.MediaService BE2 attach(mediaIds, userId) (UPLOADED → item, chat,
ATTACHED, kiểm người tải), deactivate, request, user,
remove admin
user.UserService BE1 lock, unlock (đổi is_active), findContact admin, reputation,
request
moderation.ContentModeratio BE2 checkPost(...), checkMessage(...) → item, chat, request
nService ModerationResult
setting.ConfigService BE4 getInt(ConfigKey), getDecimal(...), mọi module
lexicon(), checklist()
audit.ActivityLogService BE4 log(type, actorId, targetType, mọi module
targetId, data, reason)
notification.NotificationSe BE3 notify(userId, type, title, body, mọi module
rvice link), email(...)
reputation.ReputationServic BE4 onCompleted(requestId), deductStar(...), request, report
e grantGraceStar(...)
Ví dụ một thao tác đi qua ba module mà mỗi người chỉ sửa file của mình:
// review/service/ReviewService.java — BE4
@Service
@RequiredArgsConstructor
public class ReviewService {
private final ItemService itemService; // item (BE2)
private final PostFeeService postFeeService; // listingfee (BE1)
private final ItemReviewRepository reviews; // lát căt@ cu>a chính review
private final ActivityLogService activityLog; // audit (BE4)
@Transactional
public void approve(long itemId, long adminId, ReviewDecisionRequest req) {
postFeeService.chargeHeldFee(itemId); // trừ phí đang giữ
itemService.markApproved(itemId); // đối> status qua chu> s>ở hữu
reviews.lockById(itemId).orElseThrow()
.recordDecision(req.answers(), null); // review_snapshot
activityLog.log(REVIEW, adminId, ITEM, itemId, req.answers(), req.note());
}
}
4.8. Bảng users và items: entity "lát cắt"
Hai bảng rộng users và items được nhiều module ghi (mỗi module một nhóm cột). Nếu cả nhóm dùng
chung một file User.java thì ai cũng sửa file đó và xung đột liên tục. Cách làm: module sở hữu nhóm
cột khai báo một entity nhỏ chỉ map id và các cột của mình, trỏ vào cùng bảng.
// wallet/entity/WalletAccount.java — BE1: chỉ> các cột tiênI cu>a ba>ng users
@Entity
@Table(name = "users")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@SQLRestriction("is_deleted = false")
Trang 21 / 40

ShareLoop — Lộ trình tới khi code backend
public class WalletAccount extends SliceAudit { // map updated_at, updated_by
@Id private Long id;
@Column(name = "credit_balance") private long creditBalance;
@Column(name = "held_credit") private long heldCredit;
@Column(name = "has_topped_up") private boolean hasToppedUp;
public long available() { return creditBalance - heldCredit; }
void hold(long amount) { // package-private: chỉ> CreditService gọi
if (available() < amount)
throw new BusinessException(WalletErrorCode.WALLET_INSUFFICIENT_BALANCE);
heldCredit += amount;
}
}
// wallet/repository/WalletAccountRepository.java
public interface WalletAccountRepository extends JpaRepository<WalletAccount, Long> {
@Lock(LockModeType.PESSIMISTIC_WRITE) // SELECT ... FOR UPDATE
@Query("select w from WalletAccount w where w.id = :id")
Optional<WalletAccount> lockById(@Param("id") Long id);
}
Entity Package Bảng Cột map (ngoài id)
User (entity chính, tạo bản user users email, mật khẩu, hồ sơ, phone, status, otp_*; kế thừa
ghi) BaseEntity (6 cột chuẩn)
WalletAccount wallet users credit_balance, held_credit, has_topped_up
AiQuota search users free_ai_used, free_ai_date
ReputationProfile reputation users trust_stars, total_points, rank_level,
points_today, points_date, clean_streak,
grace_star_used
Item (entity chính, tạo bản item items nội dung, status, donor_id, category_id, area_id,
ghi) view_count, thời điểm
ItemFee listingfee items post_fee_paid, pending_fee_type, pending_fee,
fee_state, free_edit_used, approved_edit_count,
expire_at, boosted_until
ItemAiFlag moderation items ai_risk, ai_result
ItemReview review items review_snapshot, reject_reason, approved_at,
auth_status
Ba luật khi dùng entity lát cắt
• Chỉ entity chính (User, Item) được tạo bản ghi; lát cắt chỉ đọc và sửa.
• Không map một cột ở hai entity (trừ id, và updated_at, updated_by mà mọi lát cắt cùng map qua
SliceAudit để ghi đúng người sửa). Hibernate kiểm tra từng entity với schema khi khởi động (ddl-
auto: validate), nên map sai tên cột sẽ báo lỗi ngay.
• Cần khoá dòng thì khoá qua lát cắt của mình (lockById), trong transaction của service sở hữu. Tiền
luôn khoá qua WalletAccount.
Trang 22 / 40

ShareLoop — Lộ trình tới khi code backend
4.9. Migration Flyway khi 4 người cùng sửa schema
• Bốn file khởi tạo V1–V4 như mục 3.3. Sau đó mọi file mới đặt version theo thời điểm tạo:
V<yyyyMMddHHmm>__<module>_<mố_ta>>.sql, ví dụ
V202610061530__item_add_view_index.sql. Hai người gần như không bao giờ trùng số, và số
này luôn lớn hơn 1–4.
• Ở profile dev bật spring.flyway.out-of-order: true: nếu file của bạn có số nhỏ hơn file
người khác đã chạy trên máy bạn (do merge sau), Flyway vẫn chạy nó. Profile prod để mặc định
false.
• Không sửa file migration đã merge vào `develop`. Viết sai thì tạo file mới để sửa. Nếu máy
mình lỡ chạy bản nháp, xoá database dev (docker compose down -v) rồi chạy lại.
• Dữ liệu mẫu cho dev (tài khoản thử, bài mẫu) để trong db/dev-data/ dạng file lặp lại được
R__dev_seed.sql; chỉ nạp ở profile dev, không lên production.
• Mọi file trong db/migration/ cần BE1 duyệt (CODEOWNERS), vì schema đã chốt: file nào làm
lệch ERD thì phải ghi thêm vào mục 7.8 tài liệu ERD.
4.10. Thư mục resources và test
src/main/resources/
├── application.yml # chung; mố‚i module một khối@ app.<module>
├── application-dev.yml
├── application-prod.yml # chỉ> ${BIEN_MOI_TRUONG}, khống chứa bí mật
├── db/
│ ├── migration/ # V1__init.sql ... V4__..., V<thời gian>__...
│ └── dev-data/ # R__dev_seed.sql — chỉ> profile dev
├── templates/mail/ # otp.html, contact-revealed.html, ... (BE1, BE3)
└── prompts/ # prompt LLM (BE2), khống viêt@ trong mã:
# item-screening.txt, chat-moderation.txt ...
src/test/
├── java/com/shareloop/
│ ├── support/
│ │ ├── IntegrationTest.java # @SpringBootTest + container PostgreSQL
│ │ └── TestDataFactory.java # tạo nhanh user, item, request cho test
│ ├── architecture/
│ │ └── ModuleBoundaryTest.java # (tuỳ chọn) ArchUnit kiê>m ranh giới
│ ├── wallet/CreditServiceIT.java
│ ├── request/RequestStatusTest.java
│ └── ... # cùng package với mã đửợc test
└── resources/application-test.yml
application.yml là file dùng chung duy nhất mà ai cũng cần thêm cấu hình. Quy ước: mỗi module
một khối riêng (app.wallet.*, app.ai.*, app.storage.*), người sở hữu module chỉ sửa trong khối
của mình, thêm dòng ở cuối khối; như vậy Git tự gộp được hầu hết trường hợp.
4.11. CODEOWNERS và những file "nóng"
Mỗi vùng ghi hai người: người đầu là chủ, người sau là dự phòng (BE1 ↔ BE3, BE2 ↔ BE4). Khi bật
"bắt buộc review của code owner", tác giả PR không tự duyệt được PR của mình, nên vùng chỉ có một
người sẽ kẹt PR của chính người đó.
# .github/CODEOWNERS
# CODEOWNERS - nguoi duoc hoi review tu dong theo duong dan.
# Dong SAU thang dong TRUOC. Moi vung ghi 2 nguoi vi tac gia PR khong tu duyet
Trang 23 / 40

ShareLoop — Lộ trình tới khi code backend
# duoc PR cua minh: nguoi dau la chu, nguoi sau la du phong.
# Cap du phong: BE1 <-> BE3, BE2 <-> BE4.
* @arisdo-29 @dwargon73-sketch
/frontend/ @My-Mieu @arisdo-29
# backend chung (docker-compose, http/, test/support...): ca 4 BE
/backend/ @arisdo-29 @Nguyentri2531 @dwargon73-sketch @kopslngbtram2110
# ----- BE1: nen va file dung chung -----
/backend/pom.xml @arisdo-29 @dwargon73-sketch
/backend/**/shareloop/common/ @arisdo-29 @dwargon73-sketch
/backend/**/shareloop/config/ @arisdo-29 @dwargon73-sketch
/backend/src/main/resources/db/ @arisdo-29 @dwargon73-sketch
# ----- BE1: nghiep vu -----
/backend/**/shareloop/auth/ @arisdo-29 @dwargon73-sketch
/backend/**/shareloop/user/ @arisdo-29 @dwargon73-sketch
/backend/**/shareloop/wallet/ @arisdo-29 @dwargon73-sketch
/backend/**/shareloop/listingfee/ @arisdo-29 @dwargon73-sketch
/backend/**/integration/payment/ @arisdo-29 @dwargon73-sketch
/backend/**/integration/mail/ @arisdo-29 @dwargon73-sketch
# ----- BE2 -----
/backend/**/shareloop/catalog/ @Nguyentri2531 @kopslngbtram2110
/backend/**/shareloop/media/ @Nguyentri2531 @kopslngbtram2110
/backend/**/shareloop/item/ @Nguyentri2531 @kopslngbtram2110
/backend/**/shareloop/moderation/ @Nguyentri2531 @kopslngbtram2110
/backend/**/shareloop/search/ @Nguyentri2531 @kopslngbtram2110
/backend/**/integration/ai/ @Nguyentri2531 @kopslngbtram2110
/backend/**/integration/storage/ @Nguyentri2531 @kopslngbtram2110
/backend/src/main/resources/prompts/ @Nguyentri2531 @kopslngbtram2110
# ----- BE3 -----
/backend/**/shareloop/request/ @dwargon73-sketch @arisdo-29
/backend/**/shareloop/chat/ @dwargon73-sketch @arisdo-29
/backend/**/shareloop/notification/ @dwargon73-sketch @arisdo-29
# ----- BE4 -----
/backend/**/shareloop/setting/ @kopslngbtram2110 @Nguyentri2531
/backend/**/shareloop/review/ @kopslngbtram2110 @Nguyentri2531
/backend/**/shareloop/report/ @kopslngbtram2110 @Nguyentri2531
/backend/**/shareloop/reputation/ @kopslngbtram2110 @Nguyentri2531
/backend/**/shareloop/admin/ @kopslngbtram2110 @Nguyentri2531
/backend/**/shareloop/audit/ @kopslngbtram2110 @Nguyentri2531
File dùng chung Vì sao dễ xung đột Cách làm
pom.xml Ai cũng muốn thêm thư viện BE1 giữ. Thêm dependency bằng một PR riêng nhỏ, merge
trong ngày; phiên bản khai báo trong <properties>
application.yml Cấu hình của mọi module Mỗi module một khối app.<module>.*; bí mật để ${BIEN}
SecurityConfig Phân quyền URL Quy ước URL cố định (mục 3.5.4) nên không cần sửa;
quyền chi tiết dùng @PreAuthorize trong controller của
module
Mã lỗi Một enum chung ai cũng Mỗi module một enum <Module>ErrorCode
thêm dòng
ConfigKey Khoá cấu hình của mọi BE4 tạo một lần đủ các khoá ở mục 7.10 ERD; thêm khoá
module mới = một PR nhỏ kèm migration
Migration Trùng số version Version theo thời gian (mục 4.9)
Trang 24 / 40

ShareLoop — Lộ trình tới khi code backend
File dùng chung Vì sao dễ xung đột Cách làm
common/, config/ Lớp nền BE1 viết xong trong tuần 4, sau đó hầu như không sửa; cần
thêm thì mở issue trước
docs/api/ Contract Mỗi module một file
Một ngày làm việc điển hình của một bạn BE
• Sáng: git switch develop && git pull, rồi git switch -c feature/<module>-<việc>.
• Làm trong thư mục module của mình; cần gì của người khác thì gọi service cửa ngõ, thiếu hàm thì nhắn
người sở hữu thêm chữ ký hàm.
• Trước khi đẩy: ./mvnw spotless:apply rồi ./mvnw verify.
• Mở PR nhỏ vào develop; code owner được hỏi review tự động. Merge trong ngày, không để PR sống
qua 2 ngày.
Trang 25 / 40

ShareLoop — Lộ trình tới khi code backend
5. Công cụ phát triển: IDE và trợ lý AI
Nhóm được tự chọn IDE (IntelliJ IDEA, Antigravity, VS Code) và trợ lý AI (Claude Code, Codex,
Gemini…). Tự do chọn công cụ chỉ an toàn khi những thứ quyết định kết quả nằm trong repo chứ
không nằm trong IDE: phiên bản Java và Maven, định dạng mã, cách xuống dòng, và hướng dẫn cho
trợ lý AI.
5.1. Cài đặt chung (cả 4 BE)
Công cụ Phiên bản / cách cài Ghi chú
JDK Temurin 21 (LTS) Cả nhóm cùng một bản. IntelliJ tải được JDK ngay
trong IDE; VS Code và Antigravity đọc JAVA_HOME
hoặc cấu hình java.jdt.ls.java.home
Maven Không cài riêng; dùng ./mvnw Maven Wrapper khoá phiên bản Maven trong repo
(Windows: mvnw.cmd)
Docker Desktop Bản mới nhất Chạy PostgreSQL, Mailpit, MinIO bằng docker
compose up -d; Testcontainers cũng cần Docker
Git Bản mới nhất Không cần chỉnh core.autocrlf khi repo đã có
.gitattributes
Gọi thử API File .http trong backend/http/ Chạy được trong IntelliJ (HTTP Client) và VS Code /
Antigravity (extension REST Client); hoặc dùng
Postman
Xem database DBeaver, pgAdmin hoặc công cụ Kết nối localhost:5432, user/pass shareloop
Database của IntelliJ
5.2. Thiết lập từng IDE
IDE Cài thêm Lưu ý
IntelliJ IDEA Plugin Lombok (thường có sẵn); bật *Annotation Mở thư mục backend/ như project Maven.
Processing*; plugin Spotless hoặc chạy lệnh Maven Tắt "Optimize imports on the fly" nếu nó làm
đổi thứ tự import khác Spotless
VS Code *Extension Pack for Java*, *Spring Boot Extension Formatter mặc định của VS Code khác
Pack*, *Lombok Annotations Support* (nếu bản IntelliJ; không dựa vào "Format Document",
Java extension chưa gộp sẵn), *REST Client*, dùng Spotless
*Docker*
Antigravity Các extension Java như VS Code (cài từ kho Nền là VS Code nên thiết lập giống VS
extension của Antigravity); nếu thiếu extension nào Code. Agent của Antigravity đọc
thì chạy / debug bằng VS Code hoặc IntelliJ AGENTS.md ở gốc repo (mục 5.4)
Không commit thư mục cấu hình riêng của IDE (.idea/, *.iml, .vscode/ — trừ
.vscode/extensions.json để gợi ý extension cho người mới). Thêm các mục này vào .gitignore
ngay trong skeleton.
Trang 26 / 40

ShareLoop — Lộ trình tới khi code backend
5.3. Giữ mã đồng nhất giữa các IDE
Ba IDE định dạng mã khác nhau (thụt lề, xuống dòng, thứ tự import). Nếu không thống nhất, mỗi lần
một người mở file của người khác và lưu lại là cả file bị đổi, PR phình to và xung đột giả. Ba lớp bảo
vệ:
1. .editorconfig ở gốc repo: UTF-8, xuống dòng LF, Java thụt 4 dấu cách, YAML/JSON/TS thụt 2.
Cả ba IDE đều đọc file này.
2. .gitattributes: * text=auto eol=lf, riêng *.cmd và *.bat giữ CRLF, ảnh và .jar đánh dấu
binary. Quan trọng với máy Windows: không có file này, Git có thể đổi toàn bộ file sang CRLF và
mvnw không chạy được trên CI.
3. Spotless trong pom.xml là "trọng tài" duy nhất: ./mvnw spotless:apply định dạng lại mã, CI
chạy spotless:check. IDE nào cũng được, miễn là trước khi commit đã chạy lệnh này.
<!-- pom.xml, trong <build><plugins> -->
<plugin>
<groupId>com.diffplug.spotless</groupId>
<artifactId>spotless-maven-plugin</artifactId>
<version>${spotless.version}</version> <!-- khai báo trong <properties> -->
<configuration>
<java>
<palantirJavaFormat/> <!-- 4 dầu@ cách, dòng 120 ký tự -->
<removeUnusedImports/>
<importOrder/>
</java>
</configuration>
</plugin>
# .gitattributes
* text=auto eol=lf
*.cmd text eol=crlf
*.bat text eol=crlf
*.png binary
*.jpg binary
*.jar binary
5.4. Trợ lý AI: một bộ hướng dẫn cho mọi công cụ
Mỗi trợ lý AI đọc một file hướng dẫn khác nhau. Nhóm viết một file `AGENTS.md` duy nhất ở gốc
repo và trỏ các công cụ còn lại về nó, để Claude Code, Codex, Gemini và Antigravity cùng tuân theo
một cấu trúc thư mục và một bộ luật.
Công cụ File hướng dẫn nó đọc Cách trỏ về AGENTS.md
Codex (CLI, extension IDE) AGENTS.md Đọc trực tiếp, không cần làm gì
Antigravity AGENTS.md, GEMINI.md, Đọc trực tiếp AGENTS.md
.agents/rules/*.md
Claude Code CLAUDE.md Tạo CLAUDE.md chỉ có một dòng
@AGENTS.md (cú pháp import của Claude
Code)
Gemini CLI / Gemini Code GEMINI.md (mặc định) Tạo .gemini/settings.json:
Assist { "context": { "fileName":
["AGENTS.md", "GEMINI.md"] } }
Trang 27 / 40

ShareLoop — Lộ trình tới khi code backend
Nội dung gợi ý cho AGENTS.md (ngắn, chỉ ghi điều AI hay làm sai):
# ShareLoop — hửớng dầ‚n cho trợ lý AI
Tra> lời và gia>i thích bằng tiê@ng Việt. Tên class, biê@n, commit message viêt@ tiê@ng Anh.
## Git, commit và ghi cống (luật cứng)
- KHÔNG chạy git commit, git push, git merge, git rebase, gh pr create/merge.
Ngửời làm tự xem diff, tự chạy test và tự commit dửới tên mình.
- KHÔNG ghi cống AI >ở bầt@ kỳ đầu: khống Co-Authored-By, khống "Generated with/by",
khống tên cống cụ AI trong commit message, mố ta> PR, comment, @author,
README hay tài liệu.
- Khi đửợc nhờ soạn commit message hoặc mố ta> PR: chỉ> viêt@ nội dung
dạng type(scope): mố ta>, khống thêm dòng ký tên nào.
## Dự án
- Monorepo: backend/ (Java 21, Spring Boot 4.1.1, Maven, PostgreSQL 16, Flyway),
frontend/ (React + Vite; có AGENTS.md riêng, làm trong frontend/ thì theo file đó).
- Spring Boot khoá >ở 4.1.1, khống tự đố>i phiên ba>n. Viêt@ theo Spring Boot 4:
spring-boot-starter-webmvc, spring-boot-starter-flyway, Jackson 3 (tools.jackson),
@MockitoBean. Khống dùng starter "classic".
- Đặc ta>: docs/srs/ (SRS v10; LuốIng hoạt động & ERD — 17 ba>ng ĐÃ CHÔ@T;
Lộ trình triên> khai Backend). Khống tự thêm ba>ng.
- Contract API: docs/api/<module>.md. Đọc file cu>a module trửớc khi viê@t controller.
## Lệnh (chạy trong backend/)
- Database dev: docker compose up -d
- Định dạng: ./mvnw spotless:apply Build + test: ./mvnw verify
## Cầ@u trúc (Lộ trình, chửởng 4)
- Package theo module: com.shareloop.<module>/
{controller, service, repository, entity, dto, mapper, event, job}
- Chỉ> sửa> trong module cu>a task. Gọi module khác qua <Tên>Service;
KHÔNG inject repository cu>a module khác.
- Giữa các module chỉ> lửu id (Long), khống @ManyToOne sang entity module khác.
- Ba>ng users/items: mố‚i module map nhóm cột cu>a mình bằng entity lát că@t;
khống map một cột >ở hai entity (trừ updated_at, updated_by qua SliceAudit).
## Cột chuần> và xoá mêIm (ERD mục 7.11)
- Entity ba>ng chính và ba>ng danh mục kê@ thừa common.entity.BaseEntity
(created_at, updated_at, created_by, updated_by, is_active, is_deleted),
găn@ @SQLRestriction("is_deleted = false"); status là enum riêng từng entity.
- Khống xoá cứng: gọi markDeleted(). Ẩn> / khoá: is_active = false, khống đối> status.
- Khống tự gán created_by, updated_by, created_at, updated_at: JPA Auditing lo.
## Luật bă@t buộc
- TiêIn chỉ> đố>i trong wallet.CreditService, có khoá dòng và ghi credit_ledger.
- Trạng thái Request/Item chỉ> đối> qua service s>ở hữu, luốn kiê>m canTransition().
- Mọi con số @nghiệp vụ đọc ConfigService(ConfigKey), khống viê@t cứng.
- Khống tra> entity ra API; DTO là record có @Valid.
- Lối‚ nghiệp vụ: throw BusinessException(<Module>ErrorCode.X).
- Schema chỉ> đối> bằng migration MỚI: V<yyyyMMddHHmm>__<module>_<mo_ta>.sql;
khống sử>a migration cũ.
- Service có business rule pha>i có test; test DB dùng Testcontainers, khống dùng H2.
## Khống đửợc làm
- Khống đố>i pom.xml, SecurityConfig, common/, config/ trừ khi task yêu cầIu rõ.
Trang 28 / 40

ShareLoop — Lộ trình tới khi code backend
- Khống đọc, tạo hay commit .env, API key, mật khầ>u.
- Khống tă@t hoặc xoá test đê> build xanh.
Cách dùng trợ lý AI trong nhóm
• Một task — một nhánh — một phiên AI. Mở phiên mới cho mỗi issue, nói rõ module và file contract
liên quan.
• Để agent ở chế độ hỏi trước khi chạy lệnh hoặc sửa file ngoài module. Không cho agent tự push
hay tự merge vào develop.
• Mã chạm tiền (wallet, listingfee) và chuyển trạng thái (request, item): tự viết test trước, cho AI
viết phần thân sau, rồi đọc lại từng dòng.
• Không dán API key, mật khẩu, dữ liệu thật của người dùng vào prompt.
• Commit dưới tên người làm; người mở PR phải giải thích được mọi dòng khi review hỏi.
• Dùng AI để hỏi "vì sao" và để viết test, không chỉ để sinh mã.
Trang 29 / 40

ShareLoop — Lộ trình tới khi code backend
6. Kiến thức cần học
6.1. Phần cả nhóm backend đều phải nắm
Chủ đề Nội dung cụ thể cần học Vì sao dự án cần
Java 21 record (DTO), switch dạng biểu thức và pattern Nền của mọi dòng mã. Virtual
matching (state machine), text block (prompt, SQL trong thread không bắt buộc dùng.
test), Optional, Stream, exception checked/unchecked,
equals/hashCode
Spring Boot 4 căn IoC, Dependency Injection, Bean, Hiểu vì sao mã chạy được, cấu
bản @Component/@Service/@Repository, hình dev/test/prod; cũng là nội
application.yml, profile; khác biệt so với Boot 3 khi dung bài thi thực hành (Spring
đọc tài liệu cũ (hộp mục 3.5.1) Boot 4.1.1).
Spring Web (REST) @RestController, @RequestMapping, Viết API đúng contract, trả lỗi
@PathVariable, @RequestBody, ResponseEntity, thống nhất.
DTO, @Valid, @RestControllerAdvice
Spring Data JPA / Entity, quan hệ trong một module, lazy/eager, lỗi N+1, Ví Credit và trạng thái giao dịch
Hibernate phân trang, @Transactional, @Query, phải đúng tuyệt đối.
@Lock(PESSIMISTIC_WRITE), nhiều entity trên một
bảng (mục 4.8), JPA Auditing, @MappedSuperclass,
@SQLRestriction cho xoá mềm
PostgreSQL JOIN/GROUP BY, index, CHECK/UNIQUE, partial index, Viết migration, query báo cáo,
transaction, SELECT ... FOR UPDATE, JSONB, kiểm tra hiệu năng.
EXPLAIN
Flyway Đặt tên version theo thời gian, out-of-order ở dev, Bốn người cùng sửa schema mà
không sửa migration đã chạy không vỡ database của nhau.
Spring Security + Filter chain, UserDetailsService, BCrypt, OAuth2 Đăng nhập, phân quyền Admin,
JWT Resource Server (JwtEncoder/JwtDecoder), bảo vệ API.
@PreAuthorize, CORS
Kiểm thử JUnit 5, Mockito, @MockitoBean, @WebMvcTest + SRS yêu cầu test cho mọi nhánh
MockMvc, @DataJpaTest, Testcontainers PostgreSQL tiền và trạng thái.
Git & GitHub Nhánh, rebase/merge, giải xung đột, PR, Làm việc 5 người trên một repo.
CODEOWNERS
Docker Image, container, volume, Docker Compose, đọc log Môi trường dev; đóng gói để
deploy.
Công cụ nhóm Spotless, .editorconfig, file .http, AGENTS.md Nhiều IDE và trợ lý AI vẫn ra mã
đồng nhất.
6.2. Phần học thêm theo module
Người Học thêm Dùng cho
BE1 Tích hợp cổng thanh toán (redirect, IPN, chữ ký HMAC-SHA512, Nạp Credit, phí đăng bài, phí sửa,
idempotency); Spring Mail; thiết kế sổ cái và cân đối số dư; xử lý gia hạn, đẩy bài, OTP
đồng thời, khoá dòng
Trang 30 / 40

ShareLoop — Lộ trình tới khi code backend
Người Học thêm Dùng cho
BE2 Regex Java, chuẩn hoá Unicode (java.text.Normalizer), xử lý Kiểm duyệt nội dung, AI, tìm kiếm,
tiếng Việt; gọi HTTP bằng RestClient có timeout và retry; prompt ảnh
ép JSON và kiểm schema; @Async; pg_trgm; upload multipart, AWS
SDK v2 S3
BE3 State machine bằng enum; @Scheduled và job idempotent; polling / Request, chat, thông báo, job nền
WebSocket (STOMP) cho chat; sự kiện trong ứng dụng
(ApplicationEventPublisher,
@TransactionalEventListener)
BE4 Truy vấn báo cáo (GROUP BY theo thời gian, date_trunc), xuất Trang Admin, báo cáo, cấu hình,
CSV; cấu hình key-value và cache cấu hình; phân quyền Admin; khiếu nại, uy tín
nhật ký kiểm toán
FE Axios interceptor gắn JWT và xử lý 401; đọc code lỗi để hiển thị; Nối API
mock theo contract (MSW hoặc json-server); upload nhiều ảnh;
polling
6.3. Cách học hiệu quả trong thời gian ngắn
• Học bằng spike. Không đọc hết khoá học rồi mới làm; học đúng phần cần cho spike của mình,
làm spike, rồi quay lại học phần còn thiếu.
• Nguồn nên dùng: Spring Guides trên spring.io; tài liệu Spring Boot Reference (đúng phiên bản
4.1.x); Baeldung cho từng chủ đề; tài liệu PostgreSQL phần Tutorial và Indexes; tài liệu
Testcontainers; tài liệu tích hợp VNPay sandbox.
• Chia sẻ lại cho nhóm: mỗi người trình bày 15 phút về spike của mình trong buổi họp tuần.
• Dùng trợ lý AI để hỏi "vì sao", không chỉ để sinh mã. Mã chạm tiền và trạng thái phải tự hiểu
được.
Trang 31 / 40

ShareLoop — Lộ trình tới khi code backend
7. Những gì còn phải tìm hiểu
| Câu hỏi | Cách tìm hiểu | Ảnh hưởng tới | Ai  |
| ------- | ------------- | ------------- | --- |
Dùng nhà cung cấp LLM nào? Giá,  Spike S5 với cùng bộ 20 mẫu C07, C31, AiClient BE2, tuần 5
giới hạn tốc độ, chế độ JSON, tiếng
Việt?
Lưu ảnh ở đâu khi deploy? Chương 8; spike S7 với nơi  C12 BE2, khi web ổn
|     | lưu thật |     | định |
| --- | -------- | --- | ---- |
Dịch vụ PostgreSQL cụ thể nào (theo  Spike S11; trang giá của 2–3  Cấu hình kết nối deploy BE4, trước tuần 7
| nguyên tắc C06)? Giới hạn gói miễn  | dịch vụ |     |     |
| ----------------------------------- | ------- | --- | --- |
phí?
Đối tác xem chứng từ chính hãng từ  Thảo luận nhóm, đối chiếu  C26, quyền xem tệp  BE4 + nhóm,
| lúc nào? | SRS 6.16 | private | trước tuần 8 |
| -------- | -------- | ------- | ------------ |
VNPay sandbox đăng ký thế nào?  Tài liệu VNPay, spike S1 Nạp tiền tuần 5 BE1
Nhận IPN ở localhost ra sao?
Chat dùng polling hay WebSocket? Spike S9 API chat, FE BE3 + FE
Deploy backend ở đâu (VPS hay  So sánh 2 lựa chọn; kiểm  Deploy thử tuần 7 BE1
| dịch vụ chạy container)? Có HTTPS  | RAM tối thiểu cho Spring  |     |     |
| ---------------------------------- | ------------------------- | --- | --- |
| không?                             | Boot                      |     |     |
Gmail App Password giới hạn bao  Đọc giới hạn Gmail SMTP Demo OTP BE1
nhiêu email/ngày?
Trang 32 / 40

ShareLoop — Lộ trình tới khi code backend
8. Phương án lưu hình ảnh khi deploy (C12)
Ảnh là phần dữ liệu lớn nhất của ShareLoop: mỗi bài 3–8 ảnh, cộng ảnh lỗi, chứng từ hàng chính hãng
(private), ảnh chat và minh chứng giao nhận (private). Nhờ interface StorageService và việc cơ sở dữ
liệu chỉ lưu storage_key, nhóm có thể đổi nơi lưu bất cứ lúc nào mà không sửa nghiệp vụ. Vì vậy
nhóm quyết định chốt C12 sau khi web chạy ổn định: trong lúc code chỉ dùng bản Local, chương này
là căn cứ để chọn khi tới lúc deploy.
8.1. Ước lượng dung lượng
Giả định Giá trị
Ảnh sau khi nén ở backend (cạnh dài tối đa 1600 px, JPEG/WebP khoảng 200–300 KB/ảnh
chất lượng ~80)
Số ảnh trung bình mỗi bài 5 ảnh → khoảng 1,0–1,5 MB/bài
Quy mô demo (khoảng 200 bài, 100 giao dịch có minh chứng) dưới 0,5 GB
Quy mô thử nghiệm thật (khoảng 5.000 bài) khoảng 5–8 GB
Kết luận: với quy mô đồ án, mọi gói miễn phí dưới đây đều đủ dung lượng. Tiêu chí quyết định là
độ ổn định khi demo, băng thông, độ khó tích hợp và việc có hỗ trợ tệp private hay không.
8.2. So sánh các phương án
Phương án Chi phí / gói miễn phí Ưu điểm Nhược điểm và rủi ro
A. Ổ đĩa của VPS Không thêm chi phí; dùng dung Đơn giản nhất; không cần tài Mất ảnh nếu VPS hỏng hoặc đổi
(LocalStorageSer lượng VPS khoản dịch vụ khác; hợp khi máy; tốn băng thông VPS; khó mở
vice, Nginx phục vụ demo rộng; phải tự làm URL có hạn cho tệp
ảnh) private
B. Cloudflare R2 Miễn phí 10 GB-tháng, 1 triệu Dùng thư viện S3 chuẩn; URL Có thể phải thêm phương thức thanh
(tương thích S3) thao tác loại A, 10 triệu thao tác có hạn cho tệp private; gói miễn toán khi kích hoạt (kiểm tra lúc đăng
loại B mỗi tháng; vượt thì phí không hết hạn; không lo hoá ký); cần cấu hình tên miền hoặc URL
$0,015/GB-tháng; không tính đơn băng thông công khai cho bucket
phí băng thông tải xuống
C. AWS S3 (+ Tài khoản mới từ 15/07/2025: Chuẩn công nghiệp, tài liệu Sau 6 tháng phải chuyển gói trả phí;
CloudFront) nhận 100 USD tín dụng, kiếm nhiều; hợp nếu nhóm deploy cả có phí băng thông; cấu hình IAM,
thêm tối đa 100 USD; gói miễn backend trên AWS chính sách bucket phức tạp hơn; phải
phí kéo dài 6 tháng hoặc tới khi đặt cảnh báo ngân sách
hết tín dụng
D. Supabase Storage Gói miễn phí: 1 GB lưu trữ, 5 Tiện nếu dùng Supabase làm Dự án miễn phí bị tạm dừng sau 1
GB băng thông, tệp tối đa 50 PostgreSQL; có URL có hạn tuần không hoạt động — rủi ro ngay
MB trước buổi demo; 1 GB khá nhỏ
E. Cloudinary Gói miễn phí 25 credit/tháng; 1 Tự nén, đổi cỡ, cắt ảnh qua Không dùng API S3 → phải viết bản
credit = 1 GB lưu trữ, hoặc 1 URL; CDN sẵn cài đặt StorageService riêng; tệp
GB băng thông, hoặc 1.000 lượt private phức tạp hơn; credit dùng
biến đổi ảnh chung cho lưu trữ và băng thông
Trang 33 / 40

ShareLoop — Lộ trình tới khi code backend
Phương án Chi phí / gói miễn phí Ưu điểm Nhược điểm và rủi ro
F. MinIO tự chạy trên Không thêm chi phí API S3 giống hệt R2/S3 → cùng Vẫn nằm trên ổ VPS nên có cùng rủi
VPS một mã nguồn; tốt cho môi ro mất dữ liệu như A; tốn RAM của
trường dev VPS
G. Lưu ảnh trong Không thêm chi phí Một nơi lưu duy nhất Không khuyến nghị: database phình
PostgreSQL (bytea) to, sao lưu chậm, vượt nhanh giới
hạn dung lượng của gói database
miễn phí, mọi lượt xem ảnh đều đi
qua backend
Khuyến nghị — chốt khi web chạy ổn định
• Từ nay tới khi web ổn định: chỉ dùng LocalStorageService. Bản S3 chạy thử với MinIO trong
Docker Compose khi rảnh (spike S7), không chặn tính năng nào.
• Thời điểm chốt: khi luồng đăng bài → giao dịch → hoàn tất chạy ổn trên develop (dự kiến cuối tuần 6),
trước deploy thử tuần 7.
• Ứng viên hàng đầu: Cloudflare R2 — hai bucket shareloop-public (ảnh bài, ảnh đại diện) và
shareloop-private (chứng từ, minh chứng, ảnh chat), URL có hạn 10 phút cho bucket private.
• Chọn AWS S3 thay R2 chỉ khi nhóm deploy toàn bộ trên AWS và theo dõi được hạn 6 tháng của gói
miễn phí.
• Dự phòng khi demo: STORAGE_TYPE=local trên VPS nếu dịch vụ ngoài có sự cố; mã nguồn không
đổi.
• Không chọn Supabase Storage cho ảnh vì rủi ro dự án bị tạm dừng; không lưu ảnh trong database.
8.3. Thiết kế StorageService
public interface StorageService {
StoredObject put(String key, InputStream data, long size, String contentType,
Visibility v);
String publicUrl(String key); // a>nh cống khai
String signedUrl(String key, Duration ttl); // tệp private, URL có hạn
void delete(String key);
}
// Hai ba>n cài đặt, chọn bằng cầu@ hình app.storage.type = local | s3
// S3StorageService dùng AWS SDK v2; với R2: endpointOverride =
https://<account>.r2.cloudflarestorage.com, region = "auto"
Quy ước Nội dung
Đặt tên khoá public/{userId}/{uuid}.webp, avatars/{userId}/{uuid}.webp (public);
private/{userId}/{uuid}, evidence/{requestId}/{uuid}, chat/{requestId}/{uuid}
(private)
Luồng tải lên FE gửi multipart lên backend → kiểm tra loại tệp bằng nội dung (không tin đuôi tệp), ≤ 5 MB → xoá
EXIF (ảnh điện thoại có toạ độ GPS nhà người dùng) → đổi cỡ, nén → lưu → tạo media_files →
trả mediaId
Quyền xem Ảnh bài: URL công khai. Chứng từ: chỉ Admin và đối tác (thời điểm theo C26, đề xuất từ lúc
Reserved). Minh chứng: Admin và hai bên giao dịch. Backend kiểm quyền rồi mới trả URL có hạn.
Trang 34 / 40

ShareLoop — Lộ trình tới khi code backend
Quy ước Nội dung
Dọn rác Job mỗi giờ: tệp còn status UPLOADED (chưa gắn vào bài, tin nhắn hay giao dịch) quá 24 giờ thì đặt
is_deleted = true và xoá khỏi nơi lưu (ERD mục 6).
Cấu hình Biến môi trường: STORAGE_TYPE, S3_ENDPOINT, S3_BUCKET_PUBLIC, S3_BUCKET_PRIVATE,
S3_ACCESS_KEY, S3_SECRET_KEY, PUBLIC_BASE_URL. Spring:
spring.servlet.multipart.max-file-size=5MB.
Bảo mật Bucket private tắt truy cập công khai; khoá truy cập chỉ có quyền trên đúng hai bucket; cấu hình
CORS chỉ cho tên miền FE; không commit khoá.
8.4. Các bước triển khai lưu ảnh
1. Tuần 4: MediaService + POST /media dùng bản Local.
2. Tuần 5–6: khi rảnh, chạy spike S7 với MinIO để bản S3 sẵn sàng.
3. Khi web ổn định (dự kiến cuối tuần 6): tạo tài khoản R2 (hoặc phương án được chọn) và hai
bucket, chạy lại spike với nơi lưu thật; chốt C12 và ghi vào docs/decisions/.
4. Tuần 7 (deploy thử): đổi biến môi trường sang S3/R2; kiểm tra tải lên, xem ảnh, URL có hạn hết
hiệu lực sau 10 phút.
5. Tuần 9, trước buổi demo: mở thử trang có ảnh; giữ sẵn cấu hình dự phòng STORAGE_TYPE=local.
Trang 35 / 40

ShareLoop — Lộ trình tới khi code backend
9. Lịch làm việc từ tuần 4 đến tuần 9
9.1. Tuần 4 theo từng ngày
Ngày Việc chính Kết quả cuối ngày
T2 28/9 Chốt quyết định và ERD (xong buổi sáng). Mọi người cài JDK Biên bản; máy mọi người sẵn
21, IDE, Docker sàng
T3 29/9 Bổ sung cột chuẩn theo góp ý giảng viên (C34); chốt Spring Tài liệu cập nhật 29/09; repo có
Boot 4.1.1. Tối: BE1 dựng nền repo theo Hướng dẫn BE1 luật chặn ghi công AI và
(B1–B3). Mỗi BE viết API contract module mình AGENTS.md
T4 30/9 BE1 tạo project Spring Boot 4.1.1 theo cây thư mục chương 4, Repo chạy được, CI xanh;
Docker Compose, CI + Spotless, CODEOWNERS, README migration chạy sạch
(PR #1); V1__init.sql (PR #2). BE4 viết V2, V4; BE2 viết
V3. FE review contract; tạo backlog sprint tuần 4
T5 1/10 BE1: lớp nền (BaseEntity, lỗi thống nhất, JWT, Swagger), Checklist chương 10 tích đủ các
endpoint mẫu có test. Mỗi chủ service cửa ngõ merge chữ ký mục bắt buộc
hàm (mục 4.7). Mọi người bắt đầu code sprint tuần 4 trên
nhánh riêng
T6 2/10 – T7 Code sprint tuần 4 (đăng ký, OTP email, đăng nhập, tải ảnh, Mỗi người có ít nhất một PR
3/10 tạo bài, gửi yêu cầu, cấu hình); spike S2, S3, S4, S6, S8 xen được merge
kẽ
CN 4/10 Demo nội bộ trên develop; lập kế hoạch tuần 5 Mốc tuần 4: đăng ký → đăng
nhập → tạo bài chạy đầu–cuối
9.2. Các tuần tiếp theo
Tuần Trọng tâm Spike / việc hạ tầng đi kèm Mốc
5 (5/10 – 11/10) Ví, nạp tiền, phí đăng bài; kiểm duyệt tầng S1 VNPay (đầu tuần), S5 LLM Mốc 1: đăng bài
1 và tầng 3; chọn người, mở chat → chốt C07, C31; S9 chat; có phí + duyệt
S11 PostgreSQL
6 (12/10 – 18/10) Chat lọc liên hệ, chốt lịch, trao liên lạc, giao S10 job nền; cuối tuần: web ổn Mốc 2: giao dịch
nhận; tìm kiếm; AI sàng lọc bài; sửa bài định → S7 với nơi lưu thật → tới Completed —
(phí 5 Credit), gia hạn; khiếu nại, uy tín chốt C12 điểm kiểm tra cắt
giảm
7 (19/10 – 25/10) AI trợ lý (2 Credit/lượt), AI chat tầng 2; báo Deploy thử lần 1: VPS + Mốc 3: xong
cáo tài chính; đẩy bài; thẻ bài trong chat PostgreSQL managed + nơi Must, chạy online
lưu ảnh đã chốt
8 (26/10 – 1/11) Kiểm thử tích hợp, đo chất lượng bộ lọc, Deploy bản ổn định Đóng băng tính
Should theo thứ tự ưu tiên; chốt C26 trước năng
khi làm xác thực chính hãng
9 (2/11 – 8/11) Sửa lỗi, giao diện, tài liệu, kịch bản và tập Chuẩn bị chế độ dự phòng Nộp và bảo vệ
dượt demo (lưu ảnh local, cổng thanh toán
giả lập)
Trang 36 / 40

ShareLoop — Lộ trình tới khi code backend
Lịch giả định mỗi người dành khoảng 3 giờ mỗi ngày cho dự án. Chi tiết việc của từng người mỗi tuần
nằm ở SRS chương 16 và bảng mục 4.5.
Trang 37 / 40

ShareLoop — Lộ trình tới khi code backend
10. Checklist sẵn sàng code
Tích từng ô trong buổi họp tối thứ Năm 1/10. Hai mục có đánh dấu (*) được phép xong muộn hơn,
trong tuần 4–5.
Mục Người xác nhận
☑ Biên bản các quyết định nhóm 1 và ERD 17 bảng; SRS đã sửa theo (xong 28/9) BE1
☐ Cả 4 BE đã cài JDK 21, IDE, Docker; ./mvnw verify chạy xanh trên máy mình Từng người
☐ Cây thư mục đủ 18 module theo chương 4; package-info.java ghi người sở BE1
hữu
☐ CODEOWNERS, .editorconfig, .gitattributes, AGENTS.md, CLAUDE.md, BE1
.gemini/settings.json đã có trong repo
☐ V1__init.sql và V2–V4 chạy sạch trên database trống BE1, BE2, BE4
☐ Sơ đồ use case và screen flow cập nhật theo các mức phí đã chốt BE1, FE
☐ API contract tuần 4–5 + danh sách mã lỗi theo module BE + FE
☐ Service cửa ngõ (mục 4.7) đã có chữ ký hàm và được merge Chủ từng service
☐ Endpoint mẫu có test chạy xanh; Swagger UI mở được BE1
☐ CI xanh (Spotless + test); nhánh develop, main đã bật bảo vệ và bắt buộc review BE1
code owner
☐ README có hướng dẫn chạy và mục Quy ước BE1
☐ Bí mật (API key, mật khẩu SMTP, JWT secret) nằm trong biến môi trường, không BE1
có trong repo
☐ Backlog sprint tuần 4 trên GitHub Projects, mỗi issue có người nhận và nhãn BE1
module
☐ (*) Báo cáo 11 spike (xong / chưa xong + ghi chú) Người phụ trách spike
☐ (*) Bản Local của StorageService chạy thật; nơi lưu khi deploy chốt sau (C12) BE2
Trang 38 / 40

ShareLoop — Lộ trình tới khi code backend
11. Những lỗi hay gặp khi bắt đầu và cách tránh
Lỗi Hậu quả Cách tránh
Để ddl-auto=update tự tạo bảng Schema mỗi máy một kiểu; lên Luôn validate + Flyway ngay từ đầu
production vỡ
Viết business rule trong controller Trùng lặp, khó test, lệch SRS Controller chỉ gọi service; review từ chối
PR vi phạm
Trả entity ra API Lộ số điện thoại, vòng lặp JSON Luôn dùng DTO record
do quan hệ hai chiều
Cộng trừ Credit ở nhiều nơi Sai số dư, không truy vết được Chỉ CreditService đụng tới tiền, có khoá
dòng và ghi ledger
Inject repository của module khác Hai người cùng sửa một bảng, luật Gọi service cửa ngõ; bật
"cho nhanh" nghiệp vụ bị bỏ qua ModuleBoundaryTest nếu tái phạm
Mọi người cùng sửa User.java Xung đột mỗi ngày Entity lát cắt theo nhóm cột (mục 4.8)
Hai người tạo migration cùng số Flyway báo lỗi, database dev hỏng Version theo thời gian, out-of-order ở
V5 dev (mục 4.9)
Mỗi IDE định dạng một kiểu, file PR phình to, xung đột giả, mvnw lỗi .editorconfig, .gitattributes,
CRLF lẫn LF trên CI Spotless trong CI
Để trợ lý AI sửa lan ra module PR khó review, phá mã của người AGENTS.md ghi rõ phạm vi; agent hỏi trước
khác hoặc file nền khác khi sửa; CODEOWNERS chặn
Xoá cứng bản ghi hoặc tự gán Mất dữ liệu đối soát; cột chuẩn sai Gọi markDeleted(); để JPA Auditing và
created_by, updated_at người, sai giờ trigger điền cột chuẩn (ERD 7.11)
Làm theo hướng dẫn Spring Boot Lỗi biên dịch, thiếu cấu hình tự Đọc hộp lưu ý mục 3.5.1; kiểm tra import
3 (spring-boot-starter-web, động và tên starter trước
@MockBean,
com.fasterxml.jackson.datab
ind)
Test bằng H2 thay PostgreSQL Test xanh nhưng chạy thật lỗi Testcontainers
(JSONB, unaccent, cú pháp khác)
Để PR quá lớn, merge cuối tuần Xung đột nhiều, review qua loa PR nhỏ, merge hằng ngày vào develop
Commit API key lên GitHub Mất tiền, key bị thu hồi Biến môi trường, .env trong .gitignore;
bật secret scanning của GitHub
Chờ FE xong mới làm BE hoặc Dồn việc cuối kỳ Contract trước, mock sau, làm song song
ngược lại
Gọi AI đồng bộ khi người dùng Thao tác chậm vài giây, treo khi Chạy bất đồng bộ sau commit; có timeout
bấm gửi bài nhà cung cấp lỗi và nhãn "AI chưa kiểm"
Trang 39 / 40

ShareLoop — Lộ trình tới khi code backend
Nguồn tham khảo
• Cloudflare R2 — trang sản phẩm và bảng giá: https://www.cloudflare.com/products/r2/
• AWS — thông báo gói Free Tier mới (tín dụng 100–200 USD, gói miễn phí 6 tháng), 07/2025:
https://aws.amazon.com/about-aws/whats-new/2025/07/aws-free-tier-credits-month-free-plan/
• Supabase — bảng giá: https://supabase.com/pricing
• Cloudinary — bảng giá: https://cloudinary.com/pricing
• HeroDevs — các nhánh Spring Boot, ngày hết hỗ trợ (3.5 hết hỗ trợ OSS 30/06/2026):
https://www.herodevs.com/blog-posts/spring-boot-versions-eol-dates-and-latest-releases-april-
2026
• springdoc-openapi — dòng 2.x cho Spring Boot 3, dòng 3.x cho Spring Boot 4:
https://github.com/springdoc/springdoc-openapi
• Spring Boot 4.1.1 (20/08/2026): https://spring.io/blog/2026/08/20/spring-boot-4-1-1-available-now/
• Spring Boot 4.0 Migration Guide (đổi tên starter, starter Flyway, @MockitoBean, Jackson 3):
https://github.com/spring-projects/spring-boot/wiki/Spring-Boot-4.0-Migration-Guide
• Testcontainers 2 — artifact testcontainers-postgresql:
https://mvnrepository.com/artifact/org.testcontainers/testcontainers-postgresql
• Google Antigravity — Rules (đọc AGENTS.md, GEMINI.md, .agents/rules):
https://antigravity.google/docs/rules/
• So sánh CLAUDE.md, AGENTS.md, GEMINI.md và cách trỏ về một file chung:
https://inventivehq.com/blog/claude-md-vs-agents-md-vs-gemini-md
• Giá, gói miễn phí và phiên bản công cụ thay đổi thường xuyên; kiểm tra lại khi đăng ký hoặc khi
tạo project (số liệu lưu ảnh tra ngày 26/09/2026, phiên bản tra ngày 29/09/2026).
Trang 40 / 40