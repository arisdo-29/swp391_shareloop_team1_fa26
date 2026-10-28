import { createContext, useContext } from 'react';
import type { WalletResponse } from '../services/walletApi';

export interface WalletContextValue {
  wallet: WalletResponse | null;
  loading: boolean;
  error: string;
  refresh: () => void;
  replaceWallet: (wallet: WalletResponse) => void;
}

export const WalletContext = createContext<WalletContextValue | null>(null);

export function useWallet() {
  const context = useContext(WalletContext);
  if (!context) throw new Error('useWallet must be used within WalletProvider.');
  return context;
}
