---
name: ShareLoop
description: Nền tảng trao tặng và trao đổi đồ cũ trong cộng đồng, giao diện xanh rêu sạch, ấm và dễ tin cậy.
colors:
  primary: "#145C4E"
  primary-hover: "#0F4A3F"
  primary-fixed: "#CDE4DC"
  primary-soft: "#E7F1ED"
  primary-faint: "#F2F7F5"
  secondary: "#B85C35"
  secondary-hover: "#984629"
  secondary-fixed: "#F2D3C5"
  secondary-soft: "#F8EDE8"
  background: "#F5F7F6"
  surface: "#FFFFFF"
  surface-low: "#F0F3F2"
  surface-container: "#E5EAE8"
  surface-high: "#D8E0DD"
  text-primary: "#17211E"
  text-secondary: "#46534F"
  text-muted: "#5F6B66"
  border: "#D7DEDB"
  success: "#277A55"
  warning: "#8C5410"
  error: "#B44444"
  info: "#356A8A"
typography:
  page-title:
    fontFamily: "Be Vietnam Pro, system-ui, sans-serif"
    fontSize: "1.5rem"
    fontWeight: 700
    lineHeight: 1.25
  section-title:
    fontFamily: "Be Vietnam Pro, system-ui, sans-serif"
    fontSize: "1.125rem"
    fontWeight: 700
    lineHeight: 1.25
  body:
    fontFamily: "Be Vietnam Pro, system-ui, sans-serif"
    fontSize: "0.875rem"
    fontWeight: 400
    lineHeight: 1.5
  label:
    fontFamily: "Be Vietnam Pro, system-ui, sans-serif"
    fontSize: "0.75rem"
    fontWeight: 600
rounded:
  sm: "6px"
  md: "10px"
  lg: "14px"
  xl: "18px"
spacing:
  xs: "4px"
  sm: "8px"
  md: "16px"
  lg: "24px"
  xl: "32px"
components:
  button-primary:
    backgroundColor: "{colors.primary}"
    textColor: "{colors.surface}"
    rounded: "{rounded.md}"
    padding: "8px 16px"
    height: "44px"
  button-secondary:
    backgroundColor: "{colors.secondary}"
    textColor: "{colors.surface}"
    rounded: "{rounded.md}"
    height: "44px"
  button-outline:
    backgroundColor: "{colors.surface}"
    textColor: "{colors.text-secondary}"
    rounded: "{rounded.md}"
    height: "44px"
  input:
    backgroundColor: "{colors.surface}"
    textColor: "{colors.text-primary}"
    rounded: "{rounded.md}"
    padding: "10px 14px"
    height: "44px"
  card:
    backgroundColor: "{colors.surface}"
    rounded: "{rounded.lg}"
---

# Design System: ShareLoop

> Tài liệu mô tả hệ thống giao diện **đang có** của `frontend/`. Giá trị thật nằm ở `tailwind.config.js` và `src/styles/globals.css`; nếu lệch nhau thì file cấu hình là chuẩn và tài liệu này phải sửa theo.

## Overview

**Creative North Star: "Góc trao đổi của hàng xóm"**

ShareLoop là sàn trao tặng và trao đổi đồ cũ, nên giao diện phải làm người lạ thấy an tâm: nền sáng hơi xanh, xanh rêu đậm cho hành động chính, cam đất ấm cho điểm nhấn trao đổi. Màn hình ứng dụng (người dùng, quản trị) ưu tiên dễ quét và nhất quán hơn là hiệu ứng; thương hiệu thể hiện qua màu, bo góc và cách đặt chữ tiếng Việt, không qua trang trí.

Mật độ vừa phải: thẻ trắng viền mảnh trên nền xám xanh nhạt, chữ nhỏ gọn (14px) nhưng thoáng dòng. Trang công khai (Home, Browse, AI) được phép giàu hình ảnh hơn; trang thao tác (Post, Messages, Credit, Admin) giữ phẳng và rõ.

**Key Characteristics:**
- Một màu hành động duy nhất: xanh rêu `primary`. Cam đất `secondary` chỉ dùng cho nhãn "Trao đổi" và nút phụ hiếm.
- Bề mặt phẳng, phân lớp bằng viền mảnh (`ring-1 ring-border`) và nền `surface-low`; bóng đổ chỉ khi hover hoặc nổi lên (menu, hộp thoại).
- Bo góc vừa (10–14px), không tròn hoàn toàn ngoài avatar/chip.
- Icon duy nhất: Phosphor, qua component `Icon`.

