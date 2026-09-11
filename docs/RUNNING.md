# Running Ikimina

## Prerequisites

* JDK 21 (the build declares Java 21; Maven itself comes from `./mvnw`)
* PostgreSQL 18, or Docker for the compose stack
* Node 22 for the frontend

## Configuration

Nothing secret lives in a committed file. The application refuses to start
without these:

| Variable | Purpose |
| --- | --- |
| `IKIMINA_DB_PASSWORD` | PostgreSQL password |
| `IKIMINA_JWT_SECRET` | HMAC signing key, at least 32 bytes (`openssl rand -hex 32`) |

Everything else has a sensible default — see `ikimina/.env.example` for the full
list.

> The JWT secret that used to sit in `application.properties` is compromised: it
> is in the git history, so anyone with repository access can forge admin
> tokens. Generate a new one and never reuse the old value.

## Quick start with Docker

```bash
cp .env.example .env          # fill in IKIMINA_DB_PASSWORD and IKIMINA_JWT_SECRET
docker compose up --build
```

The app is then on <http://localhost:8080>. The database is not published to the
host — it is reachable only on the compose network.

On first boot the super admin is created. If `IKIMINA_SUPERADMIN_PASSWORD` is
unset, a random password is generated and printed **once** at `WARN`. Capture it
from the logs and change it immediately.

## Running the backend directly

```bash
cd ikimina
export IKIMINA_DB_PASSWORD=... IKIMINA_JWT_SECRET=...
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
```

Profiles:

| Profile | Behaviour |
| --- | --- |
| `dev` | SQL logging, security debug, Swagger UI, all actuator endpoints |
| `prod` | `ddl-auto=validate`, WARN logging, no Swagger, JSON logs, forwarded-header handling, graceful shutdown |

Neither is active by default, which gives you the middle-ground settings in
`application.properties`. Always set one explicitly in a real deployment.

## Running the frontend

```bash
cd frontend-ikimina
npm ci
npm run dev      # http://localhost:3000, proxies /api to localhost:8082
```

The Vite dev proxy forwards `/api` through unchanged. It must **not** rewrite the
prefix — every controller is mapped under `/api`, so stripping it returns 404 for
every request.

## Tests

```bash
cd ikimina && ./mvnw verify      # unit tests + coverage report
cd frontend-ikimina && npm run lint && npm run build
```

Coverage lands in `ikimina/target/site/jacoco/index.html`.

## Operations

| Endpoint | Access |
| --- | --- |
| `/actuator/health` | Unauthenticated (up/down only; details need a token) |
| `/actuator/health/liveness`, `/readiness` | Unauthenticated — used by the container healthcheck |
| `/actuator/info` | Unauthenticated |
| `/actuator/metrics`, `/actuator/prometheus` | `ROLE_SUPER_ADMIN` only |

Every request carries an `X-Request-Id` (generated if the client does not send
one) and it appears on every log line, so one member's request can be followed
end to end. In `prod` logs are one JSON object per line, with `password`,
`token`, `secret` and `authorization` fields masked.

## Known gaps

* Password reset is not implemented — a forgotten group-admin password currently
  needs a database change.
* `xlsx@0.18.5` is the newest version published to npm and carries unpatched
  prototype-pollution and ReDoS advisories. SheetJS ships fixes only from their
  own CDN. Either move the export feature to a maintained library or pin and
  accept the risk deliberately.
* Mobile money is not integrated. `PaymentTransaction` has a `MOBILE_MONEY`
  enum value and nothing behind it; money still moves by cash and is typed in.