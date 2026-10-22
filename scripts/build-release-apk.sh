#!/usr/bin/env bash
set -Eeuo pipefail

ROOT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

# Credentials are supplied through environment variables; never prompt interactively.
KEYSTORE_PATH="${KEYSTORE_PATH:-$ROOT_DIR/generative-release.jks}"
if [[ "$KEYSTORE_PATH" != /* ]]; then
  KEYSTORE_PATH="$ROOT_DIR/$KEYSTORE_PATH"
fi

: "${KEYSTORE_PASSWORD:?Set KEYSTORE_PASSWORD in the environment}"
: "${KEY_ALIAS:?Set KEY_ALIAS in the environment}"
: "${KEY_PASSWORD:?Set KEY_PASSWORD in the environment}"

if [[ ! -f "$KEYSTORE_PATH" ]]; then
  echo "ERROR: Release keystore not found: $KEYSTORE_PATH" >&2
  exit 1
fi
if [[ ! -x "$ROOT_DIR/gradlew" ]]; then
  echo "ERROR: Gradle wrapper is missing or not executable: $ROOT_DIR/gradlew" >&2
  exit 1
fi
command -v keytool >/dev/null 2>&1 || {
  echo "ERROR: keytool is required (install a JDK)." >&2
  exit 1
}

export KEYSTORE_PATH KEYSTORE_PASSWORD KEY_ALIAS KEY_PASSWORD

echo "Validating keystore and alias..."
if ! keytool -list -keystore "$KEYSTORE_PATH" -alias "$KEY_ALIAS" -storepass:env KEYSTORE_PASSWORD >/dev/null 2>&1; then
  echo "ERROR: Cannot open keystore or alias '$KEY_ALIAS' is invalid. Check KEYSTORE_PASSWORD and KEY_ALIAS." >&2
  exit 1
fi

echo "Building signed release APK (one Gradle invocation)..."
"$ROOT_DIR/gradlew" :app:assembleRelease --no-daemon

APK_DIR="$ROOT_DIR/app/build/outputs/apk/release"
shopt -s nullglob
APKS=("$APK_DIR"/*.apk)
if (( ${#APKS[@]} == 0 )); then
  echo "ERROR: Build completed but no APK was found in $APK_DIR" >&2
  exit 1
fi

for apk in "${APKS[@]}"; do
  case "$(basename "$apk")" in
    *unsigned*) echo "ERROR: Gradle produced an unsigned APK: $apk" >&2; exit 1 ;;
  esac
  echo "APK: $apk"
  if command -v apksigner >/dev/null 2>&1; then
    apksigner verify --verbose "$apk"
  elif [[ -n "${ANDROID_HOME:-}" && -x "$ANDROID_HOME/build-tools/36.0.0/apksigner" ]]; then
    "$ANDROID_HOME/build-tools/36.0.0/apksigner" verify --verbose "$apk"
  elif [[ -n "${ANDROID_SDK_ROOT:-}" && -x "$ANDROID_SDK_ROOT/build-tools/36.0.0/apksigner" ]]; then
    "$ANDROID_SDK_ROOT/build-tools/36.0.0/apksigner" verify --verbose "$apk"
  else
    echo "WARNING: apksigner not found; APK was built, but signature verification was skipped." >&2
  fi
done

echo "Signed release APK build finished."
