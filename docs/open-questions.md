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
* the checks that need an Android runtime remain a manual release step because this project
  has no CI workflow; the dual-ABI engine now allows them to run on an x86_64 emulator.

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

## Resolved: P7 uses two ABIs

The artifact lock and application package require both ABIs in one fixed order:
`arm64-v8a`, then `x86_64`. The first remains the physical-device target and the second
enables the emulator verification path. The minimum SDK remains 29.

The AAR, its embedded metadata, `core-release.lock.json`, and `ndk.abiFilters` must agree;
`scripts/prepare_core.py` rejects a missing, reordered, or additional ABI and rejects either
missing native library.

## Resolved: instrumented tests run on API 37 x86_64

The complete device-side suite (`RotationStateTest`, `KeyProgressionUiTest`, and
`LastSessionDeviceTest`) passes 21/21 on the persistent API 37 x86_64 AVD. This closes the
P5, P6 and P7 device-evidence gate for the debug variant. Release-variant instrumentation
still requires the real release signing material.

## `:app:lintDebug` cannot run offline

**Open:** the aggregate lint task fails offline because
`com.android.tools.lint:lint-gradle:32.4.1` is not in the offline Gradle cache.

**Why:** fixing it requires network access to resolve the missing lint artifact or a reviewed
change to `gradle/verification-metadata.xml`. Espresso 3.7.0 is present and the complete
instrumented suite runs successfully.

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
