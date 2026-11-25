import type { ReactNode } from 'react';
import { Link } from 'react-router-dom';
import { Icon, type IconName } from '../../components/ui';
import './admin-dashboard.css';

/** Kiểu thẻ theo nhóm: việc cần xử lý, thống kê hệ thống hoặc tài chính. */
export type DashboardCardVariant = 'task' | 'system' | 'finance';

/** Màu nhấn của thẻ. Thẻ việc dùng gold, blue, red, green; thẻ tài chính dùng cash, credit-in, credit-out. */
export type DashboardAccent =
  'gold' | 'blue' | 'red' | 'green' | 'cash' | 'credit-in' | 'credit-out';

/** Khung chung của một nhóm thẻ trên trang Tổng quan Admin. */
export function DashboardSection({
  id,
  title,
  description,
  action,
  panel = false,
  children,
}: {
  id: string;
  title: string;
  description: string;
  action?: ReactNode;
  /** Bọc cả nhóm trong một khung trắng riêng. */
  panel?: boolean;
  children: ReactNode;
}) {
  return (
    <section
      aria-labelledby={id}
      className={`admin-dash-section${panel ? ' admin-dash-panel' : ''}`}
    >
      <div className="admin-dash-section-head">
        <div className="min-w-0">
          <h2 id={id}>{title}</h2>
          <p>{description}</p>
        </div>
        {action}
      </div>
      {children}
    </section>
  );
}

/** Lưới thẻ: các thẻ cùng nhóm luôn bằng nhau và phủ kín chiều ngang. */
export function DashboardGrid({
  columns = 4,
  children,
}: {
  columns?: 3 | 4 | 5 | 6;
  children: ReactNode;
}) {
  return <div className={`admin-dash-grid admin-dash-grid--${columns}`}>{children}</div>;
}

/**
 * Một thẻ số liệu bấm được, dẫn tới trang quản lý tương ứng.
 * Thẻ việc có trạng thái pending (đang có việc) hoặc idle (không có việc) để thẻ có việc nổi bật hơn.
 */
export function DashboardStatCard({
  to,
  icon,
  label,
  value,
  unit,
  note,
  variant,
  accent,
  pending = false,
  loading = false,
  active = false,
  onClick,
  className = '',
}: {
  /** Trang quản lý tương ứng. Bỏ trống thì thẻ chỉ hiển thị số, không bấm được. */
  to?: string;
  icon: IconName;
  label: string;
  value: ReactNode;
  unit?: string;
  note: string;
  variant: DashboardCardVariant;
  accent?: DashboardAccent;
  pending?: boolean;
  loading?: boolean;
  active?: boolean;
  onClick?: () => void;
  className?: string;
}) {
  const iconNode = (
    <span className="admin-dash-icon">
      <Icon name={icon} weight="bold" />
    </span>
  );
  const valueNode = (
    <strong className="admin-dash-value">
      {value}
      {unit ? <span className="admin-dash-unit">{unit}</span> : null}
    </strong>
  );
  const cardProps = {
    'aria-busy': loading || undefined,
    'data-state': pending ? 'pending' : 'idle',
    'data-active': active || undefined,
    className: `admin-dash-card admin-dash-card--${variant}${accent ? ` admin-dash-card--${accent}` : ''}${to || onClick ? '' : ' admin-dash-card--static'}${className ? ` ${className}` : ''}`,
  };
  const content = (
    <>
      <div className="admin-dash-top">
        {iconNode}
        {valueNode}
      </div>
      <p className="admin-dash-label">{label}</p>
      <p className="admin-dash-note">{note}</p>
    </>
  );
  if (to)
    return (
      <Link to={to} {...cardProps}>
        {content}
      </Link>
    );
  if (onClick) {
    return (
      <button type="button" aria-pressed={active} onClick={onClick} {...cardProps}>
        {content}
      </button>
    );
  }
  return <div {...cardProps}>{content}</div>;
}
