#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT_DIR"

# Defaults for current project setup
OP_VAULT="${OP_VAULT:-Personal}"
OP_SIGNING_ITEM="${OP_SIGNING_ITEM:-ProxySwitcher Release Signing}"
OP_KEYSTORE_DOCUMENT_ID="${OP_KEYSTORE_DOCUMENT_ID:-lhinp7vwuep6whncgqwyp6ksbm}"

TMP_KEYSTORE="${TMPDIR:-/private/tmp}/proxyswitcher-release.jks"

cleanup() {
  rm -f "$TMP_KEYSTORE"
}
trap cleanup EXIT

if ! command -v op >/dev/null 2>&1; then
  echo "1Password CLI 'op' not found." >&2
  exit 1
fi

echo "Fetching keystore from 1Password document..."
op document get "$OP_KEYSTORE_DOCUMENT_ID" --vault "$OP_VAULT" --out-file "$TMP_KEYSTORE" >/dev/null

echo "Fetching signing fields from 1Password item..."
export RELEASE_KEYSTORE="$TMP_KEYSTORE"
export RELEASE_KEY_ALIAS
export RELEASE_KEYSTORE_PASSWORD
export RELEASE_KEY_PASSWORD

RELEASE_KEY_ALIAS="$(op item get "$OP_SIGNING_ITEM" --vault "$OP_VAULT" --fields label=username --reveal)"
RELEASE_KEYSTORE_PASSWORD="$(op item get "$OP_SIGNING_ITEM" --vault "$OP_VAULT" --fields label=password --reveal)"
RELEASE_KEY_PASSWORD="$(op item get "$OP_SIGNING_ITEM" --vault "$OP_VAULT" --fields label=key_password --reveal)"

echo "Building signed release APK..."
"$ROOT_DIR/scripts/build-release.sh"

echo "Done."
