import type { ApiError } from './http';

export function authErrorMessage(error: ApiError): string {
  if (error.code === 'AUTH_OTP_INVALID' && typeof error.details?.remainingAttempts === 'number') {
    return `${error.message} Còn ${error.details.remainingAttempts} lần thử.`;
  }
  if (error.code === 'AUTH_OTP_RESEND_TOO_SOON' && typeof error.details?.retryAfterSeconds === 'number') {
    return `${error.message} Thử lại sau ${error.details.retryAfterSeconds} giây.`;
  }
  return error.message || 'Không thể xử lý yêu cầu. Vui lòng thử lại.';
}
