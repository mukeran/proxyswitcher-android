#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
ADB="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-$HOME/Library/Android/sdk}}/platform-tools/adb"
APK="$ROOT_DIR/app/build/outputs/apk/release/app-release-signed.apk"

if [ ! -f "$APK" ]; then
  "$ROOT_DIR/scripts/build-release-from-1password.sh"
fi

"$ADB" install --no-incremental -r "$APK"
