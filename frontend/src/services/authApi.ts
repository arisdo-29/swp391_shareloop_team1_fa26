export const authApiEndpoints = {
  register: '/api/auth/register',
  sendEmailOtp: '/api/auth/email/send-otp',
  verifyEmailOtp: '/api/auth/email/verify-otp',
  login: '/api/auth/login',
  resendOtp: '/api/auth/email/resend-otp',
  forgotPassword: '/api/auth/password/forgot',
  verifyResetOtp: '/api/auth/password/verify-otp',
  resetPassword: '/api/auth/password/reset',
  changePassword: '/api/auth/password/change',
  getProfile: '/api/account/profile',
  updateProfile: '/api/account/profile',
} as const;

export interface AuthApiClient {
  register(input: RegisterRequest): Promise<RegisterResponse>;
  sendEmailOtp(input: EmailOtpRequest): Promise<void>;
  verifyEmailOtp(input: VerifyOtpRequest): Promise<void>;
  login(input: LoginRequest): Promise<LoginResponse>;
  resendOtp(input: EmailOtpRequest): Promise<void>;
  forgotPassword(input: ForgotPasswordRequest): Promise<void>;
  verifyResetOtp(input: VerifyOtpRequest): Promise<VerifyResetOtpResponse>;
  resetPassword(input: ResetPasswordRequest): Promise<void>;
  changePassword(input: ChangePasswordRequest): Promise<void>;
  getProfile(): Promise<ProfileResponse>;
  updateProfile(input: UpdateProfileRequest): Promise<ProfileResponse>;
}

export interface RegisterRequest {
  name: string;
  email: string;
  phone: string;
  username: string;
  password: string;
}

export interface RegisterResponse {
  verificationId: string;
}

export interface EmailOtpRequest {
  email: string;
  verificationId?: string;
}

export interface VerifyOtpRequest {
  verificationId: string;
  otp: string;
}

export interface LoginRequest {
  usernameOrEmail: string;
  password: string;
}

export interface LoginResponse {
  accessToken: string;
  role: 'user' | 'admin';
}

export interface ForgotPasswordRequest {
  email: string;
}

export interface VerifyResetOtpResponse {
  resetToken: string;
}

export interface ResetPasswordRequest {
  resetToken: string;
  newPassword: string;
}

export interface ChangePasswordRequest {
  currentPassword: string;
  newPassword: string;
}

export interface ProfileResponse {
  id: string;
  name: string;
  username: string;
  email: string;
  phone: string;
  avatarUrl?: string;
  credit: number;
  rank?: string;
  reputationStars?: number;
}

export interface UpdateProfileRequest {
  name: string;
  email: string;
  phone: string;
  avatarUrl?: string;
}
