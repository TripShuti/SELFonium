#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
java_bin="${JAVA_HOME:+$JAVA_HOME/bin/}java"
javac_bin="${JAVA_HOME:+$JAVA_HOME/bin/}javac"
test_output="$(mktemp -d)"
trap 'rm -rf "$test_output"' EXIT
"$javac_bin" --release 17 -d "$test_output" app/src/main/java/app/selfonium/core/*.java app/src/test/java/app/selfonium/core/CoreTest.java scripts/CheckJavaSyntax.java
"$java_bin" -cp "$test_output" app.selfonium.core.CoreTest
"$java_bin" -cp "$test_output" CheckJavaSyntax
python3 scripts/check-project.py
python3 scripts/test-storage.py
