#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
SDK_DIR="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-$HOME/Library/Android/sdk}}"
PLATFORM="$SDK_DIR/platforms/android-36/android.jar"
BUILD_TOOLS="$SDK_DIR/build-tools/36.0.0"
OUT_DIR="$ROOT_DIR/build/offline"
APK_DIR="$ROOT_DIR/build/outputs/apk/debug"
KEYSTORE="$ROOT_DIR/build/debug.keystore"
LIBSU_AAR="$ROOT_DIR/libs/libsu-core-6.0.0.aar"
LIBSU_CLASSES="$OUT_DIR/libsu-core-classes.jar"

rm -rf "$OUT_DIR"
mkdir -p "$OUT_DIR/res" "$OUT_DIR/gen" "$OUT_DIR/classes" "$OUT_DIR/dex" "$APK_DIR"

if [ ! -f "$LIBSU_AAR" ]; then
  echo "Missing $LIBSU_AAR"
  echo "Download it from https://jitpack.io/com/github/topjohnwu/libsu/core/6.0.0/core-6.0.0.aar"
  exit 1
fi

unzip -q -p "$LIBSU_AAR" classes.jar > "$LIBSU_CLASSES"

"$BUILD_TOOLS/aapt2" compile --dir "$ROOT_DIR/src/main/res" -o "$OUT_DIR/res/resources.zip"
"$BUILD_TOOLS/aapt2" link \
  -I "$PLATFORM" \
  --manifest "$ROOT_DIR/AndroidManifest.xml" \
  --java "$OUT_DIR/gen" \
  --min-sdk-version 24 \
  --target-sdk-version 36 \
  --version-code 1 \
  --version-name 0.1.0 \
  -o "$OUT_DIR/proxyswitcher-unsigned.apk" \
  "$OUT_DIR/res/resources.zip"

find "$ROOT_DIR/src/main/java" "$OUT_DIR/gen" -name '*.java' > "$OUT_DIR/sources.list"
javac -encoding UTF-8 -source 1.8 -target 1.8 \
  -classpath "$PLATFORM:$LIBSU_CLASSES" \
  -d "$OUT_DIR/classes" \
  @"$OUT_DIR/sources.list"

jar cf "$OUT_DIR/classes.jar" -C "$OUT_DIR/classes" .

"$BUILD_TOOLS/d8" \
  --lib "$PLATFORM" \
  --output "$OUT_DIR/dex" \
  "$OUT_DIR/classes.jar" \
  "$LIBSU_CLASSES"

cp "$OUT_DIR/proxyswitcher-unsigned.apk" "$OUT_DIR/proxyswitcher-with-dex.apk"
(cd "$OUT_DIR/dex" && zip -q -r "$OUT_DIR/proxyswitcher-with-dex.apk" classes.dex)
"$BUILD_TOOLS/zipalign" -f -p 4 "$OUT_DIR/proxyswitcher-with-dex.apk" "$OUT_DIR/proxyswitcher-aligned.apk"

if [ ! -f "$KEYSTORE" ]; then
  keytool -genkeypair \
    -keystore "$KEYSTORE" \
    -storepass android \
    -keypass android \
    -alias androiddebugkey \
    -keyalg RSA \
    -keysize 2048 \
    -validity 10000 \
    -dname "CN=Android Debug,O=Android,C=US"
fi

"$BUILD_TOOLS/apksigner" sign \
  --ks "$KEYSTORE" \
  --ks-pass pass:android \
  --key-pass pass:android \
  --out "$APK_DIR/proxyswitcher-debug.apk" \
  "$OUT_DIR/proxyswitcher-aligned.apk"

"$BUILD_TOOLS/apksigner" verify "$APK_DIR/proxyswitcher-debug.apk"
echo "$APK_DIR/proxyswitcher-debug.apk"
