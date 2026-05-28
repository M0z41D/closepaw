# Eval Runner Reference

Commands for each run configuration. Read this file when executing Step 3 of `/autotune`.

## Flags

| Flag | Effect |
|------|--------|
| `--remote` | Run eval on the configured remote worker instead of local machine |
| `--parallel N` | Use N emulators in parallel (currently max 2). Falls back to serial if parallel startup fails |

## Default (local, single emulator)

```bash
eval/.venv/bin/python eval/aw_bridge/runner.py --tasks-file eval/config/autotune_round_N.txt
```

## `--parallel N` (local)

```bash
./scripts/eval_parallel.sh eval/config/autotune_round_N.txt
```

Parallel preconditions:
- `AndroidWorldAvd` baseline-prepared on `emulator-5554` / gRPC `8554`
- `AndroidWorldAvd2` baseline-prepared on `emulator-5556` / gRPC `8556`
- One-time prep: `./scripts/prepare_parallel_baselines.sh`
- `eval_parallel.sh` does not create AVDs; they must already exist
- If only one emulator window appears, treat as startup failure — fallback to serial
- If either device is unavailable, fallback to serial automatically

## `--remote` (single emulator on desktop)

Before running, sync code to the remote:

```bash
# 1. Push local changes
git push

# 2. Pull and rebuild on remote
if [[ -f .closepaw-local.env ]]; then source .closepaw-local.env; fi
: "${CLOSEPAW_REMOTE:?Set CLOSEPAW_REMOTE in .closepaw-local.env}"
: "${CLOSEPAW_REMOTE_DIR:?Set CLOSEPAW_REMOTE_DIR in .closepaw-local.env}"
REMOTE="$CLOSEPAW_REMOTE"
REMOTE_DIR="$CLOSEPAW_REMOTE_DIR"
ssh "$REMOTE" "cd $REMOTE_DIR && git pull && ./gradlew assembleDebug"
```

All remote commands below assume they run in that same shell after the snippet above.

A stale checkout is a silent failure mode — eval runs but produces wrong results.

For `gpt-*` models, ensure the SSH proxy tunnel is up (OpenRouter models don't need it):

```bash
ssh "$REMOTE" "cd $REMOTE_DIR && ./scripts/remote/proxy_tunnel.sh status"
# If not running:
ssh "$REMOTE" "cd $REMOTE_DIR && ./scripts/remote/proxy_tunnel.sh start"
```

Run eval:

```bash
ssh "$REMOTE" "cd $REMOTE_DIR && eval/.venv/bin/python eval/aw_bridge/runner.py \
  --config eval/config/remote.yaml \
  --tasks-file eval/config/autotune_round_N.txt"
```

For long runs, use tmux to survive SSH disconnects:

```bash
ssh "$REMOTE" "cd $REMOTE_DIR && ./scripts/remote/eval_tmux.sh \
  --tasks-file eval/config/autotune_round_N.txt"
# Reattach: ssh "$REMOTE" 'tmux attach -t eval'
```

## `--remote --parallel N`

Sync code first (same as `--remote` above), then:

```bash
ssh "$REMOTE" "cd $REMOTE_DIR && ./scripts/eval_parallel.sh \
  --headless --config eval/config/remote.yaml \
  --tasks-file eval/config/autotune_round_N.txt"
```

Same parallel preconditions as local, plus `--headless` adds `-no-window -no-audio`. If parallel startup fails, fallback to single-emulator serial run.

## Pulling Results After Remote Runs

After any `--remote` run completes (or incrementally as tasks finish), pull results to local:

```bash
rsync -avz "$REMOTE:$REMOTE_DIR/eval/results/" eval/results/
```

All Step 4 analysis reads from local `eval/results/`. Skipping this means analysis fails or uses stale data.

## Troubleshooting

| Problem | Fix |
|---------|-----|
| Tunnel dropped | `ssh "$REMOTE" "cd $REMOTE_DIR && ./scripts/remote/proxy_tunnel.sh start"` |
| Emulator hung | `adb -s emulator-5554 emu kill`, then re-run |
| Stale checkout | `ssh "$REMOTE" "cd $REMOTE_DIR && git pull && ./gradlew assembleDebug"` |
| Second emulator won't start | Check `emulator -list-avds` shows `AndroidWorldAvd2` |
