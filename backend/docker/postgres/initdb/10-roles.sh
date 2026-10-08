#!/bin/sh
# Runs once, when the postgres container initialises an empty data volume.
# Creates the owner and application roles from docker/postgres/sql/roles.sql.
set -eu

: "${IKIMINA_DB_OWNER_PASSWORD:?IKIMINA_DB_OWNER_PASSWORD must be set}"
: "${IKIMINA_DB_APP_PASSWORD:?IKIMINA_DB_APP_PASSWORD must be set}"

psql -v ON_ERROR_STOP=1 \
     --username "$POSTGRES_USER" \
     --dbname "$POSTGRES_DB" \
     -v owner_password="$IKIMINA_DB_OWNER_PASSWORD" \
     -v app_password="$IKIMINA_DB_APP_PASSWORD" \
     -f /opt/ikimina/sql/roles.sql
