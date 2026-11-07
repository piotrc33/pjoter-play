#!/usr/bin/env bash
# Unit tests only (no lint, no install), optionally just some classes or methods, with each failure's message and
# the first app frame of its stack trace. For iterating on one part; scripts/check.sh before committing.
# Usage: scripts/test.sh [TestClass | TestClass.method ...]   e.g. scripts/test.sh VerticalPagesGestureTest
set -euo pipefail
cd "$(dirname "$0")/.."
filters=()
for name in "$@"; do filters+=(--tests "*.$name"); done
results=app/build/test-results/testDebugUnitTest
rm -rf "$results" # only this run's reports below
log=$(mktemp)
trap 'rm -f "$log"' EXIT
./gradlew --console=plain -q testDebugUnitTest "${filters[@]}" >"$log" 2>&1 || true
if ! ls "$results"/*.xml >/dev/null 2>&1; then
  echo "No test reports: the build failed or no test matched. Gradle's output:"
  grep -v '^\s*at ' "$log" | head -40
  exit 1
fi
python3 -I - "$results" <<'PY'
import glob, sys, xml.etree.ElementTree as ET
total = failed = 0
for path in sorted(glob.glob(sys.argv[1] + "/*.xml")):
    for case in ET.parse(path).getroot().iter("testcase"):
        total += 1
        problem = case.find("failure")
        if problem is None:
            problem = case.find("error")
        if problem is None:
            continue
        failed += 1
        print(f"FAIL {case.get('classname').rsplit('.', 1)[-1]}.{case.get('name')}")
        print("  " + (problem.get("message") or problem.get("type") or "").strip().replace("\n", "\n  "))
        frames = [l.strip() for l in (problem.text or "").splitlines() if l.strip().startswith("at pl.pjoterplay.")]
        # Where it failed, and the test's own line when that was in a helper.
        test_line = next((f for f in frames if f.startswith(f"at {case.get('classname')}.{case.get('name')}")), None)
        for frame in dict.fromkeys(frames[:1] + [test_line] if test_line else frames[:1]):
            print("  " + frame)
print(f"{'OK' if failed == 0 else 'FAILED'}: {total} tests ({failed} failed)")
sys.exit(1 if failed else 0)
PY
