#!/usr/bin/env bash
# Screenshots of docs/ at a phone's width (412 px, Pixel 9a), light and dark, into .shots/.
set -euo pipefail
root="$(cd "$(dirname "$0")/.." && pwd)"
port=8765
shots="$root/.shots"
mkdir -p "$shots"

python3 -m http.server "$port" --bind 127.0.0.1 --directory "$root/docs" >/dev/null 2>&1 &
server=$!
trap 'kill $server 2>/dev/null || true' EXIT
# Up to 5 s for the server: a fixed second was sometimes too short, and chromium then shot an error page.
for _ in $(seq 50); do curl -s -o /dev/null "http://127.0.0.1:$port/" && break; sleep 0.1; done

shoot() { # name, extra chromium flags...
  local name=$1; shift
  # Removed first: chromium sometimes exits without writing, and an old screenshot would pass for the new one.
  rm -f "$shots/$name.png"
  # --no-sandbox: without it headless chromium on this laptop exits silently and writes nothing.
  timeout 60 chromium --headless=new --no-sandbox --disable-gpu --hide-scrollbars --window-size=412,1400 \
    --virtual-time-budget=3000 "$@" --screenshot="$shots/$name.png" "http://127.0.0.1:$port/" >/dev/null 2>&1 || true
  [[ -s "$shots/$name.png" ]] || { echo "no screenshot: $name (chromium failed, or the server wasn't up)" >&2; return 1; }
  echo "$shots/$name.png"
}
shoot light
shoot dark --blink-settings=preferredColorScheme=0
