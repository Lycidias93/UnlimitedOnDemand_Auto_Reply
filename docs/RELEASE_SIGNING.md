# Release signing

Public UnlimitedOnDemand Auto Reply APKs must use one long-lived Android signing key. Do not publish GitHub Actions debug artifacts as stable releases: GitHub-hosted runners create a fresh Android debug keystore when no fixed key is supplied, so those APKs can have different signing certificates from run to run.

## Required GitHub Actions secrets

Configure these repository Actions secrets before running **Publish signed UODA release**:

- `UODA_RELEASE_KEYSTORE_B64` — base64 of the release keystore file.
- `UODA_RELEASE_STORE_PASSWORD` — keystore password.
- `UODA_RELEASE_KEY_ALIAS` — signing-key alias.
- `UODA_RELEASE_KEY_PASSWORD` — key password when it differs from the store password; it may be left unset when both passwords are the same.
- `UODA_RELEASE_CERT_SHA256` — SHA-256 fingerprint of the signing certificate. This is a trust anchor used by the workflow to reject an accidental signing-key change.

The private keystore and passwords must never be committed to the repository. Keep at least one secure offline backup of the keystore and its credentials; losing the private key prevents normal upgrades of already-installed public releases.

The repository ignores common Android signing material such as `*.jks`, `*.keystore`, `*.p12` and `*.pem`. Keep local keystores out of the working tree whenever possible.

## Setup helper

The repository includes `tools/prepare_release_signing_secrets.sh` for maintainer-side setup. Run it locally, never in ChatGPT, and never paste the generated keystore or passwords into an issue, pull request, chat or committed file.

Typical first-time setup:

```bash
bash tools/prepare_release_signing_secrets.sh --generate --upload
```

The helper prompts for the store and key passwords without echoing them, creates `app/AndroidKeystore/release.jks` when requested, derives the certificate SHA-256 fingerprint, and uploads the five required GitHub Actions secrets with `gh secret set`. It prints only non-secret status fields and the public certificate fingerprint.

To validate an existing local keystore without uploading secrets:

```bash
bash tools/prepare_release_signing_secrets.sh --verify
```

Use `--force` only when intentionally replacing a local keystore before any stable public release has depended on it. After a stable public APK is released, replacing the key is a package-signature migration and should be treated as a breaking install/update event.

## Local signing compatibility

`app/build.gradle.kts` supports release signing through environment variables. The public-release workflow injects these values through GitHub Actions secrets and materializes the keystore only for the lifetime of the GitHub-hosted runner.

Required local environment variables for a signed release build:

- `UODA_RELEASE_KEYSTORE_PATH`
- `UODA_RELEASE_STORE_PASSWORD`
- `UODA_RELEASE_KEY_ALIAS`
- `UODA_RELEASE_KEY_PASSWORD` when it differs from the store password

The first stable-release path keeps release minification disabled. This fork is currently focused on runtime safety, dry-run diagnostics and predictable APK update behavior; minification can be reconsidered later after explicit runtime acceptance.

## Publication flow

1. Keep the release version in `app/build.gradle.kts` and the matching top section in `CHANGELOG.md` synchronized.
2. Keep the normal debug build workflow green on `v0.2-safety-runtime`.
3. Produce the stable-signed candidate from `v0.2-safety-runtime` without publishing it. Either run **Publish signed UODA release** with **publish** left off, or bump `.github/release-candidate.trigger` on `v0.2-safety-runtime`. The push-trigger path is candidate-only: it decodes the private keystore, builds `assembleRelease`, verifies 16 KB ZIP alignment, verifies the APK signature, compares the actual certificate fingerprint with `UODA_RELEASE_CERT_SHA256`, and uploads a signed release-candidate APK without creating a public release.
4. Install that signed candidate on the target device and complete live UODA acceptance. For a stable release, do not substitute an earlier debug-signed APK for this step.
5. After live acceptance, run **Publish signed UODA release** from `v0.2-safety-runtime` with **publish** enabled. It repeats the signing and verification gates, extracts the matching user-facing changelog section, and creates the GitHub Release/tag with the signed APK attached.
6. Never rotate the public signing key casually. A key change is an Android package-signature migration and must be treated as a breaking install/update event.

The repository trigger exists so automation that can safely write to `v0.2-safety-runtime` but cannot invoke `workflow_dispatch` can request a candidate without weakening the publication gate. It never enables `publish` by itself; public publication remains an explicit `workflow_dispatch` action after live acceptance.

## First stable-release migration

All pre-public-release CI APKs in this fork are development artifacts built with Android debug signing. Because GitHub-hosted runners are ephemeral, those debug signing identities are not a stable public update channel.

For a device that already has one of those test APKs installed, the first public stable APK may require a one-time uninstall/reinstall if Android reports a package-signature mismatch. App preferences are normally removed by uninstall, so use **Copy runtime status** or otherwise record the current settings first, then re-enter the desired UODA settings and verify SMS permission, notification permission and notification-listener access afterward. Once the first stable APK is installed, subsequent stable releases must keep the same release signing key so normal in-place upgrades work.
