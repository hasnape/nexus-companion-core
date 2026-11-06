#!/usr/bin/env bash
# Optional Linux bootstrap. Local Android Studio/SDK builds use build-android-apk.sh.
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
WORK="${TMPDIR:-/tmp}/nexus-android-build"
mkdir -p "$WORK"
present=0
for name in NEXUS_ANDROID_KEYSTORE_BASE64 NEXUS_ANDROID_STORE_PASSWORD NEXUS_ANDROID_KEY_ALIAS NEXUS_ANDROID_KEY_PASSWORD; do
  if [[ -n "${!name:-}" ]]; then present=$((present + 1)); fi
done
if (( present != 0 && present != 4 )); then echo 'Incomplete release signing configuration.' >&2; exit 1; fi
# Pin and verify the bootstrap JDK; reuse it for subsequent builds.
JDK_NAME='jdk-17.0.21+9'
JDK_SHA='8b9b611d917e3dfd8419d3566b5949226f69b052fed7e109c1368add1dfe8711'
if [[ ! -x "$WORK/$JDK_NAME/bin/javac" ]]; then
  curl -fLsS --retry 3 'https://github.com/adoptium/temurin17-binaries/releases/download/jdk-17.0.21%2B9/OpenJDK17U-jdk_x64_linux_hotspot_17.0.21_9.tar.gz' -o "$WORK/jdk.tar.gz"
  printf '%s  %s\n' "$JDK_SHA" "$WORK/jdk.tar.gz" | sha256sum -c -
  tar -xzf "$WORK/jdk.tar.gz" -C "$WORK"
fi
export JAVA_HOME="$WORK/$JDK_NAME"
export PATH="$JAVA_HOME/bin:$PATH"
export ANDROID_HOME="$WORK/sdk"
if [[ ! -x "$ANDROID_HOME/cmdline-tools/19.0/bin/sdkmanager" ]]; then
  curl -fLsS --retry 3 'https://dl.google.com/android/repository/commandlinetools-linux-13114758_latest.zip' -o "$WORK/cli.zip"
  printf '%s  %s\n' '7ec965280a073311c339e571cd5de778b9975026cfcbe79f2b1cdcb1e15317ee' "$WORK/cli.zip" | sha256sum -c -
  unpack="$(mktemp -d "$WORK/cli.XXXXXX")"
  unzip -qo "$WORK/cli.zip" -d "$unpack"
  mkdir -p "$ANDROID_HOME/cmdline-tools"
  mv "$unpack/cmdline-tools" "$ANDROID_HOME/cmdline-tools/19.0"
  rmdir "$unpack"
fi
# yes may receive SIGPIPE when sdkmanager finishes reading; check sdkmanager itself.
set +o pipefail
yes | "$ANDROID_HOME/cmdline-tools/19.0/bin/sdkmanager" --sdk_root="$ANDROID_HOME" --licenses >/dev/null
license_status=${PIPESTATUS[1]}
set -o pipefail
(( license_status == 0 )) || exit "$license_status"
"$ANDROID_HOME/cmdline-tools/19.0/bin/sdkmanager" --sdk_root="$ANDROID_HOME" 'platforms;android-35' 'build-tools;35.0.0' 'platform-tools'
if (( present == 4 )); then
  umask 077
  NEXUS_ANDROID_KEYSTORE_PATH="$(mktemp "$WORK/release-signing.XXXXXX.jks")"
  export NEXUS_ANDROID_KEYSTORE_PATH
  trap 'rm -f "$NEXUS_ANDROID_KEYSTORE_PATH"' EXIT
  printf '%s' "$NEXUS_ANDROID_KEYSTORE_BASE64" | base64 --decode > "$NEXUS_ANDROID_KEYSTORE_PATH"
fi
bash "$ROOT/scripts/build-android-apk.sh"
