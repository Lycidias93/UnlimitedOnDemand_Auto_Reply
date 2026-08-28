#!/usr/bin/env bash
set -euo pipefail

repo="${UODA_GITHUB_REPO:-Lycidias93/UnlimitedOnDemand_Auto_Reply}"
keystore_path="${UODA_RELEASE_KEYSTORE_PATH:-app/AndroidKeystore/release.jks}"
key_alias="${UODA_RELEASE_KEY_ALIAS:-uoda-release}"
store_password="${UODA_RELEASE_STORE_PASSWORD:-}"
key_password="${UODA_RELEASE_KEY_PASSWORD:-}"
dname="${UODA_RELEASE_DNAME:-CN=UnlimitedOnDemand Auto Reply, OU=Android, O=Lycidias93, L=Local, ST=Local, C=DE}"
validity_days="${UODA_RELEASE_VALIDITY_DAYS:-10000}"

generate=0
upload=0
verify=0
force=0

usage() {
  cat <<'USAGE'
Usage: bash tools/prepare_release_signing_secrets.sh [--generate] [--upload] [--verify] [--force]

Creates or validates a local UODA Android release keystore and can upload the
required GitHub Actions secrets without printing secret values.

Environment overrides:
  UODA_GITHUB_REPO                  default: Lycidias93/UnlimitedOnDemand_Auto_Reply
  UODA_RELEASE_KEYSTORE_PATH        default: app/AndroidKeystore/release.jks
  UODA_RELEASE_KEY_ALIAS            default: uoda-release
  UODA_RELEASE_STORE_PASSWORD       prompted when needed
  UODA_RELEASE_KEY_PASSWORD         prompted when needed; defaults to store password
  UODA_RELEASE_DNAME                keytool distinguished name
  UODA_RELEASE_VALIDITY_DAYS        default: 10000

Examples:
  bash tools/prepare_release_signing_secrets.sh --generate --upload
  bash tools/prepare_release_signing_secrets.sh --verify

The script never prints the keystore base64 or passwords. Keep an offline backup
of the generated keystore and credentials before publishing stable releases.
USAGE
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --generate) generate=1 ;;
    --upload) upload=1 ;;
    --verify) verify=1 ;;
    --force) force=1 ;;
    -h|--help) usage; exit 0 ;;
    *) echo "Unknown argument: $1" >&2; usage >&2; exit 2 ;;
  esac
  shift
done

if (( generate == 0 && upload == 0 && verify == 0 )); then
  usage >&2
  exit 2
fi

need_cmd() {
  command -v "$1" >/dev/null 2>&1 || { echo "Missing required command: $1" >&2; exit 127; }
}

prompt_secret_if_empty() {
  local prompt="$1"
  local current="$2"
  if [[ -n "$current" ]]; then
    printf '%s' "$current"
    return 0
  fi
  local value=""
  read -r -s -p "$prompt: " value
  printf '\n' >&2
  if [[ -z "$value" ]]; then
    echo "Empty secret value is not allowed for: $prompt" >&2
    exit 1
  fi
  printf '%s' "$value"
}

cert_sha256() {
  keytool -list -v \
    -keystore "$keystore_path" \
    -alias "$key_alias" \
    -storepass "$store_password" 2>/dev/null \
    | sed -n 's/^[[:space:]]*SHA256:[[:space:]]*//p' \
    | head -n 1 \
    | tr -d ':[:space:]' \
    | tr '[:upper:]' '[:lower:]'
}

can_read_private_key() {
  local candidate_key_password="$1"
  local tmp_dir tmp_keystore
  tmp_dir="$(mktemp -d)"
  tmp_keystore="$tmp_dir/key-read-test.p12"
  if keytool -importkeystore \
    -srckeystore "$keystore_path" \
    -srcstorepass "$store_password" \
    -srcalias "$key_alias" \
    -srckeypass "$candidate_key_password" \
    -destkeystore "$tmp_keystore" \
    -deststoretype PKCS12 \
    -deststorepass "temporary-validation-password-123" \
    -destkeypass "temporary-validation-password-123" \
    -destalias "$key_alias" \
    -noprompt >/dev/null 2>&1; then
    rm -rf "$tmp_dir"
    return 0
  fi
  rm -rf "$tmp_dir"
  return 1
}

