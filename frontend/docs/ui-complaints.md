# Khiếu nại và báo cáo — đợt 6

Baseline `8fe4a80`, nhánh `feat/shareloop-ui-redesign`. Đầu đợt chỉ có `docs/design/` untracked, giữ nguyên.

## Bảng bảo toàn hành vi trước khi sửa

| Trạng thái/ngữ cảnh | Người được xem | Người thao tác | Điều kiện hiện có | Handler |
| --- | --- | --- | --- | --- |
| Báo cáo ITEM/USER từ ngữ cảnh | Member theo trang hiện có | Người báo cáo | Lý do, mô tả có nội dung, không đang gửi; target cố định từ props | `ReportDialog.submit`, upload rồi `reportApi.create` |
| COMPLETED, khiếu nại giao dịch | Một bên giao dịch theo logic trang tiến trình | Bên đang đăng nhập | Chưa `disputeExpired`, có lý do; OTHER bắt buộc nội dung | `DisputeDialog.submit`, `openDispute` |
| AWAITING_HANDOVER, sự cố giao nhận | Hai bên giao dịch | Bên chưa xác nhận | Bên kia đã xác nhận, điều kiện của `HandoverCard`/hook hiện có; mô tả ít nhất 5 ký tự | `HandoverIssueDialog.submit`, `requestApi.reportHandoverIssue` |
| Hồ sơ đã gửi | Người gửi | Chỉ xem tóm tắt hiện có | `reportApi.listMine`; không có route/API chi tiết dành riêng cho người gửi | `ActivitiesComplaints`, `SentCard` |
| Hồ sơ nhắm vào tôi | Bên được yêu cầu giải trình | Bên liên quan | `listAgainstMe` và `respondentView`; backend xác nhận quyền | `ResponsePage`, `responsePath` |
| Còn hạn giải trình | Bên liên quan | Bên liên quan | `responseWindowOpen && !submitted`; nội dung không rỗng, tối đa 2.000 ký tự, ref chống gửi lặp | `ResponseForm.submit`, `respondDispute` hoặc `reportApi.respond` |
| Đã gửi giải trình/hết hạn/đã kết luận | Bên liên quan | Chỉ xem theo cờ backend và điều kiện cũ | Không mở lại form bằng đồng hồ trình duyệt | `DeadlineNotice`, `SubmittedResponse` |
| Danh sách/chi tiết admin, mọi trạng thái | Admin qua guard cũ | Admin | Lọc status/targetType, trang API; AbortController và realtime cũ | `reportApi.list`, `detail`, effect hiện có |
| OPEN, cần thêm thông tin | Admin | Admin | Không phải REQUEST, chưa có response, không đang gửi | `canRequestResponse`, `requestResponse` |
| Chờ giải trình còn hạn | Admin | Không được kết luận | `detail.responseWindowOpen`; khóa nút và kiểm lại trong submit | Nút cập nhật dùng `setReload`, submit giữ guard |
| Chưa kết luận, có thể xử lý | Admin | Admin | Chọn quyết định riêng, ghi chú tối đa 500, review → choose → confirm, ref chống gửi lặp | `submit`, `reportApi.conclude` |
| Lỗi xung đột/còn hạn | Admin | Theo phản hồi API | REPORT_RESPONSE_WINDOW_OPEN/409 tải lại, 401/403/404 giữ xử lý cũ | catch của `submit`, `requestResponse` |
| RESOLVED/DISMISSED | Theo quyền backend hiện có | Chỉ xem | Hiện kết luận, ghi chú, người/thời gian và hệ quả từ dữ liệu hiện có | `consequences`, `DetailRow` |

Hạn phản hồi lấy từ `responseDueAt`, hạn khiếu nại từ `complaintDeadline`, cờ mở form/kết luận từ response backend và hook hiện tại. Không tự đặt 24 giờ, không thêm timeline hoặc hành động rút/mở lại hồ sơ khi source chưa có. Ảnh riêng tư vẫn tải qua API có xác thực và object URL, giữ AbortController/cleanup.

