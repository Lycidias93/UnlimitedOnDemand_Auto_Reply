# UODA Codex Handover — 2026-07-05

## Mission

Continue safety-first development of UnlimitedOnDemand Auto Reply (UODA).

- Repository: `Lycidias93/UnlimitedOnDemand_Auto_Reply`
- Working branch: `v0.2-safety-runtime`
- Upstream: `defname/UnlimitedOnDemand_Auto_Reply`
- Current work item: v0.5 safe notification runtime test

## Non-negotiable safety boundary

Do not send a real SMS during v0.5 development or validation.

- Keep `dry_run=true`.
- Never switch Dry-run off automatically.
- Do not modify the configured target number or reply text unless the user explicitly asks.
- A live test requires a separate, explicit user decision after a real provider notification has matched in Dry-run.
- Before any live test, verify target `10118`, reply `2`, provider match, cooldown, dedupe, daily limit, and the rollback path back to Dry-run.

## Known Pixel runtime state

- Package: `com.defname.unlimitedondemandautoreply`
- Installed feature level: v0.4 Profile-Light
- Reported APK metadata: `versionCode=3`, `versionName=v0.3-alpha`
- Install method: root `pm install -r` from `/data/local/tmp`
- Profile: `O2/Freenet Vollspeed`
- Profile enabled: ON
- Dry-run: ON
- Watched package: `com.google.android.apps.messaging`
- Title match: `10118`
- Body match: `Vollspeed`
- Target: `10118`
- Reply: `2`
- Delay: 5–30 seconds

Persistent runtime logs are working. The expected successful Dry-run marker is:

```text
Dry run: notification matched; SMS not sent.
```

## Implemented safety runtime

- Incomplete-configuration hard stop
- Case-insensitive title/body matching
- No raw notification or SMS contents in runtime logs
- Ten-minute dedupe window
- Fifteen-minute cooldown
- Daily limit of three
- Unique SMS `PendingIntent`
- Dry-run mode
- Config test and log-clear controls
- Persistent runtime logs (`runtime_logs`, up to 250 entries)
- Profile name and profile enable switch

Known commits:

- Persistent logs: `6c78943091d624e2322df3a1022764d10e7d9015`
- v0.4 branch head observed by Pixel workflow: `fdae2c3cd16bf8befa9a3062a23fa85632caf043`

Known installed APK hashes:

- v0.3.1: `3b85b994358f75f3858e0b01426bb641a50aa4cc4ce777fa3e47bf0b2d478b30`
- v0.4: `c9041948a41c70b08d9e558d0ece14358259af0f87dbb8d363f16e4b0ee8c987`

## v0.5 evidence so far

### Attempt v1

Failed before the test because `settings get secure enabled_notification_listeners` ran as the Termux UID. The command requires root for this use. No preferences were changed.

### Attempt v2

- Listener preflight passed.
- A visible Termux notification was posted.
- The listener did not produce the Dry-run match marker.
- Settings were restored successfully.

Most likely explanation: the notification package seen by `NotificationListenerService` was not the configured `com.termux`. A visible notification alone does not prove its `StatusBarNotification.packageName`.

### Prepared external attempt v3

- File: `pixel_local__uoda_v05_notification_dryrun_v56_workflow_v3.sh`
- SHA-256: `311eaa5e7a258ddc608a075ba4484783cbea60169531a5faba2b11c9d2325db6`

The script is intended to discover the posting package with `dumpsys notification`, temporarily configure it, clear blocking runtime state, post a match, verify the marker, and restore settings and state with their original owner/mode.

## Codex continuation strategy

Prefer an app-internal test path before another root-owned preference rewrite:

1. Add a button that posts an app-owned notification containing the configured title/body match values.
2. Permit a package-match override only for an app-owned notification carrying the private internal-test marker.
3. Require profile enabled, listener enabled, notification permission, complete configuration, and Dry-run ON before posting the test.
4. Keep title/body matching, dedupe-state recording, and the normal Dry-run outcome in the real listener path.
5. Log match booleans only; never log raw notification content.
6. Add unit tests for package, title, body, case-insensitive matching, and the internal package override.

This proves the installed listener and matching pipeline without sending an SMS. It does not replace a later real-provider Dry-run observation.

## v0.5 acceptance criteria

- Dry-run remains ON.
- Internal matching notification is visible.
- Listener writes the exact Dry-run marker.
- No SMS is sent.
- Runtime state records the notification dedupe key.
- Runtime state does not update `last_send_at`.
- A later provider notification must independently pass Dry-run before any live SMS decision.

## Codex local implementation status

Implemented and validated locally on 2026-07-05:

- Added `Post safe dry-run test notification`.
- Posting is blocked unless profile, Dry-run, listener, permission, and configuration preflight pass.
- Only an app-owned notification carrying the private marker may bypass the configured package check.
- Title and body matching still run normally.
- The listener independently blocks the internal test if Dry-run is disabled.
- Match diagnostics contain booleans only, never raw notification content.
- Fresh-install Dry-run default changed to ON.
- APK metadata updated to `versionCode=5`, `versionName=v0.5-alpha`.
- Added four notification-matching unit tests.
- Removed the obsolete manifest package attribute and deprecated Compose divider usage.

Validation:

```text
./gradlew test assembleDebug lintDebug
BUILD SUCCESSFUL
Lint: 0 errors
Debug APK SHA-256:
326A48DB8A38B1870913FB7D3238EDFF86341D03FD7C8B9DAE703D306041776C
```

The v0.5 code changes are local until published separately. This build has not been installed on Pixel and the internal marker has not been observed on-device. No ADB device was connected to the Codex Windows host during validation.

## Pixel/Termux handoff contract

Workflow source of truth:

- Repository: `Lycidias93/heimnetz-geraete`
- Branch: `main`
- PR `#256`: `cgautotail-guard v5.6`
- PR `#257`: older handover source is superseded/context-only

For UODA use:

```text
CGFLOW_LANE=chat-uoda
CGFLOW_SCOPE=uoda
CGFLOW_HOST=pixel-local
CGFLOW_ROUTE_CLASS=none
CGFLOW_SECRET_CLASS=none
```

Do not use raw `latest.log` as a multitasking-safe source of truth. A valid handoff must include its run token, lane, scope, host, route class, secret class, expected marker, and proof that a forbidden foreign marker is absent.

Expected guard evidence:

```text
RESULT: CGAUTOTAIL_GUARD_V5_LOG_SELECTED ... all matches yes
RESULT: CGAUTOTAIL_GUARD_V5_MARKER_VERIFY expected_marker_present=yes forbidden_marker_present=no
RESULT: CGAUTOTAIL_GUARD_V5_CLIPBOARD_DONE verify=PASS
RESULT: CGAUTOTAIL_GUARD_V5_DONE rc=0 tail_rc=0 verify=PASS
```

## Operational gotchas

- If `pm install -r` reports `Failed transaction (2147483646)`, force-stop the app, recopy the APK to `/data/local/tmp`, and retry the root install.
- Root-owned preference backups/restores must preserve owner and mode.
- Update APK version metadata before a public release.
- Treat the internal notification test as pipeline validation, not as proof of the provider notification package or content shape.
