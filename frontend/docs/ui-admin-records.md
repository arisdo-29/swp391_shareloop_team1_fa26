# Thành viên và giao dịch món đồ — đợt 8

Baseline `0ebc526`, nhánh `feat/shareloop-ui-redesign`. Đầu đợt chỉ có `docs/design/` untracked, giữ nguyên.

## Bảng bảo toàn hành vi trước khi sửa

| Đối tượng/trạng thái | Quyền/hành động hiện có | Điều kiện | Handler |
| --- | --- | --- | --- |
| Danh sách thành viên | Admin tìm/lọc/phân trang | Debounce từ khóa, status/role, page/size như cũ | Effect `AdminAccounts`, `adminUserApi.list` |
| USER ACTIVE/PENDING_VERIFICATION | Khóa | `availableAction` trả lock | Mở `LockDialog`, `submit` → `adminFinanceApi.setLocked(id,true,reason)` |
| USER LOCKED | Mở khóa | `availableAction` trả unlock | Cùng dialog, `setLocked(id,false,reason)` |
| ADMIN hoặc BANNED | Không khóa/mở khóa | `availableAction` trả null | Không thêm nút xử lý |
| Khóa/mở khóa | Nhập lý do, xác nhận | Lý do trim không rỗng, tối đa 500, ref chống gửi lặp/busy | `LockDialog.submit`, `onDone` tải lại danh sách sau API |
| USER | Link xem Credit hiện có | `account.role === 'USER'` | `/admin/finance/users/:id`, giữ nguyên liên kết |
| Danh sách giao dịch | Admin tìm/lọc/phân trang/tải lại | Submit q, status, stuck; xử lý trang rỗng như cũ | Effect `AdminTransactions`, `adminRequestApi.list` |
| RESERVED/AWAITING_LOGISTICS/LOGISTICS_CONFIRMED/AWAITING_HANDOVER/DISPUTED | Admin hủy | `cancellableStatuses`, backend kiểm lại | `CancelDialog.submit`, `adminRequestApi.cancel` |
| Trạng thái khác | Chỉ xem, không hủy | Không thuộc danh sách hủy cũ | Không thêm xác nhận giao/nhận hoặc chuyển trạng thái |
| Hủy giao dịch | Lý do, xác nhận | Trim ít nhất 5, tối đa 500, busy/ref chống gửi lặp | `submit`, `onCancelled` tải lại sau API |
| API lỗi/quyền/trạng thái thay đổi | Giữ dialog, hiện lỗi | 403, REQUEST_INVALID_STATE_TRANSITION, REQUEST_NOT_FOUND và thông báo cũ | `adminErrorMessage`, `errorMessage` |

## Nguồn và giới hạn chi tiết

- Thành viên chỉ có `AdminUserSummary`: tên, email, điện thoại, role, status, locked, ngày tạo, ID. Không có avatar/khu vực/uy tín/hạng/tin đăng/lịch sử xử lý. Không mở tab giả hoặc gọi thêm nguồn tài chính.
- Giao dịch chỉ có `AdminRequestRow`: mã hiện có, bài, loại, trạng thái, các bên, giờ hẹn, ngày tạo và cờ bị kẹt. Không có món đề nghị trao đổi, địa điểm, xác nhận giao/nhận từng bên, lý do hủy, lịch sử chat hay kết luận khiếu nại.
- Chưa có API chi tiết admin trong hai service này. Route `/admin/transactions/:transactionId` hiện redirect về danh sách, giữ nguyên.
- Panel chi tiết mới chỉ đọc props đang có trên hàng, state mới chỉ mở/đóng UI. Không sao chép model nghiệp vụ hoặc tạo request mới.

## Giao diện đã hoàn thành

- Tái sử dụng admin layout, sidebar, breadcrumb, token và Be Vietnam Pro của đợt trước, không sửa shell hoặc trang ngoài phạm vi.
- Thành viên: bộ lọc và bảng rõ cột tên/liên hệ/vai trò/trạng thái/ngày tạo/thao tác. Biểu tượng tài khoản dùng chung chỉ là icon giao diện, không giả lập avatar hoặc trạng thái online.
- Panel thành viên nhóm tóm tắt, vai trò/trạng thái và thông tin tài khoản hiện có. ADMIN/BANNED vẫn xem thông tin được backend trả nhưng không được mở thêm thao tác khóa/mở khóa. Link Credit hiện có giữ nguyên điều kiện và URL.
- Giao dịch: bảng cho tặng/trao đổi, các bên, trạng thái, giờ hẹn, cờ bị kẹt và thao tác. Mã giao dịch tiếp tục lấy đúng trường đang hiển thị trước đợt này, không tự tạo mã mới.
- Panel giao dịch chia tóm tắt, chủ bài/người nhận, món đồ trong bài, hình thức, trạng thái, thời điểm tạo/giờ hẹn và cờ cần xem xét nếu có. Không gán vai trò người mua/người bán hoặc tạo món trao đổi thứ hai khi schema không cung cấp.
- Nút khóa/mở khóa/hủy giữ tại hàng và mở đúng dialog cũ. Panel mới chỉ xem, không nhân đôi các nút xử lý hoặc mở quyền mới.
- `AdminRecordDetails` dùng `DialogFrame` sẵn có, mở/đóng bằng state UI, nhận trực tiếp nội dung từ props. Giữ Escape, focus trap và trả focus của component chung. Không gọi API khi mở/đóng.
- Desktop dùng bảng và panel. Mobile bảng cuộn trong khung riêng, bộ lọc xếp dọc, panel có một cột và nút về danh sách. Tên dài/thiếu số điện thoại/món đồ đã xóa được trình bày từ dữ liệu hoặc fallback cũ.
- CSS chỉ áp dụng class của hai màn và dialog mới, hỗ trợ sáng/tối theo token hiện có. Không sửa CSS global hoặc thêm dependency.

