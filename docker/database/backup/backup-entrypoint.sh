#!/bin/sh
set -eu

shell_quote() {
  printf "'%s'" "$(printf '%s' "$1" | sed "s/'/'\\\\''/g")"
}

mkdir -p "$DATA_DIR"
{
  printf 'export PGHOST=%s\n' "$(shell_quote "$PGHOST")"
  printf 'export PGUSER=%s\n' "$(shell_quote "$PGUSER")"
  printf 'export PGPASSWORD=%s\n' "$(shell_quote "$PGPASSWORD")"
  printf 'export DB_LIST=%s\n' "$(shell_quote "$DB_LIST")"
  printf 'export DATA_DIR=%s\n' "$(shell_quote "$DATA_DIR")"
  printf 'export PATH=%s\n' "$(shell_quote "/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin")"
  printf 'export TZ=%s\n' "$(shell_quote "$TZ")"
} > /etc/backup.env

touch "$DATA_DIR/.sync-db-dumps.trigger"

cat > /etc/crontabs/root <<'EOF'
SHELL=/bin/sh
TZ=Europe/Brussels

0 0,12 * * * /bin/sh /usr/local/bin/backup-run.sh
EOF

echo "[backup] scheduled for 00:00 and 12:00 Europe/Brussels"
exec crond -f -l 2
