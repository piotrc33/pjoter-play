#!/usr/bin/env bash
# Fast iteration: unit tests, then install the debug build on the attached phone and start it. No lint:
# scripts/check.sh before committing. Usage: scripts/quick.sh   (ANDROID_SERIAL when several devices are attached)
set -euo pipefail
cd "$(dirname "$0")/.."
ADB="${ADB:-$HOME/Android/Sdk/platform-tools/adb}"
if "$ADB" get-state >/dev/null 2>&1; then
  ./gradlew --console=plain -q testDebugUnitTest installDebug "$@"
  "$ADB" shell am start -W -n pl.pjoterplay.app.debug/pl.pjoterplay.app.MainActivity >/dev/null
  echo "OK: tests, installed and started"
else
  ./gradlew --console=plain -q testDebugUnitTest assembleDebug "$@"
  echo "OK: tests, built (no phone attached)"
fi
