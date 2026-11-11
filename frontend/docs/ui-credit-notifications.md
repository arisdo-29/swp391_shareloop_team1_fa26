# Ví Credit và thông báo — đợt 5

Baseline: `a06f156`, nhánh `feat/shareloop-ui-redesign`. Lúc bắt đầu chỉ có `docs/design/` untracked, được giữ nguyên. Không stage, commit, push, PR, merge hoặc deploy trong đợt này.

## Nguồn dữ liệu và hành vi được bảo toàn

| Khu vực | Nguồn chính thức | Hành vi giữ nguyên |
| --- | --- | --- |
| Số dư | `WalletProvider` → `walletApi.get()` | Hiển thị trực tiếp `available`, `heldCredit`, `creditBalance`. Đang tải/lỗi dùng `--`, không thay bằng số 0. Retry gọi `refreshWallet`. Không tự tính hoặc lưu số dư. |
| Nạp Credit | `ApiTopupSelection`, `continuePayment`, `paymentApi.createTopUpOrder` | Mức chọn nhanh chỉ điền số tiền. Giữ validation số nguyên dương, dialog cảnh báo hiện có, `createTopUpInFlight` và trạng thái submit. Chỉ tạo đơn sau khi xác nhận. Credit nhận được lấy từ đơn backend, không tự quy đổi. |
| Chuyển hướng | `startPayment`, `paymentRedirectUrl` | Giữ nguyên provider, kiểm tra URL và `window.location.assign`. Không thêm QR, phương thức nạp hoặc nút mô phỏng. |
| Kết quả | Query hiện có → `paymentApi.getOrder` → `resolveTopUpReturnResult` | Giữ ưu tiên trạng thái backend, success/pending/failed/cancelled/expired/invalid, nút kiểm tra lại và tạo đơn khác. Không suy ra thành công từ query. Success làm mới ví và lịch sử như trước. Không thêm polling. |
| Ghi nhớ đơn | `topUpSession` | Session chỉ giữ mã tham chiếu đơn. Không lưu số dư, giá trị quy đổi hoặc trạng thái thanh toán làm nguồn chính thức. |
| Lịch sử biến động | `walletApi.listTransactions`, `listAllTransactions`, `creditHistory` | Giữ ngày lọc, timezone, trang, số dòng, tab, dấu cộng/trừ, tổng hợp, chi tiết và tên món từ `itemApi.mine`. Không thêm danh sách đơn nạp giả hoặc đồng nhất đơn nạp với ledger. |
| Phí đang giữ | `walletApi.listHeldFees` | Giữ dữ liệu, nhãn, trạng thái, link món đồ, loading/empty/error/retry. |
| Trang thông báo | `notificationApi.listAll` | Giữ lọc/tìm kiếm/phân trang, chọn dòng, đọc/ẩn/dọn đã đọc, nội dung lỗi và deep link. Không thêm filter hoặc thao tác nghiệp vụ mới. |
| Dropdown | `NotificationMenu`, `notificationApi.list`, `unreadCount` | Giữ phân trang/tải thêm, chống trùng ID, đánh dấu đọc và lấy lại bộ đếm từ backend. Mở dropdown không tự đánh dấu đọc. Giữ subscription và tải lại theo realtime. |

Contract `docs/api/payment.md` xác định IPN cập nhật đơn, số dư và ledger trong cùng transaction. Return URL chỉ là tín hiệu, frontend tiếp tục đọc trạng thái đơn qua backend. Không thử IPN/chữ ký hoặc thao tác backend trong đợt này.

## Giao diện đã triển khai

- Ví: thẻ xanh rừng làm nổi bật Credit khả dụng, số đang giữ và tổng lấy từ API, liên kết cuộn tới khu nạp hiện có. Credit tách biệt với tiền VND và uy tín.
- Nạp: vùng nhập và các mức điền nhanh rõ ràng, trạng thái đang chọn, nút tạo đơn, cảnh báo và lỗi nghiệp vụ hiện có. Không thêm chính sách, mức quy đổi hoặc giới hạn.
- Kết quả: card đơn đồng bộ theme, mã/số tiền/Credit/provider/trạng thái từ props hiện có, giữ nguyên liên kết thanh toán và nút hiện tại.
- Ledger: tiêu đề cột trên desktop, dòng giao dịch có ngày/giờ, dấu và nhãn trạng thái. Mobile chuyển sang bố cục card một cột. Giữ dialog chi tiết và bộ lọc hiện tại.
- Thông báo: khối lọc, thao tác hàng loạt, hàng đã đọc/chưa đọc và dropdown đồng bộ sáng/tối. Nội dung/URL dài xuống dòng, menu có vùng cuộn riêng và footer xem tất cả.
- Dropdown member thêm xử lý phím Escape để đóng và trả focus về chuông. Đây là handler giao diện duy nhất được bổ sung. Các handler hiện có giữ nguyên.
- CSS giới hạn trong `.credit-page`, `.member-notifications`, `.member-notification-menu`. Admin không nhận các class bao này. Tiêu đề cột mới trong component lịch sử dùng chung mặc định ẩn, chỉ hiện ở ví member.
- Tái sử dụng token, font Be Vietnam Pro và component hiện có. Không đổi theme nền tảng, framework hoặc dependencies. Trang checkout thử nghiệm có sẵn và điều kiện bật của nó được giữ nguyên.

