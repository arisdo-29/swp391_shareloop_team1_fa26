import type { ReactNode } from 'react';
import { Link, NavLink } from 'react-router-dom';

/**
 * Khung chung cho các trang tài khoản (đăng nhập, đăng ký, xác thực, quên mật khẩu):
 * bên trái là câu giới thiệu ngắn, bên phải là biểu mẫu.
 * `showTabs` hiện cặp tab "Đăng nhập / Tạo tài khoản" để chuyển qua lại.
 */
export function AuthLayout({ showTabs = false, children }: { showTabs?: boolean; children: ReactNode }) {
  return (
    <div className="flex min-h-[calc(100vh-68px)] flex-col lg:flex-row">
      <aside className="flex flex-col justify-between gap-6 bg-primary-hover px-6 py-8 text-white sm:px-10 lg:w-[44%] lg:px-12 lg:py-14">
        <div className="max-w-md lg:my-auto">
          <h1 className="text-3xl font-extrabold leading-tight sm:text-4xl">
            Đồ còn tốt,
            <br />
            <span className="text-primary-fixed">trao cho người cần.</span>
          </h1>
          <p className="mt-4 text-base leading-7 text-primary-soft">
            Cho tặng và trao đổi đồ cũ với hàng xóm cùng quận.
          </p>
        </div>
        <Link to="/about" className="text-sm font-semibold text-primary-fixed underline-offset-4 hover:underline">
          ShareLoop hoạt động thế nào?
        </Link>
      </aside>
      <div className="flex flex-1 items-start justify-center px-4 py-10 sm:px-6 lg:items-center">
        <div className="w-full max-w-[420px]">
          {showTabs ? (
            <nav aria-label="Tài khoản" className="mb-6 grid grid-cols-2 gap-1 rounded-lg bg-surface-container p-1">
              <AuthTab to="/login">Đăng nhập</AuthTab>
              <AuthTab to="/register">Tạo tài khoản</AuthTab>
            </nav>
          ) : null}
          {children}
        </div>
      </div>
    </div>
  );
}

function AuthTab({ to, children }: { to: string; children: ReactNode }) {
  return (
    <NavLink
      to={to}
      className={({ isActive }) =>
        `flex min-h-11 items-center justify-center rounded-md text-sm font-semibold transition ${
          isActive ? 'bg-white text-primary shadow-sm' : 'text-text-secondary hover:text-text-primary'
        }`
      }
    >
      {children}
    </NavLink>
  );
}
