package dev.ironjanowar.fretboard.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import dev.ironjanowar.fretboard.core.ChordDto
import dev.ironjanowar.fretboard.core.ChordModeDto
import dev.ironjanowar.fretboard.core.InstrumentDefinitionDto
import dev.ironjanowar.fretboard.core.InstrumentDto
import dev.ironjanowar.fretboard.core.InstrumentKindDto
import dev.ironjanowar.fretboard.core.KeyRowDto
import dev.ironjanowar.fretboard.core.KeySuggestionDto
import dev.ironjanowar.fretboard.core.MultiKeyGroupDto
import dev.ironjanowar.fretboard.core.ProgressionDto
import dev.ironjanowar.fretboard.core.ProgressionGroupDto
import dev.ironjanowar.fretboard.core.QualityDto
import dev.ironjanowar.fretboard.core.QualityGroupDto
import dev.ironjanowar.fretboard.session.EvaluationState
import dev.ironjanowar.fretboard.session.KeyDraft
import dev.ironjanowar.fretboard.session.ProgressionCatalogState
import dev.ironjanowar.fretboard.session.ProgressionDraft
import dev.ironjanowar.fretboard.ui.controls.ChordEditor
import dev.ironjanowar.fretboard.ui.controls.InstrumentPicker
import dev.ironjanowar.fretboard.ui.keys.KeySheet
import dev.ironjanowar.fretboard.ui.keys.KeySuggestionsPanel
import dev.ironjanowar.fretboard.ui.keys.MultiKeySuggestionsPanel
import dev.ironjanowar.fretboard.ui.progressions.ProgressionSheet
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/**
 * The P5 surfaces, rendered from scripted engine values.
 *
 * A14, A15 and A16's rendering rules are pinned here without an engine: every
 * row, catalog entry and chord below is a scripted DTO, so an assertion about
 * what is on screen is an assertion about the composable's own behaviour — that
 * it renders the engine's rows in the engine's order, keeps a non-modal row an
 * independent card, expands the collapsed groups' other modes behind one shared
 * toggle, shows a keyless group and the full multi-key membership, and keeps
 * pending, empty, failed and Retry distinct.
 *
 * **It has not been run.** The build environment has no emulator (`/dev/kvm` is
 * absent) and no device, so this suite is written and compiled and left for a
 * machine with a device, exactly like the plan's `TuningSheetTest`,
 * `FrettedTouchTest` and `AnalysisCardsTest`. The plan names four androidTest
 * files for P5 (`KeyProgressionUiTest`, `RelativeModesTest`,
 * `MultiKeyMembershipTest`, `EvaluationStateUiTest`); they are consolidated into
 * this one file because they share the same scripted fixtures and the same
 * device requirement, and each section below names the plan file it covers.
 */
class KeyProgressionUiTest {

    @get:Rule
    val compose = createComposeRule()

    private fun chord(root: String, quality: String) = ChordDto(root, quality)

    private fun suggestion(tonic: String, scale: String, score: ULong = 2u, total: ULong = 2u) =
        KeySuggestionDto(tonic, scale, score, total)

    private fun show(content: @Composable () -> Unit) {
        compose.setContent { MaterialTheme { content() } }
    }

    // --------------------------------------- A14 — the two draft sheets

    @Test
    fun theFreshKeySheetShowsCMajorTriadAndTheEnginesPreview() {
        val preview = listOf(chord("C", "major"), chord("D", "minor"), chord("E", "minor"))
        var applied = false
        var cancelled = false
        show {
            KeySheet(
                draft = KeyDraft("C", "major", ChordModeDto.TRIAD, preview),
                onTonicChange = {},
                onScaleChange = {},
                onModeChange = {},
                onApply = { applied = true },
                onCancel = { cancelled = true },
            )
        }

        compose.onNodeWithTag("key-sheet").assertIsDisplayed()
        compose.onNodeWithTag("key-tonic-C").assertIsDisplayed()
        compose.onNodeWithTag("key-mode-triad").assertIsDisplayed()
        compose.onNodeWithText("C major").assertIsDisplayed()
        compose.onNodeWithTag("key-preview").assertIsDisplayed()

        compose.onNodeWithTag("key-apply").performClick()
        compose.onNodeWithTag("key-cancel").performClick()
        assertEquals(true, applied)
        assertEquals(true, cancelled)
    }

