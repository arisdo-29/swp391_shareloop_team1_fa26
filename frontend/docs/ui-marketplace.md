# Giao diện marketplace — đợt 2

## Phạm vi

Tiếp tục trên `feat/shareloop-ui-redesign`, giữ toàn bộ thay đổi có sẵn của đợt nền tảng và thư mục `docs/design/` chưa được theo dõi. Đã đọc lại hướng dẫn repo, frontend, theme, component và CSS tham khảo tại https://shareloop-demo-giao-dien.jauntyhydrangea.chatgpt.site/.

- **Trang chủ:** hero forest/lime với bài nổi bật từ dữ liệu hiện có, dải ảnh chọn được, form tìm kiếm và chọn quận; danh mục, món mới, bài nổi bật, đồ tặng, đồ trao đổi, khu vực và các khối hướng dẫn đồng bộ. Khi không có ảnh nổi bật, dùng hình minh họa có sẵn trong repo. Không dựng số liệu cộng đồng hoặc thông tin người đăng.
- **Tìm đồ:** cột lọc desktop, tìm kiếm nhanh và bộ lọc thu gọn mobile, sắp xếp, số kết quả từ API, điều kiện đang chọn và xóa bộ lọc; đủ loading/empty/error/retry/phân trang. Không đổi truy vấn hoặc lọc cục bộ kết quả phân trang.
- **Chi tiết:** ảnh giữ đầy đủ khung hình, dải ảnh thu nhỏ và chỉ số ảnh khi có ảnh thật; tách thông tin món đồ, mô tả, tình trạng, người đăng và khung hành động. Giữ đủ giao diện khách, thành viên, chủ tin, đã gửi yêu cầu, đã giao dịch, người đăng bị khóa và tin không khả dụng.
- **Dùng chung:** variant `marketplace` của `ProductCard` chỉ bật ở Home/Browse; toast và menu thông báo chuyển nền trắng sang token surface để đọc được trong chế độ tối. Không thiết kế lại các màn hình hoạt động, tin nhắn, tín dụng, khiếu nại hoặc admin.

## Các file thay đổi trong đợt 2

- `src/pages/public/Home.tsx`, `src/pages/public/home-content.css`.
- `src/pages/public/Browse.tsx`, `src/pages/public/browse.css`.
- `src/pages/public/ProductDetail.tsx`, `src/pages/public/product-detail.css` (mới).
- `src/styles/marketplace.css` (mới), `src/components/ui.tsx`.
- `src/components/NotificationMenu.tsx`, `src/components/ToastHost.tsx`: chỉ đổi class nền theo theme.
- `theme-tokens.js`: thêm màu thương hiệu cố định dùng cho hero.
- `docs/ui-marketplace.md` (báo cáo này).

## Logic được giữ nguyên

Không đổi backend, API client, endpoint, phương thức HTTP, schema, authentication, token, guard, routing, realtime, thanh toán, validation, dependencies hoặc file môi trường. Không thêm request cho thiết kế. Không đưa fixture vào source ứng dụng.

Đối chiếu với snapshot lúc bắt đầu đợt 2: phần ngoài JSX của Home/Browse/ProductDetail giữ nguyên sau khi chuẩn hóa định dạng; toàn bộ handler JSX hiện có vẫn được giữ. `itemApi`, `catalogApi`, `requestApi`, mapper, hook bài nổi bật, debounce, rotation seed, phân trang và xử lý lỗi không đổi. Các điều kiện và handler gửi/hủy yêu cầu, chọn món, chỉ xin nhận và báo cáo giữ nguyên. Điều kiện hiện nút xóa bộ lọc được mở rộng để dùng được ngay với một bộ lọc hoặc từ khóa.

## Kiểm tra

- Build baseline đạt; có cảnh báo chunk hơn 500 kB từ trước khi sửa.
- Build và lint sau thay đổi đạt.
- Vitest toàn frontend: 88 file, 593/593 test đạt với `npx.cmd vitest run --maxWorkers=2`.
- `npm.cmd run test:api`: 28/28 đạt.
- Chrome: 3 màn hình × 2 theme × 390/768/1440 px; không tràn ngang toàn trang. Đã xem ảnh chụp desktop/mobile, sáng/tối.
- Trình duyệt đã thử tìm kiếm, kết hợp danh mục/quận/hình thức, sắp xếp, phân trang có rotation seed, empty, lỗi/retry, mở chi tiết, chọn ảnh, bộ lọc mobile và xóa bộ lọc.
- Đã thử khách, gửi/hủy yêu cầu Cho, validation Trao đổi, chỉ xin nhận, đã gửi yêu cầu, chủ tin, tin đã giao dịch và người đăng bị khóa; dialog báo cáo ở dark mode/mobile nhận focus, Tab và Escape hoạt động.
- Kiểm thử trình duyệt chặn request API để trả dữ liệu kiểm thử; dùng bản đọc danh sách/danh mục/khu vực công khai làm dữ liệu hiển thị. Các mutation được đáp ứng ngay trong công cụ kiểm thử, không chuyển tới production. Không có exception JavaScript trong lượt kiểm thử. Các fixture và ảnh kiểm tra nằm trong thư mục tạm, không được đưa vào ứng dụng.

## URL local và giới hạn

Ứng dụng chạy tại **http://localhost:5175/**, trang tìm đồ tại **http://localhost:5175/browse**. Nhánh và thay đổi vẫn chưa commit; không push, PR, merge hoặc deploy.

Backend cloud hiện chỉ cho phép origin `http://localhost:5173`; GET với origin `http://localhost:5175` bị trả 403 CORS. Cổng 5173 đang thuộc dev server dự án khác. Theo lựa chọn của người dùng, giữ dev server đó và dùng 5175, không đổi cấu hình backend hoặc API client. Vì vậy, bản local ở 5175 có thể hiện lỗi tải dữ liệu thật; kiểm thử các luồng và giao diện có dữ liệu được thực hiện bằng response thử nghiệm tại trình duyệt, không phải giao dịch end-to-end với tài khoản thật. Muốn kiểm tra dữ liệu thật cần origin được backend cho phép.

Không tìm thấy chức năng/API lưu hoặc bỏ lưu món đồ trong dự án hiện tại; không thêm nút lưu giả hoặc lưu dữ liệu nghiệp vụ vào localStorage. Đây là phần bị giới hạn bởi chức năng sẵn có.

Cảnh báo bundle hơn 500 kB vẫn còn. Font Be Vietnam Pro local, bộ chọn sáng/tối/theo hệ thống và thiết kế nền tảng của đợt 1 tiếp tục được dùng.
