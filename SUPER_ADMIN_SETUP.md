# Super Admin and Group Admin Setup

## Configuration

All secrets come from the environment. See `ikimina/.env.example` for the full
list. Nothing secret belongs in `application.properties` — that file is committed.

Required before the app will start:

| Variable | Purpose |
| --- | --- |
| `IKIMINA_DB_PASSWORD` | PostgreSQL password |
| `IKIMINA_JWT_SECRET` | HMAC signing key, **at least 32 bytes** (`openssl rand -hex 32`) |

Optional:

| Variable | Default | Purpose |
| --- | --- | --- |
| `IKIMINA_SUPERADMIN_EMAIL` | `superadmin@ikimina.com` | Bootstrap super admin email |
| `IKIMINA_SUPERADMIN_PASSWORD` | *(generated)* | Bootstrap super admin password |
| `IKIMINA_CORS_ORIGINS` | `http://localhost:3000,http://localhost:3001` | Allowed browser origins |

> **The JWT secret that was previously committed to `application.properties` is
> compromised.** Anyone with repository access — including the full git history —
> can forge admin tokens with it. Generate a new one and never reuse the old value.

## Super Admin Bootstrap

`DataInitializer` creates the super admin on first boot, if no user with the
configured email exists.

- If `IKIMINA_SUPERADMIN_PASSWORD` is set, that password is used and is **not** logged.
- If it is unset, a cryptographically random password is generated and printed
  **once** to the application log at `WARN`. Sign in and change it immediately.

There is no hardcoded default password. Earlier versions of this document
published one; that account must be considered compromised on any environment
where it was ever created, and its password rotated.

## Group Admin Creation

Only a super admin can create a savings group (`POST /api/savings-groups`).
Creating a group also provisions its `ROLE_GROUP_ADMIN` account, which is why the
endpoint is privilege-granting and restricted.

The request must supply the admin's details:

```json
POST /api/savings-groups
{
  "name": "Ikimina Cyimana",
  "description": "Weekly savings group",
  "adminUserEmail": "admin@example.com",
  "adminUserFirstName": "Jean",
  "adminUserLastName": "Uwimana",
  "adminUserPhoneNumber": "+250788123456"
}
```

The response returns the generated one-time password:

```json
{
  "groupId": 7,
  "name": "Ikimina Cyimana",
  "description": "Weekly savings group",
  "adminEmail": "admin@example.com",
  "adminTemporaryPassword": "k3Jq8vRz2mPw5nTx"
}
```

The password is **never written to the log** and cannot be retrieved again — only
its bcrypt hash is stored. The Groups page surfaces it once after creation; the
super admin must pass it to the group administrator out of band.

## Roles and Authorization

Role constants are `ROLE_SUPER_ADMIN`, `ROLE_GROUP_ADMIN`, `ROLE_USER`.

In `@PreAuthorize`, `hasRole('X')` prepends `ROLE_` itself — so write
`hasRole('SUPER_ADMIN')`, **not** `hasRole('ROLE_SUPER_ADMIN')` (that resolves to
`ROLE_ROLE_SUPER_ADMIN` and can never match).

| Scope | Enforcement |
| --- | --- |
| `/api/super-admin/**` | `hasRole('SUPER_ADMIN')`, at the filter chain and class level |
| Group administration | `hasAnyRole('SUPER_ADMIN','GROUP_ADMIN')` |
| A member's own records | `@userSecurity.hasAccessToUser(authentication, #userId)` |
| Group-scoped reads | `@savingsGroupSecurity.isGroupMember` / `.canAdministerGroup` |

Endpoints carrying a `{userId}` or `{groupId}` are checked at the object level, not
just by role — a member cannot read another member's savings, loans, fines or
reports by changing the id in the URL.

### Public endpoints

Only these are reachable without a token:

- `POST /api/auth/login`
- `POST /api/auth/register`
- `GET  /api/savings-groups/public` — id and name only, for the login group picker
- Swagger UI and `/v3/api-docs/**`

`GET /api/savings-groups` (full detail) is super-admin only.

## Login

Login requires email, password **and** the savings group, because a member can
belong to more than one group. The issued JWT carries the selected group, the
user id and the role, so downstream authorization does not have to guess which
group the caller is acting in.

## Password Management

Password reset is **not implemented**. A super admin currently has no in-app way
to reset a group admin's password; it must be done against the database. This is
a known gap.

## Database Schema

### `users`
`id`, `username`, `email` (unique, used for login), `password` (bcrypt),
`first_name`, `last_name`, `full_name`, `member_number`, `phone_number`,
`role`, `is_active`, `created_at`, `updated_at`

> Note: `email` is globally unique, which conflicts with the multi-group model
> that `existsByEmailAndSavingsGroupId` implies (the same person using one email
> in two groups). Resolving this is a schema decision still outstanding.

### `savings_groups`
`id`, `name` (unique), `description`, `admin_id`, `is_active`, `is_suspended`,
`suspension_reason`, `suspended_at`, `created_at`, `updated_at`

### `group_members`
Join table linking users to groups (many-to-many).

`User` and `SavingsGroup` compare and hash **by id only**. Including the
bidirectional association in `equals`/`hashCode` made the two entities recurse
into each other and overflow the stack on any `HashSet` insert.
