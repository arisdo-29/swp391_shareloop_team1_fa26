# ShareLoop

> Nền tảng web **Cho – Nhận – Trao đổi đồ cũ** cộng đồng.

Đồ án môn **SWP391** · Đặc tả: SRS ShareLoop v8 (19/09/2026)

---

## Giới thiệu

ShareLoop giúp cộng đồng cho, nhận và trao đổi đồ cũ. Người dùng đăng bài về món đồ không còn dùng; người khác gửi yêu cầu xin hoặc đề nghị đổi một món tương đương; hai bên thống nhất thời gian, địa điểm qua khung chat của hệ thống rồi tự giao nhận.

Mục tiêu: đưa đồ còn tốt đến đúng người cần và giảm rủi ro khi hai người lạ giao dịch với nhau.

## Tính năng chính

- **Đăng bài Cho / Trao đổi** – miễn phí, có giới hạn số bài đồng thời theo hạng.
- **Gửi yêu cầu** – xin đồ hoặc đề nghị đổi; chủ bài đăng chọn đúng một người.
- **Chat có kiểm soát** – chặn mọi thông tin liên lạc cho tới khi hai bên xác nhận lịch hẹn.
- **Ví Credit** – nạp tiền qua cổng thanh toán, 1 VNĐ = 1 Credit, chỉ tiêu trong hệ thống, không rút ra.
- **Kiểm duyệt hai tầng** – chặn cứng tự động theo quy tắc + Admin duyệt thủ công theo checklist tám mục.
- **Uy tín & khiếu nại** – khiếu nại trong 7 ngày sau giao dịch; Admin xác minh rồi mới trừ sao uy tín.
- **AI hỗ trợ (2 vai trò)** – gợi ý ghép đôi trên bài Trao đổi và trợ lý tìm đồ. AI không tham gia kiểm duyệt và không phân tích hình ảnh.

## Người dùng

Hệ thống chỉ có hai loại người dùng, cùng kế thừa từ `User`:

| Actor | Mô tả |
|---|---|
| **Member** | Dùng một tài khoản duy nhất cho mọi vai: đăng bài là người cho, gửi yêu cầu là người nhận. |
| **Admin** | Duyệt bài, xử lý khiếu nại, xem báo cáo tài chính. Mọi hành động đều ghi `AuditLog`. |

Hệ thống ngoài: cổng thanh toán (VNPay sandbox), dịch vụ LLM, dịch vụ email (SMTP).

## Mô hình Credit & biểu phí

Chỉ thu phí ở hai chỗ có chi phí vận hành thật: **mở giao dịch** và **gọi AI**.

| Khoản mục | Mức phí | Ai trả |
|---|---|---|
| Đăng bài, tìm kiếm, gửi yêu cầu | 0đ | – |
| Giao dịch Trao đổi (Swap) | 2.000 Credit | Cả hai bên |
| Giao dịch Cho–Nhận (Give) | 4.000 Credit | Chỉ người nhận |
| AI gợi ý ghép đôi | 5.000 Credit/lượt (miễn phí 1 lượt/tuần) | Người bấm tìm |
| AI trợ lý tìm đồ | 1.000 Credit/lượt (miễn phí 5 lượt/ngày) | Người hỏi |

Mỗi giao dịch hoàn tất mang về đúng **4.000 Credit** cho hệ thống, bất kể hình thức. Nạp tối thiểu 10.000đ.

Số dư được kiểm tra ở **ba chốt**: lúc gửi yêu cầu, lúc được chọn, và lúc xác nhận lịch hẹn (chốt cuối giữ tạm tiền để tránh tiêu hai lần cho hai giao dịch song song).

## Luồng giao dịch

1. Người nhận gửi yêu cầu tới một bài đăng.
2. Chủ bài đăng chọn đúng một người → hệ thống mở khung chat.
3. Hai bên thương lượng thời gian, địa điểm (chat chặn thông tin liên lạc).
4. Cả hai xác nhận lịch hẹn → hệ thống trừ Credit → trao email và số điện thoại cho nhau.
5. Hai bên tự giao nhận và cùng xác nhận trên hệ thống.
6. Trong 7 ngày sau giao dịch, có thể khiếu nại; Admin xác minh rồi xử lý ở cấp tài khoản.

## Công nghệ

| Hạng mục | Lựa chọn |
|---|---|
| Backend | Java 17 · Spring Boot 3 (Web, Security + JWT, Data JPA, Validation, Scheduling) |
| Frontend | ReactJS · Vite · React Router · Axios · TailwindCSS |
| Cơ sở dữ liệu | Microsoft SQL Server |
| Kiểm thử | JUnit 5 · Mockito |
| Thanh toán | VNPay sandbox |
| Email / SMS | SMTP thật / SMS giả lập (in OTP ra console) |
| Công cụ | IntelliJ IDEA · Visual Studio Code · Antigravity |

