# ShareLoop

> Nền tảng web **Cho – Nhận – Trao đổi đồ cũ** cộng đồng.

Đồ án môn **SWP391** · Đặc tả: SRS ShareLoop v10 (29/09/2026)

---

## Giới thiệu

ShareLoop giúp cộng đồng cho, nhận và trao đổi đồ cũ. Người dùng đăng bài về món đồ không còn dùng; người khác gửi yêu cầu xin hoặc đề nghị đổi một món tương đương. Hai bên thống nhất thời gian, địa điểm qua khung chat có kiểm soát rồi tự giao nhận.

Từ v10, doanh thu nằm ở bước đăng bài chứ không nằm ở bước giao dịch: giao dịch Cho và Trao đổi hoàn toàn miễn phí. Mục tiêu là đưa đồ còn tốt đến đúng người cần và giảm rủi ro khi hai người lạ giao dịch với nhau.

**Tính năng chính (SRS v10):**

- **Tài khoản** – đăng ký có xác thực email bằng OTP, đăng nhập, hồ sơ, quên mật khẩu.
- **Ví Credit** – nạp qua VNPay (sandbox), xem số dư khả dụng, số đang giữ và lịch sử ví; Credit chỉ tiêu trong hệ thống, không rút ra.
- **Bài đăng Cho / Trao đổi** – sửa, gỡ, gia hạn, đẩy bài; vòng đời 30 ngày.
- **Kiểm duyệt ba tầng** – chặn cứng tại form (lexicon + regex), AI sàng lọc gắn cờ hỗ trợ Admin, Admin duyệt theo checklist cấu hình được. AI không tự duyệt hay từ chối.
- **Tìm đồ** – tìm kiếm, lọc thủ công miễn phí (hỗ trợ gõ không dấu); trang AI trợ lý tìm đồ.
- **Giao dịch** – gửi yêu cầu, chọn người, chat có kiểm soát (chặn thông tin liên hệ hai tầng, thẻ sản phẩm), chốt lịch hẹn, trao liên lạc, xác nhận hai chiều.
- **Uy tín & khiếu nại** – TrustStars, điểm, hạng; khiếu nại trong 7 ngày sau giao dịch, Admin xác minh rồi mới xử lý ở cấp tài khoản.
- **Quản trị** – danh mục, khu vực, lexicon, checklist, cấu hình phí và hạn mức, khoá tài khoản, báo cáo tài chính.

## Biểu phí (SRS v10, mục 4.2)

Quy đổi: **1.000đ = 1 Credit** (Credit là số nguyên). Nạp tối thiểu **10.000đ** (10 Credit), số tiền nạp là bội số của 1.000.

| Khoản mục | Mức phí | Thời điểm trừ |
|---|---|---|
| Giao dịch Cho, Trao đổi | Miễn phí | – |
| Gửi yêu cầu, chat, chốt lịch, hoàn tất | Miễn phí | – |
| Tìm kiếm, lọc thủ công | Miễn phí | – |
| Đăng bài (Cho hoặc Trao đổi) | 5 Credit / 30 ngày hiển thị | Giữ (Hold) khi gửi duyệt, trừ thật khi duyệt lần đầu; bị từ chối thì nhả lại toàn bộ |
| Sửa bài trước khi duyệt / bị chặn tự động | Miễn phí | – |
| Sửa bài sau duyệt, lần đầu | Miễn phí (1 lần / bài) | – |
| Sửa bài sau duyệt, từ lần thứ hai | 5 Credit / lần | Giữ khi gửi bản sửa, trừ khi bản sửa được duyệt |
| Gia hạn bài | 5 Credit / 30 ngày | Ngay khi bấm gia hạn |
| Đẩy bài | 5 Credit / 3 ngày (cộng dồn tối đa 14 ngày) | Ngay khi bấm; chỉ bài Approved còn hạn |
| AI trợ lý tìm đồ | 2 Credit / lượt, 5 lượt miễn phí / ngày cho tài khoản đã từng nạp | Ngay trước khi gọi LLM |

Mọi con số đọc từ cấu hình (`website_attributes`), Admin đổi được mà không sửa mã.

> **Đối chiếu với SRS:** các mức phí trong yêu cầu khớp SRS v10. Hai chi tiết SRS có mà yêu cầu chưa nêu, đã bổ sung theo SRS: (1) đẩy bài cộng dồn tối đa 14 ngày; (2) tài khoản **chưa từng nạp** chỉ có 1 lượt AI dùng thử, không có 5 lượt/ngày.

## Công nghệ

| Hạng mục | Lựa chọn |
|---|---|
| Backend | Java 21 · Spring Boot **4.1.1 (khoá phiên bản)** · Maven Wrapper |
| Cơ sở dữ liệu | PostgreSQL 16 · Flyway |
| Môi trường dev | Docker Compose (PostgreSQL, Mailpit) |
| Frontend | React · Vite · TypeScript · Tailwind |
| Thanh toán | VNPay sandbox |

## Dữ liệu

Cơ sở dữ liệu có **17 bảng** (đã chốt, không tự thêm bảng). Bảng chính có 7 cột chuẩn: `created_at`, `updated_at`, `created_by`, `updated_by`, `is_active`, `is_deleted`, `status`; bảng danh mục, cấu hình có 6 cột (không có `status`). **Không xoá cứng**: xoá là đặt `is_deleted = true`, ẩn hoặc khoá là `is_active = false`. Chi tiết ở tài liệu *Luồng hoạt động & ERD v10*, mục 7.11.

## Cấu trúc repository

```
swp391_shareloop_team1_fa26/
├── .github/       # CODEOWNERS, mẫu PR, workflows (backend, no-ai-attribution)
├── .githooks/     # commit-msg
├── backend/       # Spring Boot (Maven), chia package theo module
├── frontend/      # React + Vite
├── docs/
│   ├── srs/       # SRS v10, Luồng hoạt động & ERD, Lộ trình triển khai Backend
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

Xem SRS v10, chương 16 (*Kế hoạch từ tuần 4 đến tuần 9*).

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
