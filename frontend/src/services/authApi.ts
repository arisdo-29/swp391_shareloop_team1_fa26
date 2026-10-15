import { http, tokenStorage } from './http';

// Theo docs/api/auth.md
export interface RegisterRequest { email: string; password: string; fullName: string; phone: string }
export interface OtpSentResponse { email: string; otpExpiresInSeconds: number; resendCooldownSeconds: number }
export interface AuthUserResponse { id: number; email: string; fullName: string; isAdmin: boolean }
export interface AuthResponse { accessToken: string; tokenType: 'Bearer'; expiresIn: number; user: AuthUserResponse }
export interface ChangePasswordRequest { currentPassword: string; newPassword: string }

export const authApi = {
  async register(input: RegisterRequest) {
    const { data } = await http.post<OtpSentResponse>('/api/v1/auth/register', input);
    return data;
  },
  async verifyOtp(email: string, otp: string) {
    const { data } = await http.post<AuthResponse>('/api/v1/auth/verify-otp', { email, otp });
    return data;
  },
  async resendOtp(email: string) {
    const { data } = await http.post<OtpSentResponse>('/api/v1/auth/resend-otp', { email });
    return data;
  },
  async login(email: string, password: string) {
    const { data } = await http.post<AuthResponse>('/api/v1/auth/login', { email, password });
    tokenStorage.set(data.accessToken);
    tokenStorage.setUserId(data.user.id);
    return data;
  },
  async me(userId: number) {
    const { data } = await http.get<AuthUserResponse>('/api/v1/me', {
      params: { userId },
    });
    return data;
  },
  // Cần token. Thành công trả 204; sai mật khẩu hiện tại là 400 AUTH_CURRENT_PASSWORD_INCORRECT (không phải 401).
  async changePassword(input: ChangePasswordRequest) {
    await http.post('/api/v1/auth/change-password', input);
  },
  async logout() {
    try {
      await http.post('/api/v1/auth/logout');
    } finally {
      tokenStorage.clear();
    }
  },
};
