# Fretboard (Android)

The native application shell around the frozen Fretboard engine: the musical
answers on screen are computed by the Rust core and reach Kotlin through the
generated UniFFI bindings. Kotlin carries no musical logic — it moves DTOs across
the boundary and draws them.

## What this build contains (plan phase P4)

Two tabs of one screen:

* an **instrument picker** built from the engine's own catalog — all five
  instruments in catalog order, with the engine's display names (the ukulele
  shows as *Ukulele* while the wire id stays `ukelele`);
* a **chord editor**: the twelve sharp root names the wire accepts, plus every
  quality group and label the engine sends (`qualityGroups()`), and Add;
* the **fretboard surface** drawn from `frettedSurface(state)`: one row per
  physical string (reversed, so the instrument's first string is at the bottom
  as on a real diagram), fixed tuning notes outside the wooden board, fret
  numbers above it, inlay markers inside it, and only chord-painted or selected
  stopped notes. The stopped frets scroll sideways because 24 do not fit a
  phone;
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
  preset the engine detects for those exact pitches. Its **preset picker** is
  the engine's own ordered catalog (`presets(instrument)`): the frozen names in
  the frozen order, nothing added and nothing filtered. *Apply* commits the
  draft through the engine's own event; *Cancel* and dismissing the sheet
  discard it, and reopening always starts from the committed tuning. The
  committed page is never touched by an edit;
* the **keyboard** (the piano): the same two tabs, drawn from the engine's own
  `keyboardSurface(state)` instead of a fretboard — one key per pitch the engine
  answers, in the engine's order, with the engine's note names and the engine's
  chord marks. On the Analyzer tab a tap routes exactly one `TogglePianoKey`
  carrying that key's own pitch, and the marks shown are the engine's committed
  keys; the same pitch class in another octave is a different key. *Clear notes*
  is the same event as on the fretboard;
* the **instrument boundary**: switching instrument sends the engine's own
  `SetInstrument` and shows exactly the page it answers. Crossing between the
  keyboard and a fretted instrument converts nothing — the selection is cleared
  by the engine, the chords and the tab are kept, and the highlight is cleared.
  The active instrument is the picker's selected chip and the header caption,
  and the keyboard's missing tuning is shown as *not applicable* rather than as
  a control that could not work.

If the engine cannot answer, the screen says so in English and names the
reason — it never shows a plausible fake answer.

## What 0.4.1 fixes (the rotation bug)

Turning the phone used to lose the whole session. The activity is destroyed and
recreated on a configuration change, and every piece of the session lived in the
composition's own `remember`, so rotating came back to an empty screen. The
session now lives in a `FretboardViewModel`, which the activity's retained
`ViewModelStore` keeps across the recreation: rotate to landscape, rotate back,
and the instrument, the tuning, the tab, the marked positions and keys, the
stored chords, the highlight, the root and quality pickers and an open tuning
draft **with its unapplied edits** are all still there. The board's scroll
position survives too — Compose's `rememberScrollState` is saveable, so it is
restored from the saved instance state rather than kept here.

Nothing about *what* computes a value moved: every value is still the engine's,
arriving through the same bindings, and Kotlin still holds no musical logic. The
manifest deliberately still declares no `android:configChanges`, so the activity
is recreated as the platform intends and resources are re-resolved.

This is not persistence: nothing is written to disk and no page-params codec is
exported. Surviving process death, URLs and saved sessions belong to the
session/URL phase.

## Install it

* Requires an **arm64** device with **Android 10 (API 29)** or newer.
* With a cable and `adb`: `adb install -r fretboard-0.4.1-arm64-release.apk`
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
python3 scripts/prepare_core.py --from /path/to/fretboard-engine-0.4.0.aar
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
./gradlew :app:connectedDebugAndroidTest             # the device suite on an attached device
```

`app/src/androidTest` holds the device-side tests, including the rotation
regression (`RotationStateTest`, which recreates the activity through
`ActivityScenario`). The complete suite passes 21/21 on the persistent API 37
x86_64 AVD with KVM acceleration.

`docs/build-contract.md` records the pinned toolchain, the artifact contract and
the decisions this build depends on.
