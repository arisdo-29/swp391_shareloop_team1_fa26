# Admin layout, tổng quan và duyệt bài — đợt 7

Baseline `127627a`, nhánh `feat/shareloop-ui-redesign`. Đầu đợt chỉ có `docs/design/` untracked, giữ nguyên. Build baseline đạt, có cảnh báo bundle trên 500 kB từ trước.

## Bảng bảo toàn hành vi trước khi sửa

| Trạng thái/ngữ cảnh | Hành động hiện có | Điều kiện | Handler/nguồn |
| --- | --- | --- | --- |
| Hàng chờ | Tải, tìm trong tập đã tải, mở chi tiết | `pendingReviews({page:0,size:100})`, `matchesQuery`; bổ sung thumbnail thiếu bằng detail như trước | Effect `AdminModerationList`, `loadMissingThumbnails`, link `/admin/moderation/:itemId` |
| Chi tiết hợp lệ | Xem nội dung/ảnh/người đăng/cờ kiểm tra | ID hợp lệ, API detail + rejectReasons + checklist quản trị tải thành công | Effect `AdminModerationDetail` |
| PENDING_REVIEW | Tích checklist, duyệt | Checklist active mới nhất, mọi đáp án đạt, `inFlight` khóa gửi lặp; refetch checklist trước submit | `approve`, `activeReviewChecklist`, `sameChecklist` |
| Checklist thay đổi | Tích lại bộ mới | So sánh trước submit hoặc REVIEW_CHECKLIST_OUTDATED | `approve`/catch và tải lại hiện có |
| PENDING_REVIEW | Từ chối | Chọn ít nhất một lý do, OTHER có nội dung, không busy/inFlight | `RejectReasonsContent`, `composeRejectReason`, `reject` |
| Không còn PENDING_REVIEW | Chỉ xem | `reviewable` false | Alert hiện có, không mở nút xử lý |
| 404/ID sai/API lỗi | Báo lỗi/thử lại/quay về | Điều kiện hiện có | `adminErrorMessage`, `setReloadKey`, `backToList` |
| Xử lý thành công | Về danh sách và thông báo | Chờ API approve/reject hoàn tất | `backToList(message)`, state notice cũ |
| Ảnh bài/lỗi | Chọn thumbnail, xem lớn, trước/sau, đóng | Danh sách ảnh hiện có, resolveMediaUrl cũ | State UI và handler `ModerationImageGallery` |

Không có yêu cầu bổ sung, ẩn hoặc duyệt hàng loạt trong màn duyệt hiện tại. Không thêm các thao tác này. Không đổi trạng thái hay phí/tín dụng/uy tín ở frontend.

## Nguồn dữ liệu dashboard đã xác định

| Khối | Nguồn hiện tại | Phạm vi |
| --- | --- | --- |
| Tiền thu | `adminFinanceApi.report({}).cashAmountVnd` | `from`/`to` từ report, không truyền bộ lọc ở dashboard |
| Credit cộng/trừ | Cùng report, `creditInTotal`/`creditOutTotal` | Cùng phạm vi report, không gộp với tiền VND |
| Bài chờ | `pendingReviews({page:0,size:100}).items.length` | Lượt đọc giới hạn 100, cơ chế 100+ có sẵn |
| Khiếu nại/báo cáo mở | Hai `reportApi.list` OPEN và AWAITING_RESPONSE | Cộng `totalElements` do backend trả, không đếm dòng một trang |
| Cảnh báo giao dịch lặp | `adminAnomalyApi.report().repeatedPairs.length` | Tập cặp trong response hiện có, không tự quy định mức khẩn cấp |
| Hoạt động gần đây | `adminAuditApi.list({page:0,size:5}).content` | Tối đa 5 bản ghi mới nhất, không phải tổng nhật ký |

Dashboard không có biểu đồ hoặc bộ chọn thời gian hiện tại. Không nhúng biểu đồ tài chính hoặc gọi thêm endpoint chỉ để lấp chỗ trống.

## Giao diện đã hoàn thành

