# UODA / Pixel Handover for Codex — 2026-07-05

## Purpose

Continue the UODA Android app work in Codex without relying on chat scrollback.

Primary working repo for the app:

- `Lycidias93/UnlimitedOnDemand_Auto_Reply`
- Branch: `v0.2-safety-runtime`
- Upstream/original: `defname/UnlimitedOnDemand_Auto_Reply`

Workflow/tooling SoT for Pixel/Termux handoffs:

- `Lycidias93/heimnetz-geraete`
- Branch: `main`
- Relevant completed PRs:
  - `#256` — `cgautotail-guard v5.6`, token-/lane-aware Autocopy guard
  - `#257` — old handover/project source classified as superseded / context evidence only

## Current app runtime state

Installed on Pixel:

- Package: `com.defname.unlimitedondemandautoreply`
- Installed APK branch feature level: v0.4 Profile-Light
- `versionCode=3`
- `versionName=v0.3-alpha`
- Last known install time from package dump: `2026-07-03 17:57:30`
- Install method: root `pm install -r` from `/data/local/tmp`, not Android file picker.

Current UI/runtime values verified by screenshots:

- Send SMS permission: granted
- Post notification permission: granted
- Notification listener: enabled
- Profile name: `O2/Freenet Vollspeed`
- Profile enabled: `ON`
- Dry run mode: `ON (no SMS will be sent)`
- App package to listen for: `com.google.android.apps.messaging`
- Title match: `10118`
- Body/content match: `Vollspeed`
- Target number: `10118`
- Text to send: `2`
- Delay min/max: `5` / `30`

Current logs in app show persistence working, including entries like:

- `Test config: complete; profile_enabled=true; dry_run=true; target configured`
- Older entries from before Profile-Light remain visible, proving persisted log carry-over.

## Completed app changes

### v0.2/v0.3 Safety runtime

Implemented earlier:

- Config incomplete hard-stop.
- Case-insensitive notification matching.
- No raw SMS/notification contents in logs.
- 10 min dedupe window.
- 15 min cooldown.
- Daily limit: 3.
- Unique SMS PendingIntent.
- Dry-run mode.
- `Test current config` button.
- `Clear logs` button.
- Safe default settings.

### v0.3.1 Persistent Runtime Logs

Commit known from run:

- `6c78943091d624e2322df3a1022764d10e7d9015`
- Message: `feat: persist runtime logs`

What changed:

- `LogManager` persists logs to SharedPreferences:
  - `PREFS_NAME = "runtime_logs"`
  - `PREFS_KEY_ENTRIES = "entries_json"`
  - `MAX_LOGS = 250`
  - `MAX_MESSAGE_LENGTH = 500`
- `LogManager.init(applicationContext)` is called from `MainActivity` and `MyNotificationListenerService`.
- `clearLogs()` persists the clear state.
- Persistent logs verified by UI after app restart.

Installed APK SHA from v0.3.1 build:

- `3b85b994358f75f3858e0b01426bb641a50aa4cc4ce777fa3e47bf0b2d478b30`

### v0.4 Profile-Light

Latest known UODA branch head from CI/run:

- `fdae2c3cd16bf8befa9a3062a23fa85632caf043`

Installed APK SHA from v0.4 build:

- `c9041948a41c70b08d9e558d0ece14358259af0f87dbb8d363f16e4b0ee8c987`

What changed:

- Profile name UI field.
- `profile_enabled` ON/OFF switch.
- Default profile: `O2/Freenet Vollspeed`.
- Listener skips when profile disabled.
- Log marker: `Skipped: profile disabled`.
- `Test current config` now logs:
  - `Test config: complete; profile_enabled=true; dry_run=true; target configured`

v0.4 install had one transient Android package manager failure:

- `cmd: Failure calling service package: Failed transaction (2147483646)`
- Retry via `/data/local/tmp/uoda-v04-profile-light.apk` and `pm install -r` succeeded.
- UI verified v0.4 Profile-Light is active.

