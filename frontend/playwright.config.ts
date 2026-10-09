import { defineConfig, devices } from '@playwright/test';

/**
 * End-to-end smoke tests against a running stack (backend + database via backend/compose.yaml,
 * frontend via `vite preview`). They read one-time codes from the backend's log, which only
 * the dev profile with the fake SMS provider writes. See e2e/README.md.
 */
export default defineConfig({
  testDir: './e2e',
  timeout: 60_000,
  retries: process.env.CI ? 1 : 0,
  reporter: process.env.CI ? [['list'], ['html', { open: 'never' }]] : 'list',
  use: {
    baseURL: process.env.E2E_BASE_URL ?? 'http://localhost:4173',
    trace: 'retain-on-failure',
    screenshot: 'only-on-failure',
  },
  projects: [
    {
      // Spec 18.1: mobile-first; most users are on Android phones.
      name: 'mobile',
      use: { ...devices['Pixel 7'] },
    },
  ],
});
