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
      // Tạm ẩn "Xác minh hàng chính hãng" (/admin/authenticity): chưa có luồng xác nhận và chưa có BR. Code BE vẫn giữ.
      // Tạm ẩn "Bài quá hạn" (/admin/expired-posts): backend chưa có API danh sách cho Admin.
      ['Danh mục', '/admin/categories'],
      ['Checklist duyệt bài', '/admin/checklist'],
      ['Từ khóa cấm', '/admin/keywords'],
      ['Khu vực', '/admin/districts'],
    ],
  },
  {
    label: 'Người dùng',
    items: [
      ['Tài khoản', '/admin/users'],
      ['Uy tín và hạng', '/admin/reputation'],
      ['Giao dịch', '/admin/transactions'],
      ['Khiếu nại và báo cáo', '/admin/complaints'],
    ],
  },
  {
    label: 'Tài chính',
    items: [
      ['Báo cáo tài chính', '/admin/finance'],
      ['Đơn nạp Credit', '/admin/finance/topup-orders'],
      ['Tra cứu người dùng', '/admin/finance/users'],
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
        <div key={section.label || 'overview'} className="mb-4">
          {section.label ? (
            <p className="mb-1.5 px-3 text-2xs font-bold uppercase tracking-wider text-text-secondary">
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
                `mb-0.5 flex min-h-11 items-center rounded-md px-3 py-2 text-sm font-medium transition ${isActive ? 'bg-primary text-white' : 'text-text-secondary hover:bg-surface-low hover:text-text-primary'}`
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
          className="grid size-11 place-items-center rounded-md border border-border bg-white text-text-secondary transition hover:border-primary/50 hover:bg-primary-faint hover:text-primary"
        >
          <Icon name={open ? 'close' : 'menu'} className="size-5" />
        </button>
      </div>
      <aside
        className={`${open ? 'block' : 'hidden'} h-fit rounded-lg bg-white p-2 ring-1 ring-border/80 lg:sticky lg:top-24 lg:block lg:max-h-[calc(100dvh-120px)] lg:overflow-y-auto`}
      >
        <nav aria-label="Quản trị" className="pt-1">{nav}</nav>
      </aside>
      <main className="min-w-0 pt-2 lg:pt-0">
        <Suspense fallback={<div className="min-h-[620px]" aria-hidden="true" />}>
          <Outlet />
        </Suspense>
      </main>
    </div>
  );
}
