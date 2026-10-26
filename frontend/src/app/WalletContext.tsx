import { useCallback, useEffect, useMemo, useRef, useState, type ReactNode } from 'react';
import { useAppSelector } from './hooks';
import { selectCurrentUser } from './store';
import { toBackendUserId } from '../services/requestApi';
import { walletApi, type WalletResponse } from '../services/walletApi';
import type { ApiError } from '../services/http';
import { WalletContext, type WalletContextValue } from './useWallet';

interface WalletState {
  userId: number | null;
  wallet: WalletResponse | null;
  loading: boolean;
  error: string;
}

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
  const [reloadKey, setReloadKey] = useState(0);
  const refresh = useCallback(() => {
    requestRef.current = null;
    setReloadKey((key) => key + 1);
  }, []);
  const replaceWallet = useCallback((wallet: WalletResponse) => {
    if (!backendUserId) return;
    requestRef.current = null;
    setState({ userId: backendUserId, wallet, loading: false, error: '' });
  }, [backendUserId]);

  useEffect(() => {
    if (!backendUserId) {
      requestRef.current = null;
      setState({ userId: null, wallet: null, loading: false, error: '' });
      return;
    }

    let active = true;
    setState({ userId: backendUserId, wallet: null, loading: true, error: '' });
    if (requestRef.current?.userId !== backendUserId) {
      requestRef.current = { userId: backendUserId, promise: walletApi.get() };
    }
    requestRef.current.promise
      .then((wallet) => {
        if (active) setState({ userId: backendUserId, wallet, loading: false, error: '' });
      })
      .catch((caught) => {
        if (!active) return;
        const apiError = caught as ApiError;
        setState({
          userId: backendUserId,
          wallet: null,
          loading: false,
          error: apiError.message || 'Không tải được số dư Credit. Vui lòng thử lại.',
        });
      });
    return () => {
      active = false;
    };
  }, [backendUserId, reloadKey]);

  const value = useMemo<WalletContextValue>(() => {
    const matchesCurrentUser = backendUserId !== null && state.userId === backendUserId;
    return {
      wallet: matchesCurrentUser ? state.wallet : null,
      loading: backendUserId !== null && (!matchesCurrentUser || state.loading),
      error: matchesCurrentUser ? state.error : '',
      refresh,
      replaceWallet,
    };
  }, [backendUserId, refresh, replaceWallet, state]);

  return <WalletContext.Provider value={value}>{children}</WalletContext.Provider>;
}