    @Test
    fun choosingAScaleSendsTheEnginesOwnIdentifier() {
        var chosen: String? = null
        show {
            KeySheet(
                draft = KeyDraft("C", "major", ChordModeDto.TRIAD, emptyList()),
                onTonicChange = {},
                onScaleChange = { id -> chosen = id },
                onModeChange = {},
                onApply = {},
                onCancel = {},
            )
        }

        // The scale list is a dropdown: its options exist only while the menu is
        // open, so the test opens the anchor first.
        compose.onNodeWithTag("key-scale-anchor").performClick()
        compose.onNodeWithTag("key-scale-option-harmonic_minor").performClick()

        assertEquals("the wire identifier is sent, not a label", "harmonic_minor", chosen)
    }

    @Test
    fun choosingASeventhModeSendsTheEnginesMode() {
        var mode: ChordModeDto? = null
        show {
            KeySheet(
                draft = KeyDraft("C", "major", ChordModeDto.TRIAD, emptyList()),
                onTonicChange = {},
                onScaleChange = {},
                onModeChange = { chosen -> mode = chosen },
                onApply = {},
                onCancel = {},
            )
        }

        compose.onNodeWithTag("key-mode-seventh").performClick()

        assertEquals(ChordModeDto.SEVENTH, mode)
    }

    @Test
    fun theProgressionSheetRendersTheEnginesGroupedCatalog() {
        val groups = listOf(
            ProgressionGroupDto(
                "Pop",
                listOf(
                    ProgressionDto(
                        "pop_i_v_vi_iv",
                        "I–V–vi–IV",
                        "Pop",
                        "",
                        "",
                        "C",
                        "major",
                        emptyList(),
                        emptyList(),
                    ),
                ),
            ),
        )
        var selected: String? = null
        show {
            ProgressionSheet(
                draft = ProgressionDraft(
                    "C",
                    "pop_i_v_vi_iv",
                    listOf(chord("C", "major"), chord("G", "major")),
                ),
                catalog = ProgressionCatalogState.Ready(groups),
                onTonicChange = {},
                onSelectProgression = { id -> selected = id },
                onApply = {},
                onCancel = {},
            )
        }

        compose.onNodeWithTag("progression-sheet").assertIsDisplayed()
        // The catalog is a dropdown: open it, then pick the engine's own entry.
        compose.onNodeWithTag("progression-anchor").performClick()
        compose.onNodeWithTag("progression-option-pop_i_v_vi_iv").assertIsDisplayed()
        compose.onNodeWithText("I–V–vi–IV").performClick()

        assertEquals("pop_i_v_vi_iv", selected)
    }

    @Test
    fun anEmptyCatalogIsSaidPlainly() {
        show {
            ProgressionSheet(
                draft = ProgressionDraft("C", "pop_i_v_vi_iv", emptyList()),
                catalog = ProgressionCatalogState.NotApplicable,
                onTonicChange = {},
                onSelectProgression = {},
                onApply = {},
                onCancel = {},
            )
        }

        compose.onNodeWithTag("progression-catalog-none").assertIsDisplayed()
    }

    // --------------------------- A15 — keys rows and multi-key membership

    @Test
    fun belowTwoChordsTheSuggestionPanelIsAbsentAndSaysWhy() {
        show {
            KeySuggestionsPanel(
                chordCount = 1,
                state = EvaluationState.Absent,
                expanded = false,
                onToggleExpanded = {},
                onApplySuggestion = {},
                onRetry = {},
                enabled = true,
            )
        }

        compose.onNodeWithTag("key-suggestions-gate").assertIsDisplayed()
        compose.onNodeWithTag("key-suggestions-pending").assertDoesNotExist()
    }

    @Test
    fun aCollapsedGroupShowsTheProminentPairAndHidesTheOthers() {
        val rows = listOf(
            KeyRowDto.Group(
                prominent = listOf(suggestion("C", "major"), suggestion("A", "minor")),
                others = listOf(suggestion("D", "dorian"), suggestion("E", "phrygian")),
            ),
            KeyRowDto.Single(suggestion("G", "mixolydian")),
        )
        var toggles = 0
        show {
            KeySuggestionsPanel(
                chordCount = 2,
                state = EvaluationState.Answer(rows),
                expanded = false,
                onToggleExpanded = { toggles += 1 },
                onApplySuggestion = {},
                onRetry = {},
                enabled = true,
            )
        }

        // The prominent pair is always drawn, with the engine's own score.
        compose.onNodeWithTag("key-suggestion-C-major").assertIsDisplayed()
        compose.onNodeWithTag("key-suggestion-A-minor").assertIsDisplayed()
        compose.onNodeWithText("2/2").assertIsDisplayed()
        // The other modes are behind the shared toggle while collapsed.
        compose.onNodeWithTag("key-suggestion-D-dorian").assertDoesNotExist()

        compose.onNodeWithTag("key-modes-toggle").performClick()
        assertEquals(1, toggles)
    }

