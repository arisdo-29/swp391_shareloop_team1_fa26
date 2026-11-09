# ShareLoop

> Nền tảng web **Cho – Nhận – Trao đổi đồ cũ** cộng đồng.

Đồ án môn **SWP391** · Đặc tả chính thức: `docs/srs/README.md` (SRS Final Report 09/11/2026)

---

## Giới thiệu

ShareLoop giúp cộng đồng cho, nhận và trao đổi đồ cũ. Người dùng đăng bài về món đồ không còn dùng; người khác gửi yêu cầu xin hoặc đề nghị đổi một món tương đương. Hai bên thống nhất thời gian, địa điểm qua khung chat có kiểm soát rồi tự giao nhận.

Hai người dùng không trả tiền cho nhau: Credit chỉ dùng để trả phí dịch vụ cho nền tảng (đăng bài Trao đổi, sửa bài từ lần thứ hai, gia hạn, đẩy bài, AI trợ lý tìm đồ). Mục tiêu là đưa đồ còn tốt đến đúng người cần và giảm rủi ro khi hai người lạ giao dịch với nhau.

**Tính năng chính (theo SRS Final Report 09/11):**

- **Tài khoản** – đăng ký có xác thực email bằng OTP, đăng nhập, hồ sơ, quên mật khẩu.
- **Ví Credit** – chỉ nạp qua VNPay (sandbox), xem số dư khả dụng, số đang giữ và lịch sử ví; Credit chỉ tiêu trong hệ thống, không rút ra, không chuyển cho người khác.
- **Bài đăng Cho / Trao đổi** – sửa, gỡ, gia hạn; đẩy bài xoay vòng (nhóm bài đẩy đổi mỗi 3 phút); hiển thị 30 ngày (45 ngày từ hạng Vàng).
- **Chuyển hình thức Cho ↔ Trao đổi** – chỉ ở hai thời điểm: lúc chọn người và trong chat của giao dịch bài Cho trước khi chốt lịch.
- **Kiểm duyệt** – kiểm tra nội dung tự động tại form (từ khoá cấm, chặn SĐT, email, URL); Admin duyệt 100% bài theo checklist cấu hình được.
- **Tìm đồ** – tìm kiếm, lọc thủ công miễn phí (gõ không dấu); trợ lý AI tìm đồ (chỉ chọn bài có thật trong hệ thống); gợi ý mô tả từ tên món khi đăng bài.
- **Giao dịch** – gửi yêu cầu xin hoặc đề nghị đổi, chọn người, chat có kiểm soát, chốt lịch hẹn, xem thông tin liên lạc, xác nhận hai chiều.
- **Uy tín & khiếu nại** – sao uy tín (0–5), điểm, hạng; báo cáo bài, người dùng và khiếu nại giao dịch; Admin xử lý ở cấp tài khoản.
- **Quản trị** – danh mục, khu vực, từ khoá cấm, checklist, cấu hình phí và hạn mức, khoá tài khoản, báo cáo tài chính, cảnh báo bất thường và nhật ký hoạt động.

## Biểu phí (SRS Final Report 09/11, mục 5.1 nhóm D, E, F, G, I, K và bảng cấu hình 5.2)

Quy đổi: **1.000 đ = 1 Credit** (số nguyên, VI-01). Nạp tối thiểu **10.000 đ** (10 Credit), số tiền nạp là bội số của 1.000 (VI-03).

| Khoản mục | Mức phí | Thời điểm trừ | Mã luật |
|---|---|---|---|
| Tìm kiếm thường, gửi yêu cầu, chat, chốt lịch, giao nhận, báo cáo, khiếu nại | Miễn phí | – | PH-09 |
| Đăng bài Cho | Miễn phí (tối đa 5 bài Đang hiển thị hoặc Chờ duyệt, mọi hạng) | – | PH-01, BD-05 |
| Đăng bài Trao đổi trong hạn mức theo hạng (Đồng 5, Bạc 8, Vàng 15, Kim cương không giới hạn) | 5 Credit | Giữ khi gửi duyệt, trừ khi Admin duyệt lần đầu; bị từ chối hoặc gỡ trước khi duyệt thì nhả lại | PH-02, BD-06 |
| Đăng bài Trao đổi vượt hạn mức theo hạng | 10 Credit | Như trên | PH-02, BD-06 |
| Sửa bài: lượt đầu sau khi đã được duyệt | Miễn phí | – | PH-04, BD-08 |
| Sửa bài: từ lượt hai | 5 Credit / lần | Giữ khi gửi bản sửa, trừ khi bản sửa được duyệt; bị từ chối thì nhả | PH-04, BD-08 |
| Gia hạn bài (cả bài Cho) | 5 Credit / +30 ngày | Ngay khi bấm; không hoàn | PH-05, HT-01 |
| Đẩy bài | 5 Credit / gói 3 ngày | Ngay khi bấm; không hoàn | PH-06, HT-04 |
| Chuyển bài Cho → Trao đổi (lúc chọn người hoặc trong chat) | 5 Credit | Ngay | PH-08, CD-02, CD-03 |
| Chuyển bài Trao đổi → Cho | Không hoàn phí đã trả | – | PH-08, CD-04 |
| AI trợ lý tìm đồ | 5 lượt miễn phí / ngày (tài khoản đã từng nạp; chưa nạp có 1 lượt dùng thử), quá lượt 2 Credit / lượt | Chỉ trừ khi AI chạy xong và có kết quả; AI lỗi thì không trừ | PH-07, TT-03 |

