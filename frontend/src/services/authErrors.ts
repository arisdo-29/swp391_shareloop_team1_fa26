import type { ApiError } from './http';

// Thông báo theo mã lỗi trong docs/api/auth.md. FE chỉ dựa vào code, không dựa vào message.
const MESSAGES: Record<string, string> = {
  AUTH_INVALID_CREDENTIALS: 'Email hoặc mật khẩu chưa đúng.',
  AUTH_EMAIL_NOT_VERIFIED: 'Tài khoản chưa xác thực email. Vui lòng nhập mã OTP đã gửi.',
  AUTH_ACCOUNT_LOCKED: 'Tài khoản đã bị khoá, vui lòng liên hệ quản trị viên.',
  AUTH_EMAIL_ALREADY_REGISTERED: 'Email đã được sử dụng.',
  AUTH_PHONE_ALREADY_USED: 'Số điện thoại đã được dùng cho tài khoản khác.',
  AUTH_PHONE_INVALID: 'Số điện thoại không hợp lệ.',
  AUTH_OTP_INVALID: 'Mã OTP không đúng.',
  AUTH_OTP_EXPIRED: 'Mã OTP đã hết hạn, vui lòng bấm "Gửi lại mã".',
  AUTH_OTP_TOO_MANY_ATTEMPTS: 'Bạn đã nhập sai quá nhiều lần, vui lòng gửi lại mã mới.',
  AUTH_OTP_RESEND_TOO_SOON: 'Vui lòng chờ một chút trước khi gửi lại mã.',
  VALIDATION_FAILED: 'Thông tin chưa hợp lệ, vui lòng kiểm tra lại.',
  NETWORK_ERROR: 'Không kết nối được máy chủ, vui lòng thử lại.',
};

export function authErrorMessage(error: ApiError): string {
  if (error.code === 'AUTH_OTP_INVALID' && typeof error.details?.remainingAttempts === 'number') {
    return `Mã OTP không đúng, còn ${error.details.remainingAttempts} lần thử.`;
  }
  if (error.code === 'AUTH_OTP_RESEND_TOO_SOON' && typeof error.details?.retryAfterSeconds === 'number') {
    return `Vui lòng chờ ${error.details.retryAfterSeconds} giây trước khi gửi lại mã.`;
  }
  return MESSAGES[error.code] ?? error.message;
}
