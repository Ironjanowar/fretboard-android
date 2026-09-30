# Open questions

Decisions and blockers that shape the remaining work. Each entry says **what** is
open, **why it cannot be decided here**, and **what it blocks**. Nothing in this file
is a guess that has been implemented; where a task could proceed without the answer,
it did, and says so.

## Resolved: DEC-07 — the canonical web origin is `https://cuwano.gramos.me/`

The origin was the one thing A20 could not guess: a link this application emits is only
meaningful against the origin its web app actually serves. The user confirmed it, and it was
verified from this machine before being written down — `200` on the page route, `200` on the
legacy query the emitting codec produces, and `200` on a percent-encoded sharp
(`?chords=C%23maj`), which is the spelling a raw `#` would break.

With it, `ShareConfig.approvedBase` carries the origin, sharing is on, the note that explained
its absence is gone (it was driven by `unavailableReason`) and A20's remaining halves — the
chooser, the copy-link path and the P6 gate — are unblocked. What is *not* settled by this is
`A21`: the App Link association still needs a published `assetlinks.json` for this application
id, and a device to check `adb shell pm get-app-links`; until then no `ACTION_VIEW` filter is
declared, so the application is not a web-link handler.

## Decided: there is no CI workflow, by the user's own decision

`A22` in the plan lists `.github/workflows/android.yml` among its files and describes what
CI would run. The user's decision is explicit and overrides it: **this project does not have
a CI workflow at all**, so that file is not written, not kept and not to be rebuilt by a
later session that finds the plan item and assumes it is owed.

What that leaves in its place, and why nothing is lost:

* the checks live in scripts a person runs, and `docs/release-checklist.md` is the list:
  `scripts/check_boundaries.py`, `scripts/check_release.py` and
  `python3 -m unittest discover -s scripts/tests`;
* the release path is the one this repository actually uses — a signed APK built and verified
  on the machine that holds the signing material, which a PR-triggered workflow could never
  do anyway (it must not receive signing secrets);
* the checks that need a device were never going to run in CI here either: the engine
  publishes only `arm64-v8a` (`DEC-10`), so an x86_64 runner's emulator cannot load it.

The rest of `A22` stands: the two guardrails, their RED cases and the checklist are merged.

## DEC-07 — the canonical production web origin is unknown

**Open:** the exact HTTPS origin and path the application must accept for URL import
and emit for sharing.

**Why:** it is not in the plan, not in this repository, and the user has confirmed it
is not known yet ("no lo sé"). The plan forbids inventing one: *"Require exact approved
HTTPS origin/path for release sharing. Do not ship an example hostname."*

**Blocks:**

* **A20** (confirmed-origin share and old-web round trips) — its phase gate cannot be
  closed without it. Only the native fixture tests with the reserved
  `fretboard.example` host can be written, and they must not become a shipped default.
* the **strict** import policy (`UrlPolicyDto`: scheme, host, path, byte cap) that
  `import_url` takes. The engine compares nothing itself; it is configured by the
  caller, so there is no policy to supply until the origin is approved.

**Does not block:** **A19**, which is the *tolerant legacy* import (paste and
`ACTION_SEND` text), where an unknown or unsupported origin is a refusal with an
English sentence rather than a configured allowlist decision.

## P7 needs a decision on ABIs

**Open:** the plan's P7 gate asks for the artifact and the APK to be checked for both
ABIs (`arm64-v8a` and `x86_64`, the latter for emulators). This project ships, pins and
delivers **`arm64-v8a` only** (DEC-10, `minSdk 29`).

**Why:** the pinned toolchain and the delivered artifacts were approved with one ABI;
adding `x86_64` is a real change to the AAR build, the artifact metadata and the lock,
not a checkbox.

**Blocks:** the P7 acceptance record, which must either cite an approved
single-ABI limitation or gain the second ABI.

## Instrumented tests have never run

**Open:** every device-side test in this repository is written and compiled but
**not executed**: `RotationStateTest`, `KeyProgressionUiTest`,
`LastSessionDeviceTest`, and whatever A19+ adds.

**Why:** this environment has no device and no emulator (`/dev/kvm` is absent), and
the engine is `arm64-v8a` only, so a host JVM cannot load it either.

**Consequence:** the P5, P6 and P7 gates that ask for device evidence are open. An
APK has been delivered for a manual pass at each step, and device-only defects have
been found that way (the keys panel's self-recursive port, the edge-to-edge overlap),
which is exactly the class of defect no host test here can see.

## `:app:lintDebug` cannot run offline

**Open:** the aggregate lint task fails offline because the pre-existing
`androidTest` dependency `androidx.compose.ui:ui-test-junit4` pulls
`androidx.test.espresso:3.5.0`, which is not in the offline Gradle cache.

**Why:** it predates this work; fixing it means either network access to resolve the
missing artifacts or a reviewed change to `gradle/verification-metadata.xml`.

**Not blocked:** `:app:lintAnalyzeDebug` (main sources only) runs and is clean.

## Resolved: `page_params` is `true` in API revision 7

Revision 6 left the flag `false` because **no page-params entry point was exported**
— the URL transport reached that codec only internally, through `import_url` — and
setting it would have been a claim without a surface behind it.

Revision 7 exports the tolerant legacy reader (`import_legacy_url`), which *is* that
reader over the boundary, so the flag is now `true` and this question is closed rather
than carried into the P7 audit.


## Environment drift recorded, not fixed

`scripts/build_aar.sh` in the core repository defaults `JNA_JAR` to
`${REPO_ROOT}/../../../tools/maven/jna-5.17.0.jar`, which resolves outside the
checkout (the plan's layout has one more directory level). The artifact was built by
passing `JNA_JAR` explicitly. Where the pinned jar lives is an environment fact, so
the default was left alone and reported instead.