**Giới hạn đẩy bài (HT-04, HT-05, HT-07):** một đợt đẩy liên tục tối đa 6 ngày, hết đợt nghỉ 3 ngày mới mở đợt mới; mỗi người tối đa 3 bài đang đẩy, toàn hệ thống tối đa 30 bài; mỗi trang tìm kiếm hiển thị tối đa 3 bài đẩy, nhóm bài xoay vòng mỗi 3 phút.

Mọi con số đọc từ bảng cấu hình (`website_attributes`), Admin đổi được mà không sửa mã.

## Công nghệ

| Hạng mục | Lựa chọn |
|---|---|
| Backend | Java 21 · Spring Boot **4.1.1 (khoá phiên bản)** · Maven Wrapper |
| Cơ sở dữ liệu | PostgreSQL 16 · Flyway |
| Môi trường dev | Docker Compose (PostgreSQL, Mailpit) |
| Frontend | React · Vite · TypeScript · Tailwind |
| Thanh toán | VNPay sandbox |

## Dữ liệu

Cơ sở dữ liệu có **17 bảng** (đã chốt, không tự thêm bảng). Bảng chính có 7 cột chuẩn: `created_at`, `updated_at`, `created_by`, `updated_by`, `is_active`, `is_deleted`, `status`; bảng danh mục, cấu hình có 6 cột (không có `status`). **Không xoá cứng**: xoá là đặt `is_deleted = true`, ẩn hoặc khoá là `is_active = false`. Mô hình dữ liệu khái niệm: SRS Final Report, mục 3.1.5.

## Cấu trúc repository

```
swp391_shareloop_team1_fa26/
├── .github/       # CODEOWNERS, mẫu PR, workflows (backend, no-ai-attribution)
├── .githooks/     # commit-msg
├── backend/       # Spring Boot (Maven), chia package theo module
├── frontend/      # React + Vite
├── docs/
│   ├── srs/       # SRS Final Report 09/11, Business Rule v12
│   ├── api/       # Contract API theo module (docs/api/<module>.md)
│   └── decisions/
├── AGENTS.md      # luật chung cho trợ lý AI
├── CLAUDE.md
└── README.md
```

## Cấu trúc backend

Chia theo **module nghiệp vụ**, không chia theo tầng. Gốc: `backend/src/main/java/com/shareloop/`. Bên trong mỗi module (chỉ tạo thư mục nào cần):

```
<module>/
├── controller/   service/   repository/   entity/
├── dto/          mapper/    event/        job/
└── <Module>ErrorCode.java
```

Nguyên tắc: gọi chéo module chỉ qua `<Tên>Service` hoặc sự kiện, không inject repository của module khác; giữa các module chỉ lưu `id` (Long), không `@ManyToOne` sang entity module khác. Thư mục `common/`, `config/`, `integration/` dùng chung.

| Package | Nội dung | Người sở hữu |
|---|---|---|
| `common`, `config` | nền dùng chung, cấu hình Spring | BE1 |
| `auth` | đăng ký, OTP email, đăng nhập, JWT | BE1 |
| `user` | hồ sơ, đổi số điện thoại | BE1 |
| `wallet` | ví Credit, nạp tiền, sổ Credit | BE1 |
| `listingfee` | phí đăng, sửa, gia hạn, đẩy bài | BE1 |
| `integration/payment`, `integration/mail` | VNPay, email SMTP | BE1 |
| `catalog` | danh mục, thuộc tính động, khu vực | BE2 |
| `media` | tải ảnh, media_files | BE2 |
| `item` | bài đăng | BE2 |
| `moderation` | lexicon, regex, AI sàng lọc / chat | BE2 |
| `search` | tìm kiếm, AI trợ lý tìm đồ | BE2 |
| `integration/ai`, `integration/storage` | LLM, lưu ảnh | BE2 |
| `request` | Request, state machine, lịch hẹn, giao nhận | BE3 |
| `chat` | tin nhắn, thẻ bài, đổi món đề nghị | BE3 |
| `notification` | thông báo trong app, email giao dịch | BE3 |
| `setting` | cấu hình, lexicon, checklist | BE4 |
| `review` | hàng chờ duyệt, duyệt / từ chối | BE4 |
| `report` | báo cáo, khiếu nại, tranh chấp | BE4 |
| `reputation` | TrustStars, điểm, hạng | BE4 |
| `admin` | khoá tài khoản, báo cáo tài chính | BE4 |
| `audit` | activity_logs | BE4 |

