import { createBrowserRouter, Navigate } from 'react-router-dom';
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
import { AdminUnderDevelopment } from '../pages/admin/AdminUnderDevelopment';
import { AdminComplaints } from '../pages/admin/AdminComplaints';
import { AdminTopUpOrders } from '../pages/admin/AdminTopUpOrders';
import { AdminReputation } from '../pages/admin/AdminReputation';
import { AdminAccounts } from '../pages/admin/AdminAccounts';
import { AdminModerationList } from '../pages/admin/AdminModerationList';
import { AdminModerationDetail } from '../pages/admin/AdminModerationDetail';
import { AdminFinanceUsers, AdminFinanceUserDetail } from '../pages/admin/AdminFinanceUsers';
import { NotFound } from '../pages/NotFound';
import {
  AdminOperationsDashboard as AdminDashboard,
  AdminFinanceReport as AdminFinance,
} from '../pages/admin/AdminFinancePages';
import {
  AdminAuditLogs,
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
              { path: 'moderation', element: <AdminModerationList /> },
              { path: 'moderation/:itemId', element: <AdminModerationDetail /> },
              // Tạm ẩn trang Nhãn chính hãng (chưa có luồng xác nhận, chưa có BR): đưa về trang tổng quan.
              { path: 'authenticity', element: <Navigate to="/admin" replace /> },
              { path: 'checklist', element: <AdminContentPage kind="checklist" /> },
              { path: 'expired-posts', element: <AdminContentPage kind="expired" /> },
              { path: 'categories', element: <AdminContentPage kind="categories" /> },
              { path: 'keywords', element: <AdminContentPage kind="keywords" /> },
              { path: 'districts', element: <AdminContentPage kind="districts" /> },
              { path: 'users', element: <AdminAccounts /> },
              { path: 'reputation', element: <AdminReputation /> },
              // Gộp "Khóa tài khoản" vào Quản lý tài khoản: giữ đường dẫn cũ để link đã lưu vẫn dùng được.
              { path: 'locked-users', element: <Navigate to="/admin/users" replace /> },
              { path: 'transactions', element: <AdminUnderDevelopment title="Giao dịch" /> },
              { path: 'transactions/:transactionId', element: <AdminUnderDevelopment title="Giao dịch" /> },
              { path: 'complaints', element: <AdminComplaints /> },
              { path: 'disputes', element: <AdminComplaints /> },
              { path: 'alerts', element: <AdminContentPage kind="alerts" /> },
              { path: 'finance', element: <AdminFinance /> },
              { path: 'finance/topup-orders', element: <AdminTopUpOrders /> },
              { path: 'finance/users', element: <AdminFinanceUsers /> },
              { path: 'finance/users/:userId', element: <AdminFinanceUserDetail /> },
              { path: 'settings/fees', element: <AdminSettings /> },
              // Hạng và điểm uy tín nay cấu hình chung ở trang Uy tín & Hạng.
              { path: 'settings/ranks', element: <Navigate to="/admin/reputation" replace /> },
              { path: 'audit-logs', element: <AdminAuditLogs /> },
            ],
          },
        ],
      },
      { path: '*', element: <NotFound /> },
    ],
  },
]);
