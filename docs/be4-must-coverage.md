# Bảng đối chiếu độ phủ yêu cầu chức năng (FR Must Coverage) — BE4

Tài liệu rà soát và đối chiếu các yêu cầu chức năng cốt lõi (FR Must) thuộc phạm vi đảm nhiệm của BE4 (gồm các module: `setting`, `review`, `report`, `reputation`, `admin`, `audit`) cùng các yêu cầu quản trị liên quan trong khoảng FR-102 đến FR-115 theo SRS v10 và Lộ trình triển khai backend.

Mục tiêu: Đảm bảo 100% các yêu cầu **Must** đều có mã nguồn xử lý (Endpoint hoặc Service logic), kiểm thử tự động (Unit Test / Integration Test) và tài liệu API contract đầy đủ.

---

## 1. Bảng đối chiếu chi tiết các yêu cầu FR Must

| FR | Mô tả ngắn | Module / Phụ trách | Endpoint / Hàm | Test kiểm thử | Docs API | Trạng thái |
|---|---|---|---|---|---|---|
| **FR-45** | Hàng chờ duyệt sắp xếp theo rủi ro bài đăng và cờ AI | `review` (BE4) | `GET /api/v1/admin/reviews/pending`<br>`POST /api/v1/admin/reviews/{id}/approve`<br>`POST /api/v1/admin/reviews/{id}/reject`<br>`GET /api/v1/admin/reviews/reject-reasons` | `AdminReviewControllerTest`<br>`ReviewServiceTest` | `docs/api/review.md` | **Đủ** |
| **FR-88** | Báo cáo bài đăng hoặc người dùng vi phạm quy chế | `report` (BE4) | `POST /api/v1/reports` | `ReportControllerTest`<br>`ReportServiceTest` | `docs/api/report.md` | **Đủ** |
| **FR-89** | Khiếu nại giao dịch trong 7 ngày sau Completed | `report` (BE4) | `POST /api/v1/requests/{requestId}/disputes` | `DisputeControllerTest`<br>`ReportServiceTest` | `docs/api/report.md` | **Đủ** *(PR #263 / #112)* |
| **FR-90** | Mở 3 ngày cho bên bị khiếu nại gửi giải trình | `report` (BE4) | `POST /api/v1/disputes/{disputeId}/response` | `DisputeControllerTest`<br>`ReportServiceTest` | `docs/api/report.md` | **Đủ** *(PR #263 / #112)* |
| **FR-91** | Admin xem bằng chứng, kết luận khiếu nại, trừ 1 sao uy tín | `report` (BE4) | `POST /api/v1/admin/reports/{reportId}/conclusion`<br>`ReputationService.deductStar` | `AdminReportControllerTest`<br>`ReportServiceTest` | `docs/api/admin.md`<br>`docs/api/report.md` | **Đủ** *(PR #262 / #113)* |
| **FR-92** | Vô hiệu quyền đăng bài và gửi yêu cầu khi TrustStars về 0 | `reputation` (BE4) | `ReputationService.assertCanPostItem`<br>`ReputationService.assertCanCreateRequest` | `ReputationServiceTest`<br>`ItemServiceTest`<br>`RequestServiceTest` | `docs/api/reputation.md` | **Đủ** *(PR #258 / #114)* |
| **FR-93** | Rà soát các bài đăng khác của người vi phạm khi lý do là hàng cấm | `report` (BE4) | `ReportService.concludeReport`<br>(gọi `ItemService.hideAllOfUser`) | `AdminReportControllerTest`<br>`ReportServiceTest` | `docs/api/report.md` | **Đủ** *(PR #262 / #113)* |
| **FR-97** | Cộng điểm khi Completed: Cho +10/+2, Trao đổi +6/+6 | `reputation` (BE4) | `ReputationService.onCompleted(requestId)` | `ReputationServiceTest` | `docs/api/reputation.md` | **Đủ** *(PR #258 / #114)* |
| **FR-100** | Tính lại hạng (Rank) theo điểm và áp dụng quyền lợi ngay | `reputation` (BE4) | `ReputationService.getRank(int)`<br>`ReputationService.getRank(Long)` | `ReputationServiceTest` | `docs/api/reputation.md` | **Đủ** *(PR #258 / #114)* |
| **FR-101** | Xem thông tin điểm, hạng, sao, tỉ lệ đúng mô tả của thành viên | `reputation` (BE4) | `GET /api/v1/me/reputation`<br>`ReputationService.getReputationSummary(Long)` | `MeReputationControllerTest`<br>`ReputationServiceTest` | `docs/api/reputation.md` | **Đủ** *(#128)* |
| **FR-102** | Quản lý danh mục, cờ IsRestricted và thuộc tính động | `catalog` (BE2) | `GET, POST, PUT, DELETE /api/v1/admin/categories/**`<br>`GET, POST, PUT, DELETE /api/v1/admin/category-attributes/**` | `AdminCategoryControllerTest`<br>`AdminCategoryAttributeControllerTest` | `docs/api/catalog.md` | **Đủ** *(BE2 đã thực hiện)* |
| **FR-103** | Quản lý lexicon: từ khoá, mức độ, ngoại lệ; kiểm tra thử văn bản | `setting` (BE4) | `GET /api/v1/admin/lexicon`<br>`POST /api/v1/admin/lexicon`<br>`PUT /api/v1/admin/lexicon/{id}`<br>`PATCH /api/v1/admin/lexicon/{id}/active`<br>`POST /api/v1/admin/lexicon/test` | `AdminLexiconControllerTest`<br>`AdminLexiconServiceTest`<br>`LexiconMatcherTest` | `docs/api/setting.md` | **Đủ** |
| **FR-104** | Quản lý danh sách câu hỏi kiểm duyệt bài đăng (checklist) | `setting` (BE4) | `GET /api/v1/admin/checklist`<br>`POST /api/v1/admin/checklist`<br>`PUT /api/v1/admin/checklist/{id}`<br>`PATCH /api/v1/admin/checklist/{id}/active`<br>`PUT /api/v1/admin/checklist/reorder` | `AdminChecklistControllerTest`<br>`AdminChecklistServiceTest` | `docs/api/setting.md` | **Đủ** |
| **FR-105** | Quản lý cây khu vực hành chính (tỉnh/huyện) | `catalog` (BE2) | `GET, POST, PUT, DELETE /api/v1/admin/areas/**` | `AdminAreaControllerTest` | `docs/api/catalog.md` | **Đủ** *(BE2 đã thực hiện)* |
| **FR-106** | Cấu hình phí, hạn mức, mốc thời gian, ngưỡng AI không hard-code | `setting` (BE4) | `GET /api/v1/admin/configs`<br>`PUT /api/v1/admin/configs/{key}`<br>`PUT /api/v1/admin/configs`<br>`ConfigService.getInt/getDecimal` | `AdminConfigControllerTest`<br>`ConfigServiceTest`<br>`ConfigServiceIT` | `docs/api/setting.md` | **Đủ** |
| **FR-107** | Khoá và mở khoá tài khoản người dùng | `user` / `admin` (BE4) | `POST /api/v1/admin/users/{userId}/lock`<br>`POST /api/v1/admin/users/{userId}/unlock` | `AdminUserControllerTest`<br>`AdminUserServiceTest` | `docs/api/admin.md` | **Đủ** *(PR #257 / #116)* |
| **FR-109** | Báo cáo tài chính tiền mặt thu, Credit vào/ra theo loại | `wallet` / `admin` (BE4) | `GET /api/v1/admin/finance/report` | `AdminFinanceControllerTest`<br>`AdminFinanceServiceTest` | `docs/api/admin.md` | **Đủ** *(PR #256 / #115)* |
| **FR-110** | Dòng đối chiếu tiền mặt thanh toán với Credit nạp (TopUp) | `wallet` / `admin` (BE4) | `GET /api/v1/admin/finance/report` *(mục `reconciliation`)* | `AdminFinanceControllerTest`<br>`AdminFinanceServiceTest` | `docs/api/admin.md` | **Đủ** *(PR #256 / #115)* |
| **FR-112** | Tra cứu lịch sử tài chính và biến động số dư của người dùng | `wallet` / `admin` (BE4) | `GET /api/v1/admin/finance/users/{userId}/history` | `AdminFinanceControllerTest`<br>`AdminFinanceServiceTest` | `docs/api/admin.md` | **Đủ** *(PR #256 / #115)* |
| **FR-115** | Ghi nhật ký hành động (ActivityLogs) mọi thao tác Admin | `audit` (BE4) | `GET /api/v1/admin/activity-logs`<br>`ActivityLogService.log(...)` | `AdminActivityLogControllerTest`<br>`ActivityLogServiceTest` | `docs/api/admin.md` | **Đủ** *(PR #254 / #117)* |

---

## 2. Rà soát các yêu cầu Should / Could trong khoảng FR-102 – FR-116

Các yêu cầu này theo SRS v10 không thuộc danh sách **Must** bắt buộc cho cột mốc R5 ("Không cắt 100%"). Hiện trạng như sau:

| Mã FR | Mức ưu tiên | Mô tả yêu cầu | Hiện trạng và Đề xuất |
|---|---|---|---|
| **FR-108** | **Should** | Gỡ số điện thoại khỏi tài khoản khi có khiếu nại chiếm số | Đã có API đổi số điện thoại bằng OTP email qua `PUT /api/v1/me/phone` (#147). Chưa có API can thiệp gỡ tay cho Admin. Đề xuất: Chưa làm ở R5, chuyển sang R6 nếu cần. |
| **FR-111** | **Should** | Xuất báo cáo tài chính CSV | **Đã làm (#149):** `GET /api/v1/admin/finance/report.csv` (`AdminFinanceControllerTest`, `FinanceReportCsvWriterTest`, `docs/api/admin.md` mục 2.1). |
| **FR-113** | **Should** | Cảnh báo bất thường (cặp giao dịch lặp, nhiều tài khoản cùng IP...) | **Đã làm phần cặp giao dịch lặp (#148):** `GET /api/v1/admin/anomalies` (`AdminAnomalyServiceTest`, `AdminAnomalyControllerTest`, `AdminAnomalyIT`). Cảnh báo nhiều tài khoản cùng IP chưa làm, chờ method mới ở `UserService`. |
| **FR-114** | **Should** | Danh sách bài quá hạn và gỡ hẳn | Đã có `ItemExpiryJob` tự động quét và cập nhật trạng thái bài hết hạn. Đề xuất: Đạt yêu cầu hệ thống. |
| **FR-116** | **Could** | Thống kê số lượt gọi AI, chi phí ước tính, tỉ lệ đồng ý cờ AI | Thuộc nhóm phân tích bổ sung (Could). Đề xuất: Triển khai sau R5. |

---

## 3. Rà soát an toàn Admin & Ghi nhật ký (ActivityLogs)

Tất cả các endpoint thuộc nhóm `/api/v1/admin/**` đã được rà soát và kiểm tra kỹ lưỡng:

1. **Phân quyền và bảo mật (Admin-only)**:
   - Toàn bộ endpoint quản trị đều có kiểm thử xác thực:
     - Chưa đăng nhập trả về HTTP `401 UNAUTHORIZED`.
     - Người dùng thông thường (`USER`) gọi vào trả về HTTP `403 FORBIDDEN`.
     - Chỉ token có vai trò `ADMIN` mới được phép thực thi.
2. **Ghi vết hành động (Activity Logging - FR-115)**:
   - Mọi thao tác làm thay đổi dữ liệu hoặc trạng thái của Admin đều ghi một dòng vào bảng `activity_logs`. Cột `type` lưu đúng tên hằng của enum `ActivityType`:
     - **Khoá tài khoản**: Ghi log `USER_LOCK` kèm lý do vi phạm (`reason`).
     - **Mở khoá tài khoản**: Ghi log `USER_UNLOCK` kèm lý do (`reason`).
     - **Duyệt bài đăng**: Ghi log `REVIEW_APPROVE` kèm kết quả checklist.
     - **Từ chối bài đăng**: Ghi log `REVIEW_REJECT` kèm lý do từ chối.
     - **Cập nhật cấu hình hệ thống**: Ghi log `CONFIG_UPDATE` kèm key, giá trị cũ/mới và lý do.
     - **Kết luận báo cáo / khiếu nại**: Ghi log `REPORT_UPHELD` (có cơ sở) hoặc `REPORT_DISMISSED` (bác bỏ) kèm quyết định và ghi chú.
     - **Lexicon, checklist**: `LEXICON_CREATE/UPDATE/TOGGLE`, `CHECKLIST_CREATE/UPDATE/TOGGLE/REORDER`.
     - **Uy tín**: `GRACE_STAR` (Admin cấp sao ân hạn), `STAR_RESTORED` (hệ thống tự hồi sao).

---

## 4. Tình trạng tài liệu `docs/api/` cho các module BE4

Các tài liệu API contract đã đầy đủ và đồng bộ với mã nguồn:
- **`docs/api/admin.md`**: Nhật ký hành động (kèm danh sách loại), Báo cáo tài chính (JSON và CSV), Lịch sử giao dịch ví của người dùng, Khoá và mở khoá tài khoản, Cấp sao ân hạn, Cảnh báo bất thường.
- **`docs/api/report.md`**: Đầy đủ endpoint báo cáo vi phạm, khiếu nại giao dịch trong 7 ngày, giải trình khiếu nại trong 3 ngày và Admin kết luận.
- **`docs/api/review.md`**: Đầy đủ endpoint danh sách hàng chờ kèm checklist, duyệt bài, từ chối bài và danh sách mẫu lý do.
- **`docs/api/setting.md`**: Đầy đủ endpoint quản lý cấu hình hệ thống, từ điển lexicon và checklist câu hỏi duyệt bài.
- **`docs/api/reputation.md`**: Đầy đủ mô tả `ReputationService`, thang điểm, các ngưỡng hạng, cấu trúc `ReputationSummary` và `GET /api/v1/me/reputation` cho Member xem uy tín của chính mình (FR-101).