## Colors

Bảng màu xanh rêu – cam đất trên nền trung tính hơi xanh. Mọi màu lấy từ token Tailwind; không viết hex trong component.

### Primary
- **Xanh rêu đậm** (`primary`, #145C4E): nút chính, liên kết, mục điều hướng đang chọn, focus. Hover dùng `primary-hover`.
- **Xanh rêu nhạt** (`primary-fixed`, `primary-soft`, `primary-faint`): nền chip/badge, hover hàng bảng, mục menu đang chọn. `primary-faint` là nền hover nhẹ nhất.

### Secondary
- **Cam đất** (`secondary`, #B85C35): nhãn "Trao đổi", nút phụ. `secondary-soft` / `secondary-fixed` là nền nhãn.

### Neutral
- **Nền trang** (`background`, #F5F7F6), **thẻ** (`surface`, #FFFFFF), **nền phụ** (`surface-low`), **nền ô/skeleton** (`surface-container`), **nền nhấn** (`surface-high`).
- **Chữ**: `text-primary` (nội dung chính), `text-secondary` (nhãn, mô tả), `text-muted` (phụ chú; chỉ dùng cho chữ ≥ 14px hoặc thông tin không thiết yếu vì độ tương phản thấp, xem ui-audit).
- **Viền** (`border`, #D7DEDB).

### Semantic
- `success` #277A55, `warning` #8C5410, `error` #B44444, `info` #356A8A: màu chữ/icon cho trạng thái. Nền nhạt đi kèm hiện chưa có token (đang dùng `bg-*-50` mặc định, là việc tồn đọng của Đợt 1).

### Named Rules
**The One Voice Rule.** `primary` là giọng chính; mỗi khu vực chỉ có một nút `primary`. Cam đất không dùng cho hành động chính.

**The Token Only Rule.** Không hex, không `gray-*`/`red-*`/`amber-*`/`emerald-*`/`blue-*` khi đã có token tương đương. Thiếu token thì thêm vào `tailwind.config.js` rồi mới dùng.

## Typography

**Font:** Be Vietnam Pro (fallback `system-ui, sans-serif`), nạp trọng số 400–800 trong `globals.css`. Một họ chữ cho toàn bộ giao diện.

**Character:** Chữ không chân, đủ dấu tiếng Việt rõ nét; tiêu đề đậm, nội dung nhẹ, tạo nhịp quét nhanh.

### Hierarchy
- **Page title** (`.page-title`; 700, 24px → 30px từ `sm`, leading tight): tiêu đề trang, một `h1` mỗi trang.
- **Section title** (`.section-title`; 700, 18px → 20px): tiêu đề khối trong trang.
- **Body** (400–500, `text-sm` 14px, leading 24px): nội dung chính; mô tả trang `text-sm sm:text-base`.
- **Label** (`.label`; 600, `text-xs` 12px): nhãn trường nhập, tiêu đề cột bảng.
- **Badge** (600–700, 11px): chỉ cho badge/chip ngắn. Không dùng cỡ chữ nhỏ hơn 11px; không dùng 10px.
- **Số liệu**: `tabular-nums`, `text-2xl font-bold` (component `Stat`).

### Named Rules
**The No Tiny Text Rule.** Dưới 12px chỉ dành cho badge/chip, và chữ phải đạt tương phản AA. Không dùng `text-muted` cho chữ dưới 12px.

**The Heading Order Rule.** Mỗi trang một `h1` (qua `PageHeader`), khối con dùng `h2`/`h3` theo thứ tự.

## Layout

- **Khung trang**: `.page-shell` (tối đa 1280px, đệm `px-4 sm:px-6 lg:px-8`, `py-8 → py-12`). Header và footer của `AppShell` đang rộng 1500px, khu admin 1440px (chưa thống nhất, xem ui-audit).
- **Thang khoảng cách**: dùng thang mặc định của Tailwind (4px). Quy ước: 8px trong cụm nhỏ, 16px giữa các trường, 24px giữa các khối, 32–48px giữa các section. Tránh giá trị lẻ như `mt-[-14px]`.
- **Lưới**: thẻ sản phẩm 1 → 2 → 4 cột (`sm:grid-cols-2 lg:grid-cols-4`); trang chi tiết `1fr + 360px` từ `lg`.
- **Responsive bắt buộc**: 375px, 768px, 1280px, không cuộn ngang trang. Bảng được cuộn ngang trong `.table-wrap` (`data-table` rộng tối thiểu 720px). Vùng bấm tối thiểu 44px.
- **Header**: cao 68px, dính đầu trang (trừ Home, nơi header đè lên hero). Điều hướng ngang từ `lg`, menu thu gọn dưới `lg`.
- **Khu admin**: sidebar 230px dính ở `lg`, thu thành nút menu ở màn nhỏ.

## Elevation & Depth

Phẳng khi nghỉ, phân lớp bằng viền và tông nền; bóng đổ là phản hồi trạng thái.

### Shadow Vocabulary
- **sm** (`0 1px 2px rgba(20,45,38,.05)`): thẻ `.card` khi nghỉ.
- **md** (`0 10px 30px rgba(20,45,38,.08)`): thẻ hover, khung chat lớn.
- **lg** (`0 24px 60px rgba(20,45,38,.12)`): menu thả xuống, hộp thoại.

### Named Rules
**The Flat-By-Default Rule.** Không thêm bóng cho thẻ tĩnh. Chỉ dùng `shadow-sm|md|lg`; không dùng `shadow-xl`, `shadow-2xl` hay giá trị tuỳ ý.

## Shapes

Bo góc: `sm` 6px (badge, chip), `md` 10px (nút, ô nhập), `lg` 14px (thẻ), `xl` 18px (khối lớn, hộp thoại). Viền mảnh `ring-1 ring-border/80`. Avatar vuông bo `md`. Chỉ dùng `rounded-full` cho chấm trạng thái và chip lọc dạng viên thuốc; không dùng `rounded-2xl/3xl`.

## Components

Dùng lại component trong `src/components/ui.tsx` trước khi tự viết.

### Danh sách component dùng chung (`ui.tsx`)
`Icon`, `Button` (primary, secondary, outline, ghost, danger; sm/md/lg), `IconButton`, `Field`, `TextArea`, `Select`, `SearchField`, `TypeBadge`, `ConditionBadge`, `StatusBadge`, `RankBadge`, `EmptyState`, `LoadingState`, `ItemImage`, `Alert` (info, success, warning, error), `ProductCard`, `HorizontalProductCard`, `OwnerCard`, `Avatar`, `Stat`, `PageHeader`.

Ngoài `ui.tsx`: `Dialog` (hộp thoại xác nhận), `Pagination`, `PasswordField`, `OtpInput`, `ReputationBadge`, `NotificationMenu`, `ReportDialog`, `ShareLoopLogo`.

Lớp tiện ích trong `globals.css`: `.page-shell`, `.page-title`, `.page-intro`, `.section-title`, `.card`, `.input`, `.label`, `.table-wrap`, `.data-table`, `.surface-section`.

### Buttons
- Cao tối thiểu 44px (`md`), 48px (`lg`). Cỡ `sm` (36px) chỉ dùng trong hàng dày đặc trên desktop và không là hành động duy nhất trên mobile.
- `primary`: nền xanh rêu, chữ trắng; hover `primary-hover`. `outline`: viền `border`, chữ `text-secondary`. `ghost`: không viền. `danger`: chữ `error` trên nền nhạt.
- Disabled: `opacity-40` + `cursor-not-allowed`, không đổi hover. Đang xử lý: giữ chiều rộng, đổi nhãn (ví dụ "Đang gửi...") và `disabled`.

### Inputs / Fields
- `.input`: cao 44px, viền `border`, hover viền `text-muted`, focus viền `primary` + vòng `ring-4 ring-primary/10`. Lỗi: viền `error`, thông báo `text-xs text-error` ngay dưới ô. Disabled: nền `surface-low`.
- Mỗi ô có `label` hiển thị; placeholder không thay nhãn.

### Cards / Containers
- `.card` / `.surface-section`: nền trắng, `ring-1 ring-border`, bo `lg`/`xl`, đệm 20–24px. Không lồng thẻ trong thẻ.

### Navigation
- Header: mục đang chọn `bg-primary-faint text-primary`, chưa chọn `text-text-secondary`, hover `bg-surface-low`.
- Admin: mục đang chọn `bg-primary text-white`. Mục menu cao tối thiểu 44px trên màn cảm ứng.

### Badges / Status
- Nhãn trạng thái dùng `StatusBadge`: nền nhạt + chữ màu ngữ nghĩa (đỏ = lỗi/từ chối, xanh = hoàn tất, vàng = chờ). Không tự dựng badge mới cho trạng thái giao dịch/bài đăng.

### Bảng (admin)
- `.table-wrap` + `.data-table`: tiêu đề `surface-low`, hàng cách nhau bằng viền, hover `primary-faint`. Cột trạng thái dùng `StatusBadge`.

## Do's and Don'ts

### Do:
- **Do** dùng `PageHeader` cho tiêu đề trang, `EmptyState`/`LoadingState`/`Alert` cho trạng thái.
- **Do** có `focus-visible` rõ cho mọi phần tử bấm được; giữ vòng focus nhìn thấy được trên nền trắng và nền xanh.
- **Do** giữ nguyên `aria-label`, `role`, `data-testid` khi đổi giao diện (test dựa vào chúng).
- **Do** đặt chữ hiển thị bằng tiếng Việt có dấu, câu ngắn, dùng nhãn thân thiện.

### Don't:
- **Don't** viết hex, `rgb()` trong component; không dùng màu Tailwind mặc định khi có token.
- **Don't** dùng icon ngoài Phosphor (qua `Icon`); không dùng emoji làm icon.
- **Don't** tự viết nút/badge/hộp thoại mới khi `ui.tsx` hoặc `Dialog` đã có.
- **Don't** dùng `shadow-xl`, `shadow-2xl`, `rounded-2xl`, cỡ chữ `text-[10px]`.
- **Don't** để chữ dài tràn ngang: dùng `truncate`/`line-clamp` + `min-w-0`.

### Không được làm
- Không đổi business logic, API call, điều kiện hiển thị, luồng giao dịch/Credit, phân quyền khi chỉnh giao diện.
- Không thêm thư viện npm mới; không lấy bảng màu, font, style từ catalog hay DESIGN.md của brand khác.
- Không tính, đổi hay hiển thị sai Credit trên trình duyệt; không thay đổi trạng thái giao dịch ở frontend.
- Không dùng GPS, km, bán kính hay "gần tôi" (chỉ tìm theo quận/huyện).
- Không tự sửa `src/components/ui.tsx` khi PR dùng chung đang mở; ghi vào `docs/ui-audit.md`, mục "Dồn sang phần 2".

## Quy tắc trạng thái

- **Loading**: có khung chờ cùng hình dạng nội dung (`LoadingState` cho lưới thẻ; hàng skeleton cho bảng/chat). Nút đang gửi bị `disabled` và đổi nhãn. Không để trang trắng.
- **Rỗng**: `EmptyState` có icon, tiêu đề, một câu giải thích và (nếu có) một hành động tiếp theo.
- **Lỗi**: `Alert tone="error"` hoặc thông báo ngay dưới ô nhập; dùng `role="alert"` cho lỗi vừa phát sinh; luôn có cách thử lại khi lỗi do tải dữ liệu.
- **Thành công**: `Alert tone="success"` hoặc `role="status"`, ngắn gọn.
- **Disabled**: mờ 40%, con trỏ không cho phép, kèm lý do bằng chữ khi người dùng khó đoán.
- **Hover/Focus**: hover đổi nền nhẹ (`primary-faint`, `surface-low`); focus-visible luôn hiện vòng viền.

## Quy tắc chữ hiển thị của nhóm

- Không dùng dấu chấm phẩy (`;`) trong chữ hiển thị.
- Không hiện ID kỹ thuật (kiểu `#12`, `userId`, mã nội bộ); dùng tên, tiêu đề hoặc mã đơn dành cho người dùng.
- Không hiện enum thô (`PENDING_REVIEW`, `CREDIT_HELD`...); ánh xạ sang nhãn tiếng Việt.
- Không có chữ "demo" trong giao diện người dùng.
- Số tiền/Credit, ngày giờ định dạng theo `vi-VN`; hiển thị đúng giá trị backend trả về.
