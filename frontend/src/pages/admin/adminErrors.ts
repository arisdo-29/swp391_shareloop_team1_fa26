import type { ApiError } from '../../services/http';

/** Thông báo lỗi chung cho các trang admin: 403 và 404 có câu riêng, còn lại dùng message từ Backend. */
export function adminErrorMessage(error: unknown, fallback = 'Không tải được dữ liệu. Vui lòng thử lại.') {
  const apiError = error as Partial<ApiError>;
  if (apiError.status === 403) return 'Bạn không có quyền quản trị để thực hiện thao tác này.';
  if (apiError.status === 404) return 'Không tìm thấy dữ liệu yêu cầu.';
  return apiError.message || fallback;
}
