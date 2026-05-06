# Arcana

The most obvious vibe-coded esoteric app around. Whimsical and odd. A Material 3 Expressive tarot reference and divination companion for Android. Built with Jetpack Compose, multi-module clean architecture, and a pluggable AI interpreter.

## Features

- **Card library** — all 78 cards (22 Major Arcana + 56 Minor Arcana) with upright + reversed meanings, keywords, element, astrology, numerology. Searchable (by name, keyword, rank number, or roman numeral) and filterable by suit.
- **Spread guides** — 8 spreads bundled: Daily Draw, Past/Present/Future, Situation/Action/Outcome, Mind/Body/Spirit, Horseshoe, Celtic Cross, Year Ahead, Relationship. Tapping a spread opens a guide showing the layout, what each numbered position means, and a practice tip for laying the cards out with a physical deck.
- **Two reading modes**:
  - **Pull digitally** — shuffle, draw, optional AI interpretation, save.
  - **Log physical** — manually enter cards you drew with your real deck, set orientation per position, add notes. Saved alongside digital pulls in the journal with a kind badge.
- **Pluggable AI interpreter** — three backends:
  - **Rule-based** (offline, no AI) — composes a reading from canonical card meanings. Default. No network. Renders styled markdown output.
  - **Claude API** (cloud) — streamed interpretation from Anthropic's Messages API. Requires your own API key (paste in Settings). Fully opt-in.
  - **Local LLM** (placeholder) — surface ready for an on-device model (MLC-LLM or llama.cpp). Reports Unavailable until wired.
- **Theming** — 6 hand-tuned palettes (Mystic Twilight, Midnight Ink, Forest Oracle, Moonlight, Golden Sun, Rider-Waite Classic) plus Material You dynamic colors on Android 12+.
- **Bundled deck art** — Rider-Waite-Smith (1909), public-domain scans from Wikimedia Commons, ships with the APK (~41 MB). Deck-art system is data-driven — drop a folder of art and register a new `DeckArt` to add another deck.
- **Journal** — saved readings with timestamps, kind badge (digital vs physical), question, notes, and the rendered spread layout.

## Architecture

```
app/                        — entry, navigation, theme + LocalDeckHasArt provisioning
core/
  core-common/              — DispatcherProvider, Result, Hilt module
  core-domain/              — pure-Kotlin models, repository interfaces, use cases
  core-data/                — JSON catalog loaders (cards, spreads), repository
                              impls, DataStore-backed settings
  core-database/            — Room entities & DAOs for the journal (v2)
  core-ui/                  — theme, shared Compose components (TarotCardView,
                              SpreadBoard, MarkdownText, CardBackView)
feature/
  feature-library/          — encyclopedia + card detail
  feature-spreads/          — spread picker → overview/guide → reading flow
  feature-journal/          — saved readings + log-physical entry
  feature-settings/         — theme, deck, AI backend config
service/
  service-ai/               — TarotInterpreter interface + 3 implementations
                              (rule-based, Claude API, local-LLM stub)
```

## Building

### Mac / Linux

```bash
git clone https://github.com/PimpinPumpkin/Arcana.git
cd Arcana
echo "sdk.dir=$ANDROID_HOME" > local.properties   # or hard-code the path
./gradlew assembleRelease
```

APK lands at `app/build/outputs/apk/release/app-release.apk` (~44 MB after R8 + resource shrinking).

The `release` buildType is signed with the debug keystore for personal-install convenience — `adb install` works on any device, no Play Store keystore needed yet. Swap in a real `signingConfig` in `app/build.gradle.kts` when shipping. Plain `./gradlew assembleDebug` still works if you ever want a non-minified build for profiling.

### Windows

Plain `./gradlew assembleRelease` works on most setups. On Windows ARM with Defender enabled, the transform cache hits a file-handle race that causes spurious `Could not move temporary workspace` failures. If you see that, use the bundled recovery wrapper:

```bash
bash build-with-recovery.sh           # defaults to assembleRelease
bash build-with-recovery.sh assembleDebug   # if you want debug instead
```

It retries up to 15 times and rescues stuck temp workspaces with PowerShell between attempts. Keep it as a fallback only — it's strictly a workaround for the Defender issue.

### Prerequisites

- JDK 17 (Eclipse Temurin recommended on Windows ARM — Microsoft OpenJDK ARM has a `Files.move` NIO bug)
- Android SDK 35 (cmdline-tools, platform-tools, build-tools 35.0.0, platforms;android-35)
- Gradle 8.11.1 (the wrapper bootstraps automatically)

Android Studio Ladybug or newer handles all of this for you.

## Card art

Rider-Waite-Smith is bundled. The 78 JPEGs live at `app/src/main/assets/decks/rider-waite/` and were downloaded from Wikimedia Commons via `scripts/fetch-rider-waite.sh` — re-runnable if you want to refresh them.

To add a new deck:

1. Create `app/src/main/assets/decks/<deck-id>/` and drop image files in. Naming must match the `imageRef` field in `core/core-data/src/main/assets/cards.json` — for example `major_00_fool.jpg`, `wands_01_ace.jpg`, `wands_page.jpg`. Either `.jpg` or `.png` works.
2. Register a `DeckArt` entry in `core/core-data/src/main/java/com/arcana/core/data/repository/DeckArtCatalog.kt`.
3. The deck appears in Settings → Card deck. Switching is live; no rebuild needed beyond the asset addition.

The deck-art system is plain assets + a data catalog — there's no codegen, no bake step. The Compose-rendered card back works without any back asset.

## AI configuration

By default Arcana uses the **rule-based** interpreter — fully offline, no AI, no network. It composes a markdown-formatted reading from canonical card meanings.

To enable Claude:

1. Get an API key from the Anthropic Console.
2. Settings → AI Interpreter → Backend → "Claude API".
3. Paste your key into the API key field and tap Save.
4. Generate a reading — interpretation streams back live.

To wire a local LLM, add an Android AAR from MLC-LLM (or a llama.cpp JNI bridge) to `service/service-ai`, implement model download on first use, and replace the body of `LocalLlmInterpreter.interpret`. The `TarotInterpreter` interface contract stays the same.

The app declares the `INTERNET` permission, but only uses it when you explicitly enable the Claude backend AND provide a key. The default rule-based path does no networking.

## License

GPL-3.0. See [LICENSE](LICENSE).

Card meanings text is original to this project. Rider-Waite-Smith imagery is public domain (Pamela Colman Smith, 1909).
