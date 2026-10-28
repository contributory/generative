#!/usr/bin/env bash
set -euo pipefail

mode="${1:?Use debug or release}"
case "$mode" in debug|release) ;; *) echo "Unknown build mode: $mode" >&2; exit 2 ;; esac

# PandaCI clones shallowly; Gradle versionCode needs full git history.
if [ "$(git rev-parse --is-shallow-repository)" = true ]; then
  git fetch --unshallow
fi
git config remote.origin.fetch '+refs/heads/*:refs/remotes/origin/*'
git fetch --all --tags
# Preserve the correct branch name on detached CI checkouts.
if [ -n "${CI_BRANCH:-}" ]; then
  git switch -C "$CI_BRANCH" >/dev/null
fi

export ANDROID_SDK_ROOT="${ANDROID_SDK_ROOT:-${ANDROID_HOME:?Android SDK is missing}}"
export ANDROID_HOME="$ANDROID_SDK_ROOT"
sdkmanager_bin="$ANDROID_SDK_ROOT/cmdline-tools/latest/bin/sdkmanager"
if [ ! -x "$sdkmanager_bin" ]; then
  sdkmanager_bin="$(command -v sdkmanager)"
fi
yes | "$sdkmanager_bin" --licenses >/dev/null 2>&1 || true
"$sdkmanager_bin" 'platforms;android-37.0' 'build-tools;36.0.0'
printf 'sdk.dir=%s\n' "$ANDROID_SDK_ROOT" > local.properties
java -version
chmod +x gradlew

if [ "$mode" = debug ]; then
  ./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug --no-daemon --stacktrace
  python3 scripts/name-apks.py app/build/outputs/apk/debug Generative-debug.apk
  echo 'Debug APK: app/build/outputs/apk/debug/Generative-debug.apk'
  exit 0
fi

# PandaCI secrets are environment variables, not mounted keystore files.
: "${KEYSTORE_BASE64:?Configure KEYSTORE_BASE64 in PandaCI secrets}"
: "${KEYSTORE_PASSWORD:?Configure KEYSTORE_PASSWORD in PandaCI secrets}"
: "${KEY_ALIAS:?Configure KEY_ALIAS in PandaCI secrets}"
: "${KEY_PASSWORD:?Configure KEY_PASSWORD in PandaCI secrets}"
: "${GH_TOKEN:?Configure GH_TOKEN (or GITHUB_TOKEN) in PandaCI secrets}"
umask 077
keystore_dir="$(mktemp -d)"
trap 'rm -rf "$keystore_dir"' EXIT
export KEYSTORE_PATH="$keystore_dir/release.jks"
printf '%s' "$KEYSTORE_BASE64" | base64 -d > "$KEYSTORE_PATH"
keytool -list -keystore "$KEYSTORE_PATH" -storepass:env KEYSTORE_PASSWORD -alias "$KEY_ALIAS" >/dev/null
keytool -certreq -keystore "$KEYSTORE_PATH" -storepass:env KEYSTORE_PASSWORD \
  -keypass:env KEY_PASSWORD -alias "$KEY_ALIAS" -file "$keystore_dir/check.csr" >/dev/null
if ! command -v gh >/dev/null 2>&1; then
  apt-get update -qq
  apt-get install -y gh
fi
./gradlew :app:assembleRelease --no-daemon --stacktrace
python3 scripts/name-apks.py app/build/outputs/apk/release Generative.apk
export GITHUB_RELEASE_TAG="$(python3 -c 'import json; print(json.load(open("app/build/outputs/apk/release/output-metadata.json"))["elements"][0]["versionName"])')"
bash scripts/publish-github-release.sh
echo 'Release APK and SHA-1: app/build/outputs/apk/release/'
