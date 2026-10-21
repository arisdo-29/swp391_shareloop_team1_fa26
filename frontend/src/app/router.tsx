import { createBrowserRouter } from 'react-router-dom';
import { AppShell } from '../layouts/AppShell';
import {
  AdminRoute,
  GuestOnlyRoute,
  ProfileRoute,
  ProtectedRoute,
  PublicRoute,
} from '../routes/Guards';
import { Home } from '../pages/public/Home';
import { Browse } from '../pages/public/Browse';
import { ProductDetail } from '../pages/public/ProductDetail';
import { AI } from '../pages/public/AI';
import { Login } from '../pages/auth/Login';
import { Register } from '../pages/auth/Register';
import { VerifyEmail } from '../pages/auth/VerifyEmail';
import { ForgotPassword } from '../pages/auth/ForgotPassword';
import { ChangePassword } from '../pages/auth/ChangePassword';
import { Post } from '../pages/user/Post';
import { Activities } from '../pages/user/Activities';
import { Messages } from '../pages/user/Messages';
import { Profile } from '../pages/user/Profile';
import { Credit } from '../pages/user/Credit';
import { DemoPaymentCheckout } from '../pages/user/DemoPaymentCheckout';
import { ManualPaymentCheckout } from '../pages/user/ManualPaymentCheckout';
import { ReportResponse } from '../pages/user/ReportResponse';
import { AdminLayout } from '../pages/admin/AdminLayout';
import { AdminAuthenticity } from '../pages/admin/AdminAuthenticity';
import { AdminUnderDevelopment } from '../pages/admin/AdminUnderDevelopment';
import { AdminComplaints } from '../pages/admin/AdminComplaints';
import { AdminTopUpOrders } from '../pages/admin/AdminTopUpOrders';
import { NotFound } from '../pages/NotFound';
import {
  AdminOperationsDashboard as AdminDashboard,
  AdminFinanceReport as AdminFinance,
} from '../pages/admin/AdminFinancePages';
import {
  AdminAuditLogs,
  AdminModeration,
  AdminSettings,
  AdminContentPage,
} from '../pages/admin/AdminPages';

export const router = createBrowserRouter([
  {
    element: <AppShell />,
    children: [
      {
        element: <PublicRoute />,
        children: [
          { path: '/', element: <Home /> },
          { path: '/browse', element: <Browse /> },
          { path: '/items/:id', element: <ProductDetail /> },
          { path: '/product/:id', element: <ProductDetail /> },
        ],
      },
      {
        element: <GuestOnlyRoute />,
        children: [
          { path: '/login', element: <Login /> },
          { path: '/register', element: <Register /> },
          { path: '/verify-email', element: <VerifyEmail /> },
          { path: '/forgot-password', element: <ForgotPassword /> },
          { path: '/forgot-password/verify', element: <ForgotPassword /> },
          { path: '/reset-password', element: <ForgotPassword /> },
        ],
      },
      {
        element: <ProtectedRoute />,
        children: [
          { path: '/post', element: <Post /> },
          { path: '/ai', element: <AI /> },
          { path: '/activities', element: <Activities /> },
          { path: '/messages', element: <Messages /> },
          { path: '/requests/:requestId', element: <Messages /> },
          { path: '/me/disputes/:disputeId', element: <Messages /> },
          { path: '/me/reports/:reportId', element: <ReportResponse /> },
          { path: '/credit', element: <Credit /> },
          { path: '/wallet', element: <Credit /> },
          { path: '/wallet/topup/manual-checkout', element: <ManualPaymentCheckout /> },
          {
            path: '/wallet/topup/mock-checkout',
            element: <DemoPaymentCheckout />,
          },
        ],
      },
      {
        element: <ProfileRoute />,
        children: [
          { path: '/profile', element: <Profile /> },
          { path: '/change-password', element: <ChangePassword /> },
        ],
      },
      {
        element: <AdminRoute />,
        children: [
          {
            path: '/admin',
            element: <AdminLayout />,
            children: [
              { index: true, element: <AdminDashboard /> },
              { path: 'moderation', element: <AdminModeration /> },
              { path: 'authenticity', element: <AdminAuthenticity /> },
              { path: 'checklist', element: <AdminContentPage kind="checklist" /> },
              { path: 'expired-posts', element: <AdminContentPage kind="expired" /> },
              { path: 'categories', element: <AdminContentPage kind="categories" /> },
              { path: 'keywords', element: <AdminContentPage kind="keywords" /> },
              { path: 'districts', element: <AdminContentPage kind="districts" /> },
              { path: 'users', element: <AdminUnderDevelopment title="Quản lý tài khoản" /> },
              { path: 'users/:userId', element: <AdminUnderDevelopment title="Lịch sử Credit người dùng" /> },
              { path: 'reputation', element: <AdminUnderDevelopment title="Uy tín & Hạng" /> },
              { path: 'locked-users', element: <AdminUnderDevelopment title="Quản lý tài khoản" /> },
              { path: 'transactions', element: <AdminUnderDevelopment title="Giao dịch" /> },
              { path: 'transactions/:transactionId', element: <AdminUnderDevelopment title="Giao dịch" /> },
              { path: 'complaints', element: <AdminComplaints /> },
              { path: 'disputes', element: <AdminComplaints /> },
              { path: 'alerts', element: <AdminContentPage kind="alerts" /> },
              { path: 'finance', element: <AdminFinance /> },
              { path: 'finance/topup-orders', element: <AdminTopUpOrders /> },
              { path: 'finance/users', element: <AdminUnderDevelopment title="Lịch sử Credit người dùng" /> },
              { path: 'finance/users/:userId', element: <AdminUnderDevelopment title="Lịch sử Credit người dùng" /> },
              { path: 'settings/fees', element: <AdminSettings /> },
              { path: 'settings/ranks', element: <AdminContentPage kind="ranks" /> },
              { path: 'audit-logs', element: <AdminAuditLogs /> },
            ],
          },
        ],
      },
      { path: '*', element: <NotFound /> },
    ],
  },
]);
