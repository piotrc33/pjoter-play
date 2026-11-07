#!/usr/bin/env bash
# Phone driving for checks: `ui` lists "x y<TAB>label" of visible elements (any app, the system installer's dialogs
# too), `tap <label>` taps the first exact match for 80 ms (a finger's tap), `shot <file>` saves a screenshot.
set -euo pipefail
ADB="${ADB:-$HOME/Android/Sdk/platform-tools/adb}"
elements() {
  "$ADB" shell uiautomator dump /sdcard/ui.xml >/dev/null
  "$ADB" exec-out cat /sdcard/ui.xml | python3 -I -c '
import sys, re, xml.etree.ElementTree as ET
for n in ET.fromstring(sys.stdin.read()).iter("node"):
    label = n.get("text") or n.get("content-desc")
    if not label: continue
    label = " ".join(label.split())  # non-breaking spaces (Polish system texts use them after "z", "w")
    x1, y1, x2, y2 = map(int, re.findall(r"\d+", n.get("bounds")))
    print(f"{(x1+x2)//2} {(y1+y2)//2}\t{label}")'
}
case "${1:-}" in
  ui) elements ;;
  tap)
    pos=$(elements | awk -F'\t' -v l="$2" '$2 == l { print $1; exit }')
    [[ -n "$pos" ]] || { echo "not on screen: $2" >&2; exit 3; }
    read -r x y <<<"$pos"; "$ADB" shell input swipe "$x" "$y" "$x" "$y" 80 ;;
  shot) "$ADB" exec-out screencap -p > "$2"; echo "$2" ;;
  *) sed -n '2,3p' "$0"; exit 1 ;;
esac
