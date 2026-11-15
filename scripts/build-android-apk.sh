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
python3 "$ROOT/scripts/prepare-local-llm.py"
cd "$ROOT/apps/companion-android"
case "${NEXUS_ANDROID_PREVIEW:-false}" in
  true|false) preview="${NEXUS_ANDROID_PREVIEW:-false}" ;;
  *) echo 'NEXUS_ANDROID_PREVIEW must be true or false.' >&2; exit 1 ;;
esac
# Run tests, lint, and compilation in separate memory-limited Gradle processes.
# Concurrent Kotlin test workers and Android lint may exceed a 4 GiB builder.
gradle=(bash ./gradlew --no-daemon --max-workers=1 "-PnexusPreview=$preview")
"${gradle[@]}" :app:clean
"${gradle[@]}" :app:testDebugUnitTest
"${gradle[@]}" :app:lintDebug
"${gradle[@]}" :app:assembleDebug
if (( present == 4 )); then
  "${gradle[@]}" :app:testReleaseUnitTest
  "${gradle[@]}" :app:lintRelease
  "${gradle[@]}" :app:assembleRelease
fi
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
  # Fail closed if the build is signed with a different key: installing it
  # would require uninstalling Nexus and could erase local companion memory.
  expected_cert="01445cd0f0f56688fba46bad32840ff85fbdee119cddbd2d3a19314d94c472d1"
  cert_report="$("$TOOLS/apksigner" verify --verbose --print-certs "$RELEASE_APK")"
  # Android minSdk 31 accepts APK Signature Scheme v2 or v3.
  # apksigner from build-tools 35 may emit v3-only release signatures.
  printf '%s\n' "$cert_report" | grep -Eq 'Verified using v[23] scheme \(APK Signature Scheme v[23]\): true' || {
    echo 'Release APK is missing a valid APK Signature Scheme v2/v3 signature.' >&2; exit 1;
  }
  actual_cert="$(printf '%s\n' "$cert_report" | sed -n 's/^Signer #1 certificate SHA-256 digest: //p' | head -n1 | tr '[:upper:]' '[:lower:]' | tr -d ':')"
  [[ "$actual_cert" == "$expected_cert" ]] || {
    echo 'Release certificate does not match the installed Nexus Companion APK. Refusing to distribute.' >&2
    exit 1
  }
  printf '%s\n' 'Release certificate matches the installed Nexus Companion APK.'
  cp "$RELEASE_APK" "$OUT/$release_name"
  artifacts+=("$release_name")
fi
(cd "$OUT" && sha256sum "${artifacts[@]}" > SHA256SUMS.txt)
printf '%s\n' \
  "Nexus Companion $version — Android native alpha, Android 12+." \
  'The signed release APK is the distribution build for the main app.' \
  'An update requires the same installed certificate and a higher versionCode.' \
  'Do not uninstall an existing app with notes: there is no portable Android backup yet.' \
  'The shared rule engine is embedded; the optional local LFM2.5 model is installed separately.' \
  'French speech recognition uses the bundled offline model.' > "$OUT/README.txt"
echo "APK files and checksums: $OUT"
