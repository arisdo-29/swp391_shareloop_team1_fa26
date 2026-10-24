import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';
import { Provider } from 'react-redux';
import { actions, store } from './app/store';
import { AppBootstrap } from './app/AppBootstrap';
import { setUnauthorizedHandler } from './services/http';
import './styles/globals.css';

setUnauthorizedHandler(() => store.dispatch(actions.logout()));

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <Provider store={store}>
      <AppBootstrap />
    </Provider>
  </StrictMode>,
);