    @Test
    fun anExpandedGroupShowsTheEnginesOtherModes() {
        val rows = listOf(
            KeyRowDto.Group(
                prominent = listOf(suggestion("C", "major"), suggestion("A", "minor")),
                others = listOf(suggestion("D", "dorian"), suggestion("E", "phrygian")),
            ),
        )
        show {
            KeySuggestionsPanel(
                chordCount = 2,
                state = EvaluationState.Answer(rows),
                expanded = true,
                onToggleExpanded = {},
                onApplySuggestion = {},
                onRetry = {},
                enabled = true,
            )
        }

        compose.onNodeWithTag("key-suggestion-D-dorian").assertIsDisplayed()
        compose.onNodeWithTag("key-suggestion-E-phrygian").assertIsDisplayed()
    }

    @Test
    fun aNonModalRowStaysAnIndependentCard() {
        show {
            KeySuggestionsPanel(
                chordCount = 2,
                state = EvaluationState.Answer(listOf(KeyRowDto.Single(suggestion("G", "major")))),
                expanded = false,
                onToggleExpanded = {},
                onApplySuggestion = {},
                onRetry = {},
                enabled = true,
            )
        }

        compose.onNodeWithTag("key-row-single-0-G-major").assertIsDisplayed()
    }

    @Test
    fun tappingASuggestionAppliesTheEnginesOwnValue() {
        var applied: KeySuggestionDto? = null
        show {
            KeySuggestionsPanel(
                chordCount = 2,
                state = EvaluationState.Answer(listOf(KeyRowDto.Single(suggestion("D", "major")))),
                expanded = false,
                onToggleExpanded = {},
                onApplySuggestion = { value -> applied = value },
                onRetry = {},
                enabled = true,
            )
        }

        compose.onNodeWithTag("key-suggestion-D-major").performClick()

        assertEquals(suggestion("D", "major"), applied)
    }

    @Test
    fun theMultiKeyPanelShowsTheFullMembershipIncludingAKeylessGroup() {
        val keyed = MultiKeyGroupDto(
            key = suggestion("C", "major", 3u, 4u),
            // One chord can appear in two groups; this group keeps all four.
            chords = listOf(
                chord("C", "major"),
                chord("A", "minor"),
                chord("G", "major"),
                chord("C", "major"),
            ),
        )
        val keyless = MultiKeyGroupDto(key = null, chords = listOf(chord("C", "7#9")))
        show {
            MultiKeySuggestionsPanel(
                groups = EvaluationState.Answer(listOf(keyed, keyless)),
                onRetry = {},
                enabled = true,
            )
        }

        compose.onNodeWithTag("multi-key-group-0").assertIsDisplayed()
        compose.onNodeWithTag("multi-key-group-0-chord-3").assertIsDisplayed()
        compose.onNodeWithTag("multi-key-keyless").assertIsDisplayed()
        compose.onNodeWithText("Unmatched chords").assertIsDisplayed()
    }

    @Test
    fun theMultiKeyPanelIsAbsentWhenTheEngineReturnsNoGroups() {
        show {
            MultiKeySuggestionsPanel(
                groups = EvaluationState.Answer(emptyList()),
                onRetry = {},
                enabled = true,
            )
        }

        compose.onNodeWithTag("multi-key-group-0").assertDoesNotExist()
        compose.onNodeWithTag("multi-key-keyless").assertDoesNotExist()
    }

    // ------------------- A16 — pending, empty, failed and Retry, distinct

    @Test
    fun aPendingPanelSaysItIsCalculating() {
        show {
            KeySuggestionsPanel(
                chordCount = 2,
                state = EvaluationState.Pending,
                expanded = false,
                onToggleExpanded = {},
                onApplySuggestion = {},
                onRetry = {},
                enabled = true,
            )
        }

        compose.onNodeWithTag("key-suggestions-pending").assertIsDisplayed()
        compose.onNodeWithTag("key-suggestions-empty").assertDoesNotExist()
    }

