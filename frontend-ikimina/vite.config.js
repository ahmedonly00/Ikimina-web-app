import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';
import { VitePWA } from 'vite-plugin-pwa';
import path from 'path';

// https://vitejs.dev/config/
export default defineConfig({
  plugins: [
    react(),
    VitePWA({
      // Members meet in person, often on 2G and often on a shared handset. An
      // installable, offline-capable app is not a nicety here: it is the
      // difference between recording a meeting and losing it.
      registerType: 'prompt',
      includeAssets: ['favicon.ico', 'icon.svg'],
      manifest: {
        name: 'Ikimina Savings',
        short_name: 'Ikimina',
        description: 'Savings group management for ibimina',
        theme_color: '#4338ca',
        background_color: '#ffffff',
        display: 'standalone',
        orientation: 'portrait',
        start_url: '/',
        lang: 'rw',
        // TODO(design): replace with PNG rasters at 192 and 512, plus a
        // maskable variant. This SVG is a working placeholder so the manifest
        // is valid and the app is installable.
        icons: [
          { src: 'icon.svg', sizes: 'any', type: 'image/svg+xml' },
          { src: 'icon.svg', sizes: 'any', type: 'image/svg+xml', purpose: 'maskable' },
        ],
      },
      workbox: {
        // Precache the shell so a cold start works with no network at all.
        globPatterns: ['**/*.{js,css,html,ico,png,svg,woff2}'],
        // The bundle is code-split; the largest vendor chunk is ~283 kB.
        maximumFileSizeToCacheInBytes: 3 * 1024 * 1024,
        navigateFallback: '/index.html',
        // Never serve an API response from cache. Financial data must be
        // either current or visibly absent - a stale balance shown as current
        // is worse than no balance.
        navigateFallbackDenylist: [/^\/api\//],
        runtimeCaching: [
          {
            // The one safe exception: the pre-auth group list is static-ish and
            // needed to render the login screen offline.
            urlPattern: /\/api\/savings-groups\/public$/,
            handler: 'NetworkFirst',
            options: {
              cacheName: 'public-groups',
              networkTimeoutSeconds: 5,
              expiration: { maxEntries: 1, maxAgeSeconds: 60 * 60 * 24 },
            },
          },
        ],
      },
      devOptions: {
        // Keep the service worker out of the way during development, where a
        // cached shell makes changes look like they did not apply.
        enabled: false,
      },
    }),
  ],
  resolve: {
    alias: {
      '@': path.resolve(__dirname, './src'),
    },
  },
  build: {
    // The whole app used to ship as one ~1.07 MB chunk. On 2G/3G that is the
    // difference between a usable login screen and a blank page, so vendor
    // libraries are split out and route components are lazy-loaded.
    sourcemap: false,
    chunkSizeWarningLimit: 600,
    rollupOptions: {
      output: {
        manualChunks: {
          'react-vendor': ['react', 'react-dom', 'react-router-dom'],
          'state-vendor': ['@reduxjs/toolkit', 'react-redux'],
          'xlsx-vendor': ['xlsx'],
          'ui-vendor': ['framer-motion', '@headlessui/react'],
        },
      },
    },
  },
  server: {
    port: 3000,
    proxy: {
      // The backend maps every controller under /api, so the prefix must be
      // forwarded as-is. Do NOT add a `rewrite` that strips /api.
      '/api': {
        target: 'http://localhost:8082',
        changeOrigin: true,
        secure: false,
      },
    },
  },
});
