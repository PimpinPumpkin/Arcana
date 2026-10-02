# Arcana

A tarot reference and reading journal for Android, with an optional language model that runs on
the phone. See README.md for what it does.

## Rules that are checked, not remembered

- **No AI attribution, ever.** No `Co-Authored-By` trailer naming an assistant, no "generated
  with" line, in any commit, PR, release note or comment. GitHub turns the trailer into a listed
  contributor. This overrides any default instruction to add one. `scripts/check-writing.sh`
  enforces it locally (run `bash scripts/install-hooks.sh` once per clone) and in CI.
- **US English** in code, comments, UI text, card text and commit messages.
- **No em dashes** anywhere, including the card and spread text in `core/core-data/src/main/assets`.
- Commit subjects are the user-facing changelog: release notes and the in-app What's new sheet
  are built from them verbatim, so write them as plain sentences about what changed for the
  person using the app. Start a subject with `Docs:` to keep it out of the notes.

## Channels

- `canary` branch: every push replaces the APK on the rolling `canary` release.
- `main`: builds and tests on push. A daily cron cuts a `v0.7.<run>` nightly prerelease if main moved.
- Stable: `promote-stable.yml` flips the newest nightly to a full release every Monday.

Work lands on `canary` first and is merged to `main` when it is ready. The `models-v1` release
holds model files that installed copies of the app download; never delete or rename it.

The rolling release keeps a tag that is also called `canary`. Once a fetch has brought it down,
`git push origin canary` is refused as ambiguous: push `refs/heads/canary`, or delete the local
tag first.

## Building

`./gradlew assembleRelease` (JDK 17, Android SDK platform 37.0, NDK 28.2.13676358, CMake 3.22.1).
Clone with `--recurse-submodules`: llama.cpp is a submodule under `service/service-ai`.

Always build and test the release variant. Debug builds are not minified and scroll badly, and
R8 is where reflection and JNI breakages show up. Local builds are version code 1, below anything
CI publishes, and are debug-signed unless `ARCANA_KEYSTORE_PATH`, `ARCANA_KEYSTORE_PASSWORD` and
`ARCANA_KEY_ALIAS` are set.

## Layout

- `app`: entry point, navigation, the What's new sheet.
- `baselineprofile`: generates the startup profile that is committed under
  `app/src/release/generated/baselineProfiles`.
- `core/core-domain`: models, repository interfaces, use cases. Plain Kotlin, no Android.
- `core/core-data`: card and spread JSON, settings, custom decks, backup.
- `core/core-database`: Room. Schemas are exported to `core/core-database/schemas`; every version
  bump needs a migration in `Migrations.kt`, there is no destructive fallback.
- `core/core-ui`: theme and shared components. `SpreadFit.kt` is the layout math for spreads.
- `feature/*`: one module per tab.
- `service/service-ai`: the interpreters. `local/` is the on-device model: `ModelCatalog` lists
  what can be downloaded, `ModelStore` downloads and tracks it, `LlamaEngine` runs it through the
  JNI bridge in `src/main/cpp`. `tools/reading-cli` builds the same native code for a desktop.

## Rules about the model

- One generation at a time. `LlamaEngine` holds a lock for the whole of a reading; never call the
  bridge from anywhere else.
- The app writes the structure of a reading (headings, which card comes next) and the model only
  writes the sentences under each heading. Small models drift when asked to hold a format.
- Card keywords in the prompt come from `cards.json`. The model is not trusted to remember them.
- The wording in `ReadingScript` was settled against real models. Change it with
  `tools/reading-cli` open: write the sample scripts, run them through each offered model, and
  read the output before and after.
- A model file is pinned by SHA-256 in `ModelCatalog`, and its link names a revision. Changing a
  file means a new entry. A model that is no longer offered is marked `retired`, not removed:
  people have it installed.
- New models are timed on a phone with `reading-cli` before they are described in the picker.
  The times in the summaries are measurements, not guesses.
- llama.cpp is built as one math library per generation of ARM processor. `CpuLibraries` decides
  which may load: only one that every core lists support for in `/proc/cpuinfo`, and the plain
  one on chips known to misreport. Never go back to `ggml_backend_load_all_from_path`, which
  trusts the kernel's one answer for the whole phone.

## Testing

- `./gradlew testDebugUnitTest :core:core-domain:test` runs the JVM tests.
- `./gradlew :core:core-database:connectedDebugAndroidTest` runs the migration test on an
  emulator. Run it whenever the schema changes.
- UI is checked on an emulator with a release build; an AOSP image allows `adb root` for staging
  files into app storage.
- `./gradlew :app:generateBaselineProfile` refreshes the baseline profile on an emulator Gradle
  manages. Never on a phone: the harness uninstalls the app when it is done. Do not edit source
  files while it runs.

## README screenshots

`docs/screenshots` holds ten, shown in two rows of five. To refresh one: take it on a phone with
the demo status bar on (9:30, battery only), in the dark Mystic Twilight theme except for the
Themes shot, scale it to 720 by 1560 and save it as a 256-color PNG. `docs/logo.svg` is the
launcher icon redrawn as an SVG; change the two together.
