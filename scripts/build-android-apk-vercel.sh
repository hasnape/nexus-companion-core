#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
WORK="${TMPDIR:-/tmp}/nexus-android-build"
mkdir -p "$WORK/jdk" "$WORK/sdk/cmdline-tools" "$WORK/gradle"
curl -fLsS --retry 3 https://api.adoptium.net/v3/binary/latest/17/ga/linux/x64/jdk/hotspot/normal/eclipse -o "$WORK/jdk.tar.gz"
tar -xzf "$WORK/jdk.tar.gz" -C "$WORK/jdk" --strip-components=1
curl -fLsS --retry 3 https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip -o "$WORK/cli.zip"
unzip -qo "$WORK/cli.zip" -d "$WORK/sdk/cmdline-tools"
mv "$WORK/sdk/cmdline-tools/cmdline-tools" "$WORK/sdk/cmdline-tools/latest"
curl -fLsS --retry 3 https://services.gradle.org/distributions/gradle-8.10.2-bin.zip -o "$WORK/gradle.zip"
unzip -qo "$WORK/gradle.zip" -d "$WORK/gradle"
export JAVA_HOME="$WORK/jdk" ANDROID_HOME="$WORK/sdk" PATH="$WORK/jdk/bin:$PATH"
yes | "$WORK/sdk/cmdline-tools/latest/bin/sdkmanager" --sdk_root="$WORK/sdk" --licenses >/dev/null 2>&1 || true
"$WORK/sdk/cmdline-tools/latest/bin/sdkmanager" --sdk_root="$WORK/sdk" 'platforms;android-35' 'build-tools;35.0.0' 'platform-tools' >/dev/null
"$WORK/gradle/gradle-8.10.2/bin/gradle" -p "$ROOT/apps/companion-android" --no-daemon --max-workers=2 :app:testDebugUnitTest :app:assembleDebug
# Release signing requires all four protected environment secrets.
present=0
for name in NEXUS_ANDROID_KEYSTORE_BASE64 NEXUS_ANDROID_STORE_PASSWORD NEXUS_ANDROID_KEY_ALIAS NEXUS_ANDROID_KEY_PASSWORD; do
  if [[ -n "${!name:-}" ]]; then present=$((present + 1)); fi
done
if (( present != 0 && present != 4 )); then echo "Incomplete release signing configuration" >&2; exit 1; fi
if (( present == 4 )); then
  umask 077
  export NEXUS_ANDROID_KEYSTORE_PATH="$WORK/release-signing.jks"
  trap 'rm -f "$NEXUS_ANDROID_KEYSTORE_PATH"' EXIT
  printf "%s" "$NEXUS_ANDROID_KEYSTORE_BASE64" | base64 --decode > "$NEXUS_ANDROID_KEYSTORE_PATH"
  "$WORK/jdk/bin/keytool" -list -keystore "$NEXUS_ANDROID_KEYSTORE_PATH" -storepass "$NEXUS_ANDROID_STORE_PASSWORD" -alias "$NEXUS_ANDROID_KEY_ALIAS" >/dev/null
  "$WORK/gradle/gradle-8.10.2/bin/gradle" -p "$ROOT/apps/companion-android" --no-daemon --max-workers=2 :app:assembleRelease
  "$WORK/sdk/build-tools/35.0.0/apksigner" verify --verbose "$ROOT/apps/companion-android/app/build/outputs/apk/release/app-release.apk" >/dev/null
fi
mkdir -p "$ROOT/apk-dist"
cp "$ROOT/apps/companion-android/app/build/outputs/apk/debug/app-debug.apk" "$ROOT/apk-dist/nexus-companion-debug.apk"
if (( present == 4 )); then cp "$ROOT/apps/companion-android/app/build/outputs/apk/release/app-release.apk" "$ROOT/apk-dist/nexus-companion-v0.2.0-release.apk"; fi
(cd "$ROOT/apk-dist" && sha256sum ./*.apk > SHA256SUMS.txt)
printf '%s\n' 'APK Android de développement (non signé pour publication Play Store).' 'Android 12 minimum. Installez uniquement si vous faites confiance au dépôt source.' > "$ROOT/apk-dist/README.txt"
