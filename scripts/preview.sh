#!/usr/bin/env bash
# Screenshots of docs/ at a phone's width (412 px, Pixel 9a), light and dark, into .shots/.
set -euo pipefail
root="$(cd "$(dirname "$0")/.." && pwd)"
port=8765
shots="$root/.shots"
mkdir -p "$shots"

python3 -m http.server "$port" --directory "$root/docs" >/dev/null 2>&1 &
server=$!
trap 'kill $server' EXIT
sleep 1

shoot() { # name, extra chromium flags...
  local name=$1; shift
  # --no-sandbox: without it headless chromium on this laptop exits silently and writes nothing.
  timeout 60 chromium --headless=new --no-sandbox --disable-gpu --hide-scrollbars --window-size=412,1400 \
    --virtual-time-budget=3000 "$@" --screenshot="$shots/$name.png" "http://localhost:$port/" >/dev/null 2>&1
  echo "$shots/$name.png"
}
shoot light
shoot dark --blink-settings=preferredColorScheme=0
