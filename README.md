# Arcana

A Material 3 Expressive tarot reference and divination companion for Android. Built with Jetpack Compose, multi-module clean architecture, and pluggable AI backends.

## Features

- **Card Library** — All 78 cards (22 Major Arcana + 56 Minor Arcana) with upright + reversed meanings, keywords, element, astrology, numerology. Searchable and filterable.
- **Guided Spreads** — 8 spreads bundled: Daily Draw, Past/Present/Future, Situation/Action/Outcome, Mind/Body/Spirit, Horseshoe, Celtic Cross, Year Ahead, Relationship.
- **Pluggable AI Interpreter** — three backends:
  - **Rule-based** (offline, no AI) — composes a reading from canonical card meanings. Default. Works on day one.
  - **Claude API** (cloud) — streamed interpretation from Anthropic's Messages API. Requires your own API key (paste in Settings). Fully opt-in.
  - **Local LLM** (placeholder) — surface ready for an on-device model (MLC-LLM or llama.cpp). Reports Unavailable until wired.
- **Theming** — 6 hand-tuned palettes (Mystic Twilight, Midnight Ink, Forest Oracle, Moonlight, Golden Sun, Rider-Waite Classic) plus Material You dynamic colors on Android 12+.
- **Swappable decks** — deck art is a data-driven catalog. Default ships Rider-Waite-Smith (1909, public domain). Drop a folder of PNGs and register a `DeckArt` entry to add another.
- **Journal** — save readings with timestamps and notes. Re-open them later to revisit the spread layout and your notes.

## Architecture

```
app/                        — entry, navigation, theme observation
core/
  core-common/              — DispatcherProvider, Result, Hilt module
  core-domain/              — pure-Kotlin models, repository interfaces, use cases
  core-data/                — JSON catalog loaders, repository impls, DataStore settings
  core-database/            — Room entities & DAOs for the journal
  core-ui/                  — theme system, shared Compose components
feature/
  feature-library/          — encyclopedia + card detail
  feature-spreads/          — spread picker + guided reading flow
  feature-reading/          — (reserved for future expanded reading view)
  feature-journal/          — saved readings + notes
  feature-settings/         — theme, deck, AI backend config
service/
  service-ai/               — TarotInterpreter interface + 3 implementations
```

## Build prerequisites

- Android Studio Ladybug (or newer) — opens and auto-creates `gradle-wrapper.jar`
- JDK 17
- Android SDK 35

If you prefer the CLI, run `gradle wrapper --gradle-version 8.11.1` once with a system Gradle install to populate `gradle/wrapper/gradle-wrapper.jar`. After that `./gradlew assembleDebug` works.

## Card art

The Rider-Waite-Smith (1909) deck is public domain. To bundle the art:

1. Download a public-domain scan set. Wikimedia Commons has clean PNGs at:
   <https://commons.wikimedia.org/wiki/Category:Rider-Waite_tarot_deck>
2. Rename each file to match the `imageRef` field in `core/core-data/src/main/assets/cards.json`. The naming convention is:
   - `major_NN_name.png` for Major Arcana (e.g. `major_00_fool.png`)
   - `wands_NN.png`, `wands_ace.png`, `wands_page.png`, `wands_knight.png`, `wands_queen.png`, `wands_king.png`
   - same pattern for `cups`, `swords`, `pentacles`
3. Drop the renamed PNGs into `app/src/main/assets/decks/rider-waite/`.
4. Add a `back.png` (card back design) to the same folder.

Until art is bundled, the app shows a stylized text fallback for each card (the name and arcana are displayed). The Compose-rendered card back works without any asset.

## AI configuration

By default Arcana uses the **rule-based** interpreter — fully offline, no AI, no network. It composes a paragraph-by-paragraph reading from the canonical card meanings.

To enable Claude:
1. Get an API key from the Anthropic Console.
2. Settings → AI Interpreter → Backend → "Claude API".
3. Paste your key into the API key field and tap Save.
4. Generate a reading — interpretation streams back live.

To wire a local LLM later, add an Android AAR from MLC-LLM (or a llama.cpp JNI bridge) to `service/service-ai`, implement model download on first use, and replace the body of `LocalLlmInterpreter.interpret`. The interface contract won't change.

## License

Project code: TBD (your call). Card meanings text: original to this project. Rider-Waite imagery: public domain (1909).
