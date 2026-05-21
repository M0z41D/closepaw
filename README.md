# ClosePaw

[![License: Apache 2.0](https://img.shields.io/badge/License-Apache_2.0-blue.svg)](LICENSE)
[![Min SDK](https://img.shields.io/badge/minSdk-31-green.svg)]()
[![Target SDK](https://img.shields.io/badge/targetSdk-36-green.svg)]()

ClosePaw is an AI-powered Android agent — give natural language instructions, ClosePaw operates your phone via the accessibility service.

<!--
  TODO(publish-readme-screenshots): drop in a hero demo GIF (a real task end-to-end)
  and the Smart Capsule overlay screenshots. Untracked candidates `start-capsule.png`
  and `start-capsule2.png` already exist in the working tree — commit them alongside
  the README update once the visual layout is finalized.
-->

## What it does

- Natural-language task execution on Android (e.g. "Open Settings and turn on Bluetooth")
- Screen perception via the Android accessibility service — no rooting, no screen scraping hacks
- Multiple LLM backends: OpenAI, OpenRouter, Novita
- **Smart Capsule** — a floating overlay that lets you launch and watch tasks without leaving your current app
- ReAct-style agent loop with optional `delegate_task` subagent delegation, todo + scratchpad context hygiene, and cross-session memory

## Quick Start

### Prerequisites

- Android device or emulator running **API 31+** (Android 12 or later)
- An **OpenAI**, **OpenRouter**, or **Novita** API key
- JDK 17 and the Android SDK if you're building from source

### Build & Install

```bash
git clone https://github.com/imoonkey/closepaw.git
cd closepaw
./gradlew assembleDebug
adb install app/build/outputs/apk/debug/app-debug.apk
```

### Setup

1. Open the app → **Settings** → enter your API key
2. **Settings → Accessibility** in Android system settings → enable the ClosePaw accessibility service
3. Grant the **Display over other apps** (overlay) permission when prompted
4. Type a task in the home screen (or summon the Smart Capsule from any app) and go

## Architecture

High-level layers:

- **Agent loop** — ReAct turn engine, optional `delegate_task` subagent delegation, todo + scratchpad state, cross-session memory
- **Tools** — accessibility actions, browser scripting, optional Termux shell bridge
- **Platforms** — `AccessibilityPlatform` for normal use, `VirtualDisplayPlatform` (Shizuku) for hybrid background sessions
- **LLM** — pluggable clients (OpenAI / OpenRouter / Novita), model catalog, retry infrastructure

Full design docs live under [`doc/main/`](doc/main/README.md). Start there for the agent loop, tool system, and protocol contracts.

> **Note on on-device inference:** the codebase still ships the `LFMLLMClient` (Liquid AI Leap SDK) path, but it is not exposed in the UI for now as on-device inference is too slow to be practically useful yet. See [`doc/main/infra/llm.md`](doc/main/infra/llm.md) for details.

## Permissions & Privacy

This app requires the Android accessibility service to read screen content and dispatch gestures on your behalf. That is genuinely powerful access, so please understand what's being granted before enabling it.

<!-- TODO(publish-privacy-policy): replace this paragraph with a link to the hosted Privacy Policy once published. -->
A formal Privacy Policy will be linked here. In the meantime, the accessibility service is used solely to perceive on-screen content and execute the actions required by the task you typed; LLM requests are sent to whichever provider you configured. No analytics or telemetry are collected by the app itself.

## Contributing

<!-- TODO(publish-contributing): link CONTRIBUTING.md once it lands. -->
A `CONTRIBUTING.md` is on the way. Until then: open an issue to discuss non-trivial changes, follow Conventional Commits (`feat:`, `fix:`, `refactor:`, `docs:`, `test:`), and run `./gradlew clean assembleDebug lint test` before opening a PR.

## Security

Found a vulnerability? Please **do not** open a public issue. See [SECURITY.md](SECURITY.md) for the private disclosure process.

## License

Licensed under the [Apache License, Version 2.0](LICENSE). See [NOTICE](NOTICE) for attribution and the bundled [open-source license inventory](app/src/main/assets/open_source_licenses.json) for third-party components.
