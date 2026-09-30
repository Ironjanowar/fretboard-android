# Release checklist

What a release of this application is verified with, and what is deliberately not verified
here. `scripts/check_release.py` enforces the mechanical part of this list; the rest is
review, and the difference is stated rather than blurred.

## What the checker enforces

Run it before tagging anything:

```bash
python3 scripts/check_release.py                      # the declared version and the files
python3 scripts/check_release.py artifacts/<apk>      # plus the built APK, when there is one
python3 scripts/check_boundaries.py
python3 -m unittest discover -s scripts/tests
```

- `versionCode` is a positive integer and `versionName` is `major.minor.patch`.
- `core-release.lock.json` and this checklist exist.
- Release shrinking is only on when a device test loads the native binding through it
  (`app/src/androidTest/.../release/ReleaseNativeLoadingTest.kt`). Today shrinking is **off**
  (`isMinifyEnabled = false`), so nothing depends on keep rules, and the blanket-keep rule is
  checked only where it is in force.
- When an APK is given: its declared version matches the build file and it verifies with
  `apksigner`.
- The boundary rules (A22's other half) hold: no vendored or secret material, an immutable
  pin with a full source commit, packages matching their directories, no generated sources,
  no note table in Kotlin.

## What a release is manually verified with

- `./gradlew --offline :app:testDebugUnitTest` — the JVM suite, green.
- `./gradlew --offline :app:assembleDebug :app:assembleRelease` — both APKs, and the release
  one signed with the release certificate (`apksigner verify --print-certs`).
- `python3 scripts/prepare_core.py --from <aar>` then `--offline` — the pinned engine
  artifact installs and verifies against `core-release.lock.json`.
- The packaged APK carries what the version claims: the `.so` exports the expected UniFFI
  functions (`readelf -W --dyn-syms`) and the dex carries the expected binding names.
- The APK installs over the previously delivered build of the same signing identity.

## Running the device tests against the release variant

The instrumentation tests are not debug-only. The build files read the variant from a
property, so a release verification can point them at the release build (task `A24`):

```bash
./gradlew :app:assembleRelease
./gradlew -PtestBuildType=release :app:connectedReleaseAndroidTest
```

The default stays `debug`, which is what a developer runs. Verified without a device, which is
the only part that can be verified here: with the property, `connectedReleaseAndroidTest` is a
task of the build and without it `connectedDebugAndroidTest` is, exactly as before.

## Device verification and remaining gaps

- **Instrumented tests** pass 21/21 on the API 37 x86_64 AVD for the debug variant,
  including rotation/restore, engine snapshot storage, and the Compose UI surfaces.
  Release-variant instrumentation still requires the real release signing material.
- **`./gradlew :app:lintDebug`** cannot run offline because
  `com.android.tools.lint:lint-gradle:32.4.1` is missing from the Gradle cache.
- **Gradle dependency verification** (`gradle/verification-metadata.xml`) is **not adopted**
  in this repository. The build resolves from the local cache with `--offline`, and pinning
  the engine by SHA-256 in `core-release.lock.json` is what protects the native half. This is
  a known gap, not a passing check.
- **App Links** (`A21`) are not configured, because no web origin has been approved
  (`DEC-07`) and no `assetlinks.json` has been published. No `ACTION_VIEW` filter is
  declared, so the application is not a web-link handler.
- **Sharing is off** (`A20`): see `docs/sharing.md`. Importing is unaffected.
- **Two ABIs** are published in the required order (`arm64-v8a`, `x86_64`), and `minSdk` is 29.