## v5.6 Pixel/Termux handoff status

`cgautotail-guard v5.6` is complete in `heimnetz-geraete` main via PR #256 and locally installed on Pixel runtime.

Runtime install/verify showed:

- Runtime target: `$HOME/.local/bin/cgautotail-guard`
- PATH symlink: `$PREFIX/bin/cgautotail-guard`
- Help shows v5 CLI:
  - `--guard`
  - `--run-token`
  - `--lane`
  - `--scope`
  - `--host`
  - `--route-class`
  - `--secret-class`
  - `--expect-marker`
  - optional `--forbid-marker`
  - `--log`
  - `--copy`

v5.6 smoke was run with:

- Run token: `cgauto-v56-smoke-20260705_023425-11266`
- Lane: `chat-main`
- Scope: `repo`
- Host: `pixel-local`
- Route class: `none`
- Secret class: `none`
- Expected marker: `RESULT: CGAUTOTAIL_GUARD_V56_SMOKE_EXPECTED`

Clipboard content showed the exact token/lane/scope/host/expected marker, proving the old v4 `latest.log`/wrong-session bug is covered for v5-style handoffs.

Important rule for Codex/Pixel work:

- Do not treat raw `latest.log` as a multitasking-safe SoT.
- Valid handoff requires:
  - `CGFLOW_RUN_TOKEN`
  - `CGFLOW_LANE`
  - `CGFLOW_SCOPE`
  - `CGFLOW_HOST`
  - `CGFLOW_ROUTE_CLASS`
  - `CGFLOW_SECRET_CLASS`
  - expected result marker
  - forbidden foreign marker absent
- For UODA work use:
  - lane: `chat-uoda`
  - scope: `uoda`
  - host: `pixel-local`
  - route_class: `none`
  - secret_class: `none`

## Current v0.5 work: Safe Notification Runtime Test

Goal:

- Keep Dry-run ON.
- Do not send SMS.
- Simulate or receive a matching notification.
- Verify that the listener logs:
  - `Dry run: notification matched; SMS not sent.`
- Then only later consider real provider notification testing / release.

### v0.5 v1 result

Failed early at listener permission preflight:

- Error:
  - `java.lang.SecurityException: Permission Denial: getCurrentUser() ... requires android.permission.INTERACT_ACROSS_USERS`
- Cause:
  - `settings get secure enabled_notification_listeners` was run as Termux UID instead of root.
- No prefs were changed:
  - `settings_restore=skipped_no_backup`

### v0.5 v2 result

Preflight and notification post worked, but listener did not match.

Observed:

- `notification_listener_enabled=yes`
- `notify_method=termux-notification`
- `test_package=com.termux`
- `notification_posted=yes`
- Visible `Vollspeed` notification appeared on Pixel.
- Waited 15 seconds.
- `dryrun_match_count_after=0`
- `dryrun_listener_match=no`
- Result:
  - `RESULT: UODA_V05_NOTIFICATION_DRYRUN_RUNTIME_TEST_FAILED no_dryrun_match`
  - `RESULT: CGRUN_DONE rc=30`

Runtime evidence after v2:

- Settings were restored.
- UI still shows:
  - `sms_app=com.google.android.apps.messaging`
  - `profile_enabled=ON`
  - `dry_run=ON`
- App logs show new `Test config...` entries, but no `Dry run: notification matched; SMS not sent.` entry.

Likely cause, not yet proven:

- The visible Termux notification may not have package `com.termux` from NotificationListener perspective.
- It may be `com.termux.api` or another posting package.
- Listener currently requires exact package match before title/body match.

Relevant listener behavior in current app:

- Reads package name from `sbn.packageName`.
- Reads `sms_app` from SharedPreferences.
- Strict package check:
  - if `packageName != smsApp`, return false.
- Then checks normalized title contains title match and normalized text contains body match.