## File thay đổi

- `src/pages/admin/AdminAccounts.tsx`
- `src/pages/admin/AdminTransactions.tsx`
- `src/pages/admin/AdminRecordDetails.tsx` (mới)
- `src/pages/admin/admin-records.css` (mới)
- `src/pages/admin/AdminAccounts.test.tsx`
- `src/pages/admin/AdminTransactions.test.tsx`
- `docs/ui-admin-records.md` — bảng quyền/hành động và báo cáo này.

## Kiểm tra

- Build baseline và build sau triển khai đạt. Lint và `git diff --check` đạt. Cảnh báo bundle chính vượt 500 kB đã tồn tại từ trước.
- Vitest toàn frontend: **595/595** test đạt, bộ API hiện có **28/28** đạt. Bộ test hai màn đạt **14/14**.
- Thêm hai test kiểm tra mở đúng panel không gọi thêm API hoặc xử lý tài khoản/giao dịch, gồm trả focus khi đóng. Assertion cũ về ADMIN được giới hạn đúng vào nút khóa/mở khóa để cho phép nút chỉ xem, vẫn bảo vệ đầy đủ điều kiện quản trị. Không bỏ hoặc tắt test.
- So sánh AST với snapshot đầu đợt: phần ngoài JSX của hai file runtime cũ giữ nguyên sau khi bỏ import trình bày, mọi handler JSX cũ được bảo toàn. Không thêm handler trong hai file này.
- Backend, service/API, enum/schema, auth/guard, hook, utils, realtime, dependencies, theme/global CSS và môi trường không đổi.
- Chrome local: danh sách thành viên, panel thành viên, danh sách giao dịch và panel giao dịch tại **390/768/1440 px**, sáng/tối, không tràn ngang toàn trang hoặc dialog. Thử tên/email/món đồ dài, số điện thoại trống và dữ liệu bài/chủ bài thiếu.
- Mở panel không thêm request đọc. Escape đóng và trả focus về nút mở. Xem đúng đối tượng theo hàng hiện tại, giữ link Credit.
- Thành viên: debounce từ khóa, lọc trạng thái/vai trò, phân trang, bảo vệ ADMIN/BANNED, lý do khóa, lỗi quyền/giữ bản nháp, bấm xác nhận lặp chỉ một request ghi, mở khóa và tải lại sau phản hồi API.
- Giao dịch: cho tặng/trao đổi, các trạng thái trong response, bộ lọc status/stuck, submit tìm kiếm và phân trang. Trạng thái hoàn tất không có nút hủy, các bên hiển thị theo đúng vai trò dữ liệu.
- Hủy cần ít nhất 5 ký tự, lỗi 409 giữ dialog/thông báo cũ, thử lại và tải lại trạng thái sau thành công. Không thêm xác nhận giao/nhận hoặc chuyển trạng thái bằng local state.
- Hai màn có loading, lỗi/thử lại, rỗng theo cơ chế cũ. Kiểm tra route chi tiết giao dịch vẫn redirect về danh sách, không tự mở panel hoặc đổi URL. Member bị guard chặn khỏi admin.

Kiểm tra trình duyệt sử dụng request interception và phản hồi thử nghiệm trong script tạm ngoài repo. Mọi request ghi bị chặn, không khóa/mở khóa tài khoản thật hoặc sửa giao dịch production. Source ứng dụng tiếp tục lấy dữ liệu từ API thật, không có fixture hoặc localStorage nghiệp vụ mới. Chưa có backend test/tài khoản phù hợp để xác minh quyền backend và tác động thực tế tới giao dịch, phí, tín dụng, uy tín hoặc thông báo.

## Giới hạn giữ nguyên

- Panel chỉ xem thông tin của hàng đã tải, không phải hồ sơ đầy đủ từ API chi tiết. Không có deep link mới cho panel, không thay redirect cũ của `/admin/transactions/:transactionId`.
- Không có mã thành viên công khai, ảnh đại diện hoặc khu vực trong `AdminUserSummary`. Không dùng ID kỹ thuật làm mã hiển thị, không tự tạo thông tin xác minh/hoạt động.
- Uy tín/hạng/đánh giá, tin đăng/giao dịch liên quan, lịch sử khóa/mở khóa của thành viên chưa có nguồn ở màn hiện tại. Không thêm tab hoặc request để lấp các khu vực này. Credit vẫn là liên kết sang trang hiện có, chưa redesign tài chính.
- Chi tiết lịch hẹn, xác nhận từng bên, món đề nghị trao đổi, lý do hủy, lịch sử sự kiện/chat và khiếu nại chưa có trong `AdminRequestRow`. Không suy ra các mốc hoặc kết luận từ status và giờ hẹn.
- Hai màn giữ dữ liệu danh sách cũ trong một số lần tải lại/lọc khi đã có result, theo điều kiện loading cũ. Không thay query/cache hoặc cách giữ dữ liệu để sửa hành vi đó.
- Các lỗi quyền/trạng thái thay đổi vẫn dùng thông báo và cơ chế tải lại hiện có, không tự chuyển trạng thái hoặc sửa quy tắc xử lý.

## Local

- Thành viên: **http://localhost:5173/admin/users**
- Giao dịch món đồ: **http://localhost:5173/admin/transactions**
- Mở **Chi tiết** trên từng hàng để xem panel dữ liệu hiện có. Hành động quản trị vẫn ở hàng tương ứng.

Đăng nhập bằng tài khoản admin sẵn có. Chưa stage, commit, push, PR, merge hoặc deploy. `docs/design/` được giữ nguyên.
