import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';
import { Provider } from 'react-redux';
import { RouterProvider } from 'react-router-dom';
import { actions, store } from './app/store';
import { router } from './app/router';
import { restoreSession } from './services/session';
import { setUnauthorizedHandler } from './services/http';
import './styles/globals.css';

setUnauthorizedHandler(() => store.dispatch(actions.logout()));
await restoreSession(store.dispatch, store.getState().data.currentUserId);

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <Provider store={store}>
      <RouterProvider router={router} />
    </Provider>
  </StrictMode>,
);
