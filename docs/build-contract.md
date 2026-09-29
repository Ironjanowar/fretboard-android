# Build contract

The testable builds of the Android application (plan phases P1–P3).
Everything here is either pinned and verified, or explicitly out of scope.

## Pinned toolchain

| Piece | Version | How it is fixed |
| --- | --- | --- |
| Gradle | 9.8.0 | `gradle/wrapper/gradle-wrapper.properties` |
| Android Gradle plugin | 9.4.1 | `gradle/libs.versions.toml` (`agp`) |
| Kotlin | 2.4.20 | AGP's built-in Kotlin support; only the Compose compiler plugin is applied (`libs.versions.toml`, `kotlin`) |
| JDK | 21.0.2 (bytecode target 17) | `app/build.gradle.kts`, `engine/build.gradle.kts` |
| compileSdk / targetSdk | 37 | `app/build.gradle.kts` |
| minSdk | 29 | `app/build.gradle.kts` (DEC-10's floor) |
| Compose BOM | 2026.09.00 | `libs.versions.toml` (`composeBom`) |
| JNA (UniFFI Kotlin runtime) | 5.17.0 | the engine artifact's recorded dependency; `engine/build.gradle.kts` |
| ABI | `arm64-v8a` | `ndk.abiFilters` and `core-release.lock.json` |

AGP 9 applies Kotlin itself: adding `org.jetbrains.kotlin.android` fails the
build with an explicit message. The Compose compiler plugin
(`org.jetbrains.kotlin.plugin.compose`) is still applied, and the unit-test task
is per-variant (`testDebugUnitTest`).

## The engine artifact contract

The application never builds the core. It consumes one AAR:

* coordinates: `dev.ironjanowar:fretboard-engine:0.2.0`
* identity: `core-release.lock.json` (`sha256`, `source_commit`,
  `uniffi_runtime_dependency`, `abi`)
* installation: `scripts/prepare_core.py` verifies the digest and the embedded
  metadata against the lock, then installs the artifact (and a generated POM
  carrying the JNA runtime dependency) into the local Maven repository
  `engine/maven/`, which is never committed
* `--offline` re-verifies an installed payload; a tampered payload is reported

The AAR must also be a *consumable* AAR: AGP reads the `package` attribute of its
`AndroidManifest.xml` while generating the R class, and fails with a
NullPointerException when it is missing. That requirement is a finding from this
build, recorded in the core repository's `docs/open-questions.md`.

## Signing

The release variant is signed with the persistent release key, kept **outside**
this repository (DEC-08):

* keystore: `~/.fretboard-signing/release.jks`, 4096-bit RSA, alias `fretboard`
* configuration: `~/.fretboard-signing/keystore.properties` (mode 600), or the
  path in `FRETBOARD_SIGNING`
* the build **fails closed**: a missing configuration stops the build instead of
  producing an unsigned or debug-signed release

Certificate SHA-256: `98785d6b9bf00f1440506facec750b034f1caa411c72fdb96fca38af8693a949`.

## Evidence of the first build

```
$ ./gradlew :app:testDebugUnitTest :app:assembleRelease
BUILD SUCCESSFUL
$ apksigner verify --print-certs app-release.apk     # Verifies, v2 scheme, 1 signer
$ aapt2 dump badging app-release.apk
package: name='dev.ironjanowar.fretboard' versionCode='1' versionName='0.1.0'
minSdkVersion:'29'  targetSdkVersion:'37'  native-code: 'arm64-v8a'
application-label:'Fretboard'
$ sha256sum app-release.apk
dd052b729e1349442ff8d94f225c9148565ebcd54245bbdac7523bcff2ac680c
```

Unit tests: 4 run, 0 failures (`TuningTextTest`, pinning the unsigned reading of
the binding's `ByteArray` pitches).

## Out of scope for the P1 build

* No device or emulator was available, so the APK's *runtime* behaviour is not
  verified here: it is verified structurally (signature, package, ABI, native
  library, binding classes) and the user installs it to test behaviour.
* No shrinking: `isMinifyEnabled = false`, because R8 rules for JNA and the
  generated bindings need their own verification task.
* No persistence, no chord/scale pickers, no URL state: the milestone's screen is
  a single engine round-trip.

## Evidence of the P2 build (visualizer screen)

```sh
$ python3 scripts/prepare_core.py --offline
verified engine/maven/dev/ironjanowar/fretboard-engine/0.2.0/fretboard-engine-0.2.0.aar
$ ./gradlew --no-daemon :app:testDebugUnitTest :app:assembleRelease
BUILD SUCCESSFUL in 16s
$ apksigner verify --print-certs fretboard-0.2.0-arm64-release.apk
Signer #1 certificate SHA-256 digest: 98785d6b9bf00f1440506facec750b034f1caa411c72fdb96fca38af8693a949
$ aapt2 dump badging fretboard-0.2.0-arm64-release.apk
package: name='dev.ironjanowar.fretboard' versionCode='2' versionName='0.2.0'
minSdkVersion:'29'  targetSdkVersion:'37'  native-code: 'arm64-v8a'
uses-permission: name='dev.ironjanowar.fretboard.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION'
$ sha256sum fretboard-0.2.0-arm64-release.apk
4889fc63612c023dade62f1680c3237f8af05c35fc1f20dee1ee59200031c3b5
```

Unit tests: 32 run, 0 failures (`PaletteTest`, `FretboardGeometryTest`,
`CardModelTest`, `ClaimedPositionsTest`, and P1's `TuningTextTest`).

What Kotlin is allowed to decide, and is therefore what the tests pin: the
palette and its wrap (slot rem palette length, from the frozen fixture
`fixtures/oracle/surfaces.jsonl` in the core repository), the note paint
(`Overlap` with an *empty* membership list means "no chord claims this note", not
"overlap"), the surface geometry (25 columns, the open column distinct from fret
1, fret 24 inside the surface, inlay markers), and the card model (positional
note–interval pairing kept in the engine's order, slot lookup per occurrence).

What this build does **not** verify: the app was never run — the environment has
no emulator (`/dev/kvm` absent) and no device, so runtime behaviour is the user's
manual check, exactly as in P1. Instrument changes, highlight toggling and the
reducer's duplicate rules run only in the engine's own tests plus the user's
device, not in this repository's unit tests (they need the arm64 native library,
which cannot load on the build host).

## Out of scope for the P2 build

* The keyboard visualizer and the analyzer tab render an explicit English
  placeholder; the engine already answers `keyboardSurface`, the screens are P3
  and P4.
* No tuning sheet, no key/progression sheets, no URL import or persistence.
* No instrumented (`androidTest`) suite runs here: no emulator is available.
* Shrinking is still off.

## Evidence of the P3 build (fretted analyzer and tuning drafts)

```sh
$ python3 scripts/prepare_core.py --offline
verified engine/maven/dev/ironjanowar/fretboard-engine/0.3.0/fretboard-engine-0.3.0.aar
$ ./gradlew --no-daemon :app:testDebugUnitTest :app:assembleRelease
BUILD SUCCESSFUL in 13s
$ apksigner verify --print-certs fretboard-0.3.0-arm64-release.apk
Verifies, v2 scheme, 1 signer
Signer #1 certificate SHA-256 digest: 98785d6b9bf00f1440506facec750b034f1caa411c72fdb96fca38af8693a949
$ aapt2 dump badging fretboard-0.3.0-arm64-release.apk
package: name='dev.ironjanowar.fretboard' versionCode='3' versionName='0.3.0'
minSdkVersion:'29'  targetSdkVersion:'37'  native-code: 'arm64-v8a'
uses-permission: name='dev.ironjanowar.fretboard.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION'
$ sha256sum fretboard-0.3.0-arm64-release.apk
f197a9cfdafaa31729bc4154cd47b1f827b4541f200a62c782ab31a18374ea72
```

The APK carries `lib/arm64-v8a/libfretboard_mobile_ffi.so` (the Rust engine) and
its JNA runtime, and no network permission. Byte-for-byte reproducibility of
the APK is not claimed: a rebuild of the same source produces a different
archive (timestamps and signature), so the digest above identifies this
delivered file, not the source revision.

Unit tests: 77 run, 0 failures — the 45 that P3 added are `TuningDraftTest`
(12, the draft lifecycle as a pure state machine over a scripted engine port),
`SurfaceInputTest` (10, the hit grid and the 48dp floor), `FrettedAnalyzerTest`
(8, the tap routing and the committed marks) and `AnalysisModelTest` (15, the
typed-answer mapping, the three distinct analysis states and the missing-tone
rendering), on top of P2's 32.

What Kotlin is allowed to decide, and is therefore what the tests pin: which
cell a tap owns and that nothing outside the grid produces an event, the string
editor's physical order and labels, that a draft is a value the committed page
never sees until Apply, that a stale draft is refused outright, the mapping from
the engine's `AnalysisDto` (and its absence) to a rendered state, the positional
note–interval pairing (`Contract.D01`, the approved baseline zip) and the
spelling of the inversion. Every musical value — pitches, preset labels, note
names, bases, labels, missing tones — is the engine's, taken from its DTOs.

What this build does **not** verify: the app was never run — no emulator
(`/dev/kvm` absent) and no device, as in P1/P2. The reducer's own rules (a tap on
the same fret clears the mark, another fret replaces it, a preset commit, the
fixed-reference nearest-pitch resolution) live in the engine and run in its own
test suite and on the user's device; they cannot run on this host, where the
arm64 native library does not load.

## Out of scope or blocked for the P3 build

* **The preset picker.** The pinned 0.3.0 artifact exports `openTuningDraft`,
  `selectTuningPreset`, `changeTuningString`, `detectTuningPreset` and
  `tuningNotes`, but no entry point that lists an instrument's preset *names* —
  the core's own plan lands that catalog in `C21` (`catalogs()`). A picker would
  therefore have to carry a hand-written list of names, which the boundary rules
  forbid, so the sheet shows the engine's own detected preset label for the
  draft's exact pitches (its `Custom` included) and edits the strings instead.
  `DraftCoordinator.selectPreset` is implemented and unit-tested, ready to be
  wired to a picker as soon as the engine enumerates the names.
* The keyboard visualizer and the keyboard analyzer are P4; both render an
  explicit English placeholder. The piano's analyzer is still reachable through
  the instrument picker, unchanged from P2.
* `ui/surface/SurfaceSemantics.kt` and `ui/analyzer/AnalysisModel.kt` are two
  files the P3 task list did not name: the pure position labels and the pure
  answer mapping are separated from the composables for the same reason
  `CardModel.kt` was in P2 — a JVM unit test cannot reach into a Composable, and
  these two carry the decisions that must be pinned.
* No instrumented (`androidTest`) suite runs here: no emulator is available.
  `TuningSheetTest`, `FrettedTouchTest` and `AnalysisCardsTest` are the
  device-side tests the plan lists for P3 and none of them ran.
* Shrinking is still off; no persistence, no URL import and no key/progression
  sheets.

## Evidence of the P4 build (piano keyboard and instrument boundary)

```sh
$ python3 scripts/prepare_core.py --offline
verified engine/maven/dev/ironjanowar/fretboard-engine/0.4.0/fretboard-engine-0.4.0.aar
$ ./gradlew --no-daemon :app:testDebugUnitTest :app:assembleRelease
BUILD SUCCESSFUL in 15s
$ apksigner verify --print-certs fretboard-0.4.0-arm64-release.apk
Signer #1 certificate SHA-256 digest: 98785d6b9bf00f1440506facec750b034f1caa411c72fdb96fca38af8693a949
$ aapt2 dump badging fretboard-0.4.0-arm64-release.apk
package: name='dev.ironjanowar.fretboard' versionCode='4' versionName='0.4.0' compileSdkVersion='37'
minSdkVersion:'29'  targetSdkVersion:'37'  native-code: 'arm64-v8a'
uses-permission: name='dev.ironjanowar.fretboard.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION'
$ sha256sum fretboard-0.4.0-arm64-release.apk
b85b1a85a625abd00d14aec938d9622bbe37b1baf7cf45fe80cfba999dd9f10c
```

Unit tests: 109 run, 0 failures — P4 adds 32: `PianoGeometryTest` (15, the
keyboard layout and its one hit grid), `PianoTransitionsTest` (12, the tap
routing, the engine's committed keys and the instrument boundary) and
`PresetPickerTest` (5, the engine's ordered preset names, the keyboard's empty
catalog and a refusal), on top of P3's 77.

### What the P4 screens do

* **A12 — the keyboard.** On the piano the app draws the engine's
  `keyboardSurface(state)` instead of a fretboard: one half-open hit grid shared
  by the drawing, the pointer input and the accessibility bounds. The keys are
  the engine's own keys in the engine's own order, the notes are the engine's,
  the marks are the engine's `memberships`/`fill`, and a tap routes exactly one
  `TogglePianoKey` carrying the tapped key's own pitch. Everything scrolls
  sideways; the black keys are uniformly scaled to a 48dp minimum width.
* **A13 — the instrument boundary and the preset picker.** Switching instrument
  sends one `SetInstrument` and returns exactly the page the engine answered —
  the client assembles no page and carries no selection across the
  fretted/piano boundary. The active instrument is the picker's selected chip
  and the header caption. The tuning sheet's preset picker now reads the
  engine's `presets(instrument)`: the frozen names in the frozen order, an empty
  catalog (the keyboard) shown as *not applicable*, and a refusal shown as a
  refusal. The keyboard's missing tuning is likewise shown as not applicable
  rather than as a control that could not work.

### What Kotlin is allowed to decide, and is therefore what the tests pin

The keyboard's **layout**: which keys are drawn white or black, where each key
sits and how big it is, the half-open ownership of the hit grid, black-over-white
priority, and the spoken label. The **preset picker's wrap**, and the **switch
event** (one `SetInstrument`, nothing else).

The black/white classification is read from the engine's own note spelling — the
domain's `note.rs` produces sharp-only names — and never from pitch arithmetic,
so Kotlin performs no note math anywhere. The pinned web proportions are scaled
by an exact factor of `2.4` (black 20 → 48dp), and the web's own black offsets
(`0.62/0.81/0.58/0.71/0.86`) are pinned literally by the tests.

### Honest limits of this build

* The design asks the engine for keyboard key metadata (its contract's
  `KeyboardKey` names a white/black kind, a lower-white anchor and an
  octave-qualified label). The pinned 0.4.0 `KeyboardKeyDto` carries the pitch,
  the note, the memberships and the fill and **nothing else**, so the physical
  layout (key colour, position) is a client constant — the same class of
  constant as the fretboard's inlay frets — and the accessible label names the
  engine's note with the engine's own pitch number (`Key C#, pitch 49`) rather
  than computing an octave. This is a boundary note for the core, not a musical
  answer computed here.
* The app was never run: no emulator (`/dev/kvm` absent) and no device, exactly
  as in P1–P3. The reducer's own rules (a piano tap adds or removes, an
  out-of-range pitch is refused, crossing the boundary clears the selection and
  keeps the chords and the tab) live in the engine and run in its own test suite
  and on the user's device; they cannot run on this host, where the arm64 native
  library does not load. The plan's P4 instrumented tests (`PianoVisualizerTest`,
  `PianoAnalyzerTouchTest`, `InstrumentBoundaryTest`) did not run.
* Shrinking is still off; no key/progression sheets, no persistence and no URL
  import.

## Evidence of the 0.4.1 build (the rotation fix)

The user reported that turning the phone to landscape and back to portrait lost
the whole session — *es como si se perdieran todos los filtros*.

```sh
$ python3 scripts/prepare_core.py --offline
verified engine/maven/dev/ironjanowar/fretboard-engine/0.4.0/fretboard-engine-0.4.0.aar
$ ./gradlew --no-daemon :app:testDebugUnitTest :app:assembleRelease
BUILD SUCCESSFUL in 22s
$ ./gradlew --no-daemon :app:compileDebugAndroidTestKotlin
BUILD SUCCESSFUL
$ apksigner verify --print-certs fretboard-0.4.1-arm64-release.apk
Verifies, v2 scheme, 1 signer
Signer #1 certificate SHA-256 digest: 98785d6b9bf00f1440506facec750b034f1caa411c72fdb96fca38af8693a949
$ aapt2 dump badging fretboard-0.4.1-arm64-release.apk
package: name='dev.ironjanowar.fretboard' versionCode='5' versionName='0.4.1' compileSdkVersion='37'
minSdkVersion:'29'  targetSdkVersion:'37'  native-code: 'arm64-v8a'
$ sha256sum fretboard-0.4.1-arm64-release.apk
f1af03db8c6f65373e6db43324ebe3f01fa2b7226cbe2184073d2d457821380d   # 24 314 696 bytes
```

As in P2 and P3, byte-for-byte reproducibility is not claimed: a rebuild of the
same source produces a different archive (timestamps and signature), so the
digest identifies the delivered file, not the source revision.

The 0.4.1 APK carries `lib/arm64-v8a/libfretboard_mobile_ffi.so`, its JNA
runtime and the same release certificate as 0.4.0, so it installs over a 0.4.0
installation as an update. The engine artifact stays pinned at 0.4.0; only the
application's own `versionCode`/`versionName` moved.

### The bug, exactly

`AndroidManifest.xml` declares no `android:configChanges`, so a rotation destroys
and recreates `MainActivity`. `MainActivity.onCreate` called `setContent {
FretboardApp() }` and there was no `ViewModel` anywhere: `ui/FretboardApp.kt`
kept the whole session in plain composition memory (`view`, `error`, `busy`,
`draft`, `presets`, `root`, `quality`), so a configuration change re-ran the
composition with every `remember` back at its initial value.

### What the fix pins

* **Where the state lives.** `ui/SessionState.kt` holds `SessionState` and
  `SessionStateHolder`, free of Android and Compose, and
  `FretboardViewModel` owns one; the activity's retained `ViewModelStore` keeps
  it across the recreation, and `MainActivity` passes it into
  `FretboardApp(fretboard)` as a required parameter. The screen keeps no session
  state of its own — every value it draws is read from the holder.
* **One engine round trip per transition.** `start()` is idempotent while a
  session is loaded, because the composition calls it again after every
  rotation: re-asking would replace the user's session with a fresh one, which is
  the same bug wearing a different hat.
* **Engine only.** `session/SessionEngine.kt` is the port the holder asks through;
  its production implementation `BindingSessionEngine` is the session
  coordinator's own calls and nothing else. No musical value is computed in
  Kotlin, and no second music path was added.
* **The draft is separate from the committed page.** An open draft, its edits and
  the engine's preset list live in the holder beside the committed page; a
  rotation changes neither the edits nor the page.
* **The scroll position.** Compose's `rememberScrollState` is backed by
  `rememberSaveable`, so the board's own scroll survives the recreation through
  the saved instance state. This was confirmed by reading the pinned
  `androidx.compose.foundation` source rather than assumed — in
  `foundation-android-1.12.1`, `Scroll.kt`:

  ```kotlin
  @Composable
  fun rememberScrollState(initial: Int = 0): ScrollState {
      return rememberSaveable(saver = ScrollState.Saver) { ScrollState(initial = initial) }
  }
  ```

  which is why no scroll offset was moved into the holder.
* **No `android:configChanges`.** The activity is still recreated, so resources
  are re-resolved as the platform intends; the fix is where the state lives, not
  a suppression of the recreation.

Unit tests: 124 run, 0 failures — 109 before, plus P5's 15 in
`SessionStateHolderTest` (the engine asked once and the session read back, the
marks/chords/highlight/tab held, the piano's keys, a draft surviving with its
edits, an edit never touching the committed page, an apply and a refusal, a stale
draft dropped, the pickers coming back, the quality default, and a refused action
keeping the session). Per class: `SessionStateHolderTest` 15, `PianoGeometryTest`
15, `AnalysisModelTest` 15, `PianoTransitionsTest` 12, `TuningDraftTest` 12,
`SurfaceInputTest` 10, `PaletteTest` 9, `FrettedAnalyzerTest` 8,
`FretboardGeometryTest` 8, `CardModelTest` 7, `PresetPickerTest` 5,
`TuningTextTest` 4, `ClaimedPositionsTest` 4.

### The regression test, and what it proves

`SessionStateHolderTest` is the strongest JVM-level regression available, and it
was run against the unfixed code to prove it: with the production changes stashed
(`git stash push -u -- app/src/main/java/dev/ironjanowar/fretboard/`) and only the
new test left in place,

```
> Task :app:compileDebugUnitTestKotlin FAILED
e: .../SessionStateHolderTest.kt:16:42 Unresolved reference 'SessionEngine'.
e: .../SessionStateHolderTest.kt:123:36 Unresolved reference 'SessionEngine'.
e: .../SessionStateHolderTest.kt:198:9 Unresolved reference 'SessionStateHolder'.
BUILD FAILED in 8s
```

and with the same unfixed tree and the new test moved aside, the baseline was
green: **109 tests, 0 failures**.

That failure is a *compile* failure: the fix introduces the seam the test needs
(`SessionStateHolder`, `SessionEngine`), and before the fix there was no JVM-level
way to reach the session at all — it lived inside a private composable. So it pins
the holder's contract and the two rules the fix depends on (the session is held by
an object outside the composition, and a later screen reads it back instead of
asking the engine again); it does **not** rotate anything. A real rotation needs a
device, which is what the instrumented test is for. This is stated plainly rather
than dressed up: no JVM test here would have failed against the old code for a
behavioural reason.

