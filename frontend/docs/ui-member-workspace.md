# Giao diện thành viên — đợt 3

## Trạng thái ban đầu

- Nhánh `feat/shareloop-ui-redesign`, HEAD `8b7f9dc`. Frontend sạch thay đổi lúc bắt đầu; `docs/design/` đã tồn tại dưới dạng untracked và được giữ nguyên.
- Đã kiểm tra theme/token, Be Vietnam Pro local, layout, component dùng chung và các màn hình marketplace hiện có. Không viết lại Home/Browse/ProductDetail.
- Build baseline đạt; có cảnh báo chunk hơn 500 kB từ trước.
- Tham khảo phong cách: https://shareloop-demo-giao-dien.jauntyhydrangea.chatgpt.site/ và nền giao diện hiện có của repo.

## Màn hình và chức năng được giữ

### Hồ sơ

`/profile` giữ nguyên sửa tên, ảnh đại diện, khu vực, thông tin email chỉ đọc, đổi số điện thoại qua OTP và tab uy tín/lịch sử điểm. Link đổi mật khẩu đã có được giữ nguyên, không thêm quy trình mới. Phần đầu hồ sơ, menu tài khoản, nhóm trường nhập và thông báo dùng cùng token sáng/tối.

`/users/:userId` giữ nguyên dữ liệu công khai, điều kiện chủ hồ sơ/khách xem, báo cáo và hai danh sách ACTIVE/DONE có phân trang độc lập. Không đưa email, số điện thoại, số dư hay điểm thưởng vào hồ sơ công khai. Các chỉ số vẫn lấy trực tiếp từ response hiện có; không dựng thống kê.

Các màn hình dùng `userApi`, `catalogApi`, `reputationApi`, `mediaApi`, mapper, store phiên và handler đang có; không thay cách tải hoặc lưu dữ liệu.

### Hoạt động và tin của tôi

`/activities` và các query `tab` giữ nguyên. Bố cục mới gồm việc cần xử lý, thanh tab/bộ lọc, thẻ tin có ảnh/mã/thời gian/trạng thái, thông tin yêu cầu và nhóm hành động. Tên dài được xuống dòng thay vì làm vỡ thẻ. Thông báo từ chối hiện có được nhấn rõ hơn.

Giữ nguyên tải dữ liệu, lọc, số lượng, điều kiện sửa/gỡ/gia hạn/đẩy, chọn người nhận, xem yêu cầu, mở cuộc trò chuyện và yêu cầu xác nhận. Không tạo phân trang hoặc tổng mới: Hoạt động vẫn dùng nguồn danh sách hiện có; phân trang hồ sơ và lịch sử uy tín giữ nguyên.

Tab Khiếu nại & Báo cáo được giữ nguyên để không mất chức năng; không sửa component nội dung của tab này.

### Đăng và sửa tin

`/post` giữ form submit một lần, các nhóm hình thức/ảnh/thông tin/giao nhận; thêm hướng dẫn trình bày và phần xem trước từ chính giá trị form. Phí, quota và số dư tiếp tục lấy từ nguồn cũ.

Form sửa tin vẫn mở trong Hoạt động, giữ quy trình đọc báo giá/detail và payload cập nhật. Dùng `DialogFrame` hiện có để có role/focus thống nhất; không thêm handler đóng bằng Escape/backdrop. Nhóm rõ thông tin cơ bản, tình trạng, mô tả/giao nhận và ảnh. Ảnh cũ và ảnh mới có nhãn riêng; giữ cách chọn, xóa, giới hạn và upload ban đầu.

Không đổi validation, enum, điều kiện required, chống submit lặp, upload, lưu ảnh, giữ phí, refresh ví hoặc điều hướng sau thành công. Không tạo wizard hoặc nguồn dữ liệu nghiệp vụ khác.

## File thay đổi

