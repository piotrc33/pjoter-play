# Pjoter Play

The user's own app store for their Android apps: a download page plus (coming) an Android manager that installs and
updates the apps. The user vibe-codes it: Claude drives, the user reviews architecture. The user writes in Polish;
answer in Polish. Code and comments in English, UI text in Polish.

Plan with the decisions behind it: `~/.claude/plans/hejka-mam-nowe-marzenie-stateless-flute.md`.

## Layout
- `docs/` is the GitHub Pages site (served from `main`/`docs`): https://piotrc33.github.io/pjoter-play/
  - `apps.json`: the catalog. The page and the manager both read it, so a release only has to update this file.
  - `index.html`, `style.css`, `app.js`: plain static page, no build step (the user's choice: Angular is overkill here).
  - `icons/`: SVG source + 192 px PNG (the manager will show the PNGs: Android has no built-in SVG support).
    Regenerate with `rsvg-convert -w 192 -h 192 x.svg -o x.png`.
- The repo is public on purpose: app source repos (reader, ...) stay private, and a private repo's release assets
  can't be downloaded without a token. APKs are published as GitHub Releases of this repo.

## apps.json contract
Keep in sync with `docs/app.js` and the manager's parser.
- `schema`: bump on incompatible changes.
- `apps[]`: `id` (applicationId), `name`, `summary` (one line), `description`, `icon` (path relative to `docs/`),
  `minSdk` (API level; the page and the manager show "Android X lub nowszy"), `release`.
- `release`: `null` = not published yet; otherwise `versionCode`, `versionName`, `apk` (absolute URL), `sha256`
  (hex, the manager verifies the download), `size` (bytes).
- The manager is an entry too (`pl.pjoterplay.app`), so it updates itself; the page shows it as the download button
  instead of a card.

## Decisions
- Manager `minSdk` 26 (Android 8.0, ~97% of devices): per-app "install unknown apps" permission. Updates without a
  confirmation dialog only on API 31+ (`USER_ACTION_NOT_REQUIRED`); below that the system asks every time.
- One release signing key for all apps (`~/.android/paggio-release.jks`): Android refuses an update signed by another
  key, and the only way out, uninstalling, deletes the app's data.
- First app in the catalog: paggio (`~/projects/reader`, `pl.reader.app`, minSdk 34). Next: słownik, which needs a
  GitHub repo, the release key and CI first.

## Workflow
- `scripts/preview.sh`: serves `docs/` and saves phone-width light/dark screenshots to `.shots/` (Read them).
- Don't `pkill -f` a pattern that appears in the same command line: it kills the Bash tool's own shell.
