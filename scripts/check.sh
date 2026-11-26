#!/usr/bin/env bash
# Full local verification before committing: unit tests and Android Lint (warnings are errors).
set -euo pipefail
cd "$(dirname "$0")/.."
if command -v agent-lint >/dev/null 2>&1; then agent-lint; fi
./gradlew --console=plain -q testDebugUnitTest lintDebug detektDebug "$@"
echo "OK: tests pass, lint + detekt clean"
