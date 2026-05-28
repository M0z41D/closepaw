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

ensure_remote_checkout() {
    if ssh "$REMOTE" "test -d $REMOTE_DIR/.git"; then
        return
    fi
    echo "sync: remote checkout not found at ${REMOTE}:${REMOTE_DIR}" >&2
    echo "sync: set CLOSEPAW_REMOTE_DIR in .closepaw-local.env; see .closepaw-local.env.example" >&2
    exit 1
}

ensure_remote_checkout

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
