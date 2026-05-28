#!/usr/bin/env bash
# Sync local code to remote desktop: push, pull, rebuild, check proxy tunnel.
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
LOCAL_ENV="${CLOSEPAW_LOCAL_ENV:-$PROJECT_ROOT/.closepaw-local.env}"
if [[ -f "$LOCAL_ENV" ]]; then
    # shellcheck source=/dev/null
    source "$LOCAL_ENV"
fi

REMOTE="${CLOSEPAW_REMOTE:-desktop}"
REMOTE_DIR="${CLOSEPAW_REMOTE_DIR:-~/closepaw}"

echo "==> git push"
git push

echo "==> Remote: git pull + assembleDebug"
ssh "$REMOTE" "cd $REMOTE_DIR && git pull && ./gradlew assembleDebug"

echo "==> Remote: proxy tunnel status"
ssh "$REMOTE" "cd $REMOTE_DIR && ./scripts/remote/proxy_tunnel.sh status" || {
    echo "Tunnel not running. Starting..."
    ssh "$REMOTE" "cd $REMOTE_DIR && ./scripts/remote/proxy_tunnel.sh start"
}

echo "==> Sync complete"