set_gh_secret() {
  local name="$1"
  local value="$2"
  # gh secret set reads from stdin when no --body flag is supplied. Avoid
  # command-line secret exposure and avoid non-portable --body-file usage.
  printf '%s' "$value" | gh secret set "$name" --repo "$repo" >/dev/null
}

need_cmd keytool
need_cmd base64

if (( generate == 1 || upload == 1 || verify == 1 )); then
  store_password="$(prompt_secret_if_empty 'UODA_RELEASE_STORE_PASSWORD' "$store_password")"
  key_password="$(prompt_secret_if_empty 'UODA_RELEASE_KEY_PASSWORD' "${key_password:-$store_password}")"
fi

if (( generate == 1 )); then
  if [[ -e "$keystore_path" && "$force" != "1" ]]; then
    echo "Refusing to overwrite existing keystore: $keystore_path" >&2
    echo "Use --force only after confirming you do not need the existing key." >&2
    exit 1
  fi
  mkdir -p "$(dirname "$keystore_path")"
  umask 077
  # Modern keytool defaults to PKCS12, where the key password is often
  # effectively tied to the store password. Generate with the selected key
  # password, then validate and normalize below before uploading workflow secrets.
  keytool -genkeypair \
    -keystore "$keystore_path" \
    -storepass "$store_password" \
    -keypass "$key_password" \
    -alias "$key_alias" \
    -keyalg RSA \
    -keysize 4096 \
    -validity "$validity_days" \
    -dname "$dname"
fi

if [[ ! -s "$keystore_path" ]]; then
  echo "Missing release keystore: $keystore_path" >&2
  exit 1
fi

fingerprint="$(cert_sha256)"
if [[ ! "$fingerprint" =~ ^[0-9a-f]{64}$ ]]; then
  echo "Could not determine certificate SHA-256 fingerprint for alias '$key_alias'." >&2
  exit 1
fi

if ! can_read_private_key "$key_password"; then
  if [[ "$key_password" != "$store_password" ]] && can_read_private_key "$store_password"; then
    key_password="$store_password"
    echo "INFO: UODA release key password normalized to store password for Android signing compatibility."
  else
    echo "Could not read private key for alias '$key_alias' with the selected key password." >&2
    echo "If this is a PKCS12 keystore, retry with UODA_RELEASE_KEY_PASSWORD equal to UODA_RELEASE_STORE_PASSWORD." >&2
    exit 1
  fi
fi

if (( verify == 1 )); then
  echo "RESULT: UODA_RELEASE_SIGNING_LOCAL_VERIFY_DONE outcome=success repo=$repo keystore_path=$keystore_path key_alias=$key_alias cert_sha256=$fingerprint"
fi

if (( upload == 1 )); then
  need_cmd gh
  gh auth status --hostname github.com >/dev/null
  keystore_b64="$(base64 < "$keystore_path" | tr -d '\n\r')"
  set_gh_secret UODA_RELEASE_KEYSTORE_B64 "$keystore_b64"
  set_gh_secret UODA_RELEASE_STORE_PASSWORD "$store_password"
  set_gh_secret UODA_RELEASE_KEY_ALIAS "$key_alias"
  set_gh_secret UODA_RELEASE_KEY_PASSWORD "$key_password"
  set_gh_secret UODA_RELEASE_CERT_SHA256 "$fingerprint"
  unset keystore_b64 store_password key_password
  echo "RESULT: UODA_RELEASE_SIGNING_SECRETS_READY outcome=success repo=$repo key_alias=$key_alias cert_sha256=$fingerprint"
fi
