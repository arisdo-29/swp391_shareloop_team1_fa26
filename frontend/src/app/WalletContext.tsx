import { createContext, useContext, useEffect, useMemo, useRef, useState, type ReactNode } from 'react';
import { useAppSelector } from './hooks';
import { selectCurrentUser } from './store';
import { toBackendUserId } from '../services/requestApi';
import { walletApi, type WalletResponse } from '../services/walletApi';

interface WalletContextValue {
  isApiAccount: boolean;
  wallet: WalletResponse | null;
  loading: boolean;
  error: string;
}

interface WalletState {
  userId: number | null;
  wallet: WalletResponse | null;
  loading: boolean;
  error: string;
}

const WalletContext = createContext<WalletContextValue | null>(null);

export function WalletProvider({ children }: { children: ReactNode }) {
  const user = useAppSelector(selectCurrentUser);
  const backendUserId = user?.role === 'user' ? toBackendUserId(user.id) : null;
  const requestRef = useRef<{ userId: number; promise: Promise<WalletResponse> } | null>(null);
  const [state, setState] = useState<WalletState>({
    userId: null,
    wallet: null,
    loading: false,
    error: '',
  });

  useEffect(() => {
    if (!backendUserId) {
      requestRef.current = null;
      setState({ userId: null, wallet: null, loading: false, error: '' });
      return;
    }

    let active = true;
    setState({ userId: backendUserId, wallet: null, loading: true, error: '' });
    if (requestRef.current?.userId !== backendUserId) {
      requestRef.current = { userId: backendUserId, promise: walletApi.get(backendUserId) };
    }
    requestRef.current.promise
      .then((wallet) => {
        if (active) setState({ userId: backendUserId, wallet, loading: false, error: '' });
      })
      .catch(() => {
        if (!active) return;
        setState({
          userId: backendUserId,
          wallet: null,
          loading: false,
          error: 'Không tải được số dư Credit. Vui lòng thử lại.',
        });
      });
    return () => {
      active = false;
    };
  }, [backendUserId]);

  const value = useMemo<WalletContextValue>(() => {
    const matchesCurrentUser = backendUserId !== null && state.userId === backendUserId;
    return {
      isApiAccount: backendUserId !== null,
      wallet: matchesCurrentUser ? state.wallet : null,
      loading: backendUserId !== null && (!matchesCurrentUser || state.loading),
      error: matchesCurrentUser ? state.error : '',
    };
  }, [backendUserId, state]);

  return <WalletContext.Provider value={value}>{children}</WalletContext.Provider>;
}

export function useWallet() {
  const context = useContext(WalletContext);
  if (!context) throw new Error('useWallet must be used within WalletProvider.');
  return context;
}
