# Parity matrix

Which frozen oracle cases this client replays, and which it does not yet. The plan requires a
matrix with **no unmapped rows**; this is the honest inventory of the gap, measured from the
corpus itself rather than asserted.

## The corpus (measured, not estimated)

`fretboard-core`'s frozen corpus is **22,083 cases** across 25 files in
`fixtures/oracle/`. Operations and their case counts:

| Operation | Cases | File(s) |
|---|---:|---|
| `analyze_notes` | 18,671 | `identify-01..15.jsonl` |
| `change_tuning_note` | 1,229 | `tunings.jsonl` |
| `chord_notes`, `chord_label`, `notes_with_intervals`, `chord_formula`, `chord_interval_labels`, `infer_chord_mode` | 2,543 | `chords.jsonl` |
| `progression` / `progression_label` / `progression_chords` | 1,180 | `progressions.jsonl` |
| `scale_notes` / `scale_label` / `diatonic_chords` | 555 | `scales.jsonl` |
| `note_at` and the instrument/surface families | 525 | `surfaces.jsonl` |
| `page_params` (decode **and** encode) | 104 | `page-params.jsonl` |
| `page_event` | 49 | `page-events.jsonl` |
| `query_transport` | 36 | `query-transport.jsonl` |
| `analyzer_state` / `analyze_pitches` | 34 | `analyzer.jsonl` |
| `preset_tuning`, `tuning_notes`, `detect_preset`, … | 87 | `tunings.jsonl` |
| `suggest_keys` | 17 | `keys.jsonl` |
| `key_groups` | 16 | `key-groups.jsonl` |
| `suggest_multi_keys` | 15 | `multi-keys.jsonl` |

## What is pinned where

**In the core** (`fretboard-core`), the corpus is the expectation set of the domain: the
codec, the reducer, the catalogs, the analyzer, keys, progressions and the transport are all
tested against these cases, and the contract revisions record which surface they cover.

**In this client**, 229 JVM tests and 21 instrumented tests. What they pin is the *client's*
own behaviour, with the engine scripted — no musical value is computed here, so a JVM test can
only assert about the answer it was handed:

| Area | Tests |
|---|---:|
| Storage and the last session, including corruption and revision ordering | 18 |
| Boot arbitration and the incoming path (parser, source, share launcher) | 42 |
| Session holder, drafts, tuning edits, keys and progressions | 83 |
| Surfaces, geometry, palette, cards, analysis model, chord preview | 57 |
| Engine wiring and failure reporting | 6 |
| Instrumented (passing on API 37 x86_64) | 21 |

## Unmapped rows — the gap this matrix exists to show

These are the corpus operations the client does **not** replay today. `A23` is exactly the
work of closing them, with independent fixtures rather than scripted answers that echo the
code under test:

- `analyze_notes` / `analyzer_state` / `analyze_pitches` — the analyzer screens are exercised
  by `AnalysisModelTest` and `FrettedAnalyzerTest` for the *client's* rendering, never against
  the frozen corpus.
- `page_event` — the reducer's own expectations live in the core; the client asserts that it
  sends the event, not what the event must produce.
- `chord_notes` / `chord_label` / the chord families, and `progression_chords` — pinned in the
  core only.
- `scale_notes` / `diatonic_chords`, `key_groups`, `suggest_keys`, `suggest_multi_keys` — the
  panels are tested through scripted answers; the corpus is not replayed here.
- `note_at` and the surface families — `FretboardGeometryTest`, `PaletteTest` and
  `PianoGeometryTest` test the client's geometry and colour handling, not the frozen rows.
- `page_params` / `query_transport` — replayed in the **core** (`crates/domain/tests/`), which
  is the layer that owns them; the client's own round trip through `import_legacy_url` is
  pinned in the adapter's tests and in `ShareLauncherTest`.
- `change_tuning_note`, `detect_preset`, `preset_tuning` — the drafts are tested for the
  client's flow; the engine's own answers are the core's tests.

## What is not a row at all

- `FakeNativeBindings`-style calculation is not a source of expectations: a test that computes
  its own expected value from the code under test would make this matrix meaningless.
- The instrumented tests provide client-side device evidence on the API 37 x86_64 AVD. They
  still do not replace corpus replay for musical-domain expectations owned by the core.
