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
