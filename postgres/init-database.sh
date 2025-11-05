#!/bin/sh
set -e

echo "[init] Starting database initialization"
echo "[init] Target role: $DB_USER"

# Ensure role exists (create or update password)
ROLE_EXISTS=$(psql -U "$POSTGRES_USER" -tAc "SELECT 1 FROM pg_roles WHERE rolname='${DB_USER}'" || true)
if [ "$ROLE_EXISTS" = "1" ]; then
  echo "[init] Role $DB_USER already exists -> updating password"
  psql -v ON_ERROR_STOP=1 -U "$POSTGRES_USER" -c "ALTER ROLE \"${DB_USER}\" WITH LOGIN PASSWORD '${DB_PASS}';"
else
  echo "[init] Creating role $DB_USER"
  psql -v ON_ERROR_STOP=1 -U "$POSTGRES_USER" -c "CREATE ROLE \"${DB_USER}\" LOGIN PASSWORD '${DB_PASS}';"
fi

# Ensure databases exist and have correct owner
for db in app kyc proxy; do
  DB_EXISTS=$(psql -U "$POSTGRES_USER" -tAc "SELECT 1 FROM pg_database WHERE datname='${db}'" || true)
  if [ "$DB_EXISTS" = "1" ]; then
    echo "[init] Database $db already exists -> ensuring owner $DB_USER"
    psql -v ON_ERROR_STOP=1 -U "$POSTGRES_USER" -c "ALTER DATABASE \"${db}\" OWNER TO \"${DB_USER}\";" || true
  else
    echo "[init] Creating database $db owned by $DB_USER"
    psql -v ON_ERROR_STOP=1 -U "$POSTGRES_USER" -c "CREATE DATABASE \"${db}\" OWNER \"${DB_USER}\";"
  fi
done

echo "[init] Initialization complete."
