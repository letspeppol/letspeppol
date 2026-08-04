#!/bin/sh
set -eu

mkdir -p "$DATA_DIR"

touch "$DATA_DIR/.sync-db-dumps.trigger"

cat > /etc/crontabs/root <<'EOF'
SHELL=/bin/sh
TZ=Europe/Brussels

0 0,12 * * * /bin/sh /usr/local/bin/backup-run.sh
EOF

echo "[backup] scheduled for 00:00 and 12:00 Europe/Brussels"
exec crond -f -l 2
