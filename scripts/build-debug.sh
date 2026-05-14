#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT_DIR"

GRADLE_VERSION="8.10.2"
GRADLE_DIR="$ROOT_DIR/.gradle-dist"
GRADLE_HOME="$GRADLE_DIR/gradle-$GRADLE_VERSION"
GRADLE_BIN="$GRADLE_HOME/bin/gradle"
SDK_DIR="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-$HOME/Library/Android/sdk}}"
if [ -z "${ANDROID_NDK:-}" ] && [ -z "${ANDROID_NDK_HOME:-}" ] && [ -d "$SDK_DIR/ndk" ]; then
  ANDROID_NDK="$(ls -d "$SDK_DIR"/ndk/* 2>/dev/null | sort -V | tail -n 1)"
  export ANDROID_NDK
fi

cat > "$ROOT_DIR/local.properties" <<EOF
sdk.dir=$SDK_DIR
EOF

if [ ! -x "$GRADLE_BIN" ]; then
  mkdir -p "$GRADLE_DIR"
  ZIP_PATH="$GRADLE_DIR/gradle-$GRADLE_VERSION-bin.zip"
  if [ ! -f "$ZIP_PATH" ]; then
    curl -fL "https://services.gradle.org/distributions/gradle-$GRADLE_VERSION-bin.zip" -o "$ZIP_PATH"
  fi
  rm -rf "$GRADLE_HOME"
  unzip -q "$ZIP_PATH" -d "$GRADLE_DIR"
fi

"$GRADLE_BIN" --no-daemon :app:assembleDebug
echo "$ROOT_DIR/app/build/outputs/apk/debug/app-debug.apk"
