# Kết quả rà soát giao diện (Đợt 0)

Phạm vi: toàn bộ `frontend/src`, cộng critique 4 màn Home, Browse, ProductDetail, Messages (chỉ đọc, không sửa 3 màn công khai).
Mức độ: **Nặng** (ảnh hưởng truy cập, nhất quán toàn hệ thống hoặc luật cứng), **Vừa**, **Nhẹ**.

## A. Số liệu rà soát

| Hạng mục | Kết quả |
|---|---|
| Hex viết cứng trong `.tsx` | 11 chỗ: `ui.tsx` (1), `AppShell.tsx` (1), `FinanceCharts.tsx` (5), `Home.tsx` (3), `store.ts` (chỉ là comment) |
| Hex trong file `.css` riêng | `home-content.css` ~120, `browse.css` ~41, `login.css` ~22 |
| Màu Tailwind mặc định thay vì token | ~30 file; nhiều nhất: `Messages.tsx` (15), `ReputationBadge.tsx` (14), `ui.tsx` (13), `Activities.tsx` (8), `AdminFinancePages.tsx` (7) |
| Cỡ chữ tuỳ ý `text-[10px]`/`[11px]` | 23 chỗ, 11 file (nhiều nhất `Messages.tsx` 8, `ui.tsx` 5, `AdminLayout.tsx` 3) |
| Bóng/bo ngoài token | `shadow-xl` (Dialog, Messages, AdminAuthenticity), `shadow-2xl` (CreditHistoryView), `rounded-2xl` (AI), bóng tuỳ ý (Home) |
| `<button>` tự dựng ngoài `Button` | ~50 chỗ trong 20 file |
| Hộp thoại tự dựng thay vì `Dialog` | 16 chỗ (`role="dialog"` ở AppShell, Messages, Activities x3, Post, ProductDetail x2, Home, AdminComplaints, AdminAuthenticity, AdminPages, CreditHistoryView, ReportDialog) |
| Skill Taste trong `skills-lock.json` | Chỉ còn `design-taste-frontend` và `redesign-existing-projects`, không có skill Taste nào khác để gỡ |

## B. Làm ở phần 1

### Nặng
1. ~~Tương phản `text-muted`~~ **Đã xử lý ở Đợt 1**: `text-muted` đổi từ #73807B (khoảng 3,8–4,2:1) sang #5F6B66 (khoảng 5:1 trên nền trang, sát 4,5:1 trên `surface-container`); `warning` đổi từ #A86716 sang #8C5410 (khoảng 5,6:1 trên `warning-soft`). `warning` chỉ được dùng làm màu chữ/icon, không có chỗ nào dùng làm nền hay viền. Còn lại: `browse.css` (7 chỗ) và `home-content.css` (2 chỗ) vẫn hard-code #73807b, dồn sang phần 2. Chữ 10–11px vẫn cần xử lý theo từng trang.
2. **Focus yếu**: `globals.css` dùng vòng `rgba(20,92,78,0.2)` 3px, ô nhập dùng `ring-primary/10`; trên nền trắng gần như không thấy (dưới 3:1). `Browse.tsx` và `SearchField` có `outline-none`. *Đợt 1 (globals.css); phần `SearchField` dồn sang phần 2.*
3. **Vùng bấm dưới 44px** ở header và admin: nút tên người dùng (`p-1`, ~28px), mục menu thả xuống (`py-2`), mục sidebar admin (`py-2 text-[13px]`, ~38px), nút mở menu admin (`size-10` = 40px, lại thiếu `aria-label`). *Đợt 1.*
4. **Màu tự chế trong lớp nền tảng**: `bg-[#0d332c]/95` (menu mobile của AppShell), `bg-red-50` (nút đăng xuất), `bg-black/30`; `::selection` và `:focus-visible` trong `globals.css` dùng hex/rgba thay token. *Đợt 1.*
5. **Thiếu token nền nhạt cho trạng thái** (`success-soft`, `warning-soft`, `error-soft`, `info-soft` và viền): hiện cả hệ thống đang dùng `bg-red-50`, `bg-emerald-50`, `bg-amber-50`, `bg-blue-50` mặc định. Đây là gốc của vi phạm màu mặc định ở ~30 file. *Đợt 1: thêm token vào `tailwind.config.js`; thay ở từng trang theo đợt; thay trong `ui.tsx` dồn sang phần 2.*

