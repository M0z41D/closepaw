#!/usr/bin/env bash
# Manage the OpenAI proxy SSH tunnel on the remote eval worker.
#
# Usage:
#   ./scripts/remote/proxy_tunnel.sh install   # install systemd user service
#   ./scripts/remote/proxy_tunnel.sh start     # start tunnel
#   ./scripts/remote/proxy_tunnel.sh stop      # stop tunnel
#   ./scripts/remote/proxy_tunnel.sh status    # check status
#   ./scripts/remote/proxy_tunnel.sh logs      # show recent logs
#   ./scripts/remote/proxy_tunnel.sh manual [proxy-host]  # run in foreground (no systemd)
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
SERVICE_NAME="openai-proxy-tunnel"
SERVICE_FILE="${SCRIPT_DIR}/${SERVICE_NAME}.service"
USER_SERVICE_DIR="${HOME}/.config/systemd/user"
SERVICE_ENV_DIR="${HOME}/.config/closepaw"
SERVICE_ENV_FILE="${SERVICE_ENV_DIR}/proxy-tunnel.env"

LOCAL_ENV="${CLOSEPAW_LOCAL_ENV:-$PROJECT_ROOT/.closepaw-local.env}"
if [[ -f "$LOCAL_ENV" ]]; then
  # shellcheck source=/dev/null
  source "$LOCAL_ENV"
fi
if [[ -f "$SERVICE_ENV_FILE" ]]; then
  # shellcheck source=/dev/null
  source "$SERVICE_ENV_FILE"
fi

PROXY_HOST="${CLOSEPAW_PROXY_HOST:-${PROXY_HOST:-}}"
PROXY_USER="${CLOSEPAW_PROXY_USER:-${PROXY_USER:-$USER}}"
PROXY_PORT="${CLOSEPAW_PROXY_PORT:-${PROXY_PORT:-18080}}"

write_service_env() {
  if [[ -z "$PROXY_HOST" ]]; then
    echo "[tunnel] proxy host required. Set CLOSEPAW_PROXY_HOST in .closepaw-local.env first."
    echo "[tunnel] See .closepaw-local.env.example."
    exit 1
  fi
  mkdir -p "$SERVICE_ENV_DIR"
  umask 077
  cat >"$SERVICE_ENV_FILE" <<EOF
PROXY_HOST=${PROXY_HOST}
PROXY_USER=${PROXY_USER}
PROXY_PORT=${PROXY_PORT}
EOF
}

usage() {
  sed -n '3,9s/^# //p' "$0"
  exit 1
}

cmd_install() {
  if ! command -v autossh >/dev/null 2>&1; then
    echo "[tunnel] autossh not found. Install: sudo apt-get install -y autossh"
    exit 1
  fi
  mkdir -p "${USER_SERVICE_DIR}"
  write_service_env
  cp "${SERVICE_FILE}" "${USER_SERVICE_DIR}/${SERVICE_NAME}.service"
  systemctl --user daemon-reload
  systemctl --user enable "${SERVICE_NAME}"
  echo "[tunnel] Service installed and enabled."
  echo "[tunnel] Config written to ${SERVICE_ENV_FILE}."
  echo "[tunnel] Start with: $0 start"
}

cmd_start() {
  systemctl --user start "${SERVICE_NAME}"
  echo "[tunnel] Started. Check: $0 status"
}

cmd_stop() {
  systemctl --user stop "${SERVICE_NAME}"
  echo "[tunnel] Stopped."
}

cmd_status() {
  systemctl --user status "${SERVICE_NAME}" --no-pager || true
  echo ""
  echo "[tunnel] Health check:"
  if curl -sS --max-time 3 "http://127.0.0.1:${PROXY_PORT}/" 2>/dev/null; then
    echo ""
    echo "[tunnel] Proxy reachable."
  else
    echo "[tunnel] Proxy NOT reachable on port ${PROXY_PORT}."
  fi
}

cmd_logs() {
  journalctl --user -u "${SERVICE_NAME}" --no-pager -n 50
}

cmd_manual() {
  local host="${1:-${PROXY_HOST}}"
  if [[ -z "$host" ]]; then
    echo "[tunnel] proxy host required. Pass it as an argument or set CLOSEPAW_PROXY_HOST."
    exit 1
  fi
  echo "[tunnel] Forwarding localhost:${PROXY_PORT} -> ${host}:${PROXY_PORT}"
  echo "[tunnel] Press Ctrl-C to stop."
  exec autossh -M 0 -N \
    -o "ServerAliveInterval 30" \
    -o "ServerAliveCountMax 3" \
    -o "ExitOnForwardFailure yes" \
    -L "${PROXY_PORT}:127.0.0.1:${PROXY_PORT}" "${PROXY_USER}@${host}"
}

case "${1:-}" in
  install) cmd_install ;;
  start)   cmd_start ;;
  stop)    cmd_stop ;;
  status)  cmd_status ;;
  logs)    cmd_logs ;;
  manual)  cmd_manual "${2:-}" ;;
  *)       usage ;;
esac
