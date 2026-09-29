# Fretboard (Android)

The native application shell around the frozen Fretboard engine: the musical
answers on screen are computed by the Rust core and reach Kotlin through the
generated UniFFI bindings. Kotlin carries no musical logic — it moves DTOs across
the boundary and draws them.

## What this build contains (plan phase P3)

Two tabs of one screen:

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
* the **Analyzer tab**: the same fretted surface, now actionable — tapping a
  position sends the engine's `ToggleNote` event, which clears the string's mark
  when it is the same fret and moves it when it is another one. *Clear notes* is
  its own event. The engine's analysis of the selection is rendered as it
  answers it: an instruction when nothing is selected, a single note, an
  interval, the identifications (slash label, `exact`/`incomplete`/`partial`,
  the note–interval pairs, the inversion and the bass) or *No chord found for
  these notes*. A tone the engine reports as missing is shown as missing, never
  filled in. The visualizer's chords are kept but never colour the analyzer;
* the **Tuning sheet** (fretted instruments): it edits a draft — one note per
  physical string, labelled `String N` down to `String 1` — and shows the
  preset the engine detects for those exact pitches. *Apply* commits the draft
  through the engine's own event; *Cancel* and dismissing the sheet discard it,
  and reopening always starts from the committed tuning. The committed page is
  never touched by an edit;
* an explicit English placeholder for what this build does not draw yet (the
  keyboard visualizer and the keyboard analyzer, phase P4).

If the engine cannot answer, the screen says so in English and names the
reason — it never shows a plausible fake answer.

## Install it

* Requires an **arm64** device with **Android 10 (API 29)** or newer.
* With a cable and `adb`: `adb install -r fretboard-0.3.0-arm64-release.apk`
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
