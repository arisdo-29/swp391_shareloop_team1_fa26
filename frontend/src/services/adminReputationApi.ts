import { http } from './http';

export interface GraceStarResponse {
  userId: number;
  trustStars: number;
  graceStarUsed: boolean;
}

export const adminReputationApi = {
  async grantGraceStar(userId: number, reason: string) {
    const { data } = await http.post<GraceStarResponse>(
      `/api/v1/admin/users/${userId}/grace-star`,
      { reason },
    );
    return data;
  },
};
