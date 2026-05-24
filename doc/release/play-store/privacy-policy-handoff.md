# Privacy Policy Handoff

This repository now contains a Google Play privacy policy for ClosePaw and a GitHub Pages workflow to publish it.

## Files

- `PRIVACY_POLICY.md`: canonical repository copy for review and editing.
- `doc/release/privacy/index.md`: GitHub Pages copy that will publish at the public privacy URL.
- `.github/workflows/pages.yml`: GitHub Actions workflow that builds `docs/` and deploys the privacy page with GitHub Pages.
- `app/src/main/kotlin/ai/closepaw/ui/onboarding/OnboardingSteps.kt`: onboarding Accessibility copy links users to the published policy.

## Required owner steps

1. Push these changes to the `main` branch.
2. In GitHub repository settings, enable Pages and select GitHub Actions as the Pages source.
3. Wait for the `Deploy Privacy Policy` workflow to complete.
4. Verify this URL resolves publicly: `https://imoonkey.github.io/closepaw/privacy/`
5. Paste that URL into the Google Play Console Privacy Policy field.
6. Complete the Play Console Data Safety form consistently with the policy: no ClosePaw server-side collection, no tracking, no telemetry, no analytics, and user-directed screen-content transfer only to the selected LLM provider during active tasks.

Resulting public URL: `https://imoonkey.github.io/closepaw/privacy/`