- Hoàn thiện chính `AdminLayout`, không tạo shell thứ hai. Sidebar bề mặt trung tính, nhóm điều hướng gọn, trạng thái đang chọn rõ, giữ đủ route, biểu tượng và thao tác thu gọn hiện có.
- Breadcrumb nhẹ từ danh sách route đã có, hiển thị phần chi tiết mà không lộ ID kỹ thuật. Alias khiếu nại `/admin/disputes` tiếp tục hoạt động. Không thêm route hoặc chuyển hướng nghiệp vụ.
- Mobile có nền che cho menu, bấm nền hoặc Escape để đóng và trả focus về nút mở. Hai handler mới chỉ phục vụ giao diện. Giữ đăng xuất, menu tài khoản, thông báo và theme trong AppShell hiện có.
- Dashboard theo thứ tự số liệu → việc cần làm → nhật ký. Chỉ số có đơn vị và phạm vi ngày từ report. Credit tách khỏi VND. Loading/error không thay bằng số 0 xác nhận, nhãn loading của ô việc chờ được làm rõ.
- Hàng chờ có thanh tìm trong lượt tải, bảng gọn, ảnh, người đăng, uy tín, thời điểm, cờ kiểm tra và link chi tiết như trước. Số lượng ghi rõ là số bài trong lượt tải, không trình bày như tổng hệ thống.
- Chi tiết duyệt giữ trang riêng: trạng thái, tiêu đề, ảnh/ảnh lỗi, thông tin món đồ, người đăng, kết quả kiểm tra và panel checklist/duyệt/từ chối. Không cắt mô tả hoặc tự dựng lịch sử. Dưới 1280 px, panel chuyển xuống dưới để không bó hẹp nội dung.
- Giữ chọn thumbnail, xem lớn, trước/sau và đóng ảnh bằng cơ chế hiện có. Giữ ghi chú/lý do khi đổi theme hoặc render lại theo cùng state cũ.
- Sáng/tối/theo hệ thống và Be Vietnam Pro kế thừa nền tảng hiện tại. Không thêm thư viện, token hoặc CSS global. CSS mới chỉ áp dụng class admin mới hoặc class component kiểm duyệt mới.

## File thay đổi

- `src/pages/admin/AdminLayout.tsx`
- `src/pages/admin/AdminBreadcrumb.tsx` (mới)
- `src/pages/admin/admin-workspace.css` (mới)
- `src/pages/admin/AdminFinancePages.tsx` — chỉ JSX của `AdminOperationsDashboard`; `AdminFinanceReport` và toàn bộ hàm phía sau giữ nguyên.
- `src/pages/admin/AdminTodoCards.tsx`
- `src/pages/admin/AdminModerationList.tsx`
- `src/pages/admin/AdminModerationDetail.tsx`
- `src/pages/admin/ModerationParts.tsx`
- `docs/ui-admin-operations.md` — bảng hành vi, nguồn dữ liệu và báo cáo này.

## Kiểm tra