- `src/pages/user/Profile.tsx`, `src/pages/user/UserProfile.tsx`.
- `src/components/profile/ProfileItemsSection.tsx`: dùng variant marketplace đã có, giữ phân trang và nguồn dữ liệu.
- `src/pages/user/Activities.tsx`, `src/pages/user/Post.tsx`.
- Mới: `src/pages/user/member-profile.css`, `member-activities.css`, `member-post.css`, `member-workspace.css`.
- `src/layouts/AppShell.tsx`, `src/styles/globals.css`: sửa cách hiển thị tên dài trong menu tài khoản để header mobile không tăng chiều cao bất thường; giữ tên cho trình đọc màn hình, hiện icon tài khoản ở màn nhỏ và vùng bấm tối thiểu 44 px. Handler menu và phiên đăng nhập không đổi.
- `docs/ui-member-workspace.md`: báo cáo này.

## Kiểm tra thực tế

- Build và lint đạt. 593/593 test trong 88 file đạt; 28/28 kiểm tra API đạt. Các test tập trung hồ sơ/hoạt động và AppShell cũng được chạy sau thay đổi liên quan.
- Chrome: hồ sơ riêng, hồ sơ công khai, Hoạt động và Đăng tin ở 390/768/1440 px, sáng/tối; không tràn ngang toàn trang. Dialog sửa tin được kiểm tra riêng ở các kích thước/theme này.
- Kiểm tra hồ sơ: trường riêng tư không xuất hiện trong hồ sơ công khai; validation tên rỗng; chọn avatar; lưu lỗi/thành công; chuyển tab và phân trang lịch sử uy tín/tin đăng.
- Kiểm tra Hoạt động: chuyển tab, lọc Chờ duyệt, mở sửa tin; payload lưu giữ đủ ảnh cũ và ảnh mới; các test hiện có kiểm tra điều kiện chọn người, gia hạn/đẩy và cảnh báo phí.
- Kiểm tra Đăng tin: validation khi thiếu dữ liệu; chọn đủ ảnh, hình thức, danh mục, tình trạng, khu vực và mô tả; xác nhận gửi; lỗi rồi thử lại; trạng thái đang gửi và một request tạo cho lần xác nhận thành công; điều hướng về Hoạt động.
- Kiểm tra lại Home/Browse/ProductDetail ở 390/768/1440 px, sáng/tối, cùng header có tên dài. Không có exception JavaScript trong lượt kiểm thử trình duyệt.

**Giới hạn kiểm thử:** các luồng ghi được chạy với request interception và response thử nghiệm tại trình duyệt; danh mục, khu vực và ảnh dùng bản dữ liệu công khai đã đọc trước đó. Không dùng tài khoản thật hoặc backend test riêng để xác minh lưu bền vững/upload thực/OTP thực. Mọi mutation của lượt kiểm thử được đáp ứng trong công cụ kiểm thử, không chuyển tới production. Fixture và ảnh kiểm tra nằm trong thư mục tạm, không đưa vào ứng dụng. Đây là kiểm thử giao diện/handler/payload, không thay cho end-to-end với backend thật.

## Ranh giới và giới hạn còn lại

- Đối chiếu snapshot đầu đợt: phần ngoài JSX và toàn bộ handler JSX hiện có của các màn hình, ProfileItemsSection và AppShell được giữ nguyên sau chuẩn hóa định dạng.
- Không sửa backend, database, migration, API service/client, schema, hook dữ liệu, token/auth, guard, route/query/deep link, polling/realtime, validation, phí/quota/uy tín, dependencies hoặc file môi trường.
- Không sửa nội dung tin nhắn, ví, khiếu nại hoặc admin. Không thêm request API phục vụ thiết kế.
- Chưa có nguồn giới thiệu cá nhân, món đã lưu hoặc tab đánh giá độc lập trong các màn hình hiện tại; không thêm dữ liệu hoặc chức năng giả.
- Lý do từ chối/tạm ẩn chỉ được hiển thị khi dữ liệu đã có cung cấp, giữ fallback hiện tại; không đổi mapper/API để bổ sung trường.
- Cảnh báo bundle chính hơn 500 kB vẫn còn từ baseline.
- Không commit, push, PR, merge hoặc deploy; không stage thay đổi đợt này.

## Xem local

Frontend chạy tại **http://localhost:5173/**. Đăng nhập bằng tài khoản có sẵn để xem:

- http://localhost:5173/profile
- http://localhost:5173/activities
- http://localhost:5173/post
- Hồ sơ công khai: `/users/:userId`, giữ quyền truy cập hiện tại.
