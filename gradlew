#!/usr/bin/env bash
# Project-local, checksum-pinned Gradle launcher (no system installation).
set -euo pipefail
project_dir="$(cd "$(dirname "$0")" && pwd)"
gradle_version=8.11.1
gradle_sha256=f397b287023acdba1e9f6fc5ea72d22dd63669d59ed4a289a29b1a76eee151c6
gradle_dir="$project_dir/.tooling/gradle-$gradle_version"
export GRADLE_USER_HOME="${GRADLE_USER_HOME:-$project_dir/.tooling/gradle-home}"
if [ ! -x "$gradle_dir/bin/gradle" ]; then
    mkdir -p "$project_dir/.tooling"
    gradle_archive="$(mktemp "$project_dir/.tooling/gradle.XXXXXX.zip")"
    trap 'rm -f "$gradle_archive"' EXIT
    curl --fail --location --connect-timeout 15 --max-time 900 --output "$gradle_archive" "https://services.gradle.org/distributions/gradle-$gradle_version-bin.zip"
    printf '%s  %s\n' "$gradle_sha256" "$gradle_archive" | sha256sum --check --status
    unzip -q "$gradle_archive" -d "$project_dir/.tooling"
    chmod +x "$gradle_dir/bin/gradle"
    rm -f "$gradle_archive"
    trap - EXIT
fi
exec "$gradle_dir/bin/gradle" -p "$project_dir" "$@"