## File thay đổi

- `src/pages/user/Credit.tsx`
- `src/pages/user/credit.css` (mới)
- `src/components/CreditHistoryView.tsx`
- `src/pages/user/Notifications.tsx`
- `src/components/NotificationMenu.tsx`
- `src/components/member-notifications.css` (mới)
- `docs/ui-credit-notifications.md` (báo cáo này)

## Kiểm tra

- Build và lint đạt. Cảnh báo bundle chính vượt 500 kB đã tồn tại trước đợt này.
- Vitest toàn frontend: **593/593**, không bỏ qua test. Bộ API hiện có: **28/28**. Không sửa hoặc bỏ test.
- Đối chiếu AST với snapshot đầu đợt: phần ngoài JSX của cả bốn file TSX không đổi sau khi bỏ import trình bày. Toàn bộ handler JSX cũ được giữ nguyên. Chỉ thêm handler Escape phục vụ giao diện.
- Không thay backend, API/service, schema, auth/guard, hook/provider, utility, realtime, dependencies hoặc môi trường.
- Chrome local kiểm tra Ví, kết quả đang chờ, trang thông báo và dropdown tại **390/768/1440 px**, cả sáng/tối, không tràn ngang toàn trang. Dùng số dư dài và nội dung/URL dài để kiểm tra xuống dòng.
- Kiểm tra số dư loading/data/error/retry, lịch sử/chi tiết/phân trang/lọc ngày, validation số tiền, dialog trước khi tạo đơn và lỗi nghiệp vụ.
- Kiểm tra tạo đơn thành công với phản hồi thử nghiệm, chuyển hướng tới địa chỉ gateway local được chặn và bấm xác nhận hai lần chỉ phát sinh một request tạo đơn. Không kết nối cổng thanh toán thật.
- Kiểm tra trạng thái kết quả theo response backend: pending/success/failed/expired, hủy khi return failed và chữ ký return không hợp lệ.
- Kiểm tra thông báo không đánh dấu đọc khi mở menu, tải thêm, đọc một thông báo, cập nhật bộ đếm, Escape/focus, bộ lọc/phân trang, deep link, lỗi và rỗng.

Kiểm tra trình duyệt sử dụng request interception và phản hồi thử nghiệm trong script tạm ngoài repo. Mọi request ghi bị chặn, không tạo đơn production, thanh toán thật hoặc sửa thông báo thật. Không có fixture/mock database trong source ứng dụng. Chưa kiểm chứng VNPay sandbox, IPN, chữ ký và realtime qua backend test thật.

## Giới hạn có sẵn được giữ nguyên

- `AppShell.tsx`, vùng `headerActionsRef` có handler đóng menu khi bấm nút/link không có `aria-expanded`. Vì vậy bấm lọc, đọc hoặc tải thêm đóng dropdown như trước. Kiểm tra dữ liệu tiếp tục bằng cách mở lại. Không sửa handler dùng chung trong đợt này.
- `Credit.tsx` và `creditHistory.ts`: tab lịch sử lọc các dòng của trang API hiện tại, trong khi số trên tab lấy counts của toàn khoảng ngày. Có thể gặp trang/tab rỗng dù số đếm lớn hơn 0. Không đổi truy vấn hoặc phân trang để sửa hành vi này.
- `walletApi.listAllTransactions` mặc định giới hạn 20 trang × 100 dòng. Tổng hợp hiện có có thể thiếu nếu khoảng ngày vượt 2.000 biến động. Không thay cách tổng hợp hoặc hạn mức tải.
- `notificationApi.listAll` tải tối đa 10 trang × 100 thông báo. Tìm kiếm/lọc trang hiện tại chạy trên tập đã tải này. Các thao tác xóa/dọn hiện có thực chất ẩn theo trình duyệt qua service, không xóa trên backend. Không thêm hoặc đổi cơ chế đó.
- Dòng thành công hiện có trong `Credit.tsx` thông báo ví đã cập nhật ngay khi đọc được đơn SUCCESS, trong khi request làm mới ví còn bất đồng bộ. Thẻ số dư vẫn hiển thị `--` khi đang tải, lỗi có retry. Không thay ánh xạ/trình tự thanh toán.
- Effect đọc lại đơn trong `Credit.tsx` không xóa `topUpError` cũ khi thay query sang một đơn khác. Sau một lần tạo đơn lỗi, thông báo lỗi cũ có thể còn hiện cạnh đơn đang mở. Không sửa state/handler để khắc phục trong đợt trình bày.

## Local

- Ví và nạp: **http://localhost:5173/credit**
- Thông báo: **http://localhost:5173/notifications**
- Dropdown: chuông thông báo ở header sau khi đăng nhập bằng tài khoản sẵn có.

Kết quả thanh toán dùng query/đơn hiện có do luồng backend cung cấp. Không tạo đơn thật để xem các trạng thái trong đợt kiểm tra này.