### Prepared v0.5 v3

File already created and offered:

- `pixel_local__uoda_v05_notification_dryrun_v56_workflow_v3.sh`
- SHA256:
  - `311eaa5e7a258ddc608a075ba4484783cbea60169531a5faba2b11c9d2325db6`

Intended v3 behavior:

1. Post probe notification.
2. Detect actual notification package using `dumpsys notification`.
3. Temporarily set UODA `sms_app` to detected package.
4. Clear/backup temporary runtime state so dedupe/cooldown cannot block.
5. Post matching notification.
6. Check for `Dry run: notification matched; SMS not sent.`
7. Restore settings and runtime state.
8. Use v5.6 guard for handoff.

Do not proceed to real SMS until v3 or an equivalent real-provider dry-run proves listener matching.

## Safety / no-SMS rule

Dry-run must remain ON until explicit final live test decision.

Do not turn off Dry-run automatically.

Before any real SMS test:

- Confirm target number is still `10118`.
- Confirm answer text is `2`.
- Confirm provider notification really matches.
- Confirm cooldown/dedupe/daily-limit state.
- Confirm user explicitly wants to send a real SMS.
- Keep a rollback path to restore Dry-run ON immediately.

## Known gotchas

### Android package manager

If `pm install -r` fails with:

- `cmd: Failure calling service package: Failed transaction (2147483646)`

Retry may work after:

- app force-stop
- recopy APK to `/data/local/tmp`
- `su -c "pm install -r /data/local/tmp/<apk>"`

### Notification listener testing

Visible notification does not prove listener match.

Need to verify actual `sbn.packageName` or infer from `dumpsys notification`.

### Version naming

Feature state is v0.4, but APK still reports:

- `versionName=v0.3-alpha`
- `versionCode=3`

This is expected for now but should be fixed before any public release.

### Test prefs

The v0.5 notification test temporarily edits:

- `/data/user/0/com.defname.unlimitedondemandautoreply/shared_prefs/settings.xml`
- possibly `/data/user/0/com.defname.unlimitedondemandautoreply/shared_prefs/runtime_state.xml`

Backups and restores must preserve owner/mode. Previous v2 UI confirmed settings were restored.

## Recommended next Codex task

Continue with v0.5 v3 or implement a cleaner app-side testability/debug path.

Preferred direction:

1. Add explicit debug logging for ignored notifications without raw content:
   - package hash or package name? For local debug only; avoid secrets.
   - title_match_result yes/no
   - body_match_result yes/no
   - package_match yes/no
2. Add a debug-only dry-run simulation entry point inside the app, if acceptable.
3. Or update v3 script to detect exact posting package robustly and then rerun dry-run test.

Suggested acceptance criteria:

- Dry-run ON.
- Matching notification appears.
- App logs:
  - `Dry run: notification matched; SMS not sent.`
- No SMS sent.
- Runtime state records dedupe key but not `last_send_at`.
- v5.6 handoff shows:
  - `RESULT: CGAUTOTAIL_GUARD_V5_LOG_SELECTED ... all matches yes`
  - `RESULT: CGAUTOTAIL_GUARD_V5_MARKER_VERIFY expected_marker_present=yes forbidden_marker_present=no`
  - `RESULT: CGAUTOTAIL_GUARD_V5_CLIPBOARD_DONE verify=PASS`
  - `RESULT: CGAUTOTAIL_GUARD_V5_DONE rc=0 tail_rc=0 verify=PASS`

## Suggested git placement

Commit this file into the UODA repo as:

- `docs/handover/uoda_codex_handover_20260705.md`

Suggested commit message:

- `docs: add Codex handover for UODA v0.5 testing`

## Current risk

- App/runtime: low.
- v0.5 notification test: low to medium, because it manipulates root-owned app prefs temporarily.
- Real SMS: medium/high until dry-run match is proven with the actual provider notification.