- Build baseline và build sau triển khai đạt, lint đạt. Cảnh báo bundle chính trên 500 kB đã có trước đợt này.
- Vitest toàn frontend **593/593**, bộ API hiện có **28/28**. Bộ 37 test liên quan layout nội dung/dashboard/kiểm duyệt/khiếu nại đạt. Sau chỉnh nhãn loading, chạy lại 4 test dashboard/việc chờ và đạt.
- Đối chiếu AST với snapshot đầu đợt: phần ngoài JSX của sáu file cũ giữ nguyên sau khi bỏ import trình bày. Mọi handler cũ được bảo toàn. Chỉ thêm handler Escape và bấm nền đóng sidebar.
- Không thay backend, API/service, schema/enum, auth/guard/interceptor, hooks nghiệp vụ, utils, realtime, dependencies hoặc môi trường. Phần báo cáo tài chính và các hàm còn lại trong file dùng chung được so sánh nguyên văn và không đổi.
- Chrome local kiểm tra dashboard, hàng chờ và chi tiết tại **390/768/1440 px**, sáng/tối, không tràn ngang toàn trang. Bảng cuộn trong vùng riêng. Kiểm tra tên bài, mô tả/URL và nhật ký dài.
- Sidebar thu gọn/mở rộng, menu mobile, Escape/bấm nền và trả focus, breadcrumb/deep link. Member bị guard chặn khỏi admin. Theme theo hệ thống đổi theo media preference và giữ lựa chọn hiện có.
- Dashboard loading/data, báo cáo lỗi nhưng nhật ký vẫn hiện, nhật ký lỗi nhưng số liệu vẫn hiện, response số 0 thật và nhật ký rỗng.
- Hàng chờ tìm theo người đăng trong tập đã tải, mở đúng bài, lỗi/retry/empty. Chi tiết bài đã xử lý và không tồn tại giữ đúng trạng thái chỉ xem/lỗi.
- Chọn ảnh, phóng to, ảnh trước và Escape đóng. Checklist chưa đủ khóa duyệt, checklist thay đổi buộc tích lại, payload ID/đáp án không đổi, bấm duyệt lặp chỉ một request ghi.
- Từ chối: chưa chọn lý do bị khóa, lý do khác phải có nội dung, giữ bản nháp khi đổi theme, gửi đúng endpoint/bài đã chọn và trở về danh sách với thông báo cũ.
- Kiểm tra hồi quy danh sách/chi tiết khiếu nại sáng/tối tại ba kích thước, nút yêu cầu giải trình/kết luận và alias vẫn hiện theo trạng thái cũ.

Kiểm tra trình duyệt dùng request interception và phản hồi thử nghiệm trong script tạm ngoài repo. Mọi request ghi bị chặn, không duyệt/từ chối hoặc thay đổi tin production. Source ứng dụng tiếp tục lấy dữ liệu từ backend, không có mock/localStorage làm nguồn nghiệp vụ. Chưa có tài khoản/backend test để xác minh số liệu, kiểm duyệt, tác động phí/tín dụng/thông báo và realtime qua hệ thống thật.

## Giới hạn được giữ nguyên

- Hàng chờ chỉ có một lượt tối đa 100 bài và tìm trong tập đó. Chưa có phân trang, debounce, bộ lọc danh mục/trạng thái hoặc sắp xếp của màn này để thiết kế lại. Không thêm chức năng giả.
- Schema queue không có danh mục, hình thức và trạng thái riêng trên từng dòng. Chi tiết mới có đủ các trường này. Không thêm request để làm giàu bảng. Cơ chế bổ sung thumbnail thiếu qua detail vốn có vẫn được giữ.
- Không có mã tin công khai riêng. Test hiện có yêu cầu không hiện ID kỹ thuật, nên không tự tạo mã hoặc dùng ID thay mã hiển thị.
- Chưa có lịch sử kiểm duyệt hoặc tab lịch sử trong trang duyệt. Chỉ hiển thị trạng thái/cờ/nội dung hiện có.
- Dashboard không có bộ chọn thời gian hay biểu đồ. Tài chính vẫn là trang riêng, chưa redesign trong đợt này.
- Ô việc chờ chỉ tải một lần mỗi lần mount theo effect hiện tại. Retry báo cáo/nhật ký không làm mới riêng các ô này. Không đổi chu kỳ tải hoặc thêm realtime.
- Cảnh báo cặp giao dịch dùng tập kết quả từ endpoint hiện có. Không biến số này thành tổng mọi cảnh báo hoặc tự thêm khoảng thời gian chưa được giữ trong state màn hình.

## Local

- Tổng quan: **http://localhost:5173/admin**
- Hàng chờ: **http://localhost:5173/admin/moderation**
- Chi tiết: `/admin/moderation/:itemId` từ link bài có quyền xem.
- Hồi quy khiếu nại: **http://localhost:5173/admin/complaints**

Đăng nhập bằng tài khoản admin sẵn có. Không xử lý tin thật chỉ để thử giao diện. Chưa stage, commit, push, PR, merge hoặc deploy.
