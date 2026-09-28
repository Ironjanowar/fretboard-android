# Fretboard (Android)

The native application shell around the frozen Fretboard engine: the musical
answers on screen are computed by the Rust core and reach Kotlin through the
generated UniFFI bindings. Kotlin carries no musical logic.

## What this first build contains

One screen. It asks the engine for two things, on the thread pool, and shows
whatever the engine answers:

```
Fretboard
Engine answered
Instrument: GUITAR
Tuning: Standard 40-45-50-55-59-64
C major: Cmaj
Notes: C, E, G
```

If the engine cannot answer, the screen says so in English and names the
reason — it never shows a plausible fake answer:

```
Fretboard
Not available
The engine could not be loaded: ...
```

## Install it

* Requires an **arm64** device with **Android 10 (API 29)** or newer.
* With a cable and `adb`: `adb install -r fretboard-0.1.0-arm64-release.apk`
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
python3 scripts/prepare_core.py --from /path/to/fretboard-engine-0.1.0.aar
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
