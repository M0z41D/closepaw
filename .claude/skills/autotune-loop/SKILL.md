---
name: autotune-loop
description: Autonomous multi-round controller for `/autotune`. Reads and updates `loop_state.json`, decides continue or stop, and records completion state when the loop is finished.
---

# AutoTune Loop

Thin controller around `/autotune`.

Responsibilities:
- `autotune-loop`: autotune-specific loop policy
- `autotune`: one round worker

Do not scrape prose or infer loop state from filesystem heuristics. `projects/autotune/meta/loop_state.json` is the only control-plane file.

## Principles

- Keep loop policy simple. One loop iteration should do at most one orchestrated `/autotune` round.
- Use `.claude/skills/autotune/references/tuning_principles.md` for stop/continue judgment. Do not keep pushing eval-only patches.
- Use `projects/autotune/meta/scoreboard.json` for retry and "failed 3+ times" decisions.

## Loop

For each loop iteration:

1. Read `projects/autotune/meta/loop_state.json`.
2. If `status == "complete"`, report completion and exit.
3. Ensure the active loop run is marked `mode: "auto"` and `status: "running"` before delegating the round.
4. Invoke exactly one orchestrated `/autotune` round.
   - **Verify sync**: After `/autotune` Step 1 commits changes, confirm the remote has the latest code and APK rebuilt (`git push` + remote `git pull && ./gradlew assembleDebug`) BEFORE the eval runs. `/autotune` Step 3 owns this, but if running `--remote`, double-check the remote is up to date. Running eval on stale code wastes the entire round.
5. Re-read `projects/autotune/meta/loop_state.json`.
6. If `last_round.recommended_action` is `stop_success` or `stop_exhausted`, set `status` to `complete`, persist `stop_reason`, report completion, and exit.
7. Otherwise exit normally so the next loop iteration can continue.

## Stop Criteria

Set `last_round.recommended_action` to:

- `continue`: more targeted work is justified
- `stop_success`: all high/medium-priority items are fixed or consciously parked
- `stop_exhausted`: no actionable fixes remain, or the same targeted improvement failed 3+ times

When stopping as exhausted:
- Add genuinely out-of-reach tasks to `eval/config/cannot_handle_group.txt`.
- Update `projects/autotune/meta/issues.md` with the parked rationale.
- Base the "failed 3+ times" call on `projects/autotune/meta/scoreboard.json`, not ad hoc memory.

## Commit Policy

- Code changes belong to Step 1 inside `/autotune`: `feat(agent): autotune round N — <summary>`
- End-of-round artifacts belong after analysis: `loop_state.json`, scoreboard, changelog, per-task updates, `issues.md`, and `cannot_handle_group.txt`
- Artifact files under `projects/autotune/` are ignored by this app repo; persist them only in the configured private artifact workspace.

## Key Files

- Loop state: `projects/autotune/meta/loop_state.json`
- Scoreboard: `projects/autotune/meta/scoreboard.json`
- Changelog: `projects/autotune/meta/changelog.md`
- Issues: `projects/autotune/meta/issues.md`
- Exclusions: `eval/config/cannot_handle_group.txt`
- Round worker: `.claude/skills/autotune/SKILL.md`
- Shared tuning principles: `.claude/skills/autotune/references/tuning_principles.md`
