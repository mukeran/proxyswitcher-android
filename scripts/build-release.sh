#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT_DIR"

GRADLE_VERSION="8.10.2"
GRADLE_DIR="$ROOT_DIR/.gradle-dist"
GRADLE_HOME="$GRADLE_DIR/gradle-$GRADLE_VERSION"
GRADLE_BIN="$GRADLE_HOME/bin/gradle"
SDK_DIR="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-$HOME/Library/Android/sdk}}"

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

"$GRADLE_BIN" --no-daemon :app:assembleRelease

UNSIGNED_APK="$ROOT_DIR/app/build/outputs/apk/release/app-release-unsigned.apk"
SIGNED_APK="$ROOT_DIR/app/build/outputs/apk/release/app-release-signed.apk"

if [ ! -f "$UNSIGNED_APK" ]; then
  echo "Release unsigned APK not found: $UNSIGNED_APK" >&2
  exit 1
fi

BUILD_TOOLS_DIR="$(ls -d "$SDK_DIR"/build-tools/* | sort -V | tail -n 1)"
APKSIGNER="$BUILD_TOOLS_DIR/apksigner"

require_env() {
  local name="$1"
  if [ -z "${!name:-}" ]; then
    echo "Missing required env: $name" >&2
    echo "Set release signing envs before running:" >&2
    echo "  RELEASE_KEYSTORE" >&2
    echo "  RELEASE_KEY_ALIAS" >&2
    echo "  RELEASE_KEYSTORE_PASSWORD" >&2
    echo "  RELEASE_KEY_PASSWORD" >&2
    exit 1
  fi
}

require_env RELEASE_KEYSTORE
require_env RELEASE_KEY_ALIAS
require_env RELEASE_KEYSTORE_PASSWORD
require_env RELEASE_KEY_PASSWORD

KS_PATH="$RELEASE_KEYSTORE"
KS_ALIAS="$RELEASE_KEY_ALIAS"
KS_PASS="$RELEASE_KEYSTORE_PASSWORD"
KEY_PASS="$RELEASE_KEY_PASSWORD"

"$APKSIGNER" sign \
  --ks "$KS_PATH" \
  --ks-key-alias "$KS_ALIAS" \
  --ks-pass "pass:$KS_PASS" \
  --key-pass "pass:$KEY_PASS" \
  --out "$SIGNED_APK" \
  "$UNSIGNED_APK"

echo "$SIGNED_APK"
