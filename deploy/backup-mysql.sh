#!/usr/bin/env bash
set -Eeuo pipefail
umask 077
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
DOCKER=(docker)
if ! docker info >/dev/null 2>&1; then DOCKER=(sudo docker); fi
COMPOSE=("${DOCKER[@]}" compose -f "$ROOT/backend/docker-compose.aws.yml" --project-directory "$ROOT/backend")
directory="${BACKUP_DIR:-$ROOT/backups}"
mkdir -p "$directory"
target="$directory/veterinaria-$(date -u +%Y%m%dT%H%M%SZ).sql.gz"
temporary="$target.partial"
trap 'rm -f -- "$temporary"' EXIT
"${COMPOSE[@]}" exec -T mysql sh -c 'MYSQL_PWD="$MYSQL_PASSWORD" exec mysqldump --single-transaction --no-tablespaces --set-gtid-purged=OFF -u"$MYSQL_USER" "$MYSQL_DATABASE"' | gzip > "$temporary"
gzip -t "$temporary"
mv -- "$temporary" "$target"
printf 'Respaldo creado: %s\n' "$target"
