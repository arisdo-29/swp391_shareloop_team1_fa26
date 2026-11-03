import { Suspense, useState } from 'react';
import { NavLink, Outlet } from 'react-router-dom';
import { Icon } from '../../components/ui';
import type { IconName } from '../../components/ui';
// Nhóm menu theo bản thiết kế: nội dung, người dùng, tài chính, hệ thống.
// Mỗi mục gồm: [tên hiển thị, đường dẫn, tên icon].
const sections = [
  { label: '', items: [['Tổng quan', '/admin', 'dashboard']] },
  {
    label: 'Nội dung',
    items: [
      ['Duyệt bài', '/admin/moderation', 'moderation'],
      // L16: đã gỡ "Xác minh hàng chính hãng" (/admin/authenticity) ở cả FE lẫn BE.
      // Tạm ẩn "Bài quá hạn" (/admin/expired-posts): backend chưa có API danh sách cho Admin.
      ['Danh mục', '/admin/categories', 'category'],
      ['Checklist duyệt bài', '/admin/checklist', 'checklist'],
      ['Từ khóa cấm', '/admin/keywords', 'banned'],
      ['Khu vực', '/admin/districts', 'district'],
    ],
  },
  {
    label: 'Quản lý người dùng',
    items: [
      ['Tài khoản', '/admin/users', 'users'],
      ['Uy tín và hạng', '/admin/reputation', 'reputation'],
      ['Giao dịch', '/admin/transactions', 'transactions'],
      ['Khiếu nại và báo cáo', '/admin/complaints', 'complaints'],
      ['Tra cứu người dùng', '/admin/finance/users', 'lookup'],
    ],
  },
  {
    label: 'Tài chính & Credit',
    items: [
      ['Báo cáo tài chính', '/admin/finance', 'report'],
      ['Đơn nạp Credit', '/admin/finance/topup-orders', 'topup'],
    ],
  },
  {
    label: 'Hệ thống',
    items: [
      ['Cấu hình phí', '/admin/settings/fees', 'fees'],
      ['Nhật ký hoạt động', '/admin/audit-logs', 'auditLog'],
      ['Cảnh báo bất thường', '/admin/alerts', 'alert'],
    ],
  },
] as const;
export function AdminLayout() {
  // open: mở/đóng menu trên mobile. collapsed: thu gọn sidebar thành cột icon (chỉ có tác dụng từ màn lg).
  const [open, setOpen] = useState(false);
  const [collapsed, setCollapsed] = useState(false);
  // Ở màn lg khi thu gọn thì ẩn chữ; trên mobile menu luôn hiện đủ chữ nên chỉ gắn lg:hidden.
  const hideWhenCollapsed = collapsed ? 'lg:hidden' : '';
  const nav = (
    <>
      {sections.map((section) => (
        <div
          key={section.items[0][1]}
          className="mb-3 border-t border-admin-divider/20 pt-3 first:border-t-0 first:pt-0"
        >
          {section.label ? (
            <p
              className={`mb-1.5 rounded-lg bg-admin-group/40 px-3 py-2 text-xs font-bold uppercase tracking-wider text-admin-label ${hideWhenCollapsed}`}
            >
              {section.label}
            </p>
          ) : null}
          {section.items.map(([label, to, iconName]) => (
            <NavLink
              end={to === '/admin' || to === '/admin/finance'}
              key={to}
              to={to}
              title={collapsed ? label : undefined}
              onClick={() => setOpen(false)}
              className={({ isActive }) =>
                `mb-0.5 flex min-h-11 items-center gap-3 rounded-lg px-3 py-2 text-sm transition ${collapsed ? 'lg:justify-center lg:px-0' : ''} ${isActive ? 'bg-admin-group font-bold text-white' : 'font-medium text-admin-text hover:bg-admin-group/40'}`
              }
            >
              <Icon name={iconName as IconName} className="size-5 shrink-0" />
              <span className={hideWhenCollapsed}>{label}</span>
            </NavLink>
          ))}
        </div>
      ))}
    </>
  );
  return (
    <div
      className={`wide-shell py-6 lg:grid lg:items-start lg:gap-[clamp(1.25rem,2vw,2rem)] ${collapsed ? 'lg:grid-cols-[72px_minmax(0,1fr)]' : 'lg:grid-cols-[250px_minmax(0,1fr)]'}`}
    >
      <div className="mb-5 flex items-center justify-between lg:hidden">
        <div>
          <p className="text-xl font-bold">Quản trị</p>
        </div>
        <button
          onClick={() => setOpen(!open)}
          aria-label={open ? 'Đóng menu quản trị' : 'Mở menu quản trị'}
          aria-expanded={open}
          className="grid size-11 place-items-center rounded-md border border-border bg-secondary text-white transition hover:border-primary-fixed hover:bg-primary-hover"
        >
          <Icon name={open ? 'close' : 'menu'} className="size-5" />
        </button>
      </div>
      <aside
        className={`${open ? 'block' : 'hidden'} h-fit rounded-lg bg-admin-sidebar p-3 shadow-admin [scrollbar-width:none] [&::-webkit-scrollbar]:hidden lg:sticky lg:top-24 lg:block lg:max-h-[calc(100dvh-120px)] lg:overflow-y-auto ${collapsed ? 'lg:px-2' : ''}`}
      >
        <div
          className={`mb-3 hidden items-center border-b border-admin-divider/20 pb-3 lg:flex ${collapsed ? 'justify-center' : 'justify-between pl-2'}`}
        >
          {collapsed ? null : (
            <span className="text-xs font-bold uppercase tracking-wider text-admin-text">
              Điều khiển
            </span>
          )}
          <button
            type="button"
            onClick={() => setCollapsed(!collapsed)}
            aria-label={collapsed ? 'Mở rộng menu quản trị' : 'Thu gọn menu quản trị'}
            aria-expanded={!collapsed}
            className="grid size-9 place-items-center rounded-lg bg-admin-group/40 text-admin-text transition hover:bg-admin-group"
          >
            <Icon name={collapsed ? 'caretRight' : 'caretLeft'} className="size-4" />
          </button>
        </div>
        <nav aria-label="Quản trị">{nav}</nav>
      </aside>
      <main className="min-w-0 pt-2 lg:pt-0">
        <div className="mx-auto w-full max-w-[1200px]">
          <Suspense fallback={<div className="min-h-[620px]" aria-hidden="true" />}>
            <Outlet />
          </Suspense>
        </div>
      </main>
    </div>
  );
}
