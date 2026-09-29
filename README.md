# Fretboard (Android)

The native application shell around the frozen Fretboard engine: the musical
answers on screen are computed by the Rust core and reach Kotlin through the
generated UniFFI bindings. Kotlin carries no musical logic — it moves DTOs across
the boundary and draws them.

## What this build contains (plan phase P2)

One screen, the Visualizer:

* an **instrument picker** built from the engine's own catalog — all five
  instruments in catalog order, with the engine's display names (the ukulele
  shows as *Ukulele* while the wire id stays `ukelele`);
* a **chord editor**: the twelve sharp root names the wire accepts, plus every
  quality group and label the engine sends (`qualityGroups()`), and Add;
* the **fretboard surface** drawn from `frettedSurface(state)`: one row per
  physical string (reversed, so the instrument's first string is at the bottom
  as on a real diagram), one position per column from the open string to the
  instrument's last fret, the note text of every position, the inlay markers and
  a visible nut between the open column and fret 1. It scrolls sideways, because
  25 positions do not fit a phone;
* the **occurrence cards**: one card per active chord occurrence, coloured from
  the client palette by the engine's own colour slot, showing the chord label
  **and** every note with its interval role. Tapping a card highlights that
  identity (tapping it again clears), the ✕ removes that one occurrence, and
  *Clear chords* empties the list;
* explicit English placeholders for what this build does not draw yet (the
  keyboard visualizer and the analyzer tab).

If the engine cannot answer, the screen says so in English and names the
reason — it never shows a plausible fake answer.

## Install it

* Requires an **arm64** device with **Android 10 (API 29)** or newer.
* With a cable and `adb`: `adb install -r fretboard-0.2.0-arm64-release.apk`
* Without a cable: copy the APK to the phone, tap it, and allow installing from
  that source when the system asks.

The APK is signed with the project's persistent release key, so later builds
install over it as an update. It needs no network access and none is permitted.

## Build it

The application consumes exactly one published artifact, never a sibling
checkout: the engine AAR built by the core repository, identified by version and
SHA-256 in `core-release.lock.json`.

```sh
# 1. Install the verified engine artifact into the local engine repository.
python3 scripts/prepare_core.py --from /path/to/fretboard-engine-0.2.0.aar
python3 scripts/prepare_core.py --offline        # re-verify without rebuilding

# 2. Build and test.
./gradlew :app:testDebugUnitTest :app:assembleRelease
```

Signing is required for the release variant and never falls back to debug keys:
the build reads `~/.fretboard-signing/keystore.properties` (override the
location with `FRETBOARD_SIGNING`) and fails if it is absent.

## Checks

```sh
python3 -m unittest discover -s scripts/tests -p 'test_*.py'   # the artifact contract
./gradlew :app:testDebugUnitTest                     # the app's unit tests
```

`docs/build-contract.md` records the pinned toolchain, the artifact contract and
the decisions this build depends on.
