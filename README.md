# Danak

**Learn one small, useful thing with every scroll.**

Danak (دانَک, "a small piece of knowledge") is a native Android microlearning app in
Persian. Each vertical swipe shows one short, well-sourced idea — psychology, science,
technology, programming, economics, history, productivity and curiosities — with a
deeper read one tap away. No account, no social feed, no clutter.

Current version: **0.2.0**

## Features

- **Feed** — full-screen vertical pager; one Danak per swipe with snapping, subtle
  parallax and haptics. Title first, a 2–3 line summary, one primary action.
- **Detail** — the full explanation in short paragraphs with section headings, a key
  takeaway, the source and the photo credit.
- **Interests** — pick topics on first launch; edit them any time in Settings.
- **Saved** — bookmark Danaks and remove them with undo.
- **Settings** — light, dark or system theme. The feed and detail stay dark in every
  theme because their photography is designed for a dark background.
- **Persistence** — interests, saved items and theme survive restarts (DataStore).
- **Persian first** — RTL layout, Vazirmatn typography and Persian numerals throughout.
- **Content** — 32 Danaks across 8 categories, each linked to a Wikipedia article
  (Persian where one exists) and illustrated with a real, freely licensed photo.

## Tech stack

| Area | Choice |
|---|---|
| Language | Kotlin 2.4 |
| UI | Jetpack Compose, Material 3 |
| Navigation | Navigation Compose |
| State | ViewModel, StateFlow, Coroutines |
| Storage | DataStore Preferences |
| Images | Coil |
| Build | AGP 9.4, Gradle 9.7, version catalog |

`minSdk` 26 · `targetSdk` 36 · `compileSdk` 37

There is no backend, account system, analytics or network access: all content ships
with the app.

## Getting started

Requirements: JDK 17+ and the Android SDK with platform 37 (Android Studio installs it
automatically when the project is opened).

```bash
./gradlew assembleDebug          # build app/build/outputs/apk/debug/app-debug.apk
./gradlew installDebug           # install on a connected device
./gradlew testDebugUnitTest      # unit tests
./gradlew connectedAndroidTest   # UI tests (device or emulator required)
./gradlew lintDebug              # lint
./gradlew assembleRelease        # minified release build (unsigned)
```

## Project structure

```
app/src/main/java/ir/danak/app/
├── MainActivity.kt        entry point, splash screen, edge-to-edge
├── model/                 Danak, Category, DanakImage, ThemeMode
├── data/                  ContentPack (JSON parser), BundledContent, DanakStore (DataStore)
└── ui/
    ├── DanakApp.kt        navigation graph
    ├── DanakViewModel.kt  app state
    ├── components/        hero image, save button, source link, category chip
    ├── screens/           feed, detail, interests, saved, settings
    ├── theme/             colours, typography, shapes
    └── util/              Persian numerals, paragraphs, links

app/src/main/assets/content/  the Danaks bundled in the app (a copy of reviewed content)
├── content.json           content pack, schema v1
└── images/                one hero photo per Danak, named by id

content/                   reviewed content, published to GitHub Pages (see content/README.md)
├── sources.yml            trusted sources and their licences
├── danaks/                one Danak per file, NNNN-<id>.json
└── images/                hero photos

schema/
├── danak-v1.schema.json   Danak / content pack format: the contract with the app
└── index-v1.schema.json   published index format

tests/content/             validator tests with valid and invalid fixtures

tools/
├── danak_content.py       validates content/, builds the static site, checks the app bundle
├── check_links.py         verifies every source and photo-credit link
├── fetch_photos.py        downloads hero photos from Wikimedia Commons (CI only)
└── photos.json            the chosen photo for each Danak
```

## Continuous integration

`.github/workflows/android.yml` runs on every push:

| Job | What it does |
|---|---|
| Build, unit tests, lint | debug and R8 release builds, unit tests, lint |
| UI tests on emulator | Compose tests on API 34 at normal and 1.3× font size, with screenshots and an accessibility audit |

The debug APK, reports and screenshots are uploaded as workflow artifacts.

`.github/workflows/content.yml` runs when content, schemas or content tools change, and weekly:

| Job | What it does |
|---|---|
| Validate and build | validator tests, validates `content/`, checks the app bundle against it, builds the static site |
| Source links resolve | opens every source and photo-credit URL |
| Publish to GitHub Pages | on the default branch only, after validation passes |

`.github/workflows/photos.yml` runs only when `tools/photos.json` or the fetcher changes.

## Content

Danaks are data, not code, in the format defined by
[`schema/danak-v1.schema.json`](schema/danak-v1.schema.json). Reviewed content lives in
[`content/`](content/README.md), one file per Danak, and is published to GitHub Pages as
static files under `/v1/`. The app bundles a copy under `app/src/main/assets/content/` and
reads it at start-up; CI checks that the copy matches `content/` exactly.

### How content reaches the app

1. At start-up the app shows what it already has, from disk only: the last verified
   published set if there is one, the bundled pack otherwise. The network never delays it.
2. When the app comes to the foreground (at most every 15 minutes after a success), it
   fetches `v1/index.json` from GitHub Pages in the background, downloads only files whose
   hash it does not have, and checks each one against the index before storing it.
3. Only when every file of the new index is on disk and verified does it switch over, in
   one atomic step; the feed updates without moving the reader off the Danak they are on.
4. Any failure — offline, an HTTP error, a malformed or oversized file, a hash mismatch,
   an interrupted download — leaves the previous content in place. A Danak whose published
   version is invalid keeps its bundled version; a photo that fails keeps the bundled photo
   (or shows none).

Downloaded content lives in app-private storage (`noBackupFilesDir/content/v1`), outside
backups. The only requests the app makes are for these public files: no accounts, no
analytics, no identifiers.

```bash
pip install jsonschema pyyaml
python3 tools/danak_content.py validate          # check content/
python3 tools/danak_content.py build --out _site # build the site locally
python3 -m unittest discover -s tests/content     # validator tests
```

## Content and credits

- **Text** — every Danak is original Persian writing and links to the Wikipedia article
  it is based on.
- **Photos** — from [Wikimedia Commons](https://commons.wikimedia.org), limited to CC0,
  public domain, CC BY and CC BY-SA. Each photo's author and licence are shown in the
  app and recorded with each Danak in `content/danaks/`.
- **Font** — [Vazirmatn](https://github.com/rastikerdar/vazirmatn), SIL Open Font
  License 1.1 (`third_party/Vazirmatn-OFL.txt`).
