import { http } from './http';
import type { ApiError } from './http';

export type AuthenticityAction = 'grant' | 'reject' | 'revoke';

export interface SubmissionLock {
  current: boolean;
}

export function validateAuthenticityReason(
  action: AuthenticityAction,
  reason: string,
): string | null {
  if (action !== 'grant' && !reason.trim()) return 'Lý do là bắt buộc.';
  return null;
}

export function beginAuthenticitySubmission(lock: SubmissionLock, confirmed: boolean): boolean {
  if (!confirmed || lock.current) return false;
  lock.current = true;
  return true;
}

export function authenticityErrorMessage(error: unknown): string {
  const status = (error as Partial<ApiError> | null)?.status;
  switch (status) {
    case 400:
      return 'Dữ liệu hoặc lý do không hợp lệ.';
    case 401:
      return 'Bạn chưa đăng nhập hoặc phiên đăng nhập đã hết hạn.';
    case 403:
      return 'Tài khoản hiện tại không có quyền Admin.';
    case 404:
      return 'Không tìm thấy bài đăng.';
    case 409:
      return 'Trạng thái bài đăng không cho phép thực hiện thao tác này.';
    case 500:
      return 'Hệ thống đang gặp lỗi. Vui lòng thử lại.';
    default:
      return 'Không thể kết nối tới máy chủ. Vui lòng thử lại.';
  }
}

export const adminAuthenticityApi = {
  async grant(itemId: number): Promise<void> {
    await http.post<void>(`/api/v1/admin/authenticity/${itemId}/grant`);
  },
  async reject(itemId: number, reason: string): Promise<void> {
    await http.post<void>(`/api/v1/admin/authenticity/${itemId}/reject`, { reason });
  },
  async revoke(itemId: number, reason: string): Promise<void> {
    await http.post<void>(`/api/v1/admin/authenticity/${itemId}/revoke`, { reason });
  },
};
