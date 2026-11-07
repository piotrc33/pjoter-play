#!/usr/bin/env python3
"""Puts a new release into docs/apps.json. Run by each app's release workflow (../reader/.github/workflows/apk.yml)
after it uploaded the APK to this repo's GitHub Releases.

Usage: scripts/add-release.py <app id> <versionCode> <versionName> <apk url> <apk file>
The file gives sha256 and size, so they can't disagree with what was uploaded.
"""
import hashlib
import json
import pathlib
import sys

CATALOG = pathlib.Path(__file__).resolve().parent.parent / "docs" / "apps.json"


def main(app_id: str, version_code: str, version_name: str, apk_url: str, apk_file: str) -> None:
    catalog = json.loads(CATALOG.read_text(encoding="utf-8"))
    app = next((a for a in catalog["apps"] if a["id"] == app_id), None)
    if app is None:
        sys.exit(f"{app_id} is not in {CATALOG}: add its entry (name, description, icon...) by hand first")

    code = int(version_code)
    old = app.get("release")
    # The manager offers an update only for a higher versionCode: an equal or lower one would never reach anyone.
    if old and code <= old["versionCode"]:
        sys.exit(f"{app_id}: versionCode {code} is not above the published {old['versionCode']}")

    apk = pathlib.Path(apk_file).read_bytes()
    # Keep the fields in sync with the apps.json contract in CLAUDE.md.
    app["release"] = {
        "versionCode": code,
        "versionName": version_name,
        "apk": apk_url,
        "sha256": hashlib.sha256(apk).hexdigest(),
        "size": len(apk),
    }
    CATALOG.write_text(json.dumps(catalog, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(f"{app_id} {version_name} ({code}) in the catalog")


if __name__ == "__main__":
    if len(sys.argv) != 6:
        sys.exit(__doc__)
    main(*sys.argv[1:])
