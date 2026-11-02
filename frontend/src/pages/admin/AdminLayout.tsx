import { Suspense, useState } from 'react';
import { NavLink, Outlet } from 'react-router-dom';
import { Icon } from '../../components/ui';
// Nhóm menu theo bản thiết kế: nội dung, người dùng, tài chính, hệ thống.
const sections = [
  { label: '', items: [['Tổng quan', '/admin']] },
  {
    label: 'Nội dung',
    items: [
      ['Duyệt bài', '/admin/moderation'],
      // L16: đã gỡ "Xác minh hàng chính hãng" (/admin/authenticity) ở cả FE lẫn BE.
      // Tạm ẩn "Bài quá hạn" (/admin/expired-posts): backend chưa có API danh sách cho Admin.
      ['Danh mục', '/admin/categories'],
      ['Checklist duyệt bài', '/admin/checklist'],
      ['Từ khóa cấm', '/admin/keywords'],
      ['Khu vực', '/admin/districts'],
    ],
  },
  {
    label: 'Quản lý người dùng',
    items: [
      ['Tài khoản', '/admin/users'],
      ['Uy tín và hạng', '/admin/reputation'],
      ['Giao dịch', '/admin/transactions'],
      ['Khiếu nại và báo cáo', '/admin/complaints'],
      ['Tra cứu người dùng', '/admin/finance/users'],
    ],
  },
  {
    label: 'Tài chính & Credit',
    items: [
      ['Báo cáo tài chính', '/admin/finance'],
      ['Đơn nạp Credit', '/admin/finance/topup-orders'],
    ],
  },
  {
    label: 'Hệ thống',
    items: [
      ['Cấu hình phí', '/admin/settings/fees'],
      ['Nhật ký hoạt động', '/admin/audit-logs'],
      ['Cảnh báo bất thường', '/admin/alerts'],
    ],
  },
] as const;
export function AdminLayout() {
  const [open, setOpen] = useState(false);
  const nav = (
    <>
      {sections.map((section) => (
        <div
          key={section.items[0][1]}
          className="mb-4 border-t border-admin-divider pt-4 first:border-t-0 first:pt-0"
        >
          {section.label ? (
            <p className="mb-1.5 rounded-md bg-admin-group px-3 py-2 text-xs font-bold uppercase tracking-wider text-admin-label">
              {section.label}
            </p>
          ) : null}
          {section.items.map(([label, to]) => (
            <NavLink
              end={to === '/admin' || to === '/admin/finance'}
              key={to}
              to={to}
              onClick={() => setOpen(false)}
              className={({ isActive }) =>
                `mb-0.5 flex min-h-11 items-center rounded-md py-2 pl-5 pr-3 text-sm transition ${isActive ? 'bg-primary font-bold text-white' : 'font-medium text-admin-text hover:bg-admin-group'}`
              }
            >
              {label}
            </NavLink>
          ))}
        </div>
      ))}
    </>
  );
  return (
    <div className="mx-auto max-w-[1440px] px-4 py-6 sm:px-6 lg:grid lg:grid-cols-[230px_1fr] lg:gap-8 lg:px-8">
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
        className={`${open ? 'block' : 'hidden'} h-fit rounded-lg bg-admin-sidebar p-2 shadow-admin [scrollbar-color:var(--color-admin-divider)_transparent] [scrollbar-width:thin] [&::-webkit-scrollbar-thumb]:rounded-full [&::-webkit-scrollbar-thumb]:bg-[var(--color-admin-divider)] [&::-webkit-scrollbar-track]:bg-transparent [&::-webkit-scrollbar]:w-2 lg:sticky lg:top-24 lg:block lg:max-h-[calc(100dvh-120px)] lg:overflow-y-auto`}
      >
        <nav aria-label="Quản trị" className="pt-1">
          {nav}
        </nav>
      </aside>
      <main className="min-w-0 pt-2 lg:pt-0">
        <Suspense fallback={<div className="min-h-[620px]" aria-hidden="true" />}>
          <Outlet />
        </Suspense>
      </main>
    </div>
  );
}
