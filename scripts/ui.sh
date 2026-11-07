#!/usr/bin/env bash
# Phone driving is shared by all the apps: ~/.local/bin/phone (run `phone` for the commands). No PKG: `ui`, `tap`
# and `shot` work over any app, the system installer's and Play Protect's dialogs too.
PKG="" exec "$HOME/.local/bin/phone" "$@"
