import { Link, useLocation } from 'react-router-dom';
import { Icon } from '../../components/ui';

type NavigationSection = { readonly label: string; readonly items: readonly (readonly string[])[] };

/** Breadcrumb chỉ trình bày các route điều hướng sẵn có, không tải dữ liệu. */
export function AdminBreadcrumb({ sections }: { sections: readonly NavigationSection[] }) {
  const { pathname } = useLocation();
  const navigationPath = pathname === '/admin/disputes' ? '/admin/complaints' : pathname;
  const current = sections
    .flatMap((section) => section.items.map(([label, to]) => ({ label, to, group: section.label })))
    .filter(
      (entry) =>
        entry.to !== '/admin' &&
        (navigationPath === entry.to || navigationPath.startsWith(`${entry.to}/`)),
    )
    .sort((left, right) => right.to.length - left.to.length)[0];
  return (
    <nav className="admin-breadcrumb" aria-label="Đường dẫn quản trị">
      <Link to="/admin">Quản trị</Link>
      {current ? (
        <>
          <Icon name="caretRight" className="size-3" />
          {current.group ? (
            <>
              <span className="admin-breadcrumb-group">{current.group}</span>
              <Icon name="caretRight" className="admin-breadcrumb-group size-3" />
            </>
          ) : null}
          <Link to={current.to} aria-current={navigationPath === current.to ? 'page' : undefined}>
            {current.label}
          </Link>
          {navigationPath !== current.to ? (
            <>
              <Icon name="caretRight" className="size-3" />
              <span aria-current="page">Chi tiết</span>
            </>
          ) : null}
        </>
      ) : (
        <>
          <Icon name="caretRight" className="size-3" />
          <span aria-current="page">{pathname === '/admin' ? 'Tổng quan' : 'Trang quản trị'}</span>
        </>
      )}
    </nav>
  );
}