### Honest limits of this build

* **The instrumented rotation test did not run.**
  `app/src/androidTest/.../RotationStateTest.kt` recreates the activity through
  `ActivityScenario.recreate()` with a session built through the engine (an
  instrument switch there and back, a stored chord, a marked position, a marked
  piano key, an open tuning draft with an unapplied edit) and asserts every part
  of it afterwards. There is no emulator here (`/dev/kvm` absent) and no device,
  so the arm64 engine cannot be loaded: the suite **compiles**
  (`:app:compileDebugAndroidTestKotlin`) and was **not run**. It must be run where
  a device exists — as must the P3/P4 device tests (`TuningSheetTest`,
  `FrettedTouchTest`, `AnalysisCardsTest`, `PianoVisualizerTest`,
  `PianoAnalyzerTouchTest`, `InstrumentBoundaryTest`).
* **The physical rotation is the user's manual check.** No test here turns a
  phone. The manual list: rotate to landscape, rotate back, and confirm the
  instrument, tuning, tab, marks, chords, highlight, pickers and an open draft
  with edits are all still there.
* **The device suite needed dependencies.** `app/src/androidTest` is a new source
  set, so it needs `androidx.test:core`, `androidx.test.ext:junit`,
  `androidx.test:runner` and `androidx.compose.ui.test:ui-test-junit4` (versions
  in `gradle/libs.versions.toml`) plus `testInstrumentationRunner`. They are
  `androidTestImplementation`-only and cannot reach the APK. No production
  dependency was added: `ViewModel`, `viewModels()` and `viewModelScope` all
  arrive transitively through `androidx.activity`.
* `createAndroidComposeRule` is used from `androidx.compose.ui.test.junit4` and
  the compiler warns it is deprecated in favour of the `...junit4.v2` rule, which
  queues compositions on a `StandardTestDispatcher`. That migration changes
  execution timing, so it belongs with the first device run of this suite, not
  with a compile-only change here.
* Shrinking is still off, and there is still no persistence and no URL import: a
  rotation is survived, a process death is not.
