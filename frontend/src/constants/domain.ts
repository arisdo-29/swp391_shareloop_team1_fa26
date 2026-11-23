export const ROUTES = {
  home: '/',
  browse: '/browse',
  ai: '/ai',
  login: '/login',
  register: '/register',
  post: '/post',
  activities: '/activities',
  messages: '/messages',
  profile: '/profile',
  credit: '/credit',
  admin: '/admin',
} as const;

export const CATEGORIES = [
  'Đồ điện tử',
  'Nội thất',
  'Sách',
  'Thời trang',
  'Đồ gia dụng',
  'Phụ kiện',
  'Nước hoa',
  'Đồ học tập',
] as const;

export const DISTRICTS = [
  'Quận 1',
  'Quận 3',
  'Quận 4',
  'Quận 5',
  'Quận 6',
  'Quận 7',
  'Quận 8',
  'Quận 10',
  'Quận 11',
  'Quận 12',
  'Bình Thạnh',
  'Gò Vấp',
  'Phú Nhuận',
  'Tân Bình',
  'Tân Phú',
  'Bình Tân',
  'TP Thủ Đức',
] as const;

export const CONDITIONS = ['NEW', 'LIKE_NEW', 'GOOD', 'DEFECTIVE'] as const;
export const ELECTRONIC_DEFECT_HINT =
  'Thiết bị không hoạt động bình thường cần mô tả lỗi và ít nhất một ảnh chụp chỗ lỗi.';

export const ADMIN_ROUTES = [
  '/admin/moderation',
  '/admin/categories',
  '/admin/keywords',
  '/admin/districts',
  '/admin/users',
  '/admin/reputation',
  '/admin/locked-users',
  '/admin/complaints',
  '/admin/alerts',
  '/admin/finance',
  '/admin/settings/fees',
  '/admin/settings/ranks',
  '/admin/audit-logs',
] as const;

/** Số ảnh thật bắt buộc cho mỗi bài đăng (giống kiểm tra của backend khi tạo và sửa bài). */
export const ITEM_IMAGES_MIN = 3;
export const ITEM_IMAGES_MAX = 8;

// Nhãn tiếng Việt cho cờ cảnh báo trên thẻ yêu cầu (backend trả mã, frontend chỉ hiển thị).
export const REQUEST_FLAG_LABELS: Record<string, string> = {
  OFFERED_ITEM_BUSY: 'Món người này mang đổi đang nằm trong một giao dịch khác',
  RECEIVER_TOO_BUSY: 'Người này đang có quá nhiều giao dịch chưa xong',
  ITEM_UNDER_REVIEW: 'Bài đang bị xem xét vi phạm',
};