### Vừa
6. **Ba độ rộng khung khác nhau**: header/footer 1500px, `.page-shell` 1280px, admin 1440px; lề ngang cũng khác (`px-6 md:px-8 lg:px-12 xl:px-14` so với `px-4 sm:px-6 lg:px-8`). Nội dung lệch mép so với logo. *Đợt 1.*
7. **Messages**: file 1779 dòng, khung cố định `lg:h-[calc(100dvh-140px)] lg:min-h-[680px]` gây cuộn lồng nhau; 11 nút thô, 8 chỗ `text-[10px]/[11px]`, 15 chỗ màu mặc định, 3 hộp thoại tự dựng (overlay `bg-black/30`, `shadow-xl`). Trạng thái tải chỉ là một dòng chữ ("Đang tải cuộc trò chuyện..."), chưa có khung chờ. *Đợt 2.*
8. **Activities, Credit, Post** có cùng kiểu vấn đề (màu mặc định, hộp thoại tự dựng, nút thô, tải chỉ bằng chữ). *Đợt 2.*
9. **Footer**: chữ phụ dùng `text-muted` (tương phản thấp) và liên kết cao chỉ 24px. **Đã xử lý ở Đợt 1.** (Ghi chú: các mục "Quy tắc cộng đồng"... là chữ tĩnh, không phải liên kết giả; giữ nguyên nội dung.)
10. **Hộp thoại đăng nhập trong AppShell** thiếu đóng bằng Esc, thiếu `aria-labelledby`, lớp phủ `bg-black/30` khác `Dialog` (`bg-black/45`). *Đợt 1.*
11. **Bảng admin**: `data-table` đồng nhất nhưng mỗi trang tự dựng bộ lọc và badge (`AdminFinancePages`, `AdminTopUpOrders`, `AdminAccounts` tự ghép `bg-*-50`). *Đợt 3.*
12. **`FinanceCharts.tsx`** viết 5 mã hex (đúng giá trị token nhưng không theo token). Cần lấy từ biến CSS đặt trong `globals.css`. *Đợt 3.*
13. **`login.css`** 22 mã hex cho trang đăng nhập/đăng ký; nên chuyển sang token. *Đợt 3.*
14. **`ReputationBadge.tsx`**: 14 màu mặc định (cyan, amber, slate, orange) cho hạng. Cần token riêng cho hạng hoặc ánh xạ sang token sẵn có. File chung nhưng không nằm trong danh sách cấm. *Đợt 2.*
15. **`CreditHistoryView.tsx`**: `bg-slate-950/45`, `shadow-2xl`, hộp thoại tự dựng. *Đợt 2 (dùng trong Credit).*
16. **Chữ vi phạm luật nhóm**: `AdminAuthenticity.tsx` hiện "Bài #{id}", "Người đăng #{donorId}", "Bài đăng #{itemId}" (trang đang tạm ẩn khỏi menu). Tên `DemoPaymentCheckout` chỉ là tên mã nguồn; chưa thấy chữ "demo" hiển thị, sẽ kiểm tra lại khi làm trang thanh toán ở Đợt 2. *Đợt 3 / Đợt 2.*

