import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';
import { Provider } from 'react-redux';
import { actions, store } from './app/store';
import { AppBootstrap } from './app/AppBootstrap';
import { router } from './app/router';
import { setUnauthorizedHandler } from './services/http';
import './styles/globals.css';

setUnauthorizedHandler(({ message }) => {
  store.dispatch(actions.logout());
  if (router.state.location.pathname === '/login') return;
  void router.navigate('/login', {
    replace: true,
    state: { sessionError: message },
  });
});

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <Provider store={store}>
      <AppBootstrap />
    </Provider>
  </StrictMode>,
);
