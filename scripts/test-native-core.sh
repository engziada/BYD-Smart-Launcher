#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
TMP_DIR="$(mktemp -d)"
trap 'rm -rf "$TMP_DIR"' EXIT
kotlinc native-core/src/main/kotlin/com/ziada/bydsmartlauncher/core/*.kt native-core/src/test/kotlin/com/ziada/bydsmartlauncher/core/*.kt -include-runtime -d "$TMP_DIR/native-tests.jar"
java -jar "$TMP_DIR/native-tests.jar"
