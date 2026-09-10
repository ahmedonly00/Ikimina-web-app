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
          }}
        />
      </BrowserRouter>
    </Provider>
  </React.StrictMode>
);
