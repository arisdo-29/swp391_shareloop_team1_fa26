# Nền tảng giao diện — đợt 1

Tham khảo: https://shareloop-demo-giao-dien.jauntyhydrangea.chatgpt.site/

Phạm vi gồm token, font, component dùng chung và khung member/admin. Các trang nghiệp vụ giữ API, authentication, guards, validation và dữ liệu từ backend. Chưa chuyển toàn bộ CSS riêng của từng màn hình sang theme mới.

## Chạy local

Yêu cầu Node 24; dùng `npm.cmd` trên PowerShell nếu execution policy chặn `npm.ps1`.

```powershell
cd frontend
npm.cmd ci
npm.cmd run dev -- --host localhost
```

Vite mặc định dùng cổng 5173 và tự chọn cổng kế tiếp khi bị chiếm. Mở URL Vite in ra. Trang `/ui-foundation` cho xem button, input, select, card, badge và dialog; route này chỉ được đăng ký trong dev, không xuất hiện trong production.

Nếu muốn dùng backend local đã chạy ở cổng 8080, đặt biến cho phiên PowerShell trước khi chạy Vite:

```powershell
$env:VITE_API_BASE_URL = 'http://localhost:8080'
npm.cmd run dev -- --host localhost --port 5173 --strictPort
```

Backend dev cần JDK 21, PostgreSQL 16 và Mailpit theo README gốc. CORS dev hiện cho phép `http://localhost:5173`; cần origin tương ứng nếu chọn cổng khác. Nếu không đặt biến, HTTP client vẫn dùng backend cloud mặc định trong `src/services/http.ts`. Không có Vite proxy. Không cần tạo file môi trường cho đợt này.

## Token và component

- `theme-tokens.js` là bảng màu nguồn; `tailwind.config.js` ánh xạ sang utility Tailwind và biến CSS `--color-*`. Màu sáng: forest `#072e29`, lime `#d3fc9c`, cream `#f7f7f3`. Tránh mã màu trực tiếp trong component.
- Font Be Vietnam Pro 400–800 được phục vụ từ `public/fonts/be-vietnam-pro/`, kèm giấy phép OFL, không gọi Google Fonts khi chạy.
- `ThemeSelect` chọn sáng/tối/theo hệ thống, ghi `shareloop:theme`; mặc định theo hệ thống. Theo dõi thay đổi hệ điều hành và đồng bộ lựa chọn giữa các tab. Nếu storage bị chặn vẫn đổi được trong phiên hiện tại.
- `main.tsx` áp dụng lựa chọn trước khi render React. Header/sidebar dùng màu forest cố định để giữ tương phản; surface/text/border dùng token thay đổi theo theme.
- Dùng `Button`, `Field`, `Select`, `Card`, `Badge` trong `components/ui.tsx`; `Dialog`/`DialogFrame` trong `components/Dialog.tsx`. Giữ các badge nghiệp vụ hiện hữu để giữ nguyên mapping trạng thái.
- Dialog giữ focus bàn phím trong hộp thoại và trả focus về vị trí trước khi mở. Escape/backdrop vẫn tôn trọng `busy`.
- Layout member giữ menu responsive và dữ liệu phiên/ví/thông báo hiện có; admin giữ sidebar thu gọn và menu mobile.

## Dependencies và giới hạn

Không đổi `package.json` hoặc lockfile. Đã đối chiếu React 19, Vite 7, Tailwind 3 và Node 24. Metadata npm cho tên `astryx` chỉ có phiên bản `0.0.0`, chưa đủ xác định thư viện UI dự kiến và peer dependencies; chưa thêm Astryx.

Các màn hình có CSS/mã màu riêng vẫn cần được chuyển theo từng đợt, đặc biệt Home, Browse, Auth và nội dung admin. Chế độ tối trong đợt này hoàn thiện ở nền tảng/component/layout, chưa bảo đảm toàn bộ màn hình cũ.

Kiểm tra: `npm.cmd run build`, `npm.cmd run lint`, `npm.cmd test`. Cần đăng nhập bằng tài khoản thật có quyền tương ứng để kiểm tra end-to-end member/admin; trang component không giả lập phiên hoặc dữ liệu nghiệp vụ.

## Các file trong đợt này

- Token/style: `theme-tokens.js`, `tailwind.config.js`, `src/styles/globals.css`.
- Theme: `src/app/theme.ts`, `src/components/ThemeSelect.tsx`, `src/components/ThemeSelect.test.tsx`, `src/main.tsx`.
- Component/layout: `src/components/ui.tsx`, `src/components/Dialog.tsx`, `src/layouts/AppShell.tsx`, `src/pages/admin/AdminLayout.tsx`.
- Trang kiểm tra dev: `src/pages/UiFoundation.tsx`, `src/app/router.tsx`.
- Font: `public/fonts/be-vietnam-pro/be-vietnam-pro-{400,500,600,700,800}.ttf`, `public/fonts/be-vietnam-pro/OFL.txt`, `index.html`.
- Hướng dẫn: `README.md`, `DESIGN.md`, `docs/ui-foundation.md`.

`package.json`, `package-lock.json` và backend không thay đổi. Thư mục `docs/design/` ở gốc repo đã tồn tại dưới dạng untracked trước khi bắt đầu và không được sửa trong đợt này.

## Kết quả kiểm tra đợt 1

- Local đang chạy tại `http://localhost:5174/`; xem bộ component tại `http://localhost:5174/ui-foundation` (cổng 5173 đang được sử dụng).
- Nhánh `feat/shareloop-ui-redesign` đã có sẵn; HEAD trùng `develop` local và `develop` trên origin tại thời điểm kiểm tra: `2662d043e296ccae7e17473c58499d8748e0f0ef`.
- Build và lint đạt; 28/28 kiểm tra API đạt; chạy toàn bộ Vitest với `npx.cmd vitest run --maxWorkers=2`: 88 file, 593/593 test đạt. Lần chạy mặc định đồng thời với build có hai lỗi không lặp lại khi chạy riêng và khi chạy lại toàn bộ với hai worker.
- Chrome: trang component không tràn ngang ở 375/768/1280 px; font local nạp thành công; sáng/tối, lưu lựa chọn qua reload và chuyển theo hệ thống hoạt động. Dialog nhận focus khi mở, Tab ở trong dialog, Escape đóng và trả focus.
- Các cặp màu chữ/nền chính và trạng thái trong cả hai theme đạt tỷ lệ tương phản tối thiểu 4.5:1. Chưa kiểm tra end-to-end với tài khoản member/admin thật.
- Build vẫn có cảnh báo bundle chính hơn 500 kB, cũng xuất hiện ở baseline. Production build không chứa trang component dev.
- Không commit, push, tạo PR, merge hoặc deploy.