    @Test
    fun anEmptyAnswerIsSaidPlainlyRatherThanAsAnEmptyBox() {
        show {
            KeySuggestionsPanel(
                chordCount = 2,
                state = EvaluationState.Answer(emptyList()),
                expanded = false,
                onToggleExpanded = {},
                onApplySuggestion = {},
                onRetry = {},
                enabled = true,
            )
        }

        compose.onNodeWithTag("key-suggestions-empty").assertIsDisplayed()
        compose.onNodeWithTag("key-suggestions-pending").assertDoesNotExist()
    }

    @Test
    fun aFailureShowsTheEnginesSentenceAndOffersRetry() {
        var retried = 0
        show {
            KeySuggestionsPanel(
                chordCount = 2,
                state = EvaluationState.Failed("The engine refused the request."),
                expanded = false,
                onToggleExpanded = {},
                onApplySuggestion = {},
                onRetry = { retried += 1 },
                enabled = true,
            )
        }

        compose.onNodeWithTag("key-suggestions-failed").assertIsDisplayed()
        compose.onNodeWithTag("evaluation-retry").performClick()

        assertEquals("Retry asks the engine again", 1, retried)
    }

    @Test
    fun aPendingPreviewIsDistinctFromAnEmptyOne() {
        show {
            KeySheet(
                draft = KeyDraft("C", "major", ChordModeDto.TRIAD, emptyList()),
                onTonicChange = {},
                onScaleChange = {},
                onModeChange = {},
                onApply = {},
                onCancel = {},
                previewPending = true,
            )
        }

        compose.onNodeWithTag("key-preview-pending").assertIsDisplayed()
        compose.onNodeWithTag("key-preview").assertDoesNotExist()
    }

    // ------------- the general view's controls, mirrored from the web

    @Test
    fun everyRootAndQualityIsReachableInTheDropdowns() {
        var root: String? = null
        var quality: String? = null
        show {
            ChordEditor(
                qualityGroups = listOf(
                    QualityGroupDto(
                        group = "Triads",
                        qualities = listOf(
                            QualityDto(quality = "major", label = "maj"),
                            QualityDto(quality = "minor", label = "m"),
                        ),
                    ),
                ),
                root = "C",
                quality = "major",
                onRootChange = { name -> root = name },
                onQualityChange = { id -> quality = id },
                onAdd = {},
            )
        }

        compose.onNodeWithTag("root-anchor").performClick()
        // Every root the engine accepts, including the ones a sideways-scrolling
        // row used to leave off-screen.
        listOf("C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B")
            .forEach { name -> compose.onNodeWithTag("root-option-$name").assertExists() }
        compose.onNodeWithTag("root-option-F#").performClick()
        assertEquals("F#", root)

        compose.onNodeWithTag("quality-anchor").performClick()
        compose.onNodeWithTag("quality-group-Triads").assertExists()
        compose.onNodeWithTag("quality-option-minor").performClick()
        assertEquals("minor", quality)
    }

    @Test
    fun theInstrumentPickerIsADropdownOverTheEnginesCatalog() {
        var chosen: InstrumentDefinitionDto? = null
        val guitar = InstrumentDefinitionDto(
            instrument = InstrumentDto.GUITAR,
            name = "Guitar",
            kind = InstrumentKindDto.FRETTED,
            strings = 6u,
            frets = 24u,
            standardPitches = byteArrayOf(40, 45, 50, 55, 59, 64),
        )
        val piano = InstrumentDefinitionDto(
            instrument = InstrumentDto.PIANO,
            name = "Piano",
            kind = InstrumentKindDto.KEYBOARD,
            strings = 0u,
            frets = null,
            standardPitches = byteArrayOf(),
        )
        show {
            InstrumentPicker(
                instruments = listOf(guitar, piano),
                selected = InstrumentDto.GUITAR,
                onSelect = { definition -> chosen = definition },
            )
        }

        compose.onNodeWithTag("instrument-anchor").performClick()
        compose.onNodeWithTag("instrument-option-PIANO").assertExists()
        compose.onNodeWithTag("instrument-option-PIANO").performClick()

        assertEquals("the engine's own definition is handed back", piano, chosen)
    }
}
