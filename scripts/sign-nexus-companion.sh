#!/usr/bin/env bash
# Nexus Companion production APK signing. Run only on a trusted signing host.
# Never commit keystores or put passwords on the command line.
set -euo pipefail
umask 077

usage() {
  echo "Usage: bash scripts/sign-nexus-companion.sh <unsigned.apk> <signed.apk>" >&2
  echo "Load the four NEXUS_ANDROID_* signing variables from the private vault first." >&2
  exit 2
}
[[ $# == 2 ]] || usage
input="$1"
output="$2"
[[ -f "$input" && "$input" != "$output" ]] || usage
[[ ! -e "$output" ]] || { echo "Refusing to overwrite existing signed artifact" >&2; exit 2; }

: "${NEXUS_ANDROID_KEYSTORE_PATH:?Private signing keystore path required}"
: "${NEXUS_ANDROID_STORE_PASSWORD:?Private keystore password required}"
: "${NEXUS_ANDROID_KEY_ALIAS:?Private key alias required}"
: "${NEXUS_ANDROID_KEY_PASSWORD:?Private key password required}"
[[ -f "$NEXUS_ANDROID_KEYSTORE_PATH" ]] || { echo "Private keystore file missing" >&2; exit 2; }

sdk="${ANDROID_SDK_ROOT:-${ANDROID_HOME:-}}"
[[ -n "$sdk" ]] || { echo "Set ANDROID_SDK_ROOT or ANDROID_HOME" >&2; exit 2; }
build_tools="$sdk/build-tools/35.0.0"
apksigner="$build_tools/apksigner"
zipalign="$build_tools/zipalign"
aapt="$build_tools/aapt"
for tool in "$apksigner" "$zipalign" "$aapt"; do
  [[ -x "$tool" ]] || { echo "Missing Android build tool: $tool" >&2; exit 2; }
done

temp="$(mktemp -d)"
trap 'rm -rf -- "$temp"' EXIT
aligned="$temp/aligned.apk"
"$zipalign" -P 16 -f 4 "$input" "$aligned"

# env: references variable NAMES only. Their VALUES are not printed or inserted into argv.
"$apksigner" sign \
  --ks "$NEXUS_ANDROID_KEYSTORE_PATH" \
  --ks-key-alias "$NEXUS_ANDROID_KEY_ALIAS" \
  --ks-pass env:NEXUS_ANDROID_STORE_PASSWORD \
  --key-pass env:NEXUS_ANDROID_KEY_PASSWORD \
  --out "$output" "$aligned"

fail() { rm -f -- "$output"; echo "FAIL: $*" >&2; exit 1; }
verification="$("$apksigner" verify --verbose --print-certs "$output")" || fail "APK signature invalid"
"$zipalign" -c -P 16 4 "$output" >/dev/null || fail "APK ZIP alignment invalid"

# Certificate SHA-256 is public metadata, unlike the private keystore and passwords.
expected_cert="01445cd0f0f56688fba46bad32840ff85fbdee119cddbd2d3a19314d94c472d1"
actual_cert="$(printf '%s\n' "$verification" |
  sed -n 's/^[[:space:]]*Signer #1 certificate SHA-256 digest:[[:space:]]*//p' |
  head -n 1 | tr -d '[:space:]:' | tr '[:upper:]' '[:lower:]')"
[[ "$actual_cert" == "$expected_cert" ]] || fail "Unexpected production signing certificate"

metadata="$("$aapt" dump badging "$output" | sed -n '1p')" || fail "Unable to inspect APK manifest"
[[ "$metadata" == *"name='fr.nexuscompanion.android'"* ]] ||
  fail "Wrong applicationId (preview/debug package?)"
if [[ -n "${NEXUS_ANDROID_EXPECTED_VERSION_CODE:-}" ]]; then
  [[ "$metadata" == *"versionCode='${NEXUS_ANDROID_EXPECTED_VERSION_CODE}'"* ]] ||
    fail "Unexpected Android versionCode"
fi

printf 'APK SIGNED AND VERIFIED: %s\n' "$output"
printf 'Package: %s\n' "$metadata"
printf 'Certificate SHA-256: %s\n' "$actual_cert"
sha256sum "$output"
printf 'Next: verify update without uninstalling and test saved memory on device.\n'
