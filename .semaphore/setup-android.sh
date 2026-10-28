# Sourced by the Semaphore debug and release jobs.
set -euo pipefail

# Match the JDK 21 toolchain selected by codemagic.yaml.
if ! java -version 2>&1 | head -n 1 | grep -Eq '"21([.+-]|\")'; then
  mkdir -p "$HOME/.semaphore-jdk21"
  curl --fail --silent --show-error --location \
    https://api.adoptium.net/v3/binary/latest/21/ga/linux/x64/jdk/hotspot/normal/eclipse \
    | tar -xz -C "$HOME/.semaphore-jdk21" --strip-components=1
  export JAVA_HOME="$HOME/.semaphore-jdk21"
  export PATH="$JAVA_HOME/bin:$PATH"
fi
java -version

# The Semaphore Android image provides SDK tools; install the project's versions.
export ANDROID_SDK_ROOT="${ANDROID_SDK_ROOT:-${ANDROID_HOME:-/opt/android-sdk-linux}}"
export ANDROID_HOME="$ANDROID_SDK_ROOT"
sdkmanager_bin="$ANDROID_SDK_ROOT/cmdline-tools/latest/bin/sdkmanager"
if [ ! -x "$sdkmanager_bin" ]; then
  sdkmanager_bin="$(command -v sdkmanager)"
fi
yes | "$sdkmanager_bin" --licenses >/dev/null 2>&1 || true
"$sdkmanager_bin" 'platforms;android-37.0' 'build-tools;36.0.0'
printf 'sdk.dir=%s\n' "$ANDROID_SDK_ROOT" > local.properties

# The Android versionCode and release notes depend on the complete Git history.
if [ "$(git rev-parse --is-shallow-repository)" = true ]; then
  git fetch --unshallow
fi
git config remote.origin.fetch '+refs/heads/*:refs/remotes/origin/*'
git fetch --all --tags
