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
- `app/`: the Android manager (Kotlin + Compose, same Gradle/library versions as ../reader). `catalog/` downloads and
  parses apps.json, `AppStatus.kt` decides what each app's row says and its button, `ui/` draws it. `install/`
  downloads the APK (checked against sha256/size while it arrives) and commits a `PackageInstaller` session; the
  result comes back through `InstallReceiver` -> `InstallResults` -> `AppsViewModel`.
  `updates/UpdateCheck.kt`: a daily WorkManager check (release build only, so updates aren't announced twice) that
  notifies about installed apps with a newer release, each release once. The debug build is
  `pl.pjoterplay.app.debug`, next to the release build friends install.
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

## Releasing an app
- In the app's repo: `git tag v1.2.3 && git push origin v1.2.3`. Its workflow (first one: ../reader/.github/workflows/apk.yml)
  builds the signed APK with versionCode from the tag (major*10000 + minor*100 + patch), uploads it here as release
  `<name>-v1.2.3`, then runs `scripts/add-release.py` and pushes `docs/apps.json`. Pages redeploys in ~30 s.
- The app's repo needs the secret `PJOTER_PLAY_TOKEN`: a fine-grained token with Contents read/write on this repo only
  (releases and the catalog push). App repos are private, so their own `GITHUB_TOKEN` can't write here.
- The manager itself: `git tag v1.2.3 && git push origin v1.2.3` here; `.github/workflows/release.yml` (the same steps,
  this repo's own token, secrets RELEASE_KEYSTORE_BASE64/PASSWORD with pedefka's key) publishes `pjoter-play-v1.2.3`.
  Installed release managers show an update card on top of the list (`AppsState.managerUpdate`; never in the debug
  build, whose package differs). The workflow pushes the catalog to `main`, so pull before working on `docs/`.
- A new app needs its entry (name, texts, icon, minSdk, `release: null`) added here by hand first; the script only
  fills `release`. With the third app's workflow, extract the shared steps into a reusable workflow in this repo.

## Decisions
- Manager `minSdk` 26 (Android 8.0, ~97% of devices): per-app "install unknown apps" permission. Updates without a
  confirmation dialog only on API 31+ (`USER_ACTION_NOT_REQUIRED`); below that the system asks every time.
- One release signing key for all apps (`~/.android/paggio-release.jks`): Android refuses an update signed by another
  key, and the only way out, uninstalling, deletes the app's data.
- First app in the catalog: pedefka, "paggio" until 2026-11 (`~/projects/reader`, repo `piotrc33/pedefka`,
  `pl.reader.app`, minSdk 34). Second: definka (`~/projects/slownik`, repo `piotrc33/definka`, `pl.slownik.app`,
  minSdk 34); its workflow downloads the 58 MB dictionary DB from the definka repo's release `db`, as the DB is
  built locally and not in git.

## Workflow
- `scripts/test.sh [TestClass ...]` unit tests (~20 s warm); `scripts/quick.sh` tests + install + start on the phone;
  `scripts/check.sh` tests + lint, run by the pre-commit hook (`git config core.hooksPath .githooks` once per clone).
- `CatalogTest` parses the real `docs/apps.json` and checks its icons exist: a catalog edit is verified by `test.sh`.
- `scripts/ui.sh ui|tap <label>|shot <file>`: drives the phone, the system installer's and Play Protect's dialogs too
  (they are other apps, so `tap` works in any of them).
- What a first install shows (Android 16, Pixel): the installer's "Wymagane uprawnienia" -> Ustawienia -> "Zezwalaj z
  tego źródła" (back, by the up arrow or the edge gesture, lands in the manager, not the installer: the session ends as
  Cancelled, tap Zainstaluj again; adb's KEYCODE_BACK does nothing there, swipe from x=2 instead) ->
  "Zainstalować tę aplikację?" -> Play Protect's "Zalecane skanowanie aplikacji": "Więcej szczegółów", then the link
  "Zainstaluj bez skanowania" (not an accessibility node of its own: tap below the text, ~540,1580). The page's
  instructions (docs/index.html) say the same: keep them in sync.
- Self-update tried 2026-11-07: a local release 0.0.5 (adb-installed) -> 0.1.0 from the card: one "Zaktualizować tę
  aplikację?" and Play Protect, as the installer of record was the shell; now it is the manager itself.
- Release paggio (`pl.reader.app` 0.2.0, since renamed pedefka) is on the phone, installed by the debug manager
  (installerPackageName `pl.pjoterplay.app.debug`): the release manager is another package, so its first update of
  pedefka will ask once.
- Update check on demand (debug build): `adb shell am force-stop pl.pjoterplay.app.debug` then
  `adb shell am start -n pl.pjoterplay.app.debug/pl.pjoterplay.app.MainActivity --ez check_updates true`; result in
  `adb logcat -s UpdateCheck`. It also sees the release manager (`pl.pjoterplay.app`) as an app with updates.
- `scripts/preview.sh`: serves `docs/` and saves phone-width light/dark screenshots to `.shots/` (Read them).
- Don't `pkill -f` a pattern that appears in the same command line: it kills the Bash tool's own shell.
