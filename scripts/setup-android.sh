#!/usr/bin/env bash
# Run in the user's terminal. Installs only into this project's .tooling directory.
set -euo pipefail
cd "$(dirname "$0")/.."
if [ -z "${JAVA_HOME:-}" ] && [ -x /usr/lib/jvm/java-21-openjdk/bin/java ]; then
    export JAVA_HOME=/usr/lib/jvm/java-21-openjdk
fi
if [ -n "${JAVA_HOME:-}" ]; then export PATH="$JAVA_HOME/bin:$PATH"; fi
command -v java >/dev/null || { echo 'Install JDK 17 or 21 first.' >&2; exit 1; }
command -v python3 >/dev/null
export ANDROID_HOME="$PWD/.tooling/android-sdk"
export ANDROID_SDK_ROOT="$ANDROID_HOME"
python3 scripts/setup-sdk.py
sdk_manager="$ANDROID_HOME/cmdline-tools/12.0/bin/sdkmanager"
echo 'Review the Android SDK licenses in the following prompts.'
"$sdk_manager" --sdk_root="$ANDROID_HOME" --licenses
"$sdk_manager" --sdk_root="$ANDROID_HOME" 'platforms;android-35' 'build-tools;35.0.0' 'platform-tools'
bash scripts/build.sh
