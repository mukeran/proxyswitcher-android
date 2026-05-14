#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
ADB="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-$HOME/Library/Android/sdk}}/platform-tools/adb"
APK="$ROOT_DIR/app/build/outputs/apk/debug/app-debug.apk"
COMPONENT="codes.var.tweak.proxyswitcher/.ProxyTileService"
REINSTALL=0
RESTART_SYSTEMUI=0

usage() {
  cat <<EOF
Usage: $(basename "$0") [--reinstall] [--restart-systemui] [--component <spec>]

Options:
  --reinstall         Reinstall debug APK before refreshing tile cache
  --restart-systemui  Force-stop com.android.systemui between remove/add
  --component <spec>  Tile component spec (default: $COMPONENT)
  -h, --help          Show this help
EOF
}

while [ $# -gt 0 ]; do
  case "$1" in
    --reinstall)
      REINSTALL=1
      ;;
    --restart-systemui)
      RESTART_SYSTEMUI=1
      ;;
    --component)
      shift
      COMPONENT="${1:-}"
      if [ -z "$COMPONENT" ]; then
        echo "error: --component requires a value" >&2
        exit 1
      fi
      ;;
    -h|--help)
      usage
      exit 0
      ;;
    *)
      echo "error: unknown argument: $1" >&2
      usage
      exit 1
      ;;
  esac
  shift
done

"$ADB" wait-for-device

if [ "$REINSTALL" -eq 1 ]; then
  if [ ! -f "$APK" ]; then
    "$ROOT_DIR/scripts/build-debug.sh" >/dev/null
  fi
  "$ADB" install --no-incremental -r "$APK"
fi

"$ADB" shell cmd statusbar remove-tile "$COMPONENT" || true

if [ "$RESTART_SYSTEMUI" -eq 1 ]; then
  "$ADB" shell am force-stop com.android.systemui || true
fi

"$ADB" shell cmd statusbar add-tile "$COMPONENT"

echo "Tile cache refreshed: $COMPONENT"
