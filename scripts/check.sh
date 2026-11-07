#!/usr/bin/env bash
# Full local verification before committing: unit tests and Android Lint (warnings are errors).
set -euo pipefail
cd "$(dirname "$0")/.."
./gradlew --console=plain -q testDebugUnitTest lintDebug "$@"
echo "OK: tests pass, lint clean"
