import type { ApiError } from './http';

// Câu cố định cho các lỗi đăng ký hay gặp, để màn hình không phụ thuộc vào câu chữ backend trả về.
const knownAuthMessages: Record<string, string> = {
  AUTH_EMAIL_DOMAIN_NOT_ALLOWED:
    'Không thể đăng ký bằng email dùng một lần. Vui lòng dùng email cá nhân của bạn.',
  AUTH_TOO_MANY_ACCOUNTS_FROM_IP:
    'Mạng của bạn đã tạo quá nhiều tài khoản trong 24 giờ qua. Vui lòng thử lại sau.',
  AUTH_PHONE_ALREADY_USED: 'Số điện thoại đã được sử dụng. Vui lòng dùng số khác.',
};

export function authErrorMessage(error: ApiError): string {
  if (knownAuthMessages[error.code]) return knownAuthMessages[error.code];
  if (error.code === 'AUTH_OTP_INVALID' && typeof error.details?.remainingAttempts === 'number') {
    return `${error.message} Còn ${error.details.remainingAttempts} lần thử.`;
  }
  if (error.code === 'AUTH_OTP_RESEND_TOO_SOON' && typeof error.details?.retryAfterSeconds === 'number') {
    return `${error.message} Thử lại sau ${error.details.retryAfterSeconds} giây.`;
  }
  return error.message || 'Không thể xử lý yêu cầu. Vui lòng thử lại.';
}
