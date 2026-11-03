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
mkdir -p "$ROOT/apk-dist"
cp "$ROOT/apps/companion-android/app/build/outputs/apk/debug/app-debug.apk" "$ROOT/apk-dist/nexus-companion-debug.apk"
sha256sum "$ROOT/apk-dist/nexus-companion-debug.apk" > "$ROOT/apk-dist/SHA256SUMS.txt"
printf '%s\n' 'APK Android de développement (non signé pour publication Play Store).' 'Android 12 minimum. Installez uniquement si vous faites confiance au dépôt source.' > "$ROOT/apk-dist/README.txt"
