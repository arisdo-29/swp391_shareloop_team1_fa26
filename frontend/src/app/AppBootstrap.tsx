import { useEffect, useRef, useState } from 'react';
import { RouterProvider } from 'react-router-dom';
import { router } from './router';
import { preloadRoute } from './routePreload';
import { store } from './store';
import { tokenStorage } from '../services/http';
import { restoreSession } from '../services/session';

export function AppBootstrap() {
  const [sessionReady, setSessionReady] = useState(() => !tokenStorage.get());
  const restorationRef = useRef<Promise<void> | null>(null);

  useEffect(() => {
    if (sessionReady) return;

    let active = true;
    void preloadRoute(window.location.pathname).catch(() => undefined);
    restorationRef.current ??= restoreSession(
      store.dispatch,
      store.getState().data.currentUserId,
    );
    void restorationRef.current.finally(() => {
      if (active) setSessionReady(true);
    });
    return () => {
      active = false;
    };
  }, [sessionReady]);

  if (!sessionReady) return null;
  return <RouterProvider router={router} />;
}
