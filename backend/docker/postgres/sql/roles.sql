-- Database roles for Ikimina (spec 5.3). Run once per database, as a superuser, with psql:
--
--   psql -v ON_ERROR_STOP=1 -v owner_password=... -v app_password=... -f roles.sql
--
-- Used by docker-compose (initdb/10-roles.sh) and by the Testcontainers test
-- support, so local, CI and tests all get exactly the same role setup.
--
--   ikimina_owner  owns the "ikimina" schema and every object in it; runs Flyway.
--   ikimina_app    what the application connects as. Not a superuser, owns nothing,
--                  cannot create objects, does not bypass row-level security.
--                  Migrations grant it privileges table by table.

\set ON_ERROR_STOP on

-- Roles are cluster-wide; the schema setup below is per database. Creating the roles
-- only when absent lets the same script prepare several databases in one cluster
-- (the integration tests give each test class its own database).
SELECT format('CREATE ROLE ikimina_owner LOGIN PASSWORD %L NOSUPERUSER NOCREATEDB NOCREATEROLE NOREPLICATION NOBYPASSRLS',
              :'owner_password')
WHERE NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'ikimina_owner') \gexec

SELECT format('CREATE ROLE ikimina_app LOGIN PASSWORD %L NOSUPERUSER NOCREATEDB NOCREATEROLE NOREPLICATION NOBYPASSRLS',
              :'app_password')
WHERE NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'ikimina_app') \gexec

CREATE SCHEMA ikimina AUTHORIZATION ikimina_owner;

GRANT USAGE ON SCHEMA ikimina TO ikimina_app;

-- Identity columns draw from sequences the owner creates; the app needs to use them.
ALTER DEFAULT PRIVILEGES FOR ROLE ikimina_owner IN SCHEMA ikimina
    GRANT USAGE, SELECT ON SEQUENCES TO ikimina_app;

-- Nobody but the owner creates objects, in either schema.
REVOKE CREATE ON SCHEMA public FROM PUBLIC;

ALTER ROLE ikimina_owner SET search_path = ikimina;
ALTER ROLE ikimina_app SET search_path = ikimina;