## Bắt đầu nhanh

**Yêu cầu:** JDK 21 · Docker Desktop · Node.js (LTS)

### Backend

```bash
cd backend
docker compose up -d                 # PostgreSQL 16, Mailpit
.\mvnw.cmd spring-boot:run           # Windows
./mvnw spring-boot:run               # macOS / Linux
```

- Swagger: <http://localhost:8080/swagger-ui.html>
- Mailpit (xem email OTP): <http://localhost:8025>

### Frontend

```bash
cd frontend
npm install
npm run dev
```

## Việc bắt buộc sau khi clone

```bash
git config core.hooksPath .githooks
```

Bật hook `commit-msg` (kiểm tra định dạng commit và chặn dòng ghi công AI). Không chạy lệnh này thì commit của bạn vẫn có thể bị CI từ chối.

## Quy trình làm việc với Git

### Các nhánh

| Nhánh | Vai trò |
|---|---|
| `main` | Bản ổn định, dùng để demo và nộp bài. Chỉ nhận merge từ `develop`. |
| `develop` | Nhánh tích hợp và là **nhánh mặc định**. Mọi tính năng merge vào đây trước. |
| `feature/*`, `fix/*`, `docs/*` | Nhánh làm việc, tách từ `develop`, sống ngắn (1–3 ngày), merge lại vào `develop` bằng Pull Request. |

`main` và `develop` **không push trực tiếp**. Cuối mỗi tuần, khi `develop` ổn định, nhóm trưởng tạo PR `develop → main`.

### Làm một tính năng

```bash
git switch develop
git pull
git switch -c feature/be-wallet-topup     # tách nhánh mới từ develop
# ... code ...
cd backend
./mvnw spotless:apply                     # định dạng mã
./mvnw verify                             # build + test
cd ..
# ... commit ...
git push -u origin feature/be-wallet-topup
# mở Pull Request vào develop và điền mẫu PR
```

### Đặt tên nhánh

Có tiền tố `be` hoặc `fe` để nhìn là biết phần nào:

```
feature/be-wallet-topup
feature/be-request-choose
feature/fe-post-page
fix/be-hold-double-charge
docs/api-item
```

### Commit

Theo dạng `type(scope): mô tả ngắn`, scope là tên package:

```
feat(wallet): add topup webhook
fix(request): reject invalid state transition
docs: update README
chore: bump spring boot version
```

Các type: `feat`, `fix`, `docs`, `refactor`, `test`, `chore`.

### Review và merge

- Mọi PR cần ít nhất 1 người duyệt. Người được gọi review tự động theo `.github/CODEOWNERS`.
- **Bắt buộc review bởi người ngoài module** với mọi thay đổi trong `wallet` (`CreditService`) và `request` (`RequestService`).
- Sửa file dùng chung (`pom.xml`, `common/`, `config/`, `application.yml`, `db/migration/`): làm PR nhỏ, merge nhanh và báo cả nhóm.
- Mã do AI sinh ra phải được một thành viên đọc lại và chịu trách nhiệm.

### Không commit

`.env`, khoá API (VNPay, SMTP, LLM), mật khẩu, `application-local.properties`, `node_modules/`, `target/`.
Dùng `.env.example` hoặc file cấu hình mẫu chứa giá trị giả để hướng dẫn người khác.

## Dùng trợ lý AI

- [AGENTS.md](AGENTS.md) là luật chung cho mọi trợ lý AI trong repo này.
- AI **không tự commit, push hay mở PR**. Người làm tự xem diff, chạy test và commit dưới tên mình.
- Repo **không chứa bất kỳ dòng ghi công AI nào** (`Co-Authored-By`, `Generated with...`) trong commit, PR, mã nguồn hay tài liệu.
- Hook `.githooks/commit-msg` và CI `no-ai-attribution` sẽ chặn các dòng này.

## Kế hoạch triển khai

Kế hoạch và tiến độ: xem GitHub milestones của repo.

## Nhóm thực hiện

| Vai trò | GitHub | Phụ trách |
|---|---|---|
| Frontend | @My-Mieu | Giao diện React |
| Backend 1 | @arisdo-29 | auth, user, wallet, listingfee, nền |
| Backend 2 | @Nguyentri2531 | catalog, media, item, moderation, search |
| Backend 3 | @dwargon73-sketch | request, chat, notification |
| Backend 4 | @kopslngbtram2110 | setting, review, report, reputation, admin, audit |

## Giấy phép

Dự án phục vụ mục đích học tập trong môn SWP391.
