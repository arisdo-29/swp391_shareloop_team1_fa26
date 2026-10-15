import { http } from './http';

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

export const userApi = {
  async getMe() {
    const { data } = await http.get<MeResponse>('/api/v1/me');
    return data;
  },
  async updateMe(input: UpdateProfileRequest) {
    const { data } = await http.put<MeResponse>('/api/v1/me', input);
    return data;
  },
};
