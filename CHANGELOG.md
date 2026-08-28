# Changelog

## [v0.5-alpha]

- Adds safer dry-run diagnostics for notification matching.
- Adds a safe internal dry-run test notification that goes through the real notification listener without sending an SMS.
- Adds **Copy runtime status** for sharing bounded listener diagnostics without copying the notification body, reply text or target phone number.
- Keeps successful and relevant listener events visible even after unrelated notifications arrive.
- Keeps dry-run mode enabled by default for safer testing.
- Prepares the project for a stable signed release channel separate from GitHub Actions debug APKs.

