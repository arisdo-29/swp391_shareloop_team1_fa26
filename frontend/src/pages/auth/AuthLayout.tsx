import type { ReactNode } from 'react';
import { Link, NavLink } from 'react-router-dom';
import './auth-layout.css';

const authHighlights = [
  'Miễn phí cho tặng và trao đổi',
  'Gặp mặt gần bạn, theo quận',
  'Cộng đồng đã được kiểm duyệt',
] as const;

/**
 * Khung chung cho các trang tài khoản (đăng nhập, đăng ký, xác thực, quên mật khẩu):
 * bên trái là câu giới thiệu ngắn, bên phải là biểu mẫu.
 * `showTabs` hiện cặp tab "Đăng nhập / Tạo tài khoản" để chuyển qua lại.
 */
export function AuthLayout({
  showTabs = false,
  children,
}: {
  showTabs?: boolean;
  children: ReactNode;
}) {
  return (
    <div
      className={`flex min-h-[calc(100vh-68px)] flex-col lg:flex-row ${showTabs ? 'auth-entry' : ''}`}
    >
      <aside
        className={`flex flex-col justify-between gap-6 bg-primary-hover px-6 py-8 text-white sm:px-10 lg:w-[44%] lg:px-12 lg:py-14 ${showTabs ? 'auth-entry-panel' : ''}`}
      >
        <div className="auth-entry-copy max-w-md lg:my-auto">
          <h1 className="text-3xl font-extrabold leading-tight sm:text-4xl">
            <span className="auth-entry-title-main">Đồ còn tốt,</span>
            <br />
            <span className="auth-entry-title-accent text-primary-fixed">trao cho người cần.</span>
          </h1>
          <p className="auth-entry-description mt-4 text-base leading-7 text-primary-soft">
            Cho tặng và trao đổi đồ cũ với hàng xóm cùng quận.
          </p>
          {showTabs ? (
            <ul className="auth-entry-highlights" aria-label="Lợi ích của ShareLoop">
              {authHighlights.map((highlight) => (
                <li key={highlight}>
                  <span className="auth-entry-check" aria-hidden="true">
                    ✓
                  </span>
                  <span>{highlight}</span>
                </li>
              ))}
            </ul>
          ) : null}
        </div>
        <Link
          to="/about"
          className="auth-entry-about text-sm font-semibold text-primary-fixed underline-offset-4 hover:underline"
        >
          ShareLoop hoạt động thế nào?
        </Link>
      </aside>
      <div className="auth-entry-main flex flex-1 items-start justify-center px-4 py-10 sm:px-6 lg:items-center">
        <div className="auth-entry-card w-full max-w-[420px] rounded-xl bg-white p-5 shadow-md ring-1 ring-border/80 sm:p-6">
          {showTabs ? (
            <nav
              aria-label="Tài khoản"
              className="auth-entry-tabs mb-6 grid grid-cols-2 gap-1 rounded-lg bg-surface-container p-1"
            >
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
        `auth-entry-tab flex min-h-11 items-center justify-center rounded-md text-sm transition ${isActive ? 'is-active' : ''}`
      }
    >
      {children}
    </NavLink>
  );
}
