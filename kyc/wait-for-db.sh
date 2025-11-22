#!/bin/sh
set -e

echo "Waiting for Postgres at $DB_HOST:$DB_PORT..."

# Loop until Postgres responds
#until pg_isready -h $DB_HOST -p $DB_PORT -U $DB_USER >/dev/null 2>&1; do
until pg_isready -h $DB_HOST -p $DB_PORT -U $DB_USER; do
  echo "Postgres is unavailable - retrying in 2s..."
  sleep 2
done

echo "Postgres is available!"
exec "$@"
