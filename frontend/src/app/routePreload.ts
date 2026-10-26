export function preloadRoute(pathname: string): Promise<unknown> {
  if (pathname === '/') return import('../pages/public/Home');
  if (pathname === '/browse') return import('../pages/public/Browse');
  if (pathname.startsWith('/items/') || pathname.startsWith('/product/')) {
    return import('../pages/public/ProductDetail');
  }
  if (pathname === '/post') return import('../pages/user/Post');
  if (pathname === '/ai') return import('../pages/public/AI');
  if (pathname === '/activities') return import('../pages/user/Activities');
  if (pathname === '/messages' || pathname.startsWith('/requests/') || pathname.startsWith('/me/disputes/')) {
    return import('../pages/user/Messages');
  }
  if (pathname === '/profile') return import('../pages/user/Profile');
  if (pathname === '/change-password') return import('../pages/auth/ChangePassword');
  if (pathname === '/credit' || pathname === '/wallet') return import('../pages/user/Credit');
  if (pathname === '/wallet/topup/manual-checkout') return import('../pages/user/ManualPaymentCheckout');
  if (pathname === '/wallet/topup/mock-checkout') return import('../pages/user/DemoPaymentCheckout');
  if (pathname.startsWith('/me/reports/')) return import('../pages/user/ReportResponse');
  if (!pathname.startsWith('/admin')) return Promise.resolve();

  const layout = import('../pages/admin/AdminLayout');
  let page: Promise<unknown>;
  if (pathname === '/admin') page = import('../pages/admin/AdminFinancePages');
  else if (pathname === '/admin/moderation') page = import('../pages/admin/AdminModerationList');
  else if (pathname.startsWith('/admin/moderation/')) page = import('../pages/admin/AdminModerationDetail');
  else if (pathname === '/admin/users') page = import('../pages/admin/AdminAccounts');
  else if (pathname === '/admin/reputation') page = import('../pages/admin/AdminReputation');
  else if (pathname === '/admin/complaints' || pathname === '/admin/disputes') page = import('../pages/admin/AdminComplaints');
  else if (pathname === '/admin/finance/topup-orders') page = import('../pages/admin/AdminTopUpOrders');
  else if (pathname.startsWith('/admin/finance/users')) page = import('../pages/admin/AdminFinanceUsers');
  else if (pathname === '/admin/finance') page = import('../pages/admin/AdminFinancePages');
  else if (pathname === '/admin/alerts') page = import('../pages/admin/AdminAnomalies');
  else page = import('../pages/admin/AdminPages');
  return Promise.all([layout, page]);
}
