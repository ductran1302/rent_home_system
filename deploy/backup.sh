#!/usr/bin/env bash
# Sao luu database ra backups/ va giu 14 ban gan nhat.
# Su dung: bash deploy/backup.sh
set -euo pipefail

cd "$(dirname "$0")/.."

DC=(docker compose -f docker-compose.yml -f deploy/docker-compose.prod.yml)

mkdir -p backups
FILE="backups/ruinhome-$(date +%F_%H%M).sql.gz"

"${DC[@]}" exec -T postgres pg_dump -U ruinhome ruinhome | gzip > "$FILE"
echo "Da luu $FILE ($(du -h "$FILE" | cut -f1))"

ls -1t backups/*.sql.gz 2>/dev/null | tail -n +15 | xargs -r rm --
