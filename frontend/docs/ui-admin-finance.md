# Tài chính admin — đợt 9

Baseline `9f9b8bd`, nhánh `feat/shareloop-ui-redesign`. Đầu đợt chỉ có `docs/design/` untracked, giữ nguyên.

## Nguồn dữ liệu, quyền và handler trước khi sửa

| Khối | Nguồn | Ý nghĩa/handler giữ nguyên |
| --- | --- | --- |
| Tổng quan | `adminFinanceApi.report({from,to})` | Tiền thu VND, số đơn đã thanh toán, Credit cộng/trừ theo báo cáo backend, không cộng từ trang đơn nạp |
| Đối chiếu | `report.reconciliation` | cashCredits/topUpCredits/difference/matched từ API, không tự tính đối soát |
| Biểu đồ | cashByDay, creditInByType, creditOutByType | Recharts đang có, giữ biến đổi dữ liệu, ngày thiếu không được tự thêm 0 |
| CSV | `reportCsv` | `exportCsv` dùng bộ lọc đã áp dụng, `downloadBlob`, quyền/lỗi/busy cũ |
| Đơn nạp | `adminFinanceApi.topUpOrders` | Chỉ đọc, lọc status, trang 10 dòng. Không tạo/xác nhận/thanh toán hoặc cộng lại Credit |
| Số dư | `users`, `userWallet` | availableCredit/heldCredit/totalCredit/totalToppedUpCredit từ backend, không cộng lịch sử để suy ra số dư |
| Lịch sử | `userHistory` → `toCreditHistory` | Ledger riêng với đơn nạp, bộ lọc ngày/trang/tab và dialog chi tiết hiện có |
| Điều chỉnh thủ công | Chưa có UI/handler/service trong phạm vi này | Không thêm form, nút hoặc API |

Giữ guard admin, debounce tìm người dùng, validation khoảng ngày, export theo filters đã áp dụng, mọi hook/effect/payload. Chỉ bổ sung điều kiện trình bày theo userId để không hiển thị số dư/lịch sử người cũ sau khi đổi route. Panel đơn mới nhận dữ liệu hàng, không tải thêm.

## Triển khai

- Báo cáo: bộ lọc, phạm vi thời gian từ response, thẻ số liệu, đối chiếu và các biểu đồ hiện có đồng bộ admin. Tiền thu vẫn là VND, không đổi thành doanh thu/lợi nhuận. Credit và kết quả đối chiếu lấy trực tiếp từ API, không thêm công thức.
- Biểu đồ giữ nguyên mapper, trục và quy tắc ngày hiện tại, thêm nhãn đơn vị và tooltip theo theme. Bảng số liệu/tooltip giữ giá trị đầy đủ, không rút gọn mới. Không thêm ngày thiếu thành 0.
- Đơn nạp: thêm mã đơn công khai và nhãn phương thức từ trường sẵn có. Lọc trạng thái, trang 10 dòng và tải lại như trước. Panel mới dùng `AdminRecordDetails` từ đợt 8, nhóm mã đơn/trạng thái, thành viên, số tiền/Credit, thời gian và thông tin xử lý được cung cấp.
- Panel đơn hoàn toàn chỉ đọc, không gọi API chi tiết, tạo đơn hoặc ghi nhận thanh toán. Không hiển thị redirect URL, chữ ký, token, mã cổng chưa có trong schema hoặc raw payload. Không dùng thời gian trình duyệt để tự hết hạn.
- Tra cứu người dùng: giữ debounce, phân trang, link đúng userId và các thành phần số dư do backend trả. Thêm nhãn đơn vị Credit và thẻ số dư theo theme, không tính từ ledger.
- Khi đổi userId, tiêu đề/thẻ số dư chỉ hiện nếu summary thuộc đúng userId. Lịch sử đang tải hoặc thuộc người cũ được che bằng thuộc tính trình bày, giữ component/state bộ lọc. Dialog lịch sử người cũ không được hiển thị dưới người mới. Không đổi hook hoặc cơ chế truy vấn.
- Lịch sử Credit tách biệt đơn nạp, giữ dấu cộng/trừ, ngày, trạng thái và dialog cũ. Ghi rõ các tab phân loại những dòng trên trang hiện tại. Không tạo tổng toàn hệ thống từ một trang dữ liệu.
- Chưa có điều chỉnh Credit thủ công trong các màn/service này. Bút toán `ADMIN_ADJUST` trong lịch sử không được coi là bằng chứng có form điều chỉnh, nên không thêm form/nút/API.
- Không đổi layout chung, dashboard vận hành, cấu hình, danh mục, từ khóa hoặc nhật ký. CSS chỉ tác động class tài chính mới, tái sử dụng CSS panel đợt trước.

## File thay đổi

- `src/pages/admin/AdminFinancePages.tsx` — JSX của báo cáo tài chính và import CSS, dashboard vận hành cùng mọi hàm/handler không đổi.
- `src/pages/admin/AdminTopUpOrders.tsx`
- `src/pages/admin/AdminFinanceUsers.tsx`
- `src/pages/admin/FinanceCharts.tsx` — nhãn đơn vị và lớp trình bày tooltip.
- `src/pages/admin/admin-finance.css` (mới)
- `src/pages/admin/AdminTopUpOrders.test.tsx`
- `src/pages/admin/AdminFinanceUsers.test.tsx`
- `docs/ui-admin-finance.md` — báo cáo này.

