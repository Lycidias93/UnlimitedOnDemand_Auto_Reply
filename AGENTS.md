# Codex operating notes for UODA

This repository is the active Codex working copy for UnlimitedOnDemand Auto Reply.

## Repository context

- GitHub repo: `Lycidias93/UnlimitedOnDemand_Auto_Reply`
- Working branch: `v0.2-safety-runtime`
- Upstream/original project: `defname/UnlimitedOnDemand_Auto_Reply`
- Current app package: `com.defname.unlimitedondemandautoreply`
- Current feature level: v0.5 safe internal dry-run notification test
- Durable handover: `docs/handover/uoda_codex_handover_20260705.md`

## Non-negotiable safety rules

- Do not send a real SMS unless the user explicitly asks for a live SMS test in that turn.
- Keep Dry-run ON by default.
- Never turn Dry-run off automatically.
- Do not change the configured target number or reply text unless explicitly requested.
- Before any real SMS test, verify:
  - target number is `10118`
  - reply text is `2`
  - provider notification has already matched in Dry-run
  - cooldown, dedupe, and daily-limit state are understood
  - there is a rollback path to restore Dry-run immediately
- Runtime logs must not include raw notification bodies or raw SMS content.

## Current v0.5 intent

v0.5 adds a safe app-internal notification test path:

- The UI can post an app-owned test notification.
- The notification listener still performs the real title/body match.
- Package matching is bypassed only for an app-owned notification with the private internal-test marker.
- Dry-run must be ON; the internal test is blocked if Dry-run is OFF.
- The success marker remains:

```text
Dry run: notification matched; SMS not sent.
```

This validates the listener pipeline without sending SMS. It does not replace a later real-provider Dry-run validation.

## Build and validation

Use JDK 17 and the Android SDK. On this Windows workstation they are usually at:

```text
C:\Users\lycid\AppData\Local\Programs\Microsoft\Java\jdk-17.0.19+10
C:\Users\lycid\AppData\Local\Android\Sdk
```

Recommended validation before publishing app changes:

```powershell
$env:JAVA_HOME='C:\Users\lycid\AppData\Local\Programs\Microsoft\Java\jdk-17.0.19+10'
$env:ANDROID_HOME='C:\Users\lycid\AppData\Local\Android\Sdk'
$env:ANDROID_SDK_ROOT=$env:ANDROID_HOME
$env:Path="$env:JAVA_HOME\bin;$env:ANDROID_HOME\platform-tools;$env:ANDROID_HOME\cmdline-tools\latest\bin;$env:Path"
$env:GRADLE_USER_HOME=Join-Path $env:TEMP 'gradle-cache-codex'
.\gradlew.bat clean test assembleDebug lintDebug --no-daemon
```

Expected debug APK path:

```text
app\build\outputs\apk\debug\app-debug.apk
```

## Git hygiene

- Build output is intentionally ignored.
- Stage files explicitly; do not use broad staging when build folders are present.
- Preserve user changes. If unrelated local edits appear, leave them alone and report them.
- Prefer small commits with clear scope.

## Pixel / Termux handoff

For Pixel-side workflows, use the v5.6 guarded handoff from `Lycidias93/heimnetz-geraete`, not raw `latest.log`.

UODA handoff values:

```text
CGFLOW_LANE=chat-uoda
CGFLOW_SCOPE=uoda
CGFLOW_HOST=pixel-local
CGFLOW_ROUTE_CLASS=none
CGFLOW_SECRET_CLASS=none
```

A valid handoff must include run token, lane, scope, host, route class, secret class, expected marker, and proof that forbidden foreign markers are absent.

