# Android Agent Development Scripts

## Workflow

```
After code changes:
    ./scripts/setup.sh              # Build, install, setup permissions

Run tests:
    ./scripts/dev.sh run            # Run agent test
    ./scripts/dev.sh logs           # View logs
```

## Scripts

### `setup.sh` - Build & Deploy

Run this after code changes to deploy a new version.

```bash
./scripts/setup.sh
```

What it does:
- Build APK
- Install APK (replacement install with `-r`, preserves data)
- Grant Overlay permission
- Enable Accessibility service
- Launch app

### `dev.sh` - Run & Test

Run agent tests and view logs. Assumes app is already installed via `setup.sh`.

```bash
./scripts/dev.sh run                # Run with default goal ("Open Settings")
./scripts/dev.sh run "Open Chrome"  # Run with custom goal
./scripts/dev.sh logs               # View all agent logs
./scripts/dev.sh logs orch          # Orchestration logs only
./scripts/dev.sh logs llm           # LLM call logs only
./scripts/dev.sh status             # Check device status
```

Log filter options:
| filter | content |
|--------|---------|
| (default) | All agent-related logs |
| `orch` | Orchestration logs |
| `llm` | LLM/API call logs |
| `session` | Session lifecycle logs |
| `action` | Action execution logs |
| `all` | Unfiltered all logs |

## Configuration

### API Key

Create `.env` file in project root:

```
OPENAI_API_KEY=sk-proj-your-key-here
```

### Orchestration Mode

In `AgentService.kt` in the `runAgent()` function:

```kotlin
// New Orchestration (MobileV3)
SessionConfig(useNewOrchestration = true, ...)

// Legacy Orchestration
SessionConfig(useNewOrchestration = false, ...)
```

## Troubleshooting

### "App not installed"

Run `./scripts/setup.sh` to build and install.

### "Accessibility service not enabled"

Run `./scripts/setup.sh` - it automatically enables accessibility.
If it fails, enable manually:
1. Settings > Accessibility > Downloaded apps
2. Find "Android Agent"
3. Toggle ON

### "No device detected"

1. Check USB connection
2. Ensure USB debugging is enabled
3. Run `adb devices` to verify
4. Try `adb kill-server && adb start-server`

### Agent not responding

1. Run `./scripts/dev.sh status` to check status
2. Run `./scripts/setup.sh` to reinstall and reconfigure
3. View logs: `./scripts/dev.sh logs`
