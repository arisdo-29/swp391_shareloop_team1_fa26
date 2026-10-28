import { lazy } from 'react';
import { createBrowserRouter, Navigate } from 'react-router-dom';
import { AppShell } from '../layouts/AppShell';
import {
  AdminRoute,
  GuestOnlyRoute,
  ProfileRoute,
  ProtectedRoute,
  PublicRoute,
} from '../routes/Guards';

const Home = lazy(() => import('../pages/public/Home').then((module) => ({ default: module.Home })));
const Browse = lazy(() => import('../pages/public/Browse').then((module) => ({ default: module.Browse })));
const ProductDetail = lazy(() => import('../pages/public/ProductDetail').then((module) => ({ default: module.ProductDetail })));
const About = lazy(() => import('../pages/public/About').then((module) => ({ default: module.About })));
const AI = lazy(() => import('../pages/public/AI').then((module) => ({ default: module.AI })));
const Login = lazy(() => import('../pages/auth/Login').then((module) => ({ default: module.Login })));
const Register = lazy(() => import('../pages/auth/Register').then((module) => ({ default: module.Register })));
const VerifyEmail = lazy(() => import('../pages/auth/VerifyEmail').then((module) => ({ default: module.VerifyEmail })));
const ForgotPassword = lazy(() => import('../pages/auth/ForgotPassword').then((module) => ({ default: module.ForgotPassword })));
const ChangePassword = lazy(() => import('../pages/auth/ChangePassword').then((module) => ({ default: module.ChangePassword })));
const Post = lazy(() => import('../pages/user/Post').then((module) => ({ default: module.Post })));
const Activities = lazy(() => import('../pages/user/Activities').then((module) => ({ default: module.Activities })));
const Messages = lazy(() => import('../pages/user/Messages').then((module) => ({ default: module.Messages })));
const Profile = lazy(() => import('../pages/user/Profile').then((module) => ({ default: module.Profile })));
const Credit = lazy(() => import('../pages/user/Credit').then((module) => ({ default: module.Credit })));
const DemoPaymentCheckout = lazy(() => import('../pages/user/DemoPaymentCheckout').then((module) => ({ default: module.DemoPaymentCheckout })));
const ManualPaymentCheckout = lazy(() => import('../pages/user/ManualPaymentCheckout').then((module) => ({ default: module.ManualPaymentCheckout })));
const ReportResponse = lazy(() => import('../pages/user/ReportResponse').then((module) => ({ default: module.ReportResponse })));
const AdminLayout = lazy(() => import('../pages/admin/AdminLayout').then((module) => ({ default: module.AdminLayout })));
const AdminAnomalies = lazy(() => import('../pages/admin/AdminAnomalies').then((module) => ({ default: module.AdminAnomalies })));
const AdminComplaints = lazy(() => import('../pages/admin/AdminComplaints').then((module) => ({ default: module.AdminComplaints })));
const AdminTransactions = lazy(() => import('../pages/admin/AdminTransactions').then((module) => ({ default: module.AdminTransactions })));
const AdminTopUpOrders = lazy(() => import('../pages/admin/AdminTopUpOrders').then((module) => ({ default: module.AdminTopUpOrders })));
const AdminReputation = lazy(() => import('../pages/admin/AdminReputation').then((module) => ({ default: module.AdminReputation })));
const AdminAccounts = lazy(() => import('../pages/admin/AdminAccounts').then((module) => ({ default: module.AdminAccounts })));
const AdminModerationList = lazy(() => import('../pages/admin/AdminModerationList').then((module) => ({ default: module.AdminModerationList })));
const AdminModerationDetail = lazy(() => import('../pages/admin/AdminModerationDetail').then((module) => ({ default: module.AdminModerationDetail })));
const AdminFinanceUsers = lazy(() => import('../pages/admin/AdminFinanceUsers').then((module) => ({ default: module.AdminFinanceUsers })));
const AdminFinanceUserDetail = lazy(() => import('../pages/admin/AdminFinanceUsers').then((module) => ({ default: module.AdminFinanceUserDetail })));
const NotFound = lazy(() => import('../pages/NotFound').then((module) => ({ default: module.NotFound })));
const AdminDashboard = lazy(() => import('../pages/admin/AdminFinancePages').then((module) => ({ default: module.AdminOperationsDashboard })));
const AdminFinance = lazy(() => import('../pages/admin/AdminFinancePages').then((module) => ({ default: module.AdminFinanceReport })));
const AdminAuditLogs = lazy(() => import('../pages/admin/AdminPages').then((module) => ({ default: module.AdminAuditLogs })));
const AdminSettings = lazy(() => import('../pages/admin/AdminPages').then((module) => ({ default: module.AdminSettings })));
const AdminContentPage = lazy(() => import('../pages/admin/AdminPages').then((module) => ({ default: module.AdminContentPage })));

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
          { path: '/about', element: <About /> },
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
              // Tạm ẩn Bài quá hạn và Giao dịch: backend chưa có API cho Admin, đưa về trang tổng quan.
              { path: 'expired-posts', element: <Navigate to="/admin" replace /> },
              { path: 'categories', element: <AdminContentPage kind="categories" /> },
              { path: 'keywords', element: <AdminContentPage kind="keywords" /> },
              { path: 'districts', element: <AdminContentPage kind="districts" /> },
              { path: 'users', element: <AdminAccounts /> },
              { path: 'reputation', element: <AdminReputation /> },
              // Gộp "Khóa tài khoản" vào Quản lý tài khoản: giữ đường dẫn cũ để link đã lưu vẫn dùng được.
              { path: 'locked-users', element: <Navigate to="/admin/users" replace /> },
              { path: 'transactions', element: <AdminTransactions /> },
              { path: 'transactions/:transactionId', element: <Navigate to="/admin/transactions" replace /> },
              { path: 'complaints', element: <AdminComplaints /> },
              { path: 'disputes', element: <AdminComplaints /> },
              { path: 'alerts', element: <AdminAnomalies /> },
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
