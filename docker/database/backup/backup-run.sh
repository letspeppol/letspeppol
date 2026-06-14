#!/bin/sh
set -eu

. /etc/backup.env

mkdir -p "$DATA_DIR"

status=0
ts=$(date "+%Y-%m-%dT%H-%M-%S%z")

for d in $DB_LIST; do
  if ls "$DATA_DIR"/"$d"-*.dump.tmp >/dev/null 2>&1; then
    echo "[backup] refusing $d: stale .dump.tmp exists"
    status=1
    continue
  fi

  if ls "$DATA_DIR"/"$d"-*.dump >/dev/null 2>&1; then
    echo "[backup] refusing $d: unsynced .dump exists"
    status=1
    continue
  fi

  tmp="$DATA_DIR/$d-$ts.dump.tmp"
  final="$DATA_DIR/$d-$ts.dump"

  echo "[backup] dumping $d to temp file $tmp"

  if pg_dump \
    -h "$PGHOST" \
    -U "$PGUSER" \
    -d "$d" \
    -F c \
    -Z 6 \
    -f "$tmp"
  then
    mv "$tmp" "$final"
    echo "[backup] completed $d -> $final"
  else
    echo "[backup] FAILED $d, leaving $tmp in place"
    status=1
  fi
done

touch "$DATA_DIR/.sync-db-dumps.trigger"
exit "$status"
