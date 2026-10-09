# End-to-end smoke tests

Playwright drives the real app on a mobile viewport against a running stack.

```sh
# 1. Backend + database, dev profile (fake SMS writes codes to the log)
cd backend && cp .env.example .env    # fill in the secrets
docker compose -p ikimina-e2e up -d --build --wait

# 2. Frontend, built and served with /api proxied to the backend
cd ../frontend && npm run build && npx vite preview &

# 3. Tests (reads one-time codes from `docker compose logs backend`)
npx playwright install chromium
E2E_COMPOSE_PROJECT=ikimina-e2e E2E_COMPOSE_FILE=../backend/compose.yaml \
  E2E_COMPOSE_ENV_FILE=../backend/.env npm run test:e2e
```

CI runs the same steps in the `frontend-e2e` job.