### Nhẹ
17. `globals.css` nạp font bằng `@import url(...)` đầu file CSS (chặn hiển thị); có thể chuyển sang `<link rel="preconnect">` trong `index.html`.
18. `body { overflow-x: hidden }` che lỗi tràn ngang thay vì lộ ra; giữ nhưng phải kiểm tra tràn ở 375px từng trang.
19. Giá trị lẻ `mt-[-14px]`, `mt-[-10px]`, `w-[84px]`, `w-[82%]`... rải rác; thay bằng thang khoảng cách chuẩn khi gặp.
20. Chiều cao khung tuỳ ý (`min-h-[620px]` x5, `min-h-[680px]` x3) làm Suspense fallback trắng dài; nên dùng khung chờ thật.
21. `IconButton` nhãn "Mở điều hướng" giữ nguyên dù menu đang mở (test dựa vào chữ này, chỉ ghi nhận).
22. `docs/DesignSystem.tsx` vẫn ghi bảng màu cũ (#00685F, #9D4300, #F8F9FF); tài liệu nội bộ, không ảnh hưởng ứng dụng. Cần quyết định sửa hay bỏ sau phần 1.

## C. Dồn sang phần 2 (đụng `ui.tsx` hoặc trang công khai)

### Nặng
- **`ui.tsx` `Button` cỡ `sm` cao 36px (`min-h-9`) và `IconButton` 40px** (`size-10`): dưới 44px. Cần đổi `sm` thành `min-h-11` trên mobile (hoặc `min-h-9 sm:...` có chủ đích) và `IconButton` thành `size-11`.
- **`ui.tsx` `StatusBadge`, `Alert`, `Button danger`** dùng `bg-red-50`, `bg-emerald-50`, `bg-amber-50`, `bg-blue-50`, `bg-red-100`, `border-*-100`: thay bằng token nền nhạt mới (xem mục B.5). `StatusBadge` còn lồng 8 tầng toán tử ba ngôi, nên tách bảng ánh xạ nhãn/tông.
- **`ui.tsx` `ProductCard`** dùng hex `bg-[#FFFAF5]` (nền kem không có trong token): đổi sang `surface` hoặc `secondary-soft`.
- **`Field` thiếu `aria-invalid` và `aria-describedby`** cho thông báo lỗi/gợi ý; `TextArea`/`Select` không có `hint`, `Select` không có `error`. Đồng bộ ba control.

### Vừa
- Thêm component dùng chung còn thiếu: `Spinner`/`Skeleton` (hàng bảng, chat, trang chi tiết), `Badge` chung (thay `TypeBadge`/`ConditionBadge`/`RankBadge` trùng kiểu), `Tabs`, và gộp `Dialog` + các hộp thoại tự dựng (xem A). `LoadingState` hiện chỉ có dạng lưới thẻ.
- `SearchField` dùng `outline-none` + `focus-within:ring-4 ring-primary/10`: tăng độ rõ focus như mục B.2.
- `ProductCard`/`HorizontalProductCard`/`OwnerCard`: chữ `text-[11px]`/`text-xs text-text-muted` đạt thấp về tương phản.
- `PageHeader` luôn là `h1` và `mb-7` cố định, không có biến thể gọn cho admin.

### Critique 4 màn quan trọng (chỉ đọc)
- **Home** (`Home.tsx`, `home-content.css`): dùng hex `#0d332c`, `#a9d5c6` và ~120 hex trong CSS riêng; bóng tuỳ ý `shadow-[0_24px_70px_...]`; vài liên kết dựng như nút thay vì dùng `Button`; hero đè lên header trong suốt nên chữ trắng phụ thuộc ảnh nền (cần kiểm tra tương phản). Hướng: gom sang token, tái dùng `Button`.
- **Browse** (`Browse.tsx`, `browse.css`): ~41 hex trong CSS, `select` và nút lọc tự dựng (`browse-filter-reset`), `outline-none` ở ô tìm; trạng thái tải dùng `EmptyState` ("Đang tải bài đăng") thay vì khung xương `LoadingState`, gây cảm giác trống rồi nhảy nội dung.
- **ProductDetail** (`ProductDetail.tsx`): trạng thái tải là `Alert tone="warning"` ("Đang tải chi tiết bài đăng...") nên nhìn như cảnh báo; hộp ghi chú dùng `bg-emerald-50`/`bg-amber-50` thô; 2 hộp thoại tự dựng; sidebar `lg:sticky lg:top-24` hợp lý. Hướng: khung chờ thật, hộp ghi chú qua `Alert`, dùng `Dialog`.
- **Messages** (`Messages.tsx`): xem B.7. Điểm tốt: có `role="status"`/`role="alert"` cho thông báo, chia hai cột rõ. Điểm yếu: quá dày đặc cỡ chữ 10–11px, trạng thái bằng màu thô, trạng thái tải bằng chữ.

### Nhẹ
- `Home.tsx`, `Browse.tsx`: các giá trị chiều cao/lề tuỳ ý (`mt-[-14px]`), `text-[52px]`.
- `AdminPages.tsx` phần bảng khu vực (cột Cấp): giữ nguyên, thuộc PR khác.
- `store.ts`: comment "Tạm giữ cho ProductDetail.tsx tới khi PR #332 merge" — dọn sau khi PR merge (không phải việc giao diện).

## D. Top 10 vấn đề (xếp theo ảnh hưởng)

1. Tương phản `text-muted` và chữ 10–11px dưới AA (B.1, B.7).
2. Focus quá nhạt, một số ô `outline-none` (B.2).
3. Vùng bấm dưới 44px ở header, sidebar admin, `Button sm`, `IconButton` (B.3, C).
4. Không có token nền nhạt cho trạng thái nên ~30 file dùng màu Tailwind mặc định (B.5).
5. Hex tự chế ở lớp nền tảng (AppShell, globals.css) và trang chung (`ui.tsx`, `FinanceCharts`, 3 file CSS) (B.4, B.12, B.13).
6. 16 hộp thoại tự dựng, mỗi nơi một lớp phủ/bóng/bo (A, C).
7. Ba độ rộng khung và lề khác nhau giữa header, trang, admin (B.6).
8. Messages: file quá lớn, cuộn lồng nhau, chữ quá nhỏ, tải chỉ bằng chữ (B.7).
9. Trạng thái tải thiếu khung xương ở Browse, ProductDetail, Messages, bảng admin (C).
10. `StatusBadge` và `Alert` trong `ui.tsx` vi phạm token và khó bảo trì (C).

## E. Thứ tự xử lý đề xuất

1. **Quyết định token trước Đợt 1**: có đổi giá trị `text-muted` không, thêm `*-soft` cho 4 màu ngữ nghĩa, thêm cỡ chữ tối thiểu (`text-2xs` 11px) và token vòng focus.
2. **Đợt 1**: `globals.css` (focus, selection, biến màu cho biểu đồ), `AppShell.tsx` (vùng bấm, màu menu mobile, hộp thoại đăng nhập, chữ tĩnh footer), `AdminLayout.tsx` (vùng bấm, `aria-label` nút menu, cỡ chữ), thống nhất khung.
3. **Đợt 2** theo mức rủi ro tăng dần: Profile, ReportResponse, ManualPaymentCheckout, DemoPaymentCheckout, Post, Credit, Activities, Messages (lớn nhất, làm sau cùng).
4. **Đợt 3**: auth (login.css), rồi admin: bảng, bộ lọc, `FinanceCharts`.
5. **Phần 2** (sau khi PR #332 merge): `ui.tsx` (Button sm, IconButton, StatusBadge, Alert, ProductCard, Field a11y, Skeleton/Badge/Tabs), Home, Browse, ProductDetail, AI.

## F. Cập nhật sau Đợt 2 (trang người dùng)

Đã xử lý trong các trang `pages/user` cùng `CreditHistoryView` và `ReputationBadge`: màu mặc định đổi sang token `*-soft`, `text-[10px]/[11px]` đổi sang `text-2xs`, bỏ `size="sm"` ở nút hành động, tab và mục menu cao tối thiểu 44px, lớp phủ hộp thoại `bg-black/45`, bỏ `shadow-xl`/`shadow-2xl`, sửa class sai `text-text` trong Profile.

Còn lại (không xử lý trong Đợt 2 vì rủi ro cao hoặc đụng `ui.tsx`):
- Các hộp thoại tự dựng trong Messages, Activities, Post chưa gộp vào `Dialog` (cần hỗ trợ thêm kiểu bottom-sheet trên mobile, dồn sang phần 2).
- Trạng thái tải ở Activities, Messages, Credit vẫn là dòng chữ hoặc `Alert`/`EmptyState`, chưa có khung chờ (cần `Skeleton` trong `ui.tsx`, phần 2).
- Messages vẫn có khung cao cố định `lg:h-[calc(100dvh-140px)] lg:min-h-[680px]` và file 1779 dòng; chưa tách file vì ngoài phạm vi chỉnh giao diện.
- Nút xếp lại và xóa ảnh trong Post chỉ cao 32–36px vì nằm trên ảnh thu nhỏ (ngoại lệ có chủ đích).
- Nút "Nạp Credit" trên thẻ số dư là `<button>` thô vì cần nền trắng trên nền xanh; nên có biến thể `Button` nền sáng (phần 2).

## G. Cập nhật sau Đợt 3 (auth và admin) — tổng kết phần 1

Đã xử lý:
- `login.css`: bỏ toàn bộ hex và `rgba(...)`, dùng biến CSS trong `globals.css` (`var(--color-*)` và `color-mix`); liên kết "Quên mật khẩu" và "Đăng ký ngay" cao 44px; vòng focus đậm hơn.
- `PasswordField`: nút hiện/ẩn mật khẩu `size-11`. `Register`: liên kết "Đăng nhập" cao 44px. Chữ đếm ngược OTP dùng `text-secondary`.
- Admin: tất cả badge/ghi chú/lỗi dùng `*-soft`, bỏ `size="sm"`, `text-2xs`, lớp phủ `bg-black/45`, `shadow-lg`. `FinanceCharts` dùng `var(--color-*)` thay hex.
- `AdminAuthenticity`: bỏ ID kỹ thuật (`Bài #12`, `Người đăng #5`, `Bài đăng #12`) khỏi chữ hiển thị.
- Bỏ qua đúng yêu cầu: bảng khu vực trong `AdminPages.tsx` (`ApiAdminAreas`, `ApiAreaModal`), còn nguyên `size="sm"`.

Còn lại cho phần 2 (cần sửa `ui.tsx` hoặc trang công khai): xem mục C; thêm `home-content.css` và `browse.css` còn hex, trong đó 9 chỗ vẫn dùng #73807b cũ; bảng admin chưa có khung chờ dạng skeleton; hộp thoại tự dựng ở AdminComplaints, AdminAuthenticity, AdminPages chưa gộp vào `Dialog`.

## H. Cập nhật sau Đợt 4 (component dùng chung `ui.tsx`)

Đã xử lý trong mục C (nặng):
- `Button`: cỡ `sm` đổi `min-h-9` → `min-h-11`; `danger` dùng `bg-error-soft`, hết `bg-red-50`/`bg-red-100`.
- `IconButton`: `size-10` → `size-11`, thêm trạng thái disabled.
- `StatusBadge`: tách bảng nhãn (`itemStatusLabel`, `otherStatusLabel`) và hai hàm `statusLabel`, `statusTone`; nền `*-soft`; `text-2xs`. Nhãn và thứ tự ưu tiên giữ như cũ.
- `Alert`: nền và viền `*-soft`, `border-*/20`. Chưa thêm `role` vì nhiều trang đã tự bọc `role="alert"`/`role="status"`, thêm nữa sẽ đọc trùng.
- `ProductCard`: `bg-[#FFFAF5]` → `bg-surface`.
- `Field`, `TextArea`, `Select`: có `aria-invalid`, `aria-describedby`, `hint` và `error` đồng bộ; dòng lỗi có `role="alert"`.
- `SearchField`: vòng focus `ring-primary/25`, hover viền rõ hơn, placeholder đủ tương phản.
- Thêm `Skeleton` và `SkeletonRows` (chưa dùng ở trang nào, dành cho Đợt 5).
- `TypeBadge`, `ConditionBadge`, `Avatar`: `text-[11px]` → `text-2xs`.

Còn lại: `ProductCard`/`HorizontalProductCard`/`OwnerCard` vẫn dùng `text-xs text-text-muted`; `PageHeader` chưa có biến thể gọn; `LoadingState` chỉ dạng lưới thẻ.

### Ghi chú cho PR #332
Đợt 4 chỉ sửa `ui.tsx`, không đổi tên, props hay export. Khối đã đổi:
- `ProductCard`: chỉ đổi className ở thẻ `Link` ngoài cùng (`bg-[#FFFAF5]` → `bg-surface`). Footer thẻ bài và khối `owner` không đụng tới.
- `OwnerCard`, `HorizontalProductCard`: không sửa.
- `TypeBadge`, `ConditionBadge`, `StatusBadge`, `Avatar`: chỉ đổi className cỡ chữ. `StatusBadge` đổi cấu trúc bên trong (hàm `statusLabel`, `statusTone`); nếu #332 thêm trạng thái mới (ví dụ nhãn "Được đẩy") thì thêm vào `otherStatusLabel`.
- `Home.tsx`, `Browse.tsx`, `ProductDetail.tsx`: chưa sửa ở đợt này.
