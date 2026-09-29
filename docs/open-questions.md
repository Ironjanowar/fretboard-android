# Open questions

Decisions and blockers that shape the remaining work. Each entry says **what** is
open, **why it cannot be decided here**, and **what it blocks**. Nothing in this file
is a guess that has been implemented; where a task could proceed without the answer,
it did, and says so.

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

## `page_params` is left `false` in API revision 6

**Open:** revision 6 sets `snapshot: true` because C21 exports the snapshot codecs,
but leaves `page_params: false`, because no page-params *entry point*
(`decode_page_params`/`encode_page_params`) is exported — the URL transport reaches
that codec internally through `import_url`.

**Why:** whether reachability through `import_url` should set the flag is a contract
decision, not an implementation detail.

**For:** the P7 audit (`C22`), which owns the contract's final state.

## Environment drift recorded, not fixed

`scripts/build_aar.sh` in the core repository defaults `JNA_JAR` to
`${REPO_ROOT}/../../../tools/maven/jna-5.17.0.jar`, which resolves outside the
checkout (the plan's layout has one more directory level). The artifact was built by
passing `JNA_JAR` explicitly. Where the pinned jar lives is an environment fact, so
the default was left alone and reported instead.
