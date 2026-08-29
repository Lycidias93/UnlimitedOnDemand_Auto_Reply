#!/usr/bin/env bash
set -euo pipefail

bundle="${1:-}"
if [[ -z "$bundle" ]]; then
  echo "Usage: $0 <cg-handoff-bundle.zip>" >&2
  exit 2
fi
if [[ ! -s "$bundle" ]]; then
  echo "FAIL: bundle_missing path=$bundle" >&2
  exit 2
fi
need_cmd() { command -v "$1" >/dev/null 2>&1 || { echo "Missing required command: $1" >&2; exit 127; }; }
need_cmd unzip
need_cmd sha256sum
need_cmd wc

work="$(mktemp -d)"
trap 'rm -rf "$work"' EXIT
unzip -qq "$bundle" -d "$work"

manifest_count="$(find "$work" -maxdepth 1 -type f -name BUNDLE_MANIFEST.txt | wc -l | tr -d ' ')"
if [[ "$manifest_count" != "1" ]]; then
  echo "FAIL: bundle_manifest_missing_or_duplicate count=$manifest_count" >&2
  exit 1
fi
manifest="$work/BUNDLE_MANIFEST.txt"
if LC_ALL=C grep -q $'\r' "$manifest"; then
  echo "FAIL: bundle_manifest_crlf_present" >&2
  exit 1
fi

get_field() {
  local key="$1"
  local count
  count="$(grep -Ec "^${key}=" "$manifest" || true)"
  if [[ "$count" != "1" ]]; then
    echo "FAIL: manifest_field_not_unique field=$key count=$count" >&2
    exit 1
  fi
  sed -n "s/^${key}=//p" "$manifest" | head -n 1
}

bundle_format_version="$(get_field bundle_format_version)"
if [[ "$bundle_format_version" != "1" ]]; then
  echo "FAIL: bundle_format_version_unsupported value=$bundle_format_version" >&2
  exit 1
fi

entrypoint="$(get_field entrypoint)"
entrypoint_mode="$(get_field entrypoint_mode)"
if [[ "$entrypoint_mode" != "run" && "$entrypoint_mode" != "verify" ]]; then
  echo "FAIL: manifest_entrypoint_mode_invalid value=$entrypoint_mode" >&2
  exit 1
fi
script="$work/$entrypoint"
if [[ ! -s "$script" ]]; then
  echo "FAIL: entrypoint_missing entrypoint=$entrypoint" >&2
  exit 1
fi
if LC_ALL=C grep -q $'\r' "$script"; then
  echo "FAIL: script_crlf_present entrypoint=$entrypoint" >&2
  exit 1
fi

start_count="$(grep -Fxc '# CG_HANDOFF_V1_START' "$script" || true)"
end_count="$(grep -Fxc '# CG_HANDOFF_V1_END' "$script" || true)"
if [[ "$start_count" != "1" || "$end_count" != "1" ]]; then
  echo "FAIL: metadata_block_not_unique start_count=$start_count end_count=$end_count" >&2
  exit 1
fi

run_mode_count="$(grep -Ec '^# cg_handoff_run_mode=' "$script" || true)"
if [[ "$run_mode_count" != "1" ]]; then
  echo "FAIL: metadata_run_mode_not_unique count=$run_mode_count" >&2
  exit 1
fi
run_mode="$(sed -n 's/^# cg_handoff_run_mode=//p' "$script" | head -n 1)"
if [[ "$run_mode" != "$entrypoint_mode" ]]; then
  echo "FAIL: metadata_manifest_run_mode_mismatch metadata=$run_mode manifest=$entrypoint_mode" >&2
  exit 1
fi

for field in lane scope host route_class secret_class expected_marker; do
  count="$(grep -Ec "^# cg_handoff_${field}=" "$script" || true)"
  if [[ "$count" != "1" ]]; then
    echo "FAIL: metadata_field_not_unique field=$field count=$count" >&2
    exit 1
  fi
done

member_count="$(get_field member_count)"
if [[ ! "$member_count" =~ ^[0-9]+$ || "$member_count" -lt 1 ]]; then
  echo "FAIL: manifest_member_count_invalid value=$member_count" >&2
  exit 1
fi
for i in $(seq 1 "$member_count"); do
  name="$(get_field member_${i}_name)"
  size="$(get_field member_${i}_size)"
  sha="$(get_field member_${i}_sha256)"
  path="$work/$name"
  if [[ ! -f "$path" ]]; then
    echo "FAIL: manifest_member_missing member=$name" >&2
    exit 1
  fi
  actual_size="$(wc -c < "$path" | tr -d ' ')"
  actual_sha="$(sha256sum "$path" | awk '{print $1}')"
  if [[ "$actual_size" != "$size" ]]; then
    echo "FAIL: manifest_member_size_mismatch member=$name expected=$size actual=$actual_size" >&2
    exit 1
  fi
  if [[ "$actual_sha" != "$sha" ]]; then
    echo "FAIL: manifest_member_sha_mismatch member=$name expected=$sha actual=$actual_sha" >&2
    exit 1
  fi
done

if command -v cglint >/dev/null 2>&1; then
  if ! cglint "$script"; then
    echo "FAIL: cglint_gate_failed entrypoint=$entrypoint" >&2
    exit 1
  fi
else
  echo "WARN: cglint_unavailable_nonblocking"
fi

bundle_sha="$(sha256sum "$bundle" | awk '{print $1}')"
echo "RESULT: UODA_CG_HANDOFF_BUNDLE_VALIDATE_DONE outcome=success bundle=$bundle bundle_sha256=$bundle_sha bundle_format_version=$bundle_format_version metadata_block=present cglint_gate=checked_if_available entrypoint=$entrypoint run_mode=$run_mode member_count=$member_count"
