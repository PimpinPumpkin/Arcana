# reading-cli

Runs a reading on a desktop with the same code the app runs on a phone: the same chat formatting, grammar and sampling (`src/main/cpp/arcana-session.cpp`). It is for trying a model, or a change to the prompt, without building and installing the app.

## Build

```bash
cmake -S service/service-ai/tools/reading-cli -B /tmp/reading-cli -G Ninja
ninja -C /tmp/reading-cli reading-cli
```

The CMake and Ninja that come with the Android SDK will do (`$ANDROID_HOME/cmake/3.22.1/bin`).

## Get some readings to run

The prompts are built by the app's own code (`ReadingScript`), so the scripts are written by a test:

```bash
ARCANA_SCRIPT_DIR=/tmp/scripts ./gradlew :service:service-ai:testDebugUnitTest --tests '*ReadingScriptSamples'
```

That writes one JSON file per sample reading: a one-card draw, three-card spreads in different tones, a five-card and a ten-card spread, and the ten-card one restarted halfway.

## Run

```bash
/tmp/reading-cli/reading-cli model.gguf /tmp/scripts/past-present-future.json
```

It prints the reading as the app would show it, then how long loading, reading the prompt and writing took. Options: `--temp`, `--top-k`, `--top-p`, `--repeat-penalty`, `--seed`, `--threads`, `--batch-threads`, `--no-grammar`, `--cpu-libraries`.

## On a phone

To time a model on real hardware, build the tool with the NDK using the options in `src/main/cpp/CMakeLists.txt`, push `reading-cli`, the `.so` files beside it, a model and a script to `/data/local/tmp`, and run it from `adb shell` with `LD_LIBRARY_PATH=.`. The app writes with the phone's fast cores and reads the prompt with all of them; `--threads 2 --batch-threads 8` is that on a phone with two fast cores.

On a phone the processor library has to be named, best first, the way the app's `CpuLibraries` would: `--cpu-libraries android_armv8.2_2,android_armv8.2_1,android_armv8.0_1`. The first one the processor can run is used and printed. Giving only `android_armv8.0_1` shows what an old phone gets.

## Adding a model to the picker

Run the samples through it and read what comes out. Time a three-card reading on a mid-range phone. Then add an entry to `ModelCatalog` with the file's size, SHA-256 and a download link that names a revision.
