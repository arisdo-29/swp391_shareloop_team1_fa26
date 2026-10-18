import { http, tokenStorage } from './http';

// Theo docs/api/auth.md
export interface RegisterRequest { email: string; password: string; fullName: string; phone: string }
export interface VerifyOtpRequest { email: string; otp: string }
export interface ResendOtpRequest { email: string }
export interface LoginRequest { email: string; password: string }
export interface ForgotPasswordRequest { email: string }
export interface OtpSentResponse { email: string; otpExpiresInSeconds: number; resendCooldownSeconds: number }
export interface AuthUserResponse { id: number; email: string; fullName: string; isAdmin: boolean }
export interface AuthResponse { accessToken: string; tokenType: string; expiresIn: number; user: AuthUserResponse }
export interface ChangePasswordRequest { currentPassword: string; newPassword: string }
export interface ResetPasswordRequest { email: string; otp: string; newPassword: string }

export const authApi = {
  async register(input: RegisterRequest) {
    const { data } = await http.post<OtpSentResponse>('/api/v1/auth/register', input);
    return data;
  },
  async verifyOtp(input: VerifyOtpRequest) {
    const { data } = await http.post<AuthResponse>('/api/v1/auth/verify-otp', input);
    tokenStorage.set(data.accessToken, data.tokenType, data.expiresIn);
    tokenStorage.setUserId(data.user.id);
    return data;
  },
  async resendOtp(input: ResendOtpRequest) {
    const { data } = await http.post<OtpSentResponse>('/api/v1/auth/resend-otp', input);
    return data;
  },
  async login(input: LoginRequest) {
    const { data } = await http.post<AuthResponse>('/api/v1/auth/login', input);
    tokenStorage.set(data.accessToken, data.tokenType, data.expiresIn);
    tokenStorage.setUserId(data.user.id);
    return data;
  },
  // Cần token. Thành công trả 204; sai mật khẩu hiện tại là 400 AUTH_CURRENT_PASSWORD_INCORRECT (không phải 401).
  async changePassword(input: ChangePasswordRequest) {
    await http.post('/api/v1/auth/change-password', input);
  },
  // Luôn 200 cùng nội dung dù email có tài khoản hay không; dùng resendCooldownSeconds để đếm ngược "Gửi lại mã".
  async forgotPassword(input: ForgotPasswordRequest) {
    const { data } = await http.post<OtpSentResponse>('/api/v1/auth/forgot-password', input);
    return data;
  },
  // Không cần token, thành công trả 204 rồi chuyển sang đăng nhập. Không có bước xác nhận OTP riêng.
  async resetPassword(input: ResetPasswordRequest) {
    await http.post('/api/v1/auth/reset-password', input);
  },
  async logout() {
    tokenStorage.clear();
    try {
      await http.post('/api/v1/auth/logout');
    } catch {
      // JWT không có state ở server; đăng xuất phía client đã hoàn tất ở trên.
    }
  },
};
