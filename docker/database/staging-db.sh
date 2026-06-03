#!/bin/sh
set -eu

SCRIPT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
ENV_FILE="$SCRIPT_DIR/.env"
BACKUP_ROOT="/opt/backup"
BACKUP_NAME="current"
COMPOSE_FILE="$SCRIPT_DIR/docker-compose.yml"
DB_LIST="app kyc proxy"

usage() {
  cat <<EOF
Usage:
  staging-db.sh backup  [--env-file FILE] [--backup-dir DIR] [--name NAME]
  staging-db.sh restore [--env-file FILE] [--backup-dir DIR] [--name NAME]
  staging-db.sh list    [--backup-dir DIR]

Examples:
  ./staging-db.sh backup --env-file .env --name pr-123
  ./staging-db.sh restore --env-file .env --name pr-123

Notes:
  - Stop application containers before restore so they do not hold DB connections.
  - Backups are written as PostgreSQL custom-format dumps: app.dump, kyc.dump, proxy.dump.
EOF
}

fail() {
  echo "[staging-db] ERROR: $*" >&2
  exit 1
}

log() {
  echo "[staging-db] $*"
}

require_command() {
  command -v "$1" >/dev/null 2>&1 || fail "Missing required command: $1"
}

resolve_path() {
  path=$1
  base_dir=$2

  case "$path" in
    /*) printf '%s\n' "$path" ;;
    *)
      if [ -e "$path" ]; then
        printf '%s/%s\n' "$(pwd)" "$path"
      else
        printf '%s/%s\n' "$base_dir" "$path"
      fi
      ;;
  esac
}

load_env() {
  ENV_FILE=$(resolve_path "$ENV_FILE" "$SCRIPT_DIR")

  [ -f "$ENV_FILE" ] || fail "Environment file not found: $ENV_FILE"

  set -a
  # shellcheck disable=SC1090
  . "$ENV_FILE"
  set +a

  : "${POSTGRES_USER:?POSTGRES_USER is required in $ENV_FILE}"
  : "${POSTGRES_PASSWORD:?POSTGRES_PASSWORD is required in $ENV_FILE}"
  : "${APP_USER:?APP_USER is required in $ENV_FILE}"
  : "${KYC_USER:?KYC_USER is required in $ENV_FILE}"
  : "${PROXY_USER:?PROXY_USER is required in $ENV_FILE}"
}

compose() {
  docker compose --env-file "$ENV_FILE" -f "$COMPOSE_FILE" "$@"
}

db_owner() {
  case "$1" in
    app) printf '%s\n' "$APP_USER" ;;
    kyc) printf '%s\n' "$KYC_USER" ;;
    proxy) printf '%s\n' "$PROXY_USER" ;;
    *) fail "No owner configured for database: $1" ;;
  esac
}

quote_sql_literal() {
  printf "%s" "$1" | sed "s/'/''/g"
}

backup_path() {
  printf '%s/%s\n' "$BACKUP_ROOT" "$BACKUP_NAME"
}

write_manifest() {
  target_dir=$1

  {
    echo "name=$BACKUP_NAME"
    echo "created_at=$(date -u '+%Y-%m-%dT%H:%M:%SZ')"
    echo "databases=$DB_LIST"
  } > "$target_dir/manifest.txt"
}

backup_database() {
  db=$1
  target_dir=$2
  tmp_file="$target_dir/$db.dump.tmp"
  dump_file="$target_dir/$db.dump"

  log "Dumping $db"
  rm -f "$tmp_file"

  compose exec -T \
    -e PGPASSWORD="$POSTGRES_PASSWORD" \
    db pg_dump \
      -U "$POSTGRES_USER" \
      -d "$db" \
      -F c \
      -Z 6 > "$tmp_file"

  mv "$tmp_file" "$dump_file"
}

restore_database() {
  db=$1
  source_dir=$2
  owner=$(db_owner "$db")
  dump_file="$source_dir/$db.dump"
  escaped_db=$(quote_sql_literal "$db")

  [ -f "$dump_file" ] || fail "Missing dump file: $dump_file"

  log "Restoring $db"

  compose cp "$dump_file" "db:/tmp/$db.dump"

  compose exec -T \
    -e PGPASSWORD="$POSTGRES_PASSWORD" \
    db psql \
      -v ON_ERROR_STOP=1 \
      -U "$POSTGRES_USER" \
      -d postgres \
      -c "ALTER DATABASE \"$db\" WITH ALLOW_CONNECTIONS false;" \
      -c "REVOKE CONNECT ON DATABASE \"$db\" FROM PUBLIC;" \
      -c "SELECT pg_terminate_backend(pid) FROM pg_stat_activity WHERE datname = '$escaped_db' AND pid <> pg_backend_pid();"

  compose exec -T \
    -e PGPASSWORD="$POSTGRES_PASSWORD" \
    db dropdb \
      -U "$POSTGRES_USER" \
      --if-exists \
      "$db"

  compose exec -T \
    -e PGPASSWORD="$POSTGRES_PASSWORD" \
    db createdb \
      -U "$POSTGRES_USER" \
      -O "$owner" \
      "$db"

  compose exec -T \
    -e PGPASSWORD="$POSTGRES_PASSWORD" \
    db pg_restore \
      -U "$POSTGRES_USER" \
      -d "$db" \
      --no-owner \
      --role="$owner" \
      "/tmp/$db.dump"

  compose exec -T db rm -f "/tmp/$db.dump"
}

parse_common_args() {
  while [ "$#" -gt 0 ]; do
    case "$1" in
      --env-file)
        [ "$#" -ge 2 ] || fail "--env-file requires a value"
        ENV_FILE=$2
        shift 2
        ;;
      --backup-dir)
        [ "$#" -ge 2 ] || fail "--backup-dir requires a value"
        BACKUP_ROOT=$2
        shift 2
        ;;
      --name)
        [ "$#" -ge 2 ] || fail "--name requires a value"
        BACKUP_NAME=$2
        shift 2
        ;;
      -h|--help)
        usage
        exit 0
        ;;
      *)
        fail "Unknown argument: $1"
        ;;
    esac
  done
}

do_backup() {
  target_dir=$(backup_path)

  if [ -e "$target_dir" ]; then
    fail "Backup already exists: $target_dir"
  fi

  mkdir -p "$target_dir"
  trap 'rm -rf "$target_dir"' INT TERM HUP

  for db in $DB_LIST; do
    backup_database "$db" "$target_dir"
  done

  write_manifest "$target_dir"
  log "Backup complete: $target_dir"
}

do_restore() {
  source_dir=$(backup_path)

  [ -d "$source_dir" ] || fail "Backup directory not found: $source_dir"

  for db in $DB_LIST; do
    restore_database "$db" "$source_dir"
  done

  log "Restore complete: $source_dir"
}

do_list() {
  [ -d "$BACKUP_ROOT" ] || {
    log "No backup directory found: $BACKUP_ROOT"
    exit 0
  }

  find "$BACKUP_ROOT" -mindepth 1 -maxdepth 1 -type d -print | sort
}

main() {
  [ "$#" -gt 0 ] || {
    usage
    exit 1
  }

  command=$1
  shift

  require_command docker

  case "$command" in
    backup)
      parse_common_args "$@"
      load_env
      do_backup
      ;;
    restore)
      parse_common_args "$@"
      load_env
      do_restore
      ;;
    list)
      parse_common_args "$@"
      do_list
      ;;
    -h|--help)
      usage
      ;;
    *)
      fail "Unknown command: $command"
      ;;
  esac
}

main "$@"