## Phần giao diện đã hoàn thành

- Member: thẻ hồ sơ phân biệt phần cần giải trình và phần đã gửi. Hồ sơ cần chính người dùng phản hồi được nhấn bằng viền, nền và nhãn chữ từ cờ backend hiện có. Không tự tạo mã hoặc tổng thống kê.
- Form tạo báo cáo, khiếu nại giao dịch và báo sự cố: giữ ngữ cảnh cố định, lý do/mô tả, bằng chứng, validation, cảnh báo, lỗi và các nút cũ. Vùng chọn ảnh, preview, tên file và nút bỏ ảnh được trình bày đồng bộ.
- Chi tiết bên liên quan: tóm tắt, badge trạng thái backend, ngày gửi, hạn phản hồi, nội dung phản ánh và giải trình chia thành các vùng riêng. Desktop dùng hai cột, tablet/mobile xếp dọc. Giữ kín người gửi như schema respondent hiện có.
- Admin: tái sử dụng layout/sidebar cùng khả năng thu gọn có sẵn. Chỉ trang khiếu nại được đổi thành danh sách gọn, hàng có phân cấp nội dung, bộ lọc và dialog xem xét chia vùng nội dung/quyết định. Không redesign các trang admin khác.
- Dialog admin giữ toàn bộ bước review → choose → confirm. Header cố định khi cuộn, mobile có nút về danh sách dùng đúng handler đóng cũ. Khu quyết định không dính theo cuộn để tiêu đề dài không che nút thao tác.
- Kết quả đã lưu tiếp tục trình bày kết luận, ghi chú, người xử lý, thời điểm và hệ quả bằng dữ liệu/hàm hiện tại. Không thêm timeline, người phụ trách, mức rủi ro hay quyết định mặc định.
- Token sáng/tối, xanh rừng/lime/nền kem và Be Vietnam Pro từ nền tảng hiện tại. CSS chỉ tác động các class khiếu nại mới, không sửa global/theme/component chung hoặc dependencies.

## File thay đổi

- `src/components/ReportDialog.tsx`
- `src/components/ReportEvidenceField.tsx`
- `src/components/transaction/TransactionDialogs.tsx` — chỉ phần trình bày hai dialog tạo khiếu nại/sự cố, giữ dialog hủy.
- `src/pages/user/ActivitiesComplaints.tsx`
- `src/pages/user/ReportResponse.tsx`
- `src/pages/admin/AdminComplaints.tsx`
- `src/components/ReportCaseStatus.tsx` (mới) — component thuần trình bày trạng thái.
- `src/components/complaints.css` (mới).
- `docs/ui-complaints.md` — bảng bảo toàn hành vi và báo cáo này.

## Kiểm tra

- Build và lint đạt. Cảnh báo bundle chính trên 500 kB có từ trước.
- Vitest toàn frontend đạt **593/593**, bộ API hiện có đạt **28/28**. Bộ 70 test liên quan cũng đạt. Không sửa hoặc bỏ test.
- Đối chiếu AST với snapshot đầu đợt: phần ngoài JSX của sáu file cũ giữ nguyên sau khi bỏ import trình bày, mọi handler JSX cũ được bảo toàn, không thêm handler nghiệp vụ.
- Không thay backend, API/service, enum/schema, auth/guard, hook, utility, realtime, upload/storage, dependency hoặc môi trường.
- Chrome local: danh sách member, chi tiết/giải trình, form báo cáo, danh sách admin và dialog xem xét tại **390/768/1440 px**, sáng/tối. Nội dung/URL, tên đối tượng và tên file dài không gây tràn ngang toàn trang hoặc dialog.
- Member: deep link, giấu tên người gửi, hạn/backend flag, hết hạn/đã kết luận, validation giải trình, upload lỗi/thử lại và preview/bỏ ảnh. Hai loại giải trình gửi đúng `note` hoặc `response` qua endpoint cũ.
- Tạo báo cáo: đúng ITEM từ ngữ cảnh bài, không có trường đổi đối tượng, validation, chống bấm gửi lặp. Khiếu nại từ giao dịch COMPLETED giữ quy tắc OTHER bắt buộc nội dung.
- Admin: guard chặn member, không chọn sẵn quyết định, yêu cầu giải trình, khóa kết luận trong hạn, chọn/xác nhận kết luận, chống gửi lặp và xử lý 409/tải lại kết quả.
- Admin: loading, lọc trạng thái/loại hồ sơ, phân trang qua query cũ, lỗi/retry/empty. Member nhận màn lỗi theo cơ chế cũ khi backend từ chối xem hồ sơ.
- Kiểm tra bổ sung dialog admin sau chỉnh bố cục: title dài, vùng cuộn và nút quyết định không bị header cố định che.

