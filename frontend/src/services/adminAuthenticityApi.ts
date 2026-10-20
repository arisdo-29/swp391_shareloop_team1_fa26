import { http } from './http';

export type AuthenticityAction = 'grant' | 'reject' | 'revoke';

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
