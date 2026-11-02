import { http } from './http';
import type { ItemResponse, PageResponse } from './itemApi';

// Theo docs/api/user.md. Id người dùng lấy từ token, không gửi kèm.
export interface MeResponse {
  id: number;
  email: string;
  fullName: string;
  phone: string;
  isAdmin: boolean;
  areaId: number | null;
  avatarMediaId: number | null;
  avatarUrl: string | null; // đường dẫn tương đối, ghép bằng resolveMediaUrl
}

// Gửi đủ cả 3 trường; null nghĩa là bỏ khu vực / bỏ ảnh. Email và số điện thoại không đổi ở đây.
export interface UpdateProfileRequest {
  fullName: string;
  areaId: number | null;
  avatarMediaId: number | null;
}

export interface SendChangePhoneOtpRequest {
  newPhone: string;
}

export interface ChangePhoneOtpResponse {
  email: string;
  otpExpiresInSeconds: number;
  resendCooldownSeconds: number;
}

export interface ChangePhoneRequest {
  newPhone: string;
  otp: string;
}

// Theo docs/api/user.md, GET /api/v1/users/{id}/profile. Không có email, số điện thoại, Credit, điểm thưởng.
export interface PublicProfileResponse {
  id: number;
  fullName: string;
  avatarUrl: string | null;
  avatarInitials: string;
  district: string | null;
  rank: 'BRONZE' | 'SILVER' | 'GOLD' | 'DIAMOND';
  trustStars: number;
  // Thang 0–1; null khi dưới 3 giao dịch đã được đánh giá.
  asDescribedRate: number | null;
  completedTransactions: number;
  joinedAt: string;
}

// Theo docs/api/item.md mục 5: ACTIVE là bài đang hiển thị, DONE là bài đã giao dịch thành công.
export type UserItemTab = 'ACTIVE' | 'DONE';

export const userApi = {
  async getPublicProfile(id: number) {
    const { data } = await http.get<PublicProfileResponse>(`/api/v1/users/${id}/profile`);
    return data;
  },
  async getUserItems(id: number, tab: UserItemTab, page: number, size: number) {
    const { data } = await http.get<PageResponse<ItemResponse>>(`/api/v1/users/${id}/items`, {
      params: { tab, page, size },
    });
    return data;
  },
  async getMe() {
    const { data } = await http.get<MeResponse>('/api/v1/me');
    return data;
  },
  async updateMe(input: UpdateProfileRequest) {
    const { data } = await http.put<MeResponse>('/api/v1/me', input);
    return data;
  },
  async sendChangePhoneOtp(input: SendChangePhoneOtpRequest) {
    const { data } = await http.post<ChangePhoneOtpResponse>('/api/v1/me/phone/otp', input);
    return data;
  },
  async changePhone(input: ChangePhoneRequest) {
    const { data } = await http.put<MeResponse>('/api/v1/me/phone', input);
    return data;
  },
};