## Kiểm tra

- Build baseline và build sau triển khai đạt, lint và `git diff --check` đạt. Cảnh báo bundle trên 500 kB đã tồn tại từ trước.
- Vitest toàn frontend **597/597**, kiểm tra API hiện có **28/28**. Bộ 21 test liên quan đạt. Sau bổ sung nhãn tải số dư, chạy lại 9 test tra cứu/lịch sử và đạt.
- Hai test mới xác nhận mở panel đơn không tải thêm/không có thao tác tài chính và chuyển người dùng không hiển thị số dư/lịch sử người cũ. Test đơn nạp cập nhật để cho phép mã đơn công khai theo yêu cầu mới, vẫn chặn định danh kỹ thuật và thao tác duyệt thủ công. Không bỏ hoặc tắt test.
- Đối chiếu AST với snapshot: phần ngoài JSX và mọi handler cũ của bốn file runtime được bảo toàn sau khi bỏ import trình bày. Không thêm handler nghiệp vụ.
- Backend, service/API, schema/enum, auth/guard, hook truy vấn, mapper/utility tài chính, theme/global CSS, dependencies và môi trường không đổi.
- Chrome local: báo cáo, đơn nạp, panel đơn, tra cứu, chi tiết số dư tại **390/768/1440 px**, sáng/tối, không tràn ngang trang hoặc dialog. Kiểm tra tên/mã/email/số tiền dài và dữ liệu thiếu.
- Biểu đồ có giá trị đầy đủ trong dạng bảng, ngày thưa không được chèn thêm dòng. Lọc ngày sai không gọi API theo khoảng sai, bộ lọc đúng gửi tham số cũ. CSV gọi đúng phạm vi đã áp dụng.
- Đơn nạp: loading, lọc SUCCESS/EXPIRED, phân trang, mở đúng orderCode, không thêm request đọc hoặc mutation khi mở panel, Escape và trả focus.
- Tra cứu: loading, debounce, phân trang, số dư đúng thành viên, chuyển route sang người mới khi request chậm, ledger cộng/trừ, dialog và phân trang/ngày lọc hiện có.
- Báo cáo/đơn/tra cứu/ví có lỗi/retry/empty hoặc giá trị 0 thật theo response. Không phát sinh request ghi trong toàn bộ lượt kiểm thử tài chính.

Kiểm tra trình duyệt dùng request interception và response thử nghiệm trong script tạm ngoài repo. Không thanh toán thật, sửa đơn, điều chỉnh số dư hoặc gọi mutation production. Source ứng dụng không có fixture/localStorage tài chính mới. Chưa có sandbox/backend test và tài khoản phù hợp để xác minh số liệu, VNPay/IPN/chữ ký hoặc cập nhật sổ thật.

## Giới hạn và hành vi có sẵn

- **CSV:** unit test và trình duyệt xác nhận request/bộ lọc/blob/thông báo hiện có. Thử click thật qua Chrome headless ghi nhận `downloadWillBegin` và nhận đủ 25 byte CSV thử nghiệm, nhưng kết thúc `canceled`, không có file trong thư mục cấu hình. Chưa xác định nguyên nhân môi trường/trình duyệt. Không coi thông báo “Đã tải” là xác nhận file đã lưu thành công.
- `AdminFinancePages.tsx`, `downloadBlob`/`exportCsv`: thông báo thành công hiện có được đặt sau khi gọi tải blob, không chờ trình duyệt xác nhận lưu file. Giữ nguyên handler theo phạm vi UI, không sửa logic xuất dữ liệu.
- Đơn nạp chưa có route/API chi tiết riêng tại màn admin. Panel chỉ dùng response danh sách. Không có mã giao dịch cổng hoặc trạng thái ledger riêng để tự dựng timeline/đối soát.
- Không có tìm kiếm hoặc debounce trên trang đơn nạp hiện tại. Chỉ giữ bộ lọc status/phân trang có sẵn.
- Ledger không có route đáng tin cậy từ paymentOrderId nội bộ sang orderCode, nên không tự tạo link chi tiết đơn hoặc gọi thêm API ánh xạ.
- `CreditHistoryView.tsx` hiện có cách tính số dư trước từ số dư sau trừ amount. Đây là hành vi có sẵn, không thêm hoặc sửa công thức trong đợt này. Số dư ví vẫn lấy riêng từ `userWallet`.
- Tab lịch sử lọc trong trang dữ liệu hiện tại, không phải toàn bộ ledger. UI ghi rõ phạm vi này, giữ nguyên query và phân trang.
- Formatter rút gọn trục VND của biểu đồ là hành vi cũ. Tooltip và dạng bảng dùng số đầy đủ. Không thêm làm tròn hoặc ép kiểu mới.
- Hai trang danh sách giữ result cũ trong một số lượt tải lại theo điều kiện loading trước đây. Không thay query/cache để sửa hành vi đó.

## Local

- Báo cáo: **http://localhost:5173/admin/finance**
- Đơn nạp: **http://localhost:5173/admin/finance/topup-orders**
- Tra cứu: **http://localhost:5173/admin/finance/users**
- Chi tiết số dư: `/admin/finance/users/:userId` từ link trong danh sách.

Đăng nhập bằng tài khoản admin sẵn có. Chưa stage, commit, push, PR, merge hoặc deploy. `docs/design/` giữ nguyên.
