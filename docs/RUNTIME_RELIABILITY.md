# Runtime reliability

This document describes the reliability surfaces used by the maintained fork of UnlimitedOnDemand Auto Reply.

## Goals

- Keep dry-run mode safe by default.
- Make notification-listener problems diagnosable without copying SMS body text, reply text or the target phone number.
- Keep public release artifacts tied to the APK that was actually accepted on a device.
- Avoid silent false positives in notification matching and SMS state transitions.

## Full dry-run self-test

The settings screen contains **Run full dry-run self-test**. It posts an app-owned notification and routes it through the real Android notification listener.

The self-test requires:

- profile enabled;
- dry-run enabled;
- complete matching and SMS settings;
- notification-listener access enabled;
- notification permission available on Android versions that require it.

The test never sends an SMS. It records a bounded self-test result such as:

- `requested`
- `posted`
- `passed`
- `blocked_profile_disabled`
- `blocked_dry_run_disabled`
- `blocked_configuration_incomplete`
- `blocked_notification_listener_disabled`
- `blocked_notification_permission_missing`
- `failed_title_mismatch`
- `failed_body_mismatch`

## Runtime status

**Copy runtime status** intentionally omits message content, reply text and the target number. It includes bounded state fields that are useful for diagnosing reliability issues:

- listener lifecycle state and health;
- listener callback and evaluation ages;
- package/title/body match booleans;
- latest relevant evaluation and latest successful dry-run/send decision;
- latest full dry-run self-test result;
- latest SMS result code and bounded SMS result decision;
- dry-run/profile state and daily runtime counters.

`listener_health` is derived from local listener state:

- `ok` — the listener has received a recent notification callback.
- `waiting_for_callback` — the listener exists but has not yet received a callback.
- `stale_callback` — the last callback is older than the current stale threshold.
- `disconnected` — Android reported listener disconnection.
- `destroyed` — the listener service was destroyed.
- `not_created` — no listener service creation has been recorded.

## Matching reliability

Matching checks the configured package, title and body values. Package matching remains exact except for the internal dry-run self-test package override. Title and body matching are normalized before substring comparison:

- case is ignored;
- repeated whitespace is collapsed;
- zero-width characters are removed;
- common Unicode variants are normalized;
- common notification extras such as big text, subtext, summary text and text lines are considered.

This keeps provider or messaging-app formatting changes from hiding a valid match while still requiring the configured package/title/body gate.

## SMS reliability

Real SMS sending is intentionally separate from dry-run acceptance. When dry-run is disabled and a matching notification passes dedupe, cooldown and daily-limit gates, the app records the scheduled/send-request state and then records the Android SMS result callback as a bounded decision such as `sms_sent`, `sms_failed_no_service` or `sms_failed_radio_off`.

## Release reliability

Public releases must not publish a newly rebuilt APK after live acceptance. The release workflow has two distinct paths:

1. Candidate path: build and verify a signed release candidate, then upload it as a GitHub Actions artifact.
2. Publish path: download the accepted candidate artifact by run ID, verify its SHA-256 against the accepted device-installed APK, verify signing and alignment again, then attach that exact APK to the GitHub Release.

The workflow also uploads an internal release-evidence artifact. Public changelogs remain user-facing and do not include internal hashes, workflow IDs or verification walls.


## Real SMS arming

Dry-run OFF is not sufficient to send a real SMS. The app requires a short explicit arm window before a matched notification can schedule or request a real SMS send. If a notification matches while dry-run is off and the arm window is not active, the listener records `real_sms_disarmed`, logs the block and does not send an SMS.

Copied runtime status includes `real_sms_armed`, `real_sms_armed_until` and `real_sms_armed_seconds_remaining` so a handoff can distinguish a safe dry-run state from an intentionally armed live-send state.
