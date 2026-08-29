# Changelog

## [v0.6-alpha]

- Publishes accepted release-candidate APKs without rebuilding them during the public-release step.
- Adds internal release evidence artifacts for candidate and publish runs.
- Adds listener health, callback age, self-test result and SMS result fields to copied runtime status.
- Renames the safe internal dry-run action to a full dry-run self-test and records blocked, posted and passed self-test states.
- Improves notification matching by normalizing whitespace, zero-width characters and Unicode variants before comparing configured title/body strings.
- Includes additional safe notification title/text extras in matching so provider or messaging-app formatting changes are less likely to hide a valid match.
- Runs unit tests before debug APK builds and adds normalization coverage for notification matching.

## [v0.5-alpha]

- Adds safer dry-run diagnostics for notification matching.
- Adds a safe internal dry-run test notification that goes through the real notification listener without sending an SMS.
- Adds **Copy runtime status** for sharing bounded listener diagnostics without copying the notification body, reply text or target phone number.
- Keeps successful and relevant listener events visible even after unrelated notifications arrive.
- Keeps dry-run mode enabled by default for safer testing.
- Prepares the project for a stable signed release channel separate from GitHub Actions debug APKs.

