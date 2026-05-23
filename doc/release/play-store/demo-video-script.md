# Accessibility Declaration Demo Video Script

## Format

- MP4, 30 seconds or less.
- 1080x1920 portrait preferred, or 1280x720 landscape minimum.
- English narration.
- Use a real phone or emulator with a test Google account and non-sensitive test Gmail messages.
- Use hard cuts if needed; do not overlay fake UI or hide permission screens.

## Equipment

- Android phone or emulator with ClosePaw installed.
- USB cable and `adb`.
- Screen recording:

```bash
adb shell screenrecord --size 1080x1920 --bit-rate 8000000 --time-limit 30 /sdcard/closepaw-a11y-demo.mp4
adb pull /sdcard/closepaw-a11y-demo.mp4 doc/release/play-store/a11y-demo.mp4
adb shell rm /sdcard/closepaw-a11y-demo.mp4
```

## Clean Device Setup

Use a test Gmail account with 3-5 harmless seed emails. Complete AI-provider auth before the final task segment if a 30-second cut cannot include auth.

```bash
export PKG=ai.closepaw
adb devices
adb shell am force-stop "$PKG"
adb shell input keyevent KEYCODE_HOME

# Make the recording readable and quick.
adb shell settings put global window_animation_scale 0
adb shell settings put global transition_animation_scale 0
adb shell settings put global animator_duration_scale 0

# Optional full first-run reset for the disclosure and Settings shots.
# Run only before recording the onboarding segment; it clears auth.
adb shell pm clear "$PKG"

# Reset Accessibility so the video shows manual user enablement.
# If the device denies secure settings writes, disable ClosePaw manually in Settings.
adb shell settings put secure enabled_accessibility_services ""
adb shell settings put secure accessibility_enabled 0

# Pre-grant overlay for the task segment if needed so the Smart Capsule is visible.
adb shell appops set "$PKG" SYSTEM_ALERT_WINDOW allow

adb shell am start -n "$PKG/.app.MainActivity"
```

## Demonstrated Task

Use: `Catch me up on my emails`

This matches the existing Play Store positioning: the canonical description lists "catch up on emails" as a supported task, and the screenshot order includes a Gmail live-view slide [doc/release/play-store/full-description.txt:3; doc/release/play-store/README.md:15-22].

## 30-Second Shot List

| Time | On Screen | Narration |
| --- | --- | --- |
| 0:00-0:04 | ClosePaw onboarding Accessibility step. Expand "Data & privacy details" if it is collapsed. | "ClosePaw asks for Accessibility so it can read the screen and perform taps only for tasks I start." |
| 0:04-0:10 | Tap "Open Accessibility Settings", show Android Accessibility Settings, open ClosePaw service, and toggle it on. | "I enable the service myself in Android Settings after this disclosure." |
| 0:10-0:13 | Return to ClosePaw. Show the permission marked enabled or the next onboarding state. | "The app verifies the service after I return." |
| 0:13-0:17 | Hard cut to configured test device if needed. Type or speak: "Catch me up on my emails". | "Now I ask it to catch me up on my emails." |
| 0:17-0:25 | Gmail opens or is foregrounded. Smart Capsule is visible while the agent reads and acts. Show one visible tap/swipe or status change. | "The Smart Capsule stays visible so I can see and control the task." |
| 0:25-0:30 | Tap the Smart Capsule "Stop" button. Show "Stopping..." or stopped state. | "I can stop the task immediately at any time." |

## Recording Notes

- Keep the task short. A successful demo does not need to finish the whole email summary; the point is disclosure, manual enablement, visible task operation, and Stop.
- Use only test data. Do not show personal email, contacts, financial apps, authentication screens, or one-time codes.
- If Accessibility Settings navigation takes too long, record it normally and trim dead time with hard cuts while preserving the actual toggle-on screen.
- If the Android permission warning appears, keep it visible long enough to show that the OS, not ClosePaw, grants the service.
