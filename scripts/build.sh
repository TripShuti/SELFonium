#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
if [ -z "${JAVA_HOME:-}" ] && [ -x /usr/lib/jvm/java-21-openjdk/bin/java ]; then
    export JAVA_HOME=/usr/lib/jvm/java-21-openjdk
fi
sdk_dir="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}"
if [ -z "$sdk_dir" ]; then
    for candidate in "$PWD/.tooling/android-sdk" "$HOME/Android/Sdk" /opt/android-sdk; do
        if [ -f "$candidate/platforms/android-35/android.jar" ]; then sdk_dir="$candidate"; break; fi
    done
fi
if [ -z "$sdk_dir" ] || [ ! -f "$sdk_dir/platforms/android-35/android.jar" ]; then
    echo 'Android SDK Platform 35 is missing. See README.md → Збірка.' >&2
    exit 3
fi
export ANDROID_HOME="$sdk_dir"
bash scripts/test-core.sh
bash gradlew --no-daemon :app:assembleDebug :app:lintDebug
python3 scripts/test-providers.py
echo "APK: $PWD/app/build/outputs/apk/debug/app-debug.apk"