## Kiến trúc

```
React SPA  →  Security (JWT)  →  Controller  →  Service  →  Repository (JPA)  →  SQL Server
                                                   ↓
                                    Integration (PaymentGateway / Ai / Notification)
                                    Scheduler (job dọn dẹp theo giờ)
```

Các nguyên tắc bắt buộc giữ xuyên suốt:

- Tiền chỉ được đụng tới trong `CreditService`, mọi thao tác chạy trong transaction có khoá dòng.
- Trạng thái `Request` chỉ được đổi trong `RequestService`, theo đúng sơ đồ trạng thái.
- Controller không chứa business rule; Service không trả entity thô, luôn đi qua DTO.
- Thông tin liên lạc của đối phương chỉ được trả về sau khi `ContactRevealedAt` có giá trị.
- Mọi lời gọi ra ngoài đều có timeout và đường lui.
- Mọi business rule kiểm tra ở FE đều phải được kiểm lại ở BE.

## Cấu trúc repository

```
ShareLoop/
├── .github/      # CODEOWNERS, mẫu Pull Request
├── frontend/     # ReactJS (Vite)
├── backend/      # Spring Boot (Maven), chia package theo module
├── docs/         # Tài liệu: SRS, ERD, wireframe, hợp đồng API
├── .editorconfig
├── .gitattributes
├── .gitignore
└── README.md
```

> Các thư mục sẽ được bổ sung dần theo từng nhánh.

## Bắt đầu nhanh

**Yêu cầu:** Node.js (LTS) · JDK 17 · SQL Server

### Frontend

```bash
cd frontend
npm install
npm run dev
```

### Backend

_Sẽ bổ sung khi nhánh backend được đẩy lên._

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
git switch -c feature/be-credit-topup     # tách nhánh mới từ develop
# ... code, commit ...
git push -u origin feature/be-credit-topup
# mở Pull Request vào develop và điền mẫu PR
```

### Đặt tên nhánh

Có tiền tố `be` hoặc `fe` để nhìn là biết phần nào:

```
feature/be-credit-topup
feature/be-request-choose
feature/fe-post-page
fix/be-hold-double-charge
docs/api-item
```

### Commit

Theo dạng `type(scope): mô tả ngắn`, scope là tên module:

```
feat(credit): add topup webhook
fix(request): reject invalid state transition
docs: update README
chore: bump spring boot version
```

Các type: `feat`, `fix`, `docs`, `refactor`, `test`, `chore`.

### Review và merge

- Mọi PR cần ít nhất 1 người duyệt. Người được gọi review tự động theo `.github/CODEOWNERS`.
- **Bắt buộc review bởi người ngoài module** với mọi thay đổi trong `CreditService` và `RequestService`.
- Sửa file dùng chung (`pom.xml`, `common/`, `config/`, `security/`): làm PR nhỏ, merge nhanh và báo cả nhóm.
- Mã do AI sinh ra phải được một thành viên đọc lại và chịu trách nhiệm.

### Không commit

`.env`, khoá API (VNPay, SMTP, LLM), mật khẩu, `application-local.properties`, `node_modules/`, `target/`.
Dùng `.env.example` hoặc file cấu hình mẫu chứa giá trị giả để hướng dẫn người khác.

## Kế hoạch 8 tuần

| Tuần | Trọng tâm |
|---|---|
| 1 | Chốt use case, ERD, wireframe; dựng khung React |
| 2 | Auth + OTP, Users, Items, chặn cứng |
| 3 | Module Credit và nạp tiền sandbox |
| 4 | Kiểm duyệt hai tầng, Requests |
| 5 | Chat, Hold, trao thông tin liên lạc (mốc quan trọng nhất) |
| 6 | State machine đầy đủ, khiếu nại, uy tín, job tự động, AI |
| 7 | Kiểm thử tích hợp, báo cáo tài chính, yêu cầu Should |
| 8 | Sửa lỗi, hoàn thiện, chuẩn bị demo |

## Nhóm thực hiện

| Vai trò | Họ tên |
|---|---|
| Frontend (ReactJS) | _điền tên_ |
| Backend 1 (Auth, Credit, Scheduler) | _điền tên_ |
| Backend 2 (Items, chặn cứng, AI) | _điền tên_ |
| Backend 3 (Requests, Chat) | _điền tên_ |
| Backend 4 (Admin, Khiếu nại, Uy tín) | _điền tên_ |

## Giấy phép

Dự án phục vụ mục đích học tập trong môn SWP391.
