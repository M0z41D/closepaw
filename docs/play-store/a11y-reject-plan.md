# Accessibility Service Rejection Plan

## 1. "This is general automation, not an accessibility feature."

Likely reason: AI agents that operate arbitrary apps look like convenience automation. The strongest mitigation is to frame ClosePaw as an assistive input replacement: the user states a goal in natural language and delegates tapping, swiping, reading, and typing that would otherwise require manual phone operation [docs/play-store/full-description.txt:3,8-11]. The declaration should avoid "growth", "productivity hack", or "background automation" language.

Pre-emptive mitigation: emphasize user-initiated operation, Smart Capsule visibility, Stop/Takeover controls, and manual Android Settings enablement [app/src/main/kotlin/ai/closepaw/ui/overlay/model/CapsuleRenderSpec.kt:63-97,170-172; app/src/main/kotlin/ai/closepaw/app/MainActivityUiHelpers.kt:21-23].

Fallback wording: "ClosePaw is an assistive control surface for users who cannot or do not want to manually perform each tap, swipe, and text-entry step. It does not automate unattended workflows; it operates only after the user gives a task and remains visible through the Smart Capsule."

## 2. "The service has overly broad access to third-party app content."

Likely reason: the service declares `canRetrieveWindowContent`, `canPerformGestures`, `canTakeScreenshot`, and interactive-window retrieval [app/src/main/res/xml/agent_accessibility_config.xml:5-8]. This is broad and review-sensitive.

Pre-emptive mitigation: point out that production perception defaults to Accessibility-tree only [app/src/main/kotlin/ai/closepaw/perception/PerceptionConfig.kt:11-35; app/src/main/kotlin/ai/closepaw/protocol/SessionConfig.kt:31-33], that blocked apps are masked to empty elements/no image [app/src/main/kotlin/ai/closepaw/platform/AccessibilityPlatform.kt:64-84; app/src/main/kotlin/ai/closepaw/tool/AppClassifier.kt:68-80], and that screen capture happens inside the active agent turn pipeline [app/src/main/kotlin/ai/closepaw/agent/AgentTurnRunner.kt:132-169].

Fallback wording: "We can remove screenshot capability from the initial Play build and rely only on accessibility-node content for review."

## 3. "Disclosure and consent are not prominent enough."

Likely reason: the service description and onboarding mention read/tap behavior, but reviewers may still judge AI data flow as insufficiently prominent.

Pre-emptive mitigation: cite the Android service description string [app/src/main/res/values/strings.xml:3-4], onboarding disclosure [app/src/main/kotlin/ai/closepaw/ui/onboarding/OnboardingSteps.kt:810-819], onboarding CTA [app/src/main/kotlin/ai/closepaw/ui/onboarding/OnboardingSteps.kt:114-140], and manual Settings launch [app/src/main/kotlin/ai/closepaw/onboarding/OnboardingViewModel.kt:182-198; app/src/main/kotlin/ai/closepaw/app/MainActivity.kt:875-879].

Fallback wording: "We will add an explicit pre-permission confirmation screen that repeats: ClosePaw reads visible screen content, sends task-relevant content to the selected LLM provider, performs taps/swipes/text entry only for user-started tasks, and can be stopped at any time."

## 4. "The app may monitor users in the background."

Likely reason: once enabled, Android keeps the AccessibilityService connected, and the XML includes content-change events [app/src/main/res/xml/agent_accessibility_config.xml:2-10]. The current code handles window-state events for overlay location even outside task execution [app/src/main/kotlin/ai/closepaw/app/AgentService.kt:232-245].

Pre-emptive mitigation: be precise. Do not claim the Android service process is disabled between tasks. Claim that the agent's screen-reading/action loop runs after a user task starts: `runAgent()` creates a session and submits `Op.UserInput` [app/src/main/kotlin/ai/closepaw/app/AgentService.kt:353-402], first task startup calls `platform.start()` [app/src/main/kotlin/ai/closepaw/session/AgentSession.kt:304-377], and turns capture screen only inside the loop [app/src/main/kotlin/ai/closepaw/agent/AgentTurnRunner.kt:132-169].

Fallback wording: "ClosePaw's AccessibilityService may be connected by Android, but ClosePaw does not run the agent perception/action loop until the user starts a task."

## 5. "The app could perform sensitive actions without enough user confirmation."

Likely reason: a general AI agent can click, type, scroll, swipe, and open apps [app/src/main/kotlin/ai/closepaw/platform/AccessibilityPlatform.kt:304-353].

Pre-emptive mitigation: cite the policy layer: screen-changing tools are evaluated by `PolicyEngine`, blocked financial/auth apps are denied, cautious apps require approval in SMART mode, and session allow-listing is bounded by the approval mode [app/src/main/kotlin/ai/closepaw/tool/PolicyEngine.kt:10-24,64-110,125-140]. The approval router waits for user approval, times out after 60 seconds, rejects denial, and re-checks foreground app after approval [app/src/main/kotlin/ai/closepaw/tool/ToolRouter.kt:126-207,216-250].

Fallback wording: "For Play review, we can default to approval for every screen-changing action and disable persistent 'Always' approval until the app has review history."

## Negotiation Product Changes

1. Reduce Hot Idle from 5 minutes to 60 seconds and make the stopped/idle state more visible. Current idle auto-shutdown is 300,000 ms [app/src/main/kotlin/ai/closepaw/session/AgentSession.kt:52-57,710-716].

2. Remove `android:canTakeScreenshot="true"` from the accessibility-service XML for the initial Play build and keep `PerceptionConfig.AccessibilityOnly` as the only production mode [app/src/main/res/xml/agent_accessibility_config.xml:6-8; app/src/main/kotlin/ai/closepaw/perception/PerceptionConfig.kt:11-35].

3. Ship Play review mode with `ApprovalMode.ALWAYS_ASK` for all screen-changing actions, remove the "Always" approval option, and keep only per-action or per-session approval [app/src/main/kotlin/ai/closepaw/protocol/SessionConfig.kt:71-79; app/src/main/kotlin/ai/closepaw/ui/overlay/model/CapsuleRenderSpec.kt:128-139].
