# Arcana

The most obvious vibe-coded esoteric app around. Whimsical and odd. A tarot reference and reading journal for Android, with an optional language model that writes readings on the phone.

## What it does

- **Card library.** All 78 cards with upright and reversed meanings, keywords, element, astrology and numerology. Search by name, keyword, number or roman numeral, and filter by suit.
- **Spreads.** Eight are built in: Daily Draw, three three-card spreads, Horseshoe, Celtic Cross, Relationship and Year Ahead. Each has a guide to its positions, and there is an editor for spreads of your own. Cards are drawn as large as the screen allows, with each position named under its card.
- **Readings.** Pull cards in the app, or log a reading you did with a real deck. Either one can be saved to the journal with its question and your notes.
- **Interpretation, three ways.**
  - The cards' own meanings, position by position. Built in, offline, and what the app does by default.
  - A language model on the phone. One download of 0.7 to 1.3 GB. After that it needs no connection and nothing you ask leaves the phone.
  - Claude, with your own Anthropic API key.
- **Decks.** Rider-Waite-Smith (1909, public domain) is bundled. Import your own from a folder or ZIP of images, or set cards one at a time.
- **Themes.** Six palettes, each in light and dark, or your wallpaper's colors.
- **Backup.** Export readings and custom spreads to a file and bring them in on another phone.

Android 8.0 and later. The on-device model needs a 64-bit ARM phone; everything else runs anywhere.

## Install

Builds are on the [releases page](https://github.com/PimpinPumpkin/Arcana/releases).

| Channel | What it is |
| --- | --- |
| Stable | The newest nightly, promoted once a week. |
| Nightly | Built once a day from `main`, when `main` has changed. Marked prerelease. |
| Canary | Rebuilt on every push to the `canary` branch. For testing. |

All three are the same app signed with the same key, on one rising version line, so moving between them is an ordinary update.

## The on-device model

Three models are offered. Sizes are downloads. Times are for a three-card reading on a Pixel 4a 5G (2020, mid-range) with the model load included; newer phones are quicker.

| Picker name | Model | Size | Three cards |
| --- | --- | --- | --- |
| Quick | LFM2.5 1.2B, Liquid AI, LFM Open License | 731 MB | 23 s |
| Balanced | Gemma 3 1B, Google, Gemma Terms of Use | 806 MB | 33 s |
| Thorough | Qwen3.5 2B, Alibaba, Apache 2.0 | 1.3 GB | 41 s |

Any other GGUF chat model works too: Settings has a row for a file you already have.

A reading is run as a short conversation, one card per turn. The app writes the headings and decides which card comes next; the model writes two or three sentences under each, then a summary. Each card's keywords are given to the model from the app's own card data, and a grammar keeps every reply to whole sentences. Small models drift when asked to hold a format across a whole reading, and this is what stopped it.

The model runs through [llama.cpp](https://github.com/ggml-org/llama.cpp), built from source as a submodule. `service/service-ai/tools/reading-cli` runs the same code on a desktop, which is how a new model gets tried before it goes in the picker.

## Building

```bash
git clone --recurse-submodules https://github.com/PimpinPumpkin/Arcana.git
cd Arcana
./gradlew assembleRelease
```

Needs JDK 17 and the Android SDK with platform 37, NDK 28.2.13676358 and CMake 3.22.1. If you cloned without `--recurse-submodules`, run `git submodule update --init --recursive`.

The APK lands at `app/build/outputs/apk/release/app-release.apk`, about 34 MB. It is signed with the debug key unless `ARCANA_KEYSTORE_PATH`, `ARCANA_KEYSTORE_PASSWORD` and `ARCANA_KEY_ALIAS` are set. Build the release variant even for everyday use: debug builds are not minified, and scroll badly.

```bash
./gradlew testDebugUnitTest :core:core-domain:test        # JVM tests
./gradlew :core:core-database:connectedDebugAndroidTest   # database migrations, on an emulator
```

## Layout

```
app/                    entry point, navigation, What's new
baselineprofile/        records the startup profile the release build carries
core/
  core-common/          dispatchers and shared plumbing
  core-domain/          models, repository interfaces, use cases. Plain Kotlin.
  core-data/            card and spread JSON, settings, custom decks, backup
  core-database/        Room: the journal and custom spreads, with migrations
  core-ui/              theme and shared components, spread layout math
feature/
  feature-library/      card grid and card pages
  feature-spreads/      spread list, guide, editor, and the reading itself
  feature-journal/      saved readings, logging a physical reading
  feature-settings/     themes, decks, models, backup
service/
  service-ai/           the three interpreters, and the on-device model
```

## Card art

The 78 Rider-Waite-Smith scans are in `app/src/main/assets/decks/rider-waite/` as WebP. They came from Wikimedia Commons; `scripts/fetch-rider-waite.sh` fetches and encodes them again.

## License

GPL-3.0. See [LICENSE](LICENSE).

The card meanings are original to this project. The Rider-Waite-Smith images are in the public domain (Pamela Colman Smith, 1909). The models are downloaded from their publishers under the terms named above and are not part of this repository.
