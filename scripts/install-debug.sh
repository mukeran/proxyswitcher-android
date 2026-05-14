#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
ADB="${ANDROID_HOME:-/Users/bytedance/Library/Android/sdk}/platform-tools/adb"
APK="$ROOT_DIR/app/build/outputs/apk/debug/app-debug.apk"

if [ ! -f "$APK" ]; then
  "$ROOT_DIR/scripts/build-debug.sh"
fi

"$ADB" install --no-incremental -r "$APK"
