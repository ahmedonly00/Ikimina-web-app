import React from 'react';
import { createRoot } from 'react-dom/client';
import { BrowserRouter } from 'react-router-dom';
import { Provider } from 'react-redux';
import { Toaster } from 'react-hot-toast';
// Imported for its side effect: initialises i18next before the first render,
// so no component ever renders a translation key instead of a string.
import './i18n';
import { store } from './app/store';
import App from './App';
import './index.css';

const container = document.getElementById('root');
if (!container) throw new Error('Failed to find the root element');
const root = createRoot(container);

// Provider, BrowserRouter and the toast host are mounted here exactly once.
// App.jsx must not re-mount them or the store/toast queue gets duplicated.
root.render(
  <React.StrictMode>
    <Provider store={store}>
      <BrowserRouter>
        <App />
        <Toaster
          position='top-right'
          toastOptions={{
            duration: 5000,
            error: { duration: 6000 },
            /*
             * react-hot-toast hardcodes a white background inline, so in dark
             * mode the toast came out white-on-white. Pointing it at the theme
             * tokens makes it follow whichever theme is active.
             */
            style: {
              background: 'rgb(var(--c-surface))',
              color: 'rgb(var(--c-fg))',
              border: '1px solid rgb(var(--c-border))',
            },
          }}
        />
      </BrowserRouter>
    </Provider>
  </React.StrictMode>
);