Kiểm tra trình duyệt dùng request interception và phản hồi thử nghiệm trong script tạm ngoài repo. Mọi request ghi đều bị chặn, không gửi khiếu nại, giải trình hoặc quyết định tới production. Không có mock/localStorage làm nguồn hồ sơ trong ứng dụng. Các kiểm tra này chưa xác minh quyền, upload/storage, hạn thực tế và realtime bằng hai tài khoản cùng backend test thật.

## Giới hạn có sẵn

- Schema hiện chỉ có ID kỹ thuật, không có mã hồ sơ công khai riêng. Test hiện có yêu cầu không hiện ID kỹ thuật cho member, nên không dùng ID này làm mã hiển thị hoặc tự tạo mã giả.
- Source chưa có API/route chi tiết riêng cho người gửi. Danh sách đã gửi vẫn là bản tóm tắt theo quyền hiện có, không mở respondent-view cho người gửi hoặc tự gọi API admin.
- Không có chức năng rút khiếu nại, mở lại, phân công hoặc lịch sử sự kiện đầy đủ trong các màn hiện tại. Không dựng nút hoặc timeline mô phỏng.
- `ResponseForm` báo gửi thành công bằng state `submitted`, nhưng không tải lại detail. Giải trình vừa gửi có thể chưa xuất hiện ở phần xem lại cho tới khi tải lại trang. Giữ nguyên cơ chế này.
- `ReportEvidenceField` hiện chỉ đặt `accept="image/*"` và loại file trùng theo tên/kích thước/thời điểm. Component chưa có kiểm tra số lượng hoặc kích thước cục bộ. Giữ cơ chế upload/lỗi backend, không tự thêm hạn mức.
- API bằng chứng chỉ cung cấp ID, URL và cờ khả dụng, không có tên file gốc. Chỉ preview ảnh mới chọn có tên file, ảnh hồ sơ giữ nhãn hiện có và chức năng mở lớn ở tab mới.
- Điều kiện kết luận tiếp tục dùng `responseWindowOpen` do backend trả về, không suy ra từ đồng hồ trình duyệt. Chưa kiểm chứng trường hợp thiếu cờ này qua backend test.

## Local

- Danh sách member: **http://localhost:5173/activities?tab=complaints**
- Giải trình: `/me/reports/:reportId` hoặc `/me/disputes/:reportId` từ thông báo/hồ sơ được phép xem.
- Form tạo: nút báo cáo tại bài đăng/thành viên, hoặc khiếu nại tại tiến trình giao dịch đủ điều kiện.
- Admin: **http://localhost:5173/admin/complaints** (alias `/admin/disputes` giữ nguyên).

Đăng nhập bằng tài khoản có sẵn và dùng hồ sơ được cấp quyền. Không tạo hồ sơ thật chỉ để kiểm thử. Chưa stage, commit, push, PR, merge hoặc deploy.
