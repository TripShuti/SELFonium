#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
version=1.7.12
sha=8aca8db96f1b94770f1b0d72b6dddcb1ebb8123cb3712530b08cc387b349a3d8
case "$(uname -s)/$(uname -m)" in Linux/x86_64) ;; *) echo 'This actionlint bootstrap supports Linux x86_64; use actionlint directly on other hosts.' >&2; exit 1;; esac
dir="$PWD/.tooling/actionlint"
mkdir -p "$dir"
archive="$dir/actionlint_${version}_linux_amd64.tar.gz"
if [ ! -f "$archive" ]; then
    curl --fail --location --connect-timeout 15 --max-time 120 --output "$archive" "https://github.com/rhysd/actionlint/releases/download/v${version}/actionlint_${version}_linux_amd64.tar.gz"
fi
printf '%s  %s\n' "$sha" "$archive" | sha256sum --check --status
tar -xzf "$archive" -C "$dir" actionlint
"$dir/actionlint" -shellcheck= -pyflakes= .github/workflows/*.yml
