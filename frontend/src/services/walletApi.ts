import { http } from './http';

export interface WalletResponse {
  creditBalance: number;
  heldCredit: number;
  available: number;
  hasToppedUp: boolean;
}

export const walletApi = {
  async get(currentUserId: number) {
    const { data } = await http.get<WalletResponse>('/api/v1/wallet', {
      params: { currentUserId },
    });
    return data;
  },
};
