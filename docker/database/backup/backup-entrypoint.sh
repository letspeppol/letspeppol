#!/bin/sh
set -eu

mkdir -p "$DATA_DIR"

cat > /etc/crontabs/root <<'EOF'
SHELL=/bin/sh
TZ=Europe/Brussels

0 0,12 * * * /bin/sh -eu -c 'ts=$(date "+%Y-%m-%dT%H-%M-%S%z"); for d in $DB_LIST; do echo "[backup] preparing $d at $ts"; ls -1t "$DATA_DIR"/$d-*.dump 2>/dev/null | tail -n +2 | xargs -r rm -f --; tmp="$DATA_DIR/.$d-$ts.dump.tmp"; final="$DATA_DIR/$d-$ts.dump"; echo "[backup] dumping $d to temp file $tmp"; if pg_dump -h "$PGHOST" -U "$PGUSER" -d "$d" -F c -Z 6 -f "$tmp"; then mv "$tmp" "$final"; echo "[backup] completed $d -> $final"; else echo "[backup] FAILED $d"; rm -f "$tmp"; exit 1; fi; done'
EOF

echo "[backup] scheduled for 00:00 and 12:00 Europe/Brussels"
exec crond -f -l 2
