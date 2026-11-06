#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SDK="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}"
[[ -n "$SDK" ]] || { echo 'Set ANDROID_HOME to your Android SDK directory.' >&2; exit 1; }
TOOLS="$SDK/build-tools/35.0.0"
[[ -x "$TOOLS/apksigner" && -x "$TOOLS/aapt" ]] || { echo 'Install Android SDK 35 and build-tools 35.0.0.' >&2; exit 1; }
present=0
for name in NEXUS_ANDROID_KEYSTORE_PATH NEXUS_ANDROID_STORE_PASSWORD NEXUS_ANDROID_KEY_ALIAS NEXUS_ANDROID_KEY_PASSWORD; do
  if [[ -n "${!name:-}" ]]; then present=$((present + 1)); fi
done
if (( present != 0 && present != 4 )); then echo 'Incomplete release signing configuration.' >&2; exit 1; fi
if (( present == 4 )); then
  [[ -f "$NEXUS_ANDROID_KEYSTORE_PATH" ]] || { echo 'Release keystore not found.' >&2; exit 1; }
  keytool -list -keystore "$NEXUS_ANDROID_KEYSTORE_PATH" -storepass:env NEXUS_ANDROID_STORE_PASSWORD -alias "$NEXUS_ANDROID_KEY_ALIAS" >/dev/null
fi
cd "$ROOT/apps/companion-android"
tasks=(:app:clean :app:testDebugUnitTest :app:lintDebug :app:assembleDebug)
if (( present == 4 )); then tasks+=(:app:testReleaseUnitTest :app:lintRelease :app:assembleRelease); fi
case "${NEXUS_ANDROID_PREVIEW:-false}" in
  true|false) preview="${NEXUS_ANDROID_PREVIEW:-false}" ;;
  *) echo 'NEXUS_ANDROID_PREVIEW must be true or false.' >&2; exit 1 ;;
esac
bash ./gradlew --no-daemon --max-workers=2 "-PnexusPreview=$preview" "${tasks[@]}"
DEBUG_APK="$PWD/app/build/outputs/apk/debug/app-debug.apk"
version="$("$TOOLS/aapt" dump badging "$DEBUG_APK" | sed -n "s/^package:.*versionName='\([^']*\)'.*/\1/p")"
[[ "$version" =~ ^[a-zA-Z0-9._-]+$ ]] || { echo 'Invalid APK version name.' >&2; exit 1; }
OUT="$ROOT/apk-dist/$version"
mkdir -p "$OUT"
debug_name="nexus-companion-v$version-debug.apk"
cp "$DEBUG_APK" "$OUT/$debug_name"
artifacts=("$debug_name")
if (( present == 4 )); then
  release_name="nexus-companion-v$version-release.apk"
  RELEASE_APK="$PWD/app/build/outputs/apk/release/app-release.apk"
  "$TOOLS/apksigner" verify --verbose --print-certs "$RELEASE_APK"
  cp "$RELEASE_APK" "$OUT/$release_name"
  artifacts+=("$release_name")
fi
(cd "$OUT" && sha256sum "${artifacts[@]}" > SHA256SUMS.txt)
printf '%s\n' \
  "Nexus Companion $version — Android native alpha, Android 12+." \
  'Install the release APK for owner-key-signed alpha testing.' \
  'An update requires the same installed certificate and a higher versionCode.' \
  'Do not uninstall an existing app with notes: there is no portable Android backup yet.' \
  'The shared Nexus rule engine is embedded locally; this is not a general-purpose LLM.' \
  'This build is not device-certified and does not include a bundled ASR model or full cognitive engine.' > "$OUT/README.txt"
echo "APK files and checksums: $OUT"
