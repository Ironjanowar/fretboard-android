package dev.ironjanowar.fretboard

import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The reported bug, pinned on a device: a rotation must not lose the session.
 *
 * `ActivityScenario.recreate()` is what a rotation does to an activity — the
 * activity is destroyed, its composition thrown away, and a new one is built
 * from the same intent with the retained `ViewModelStore` — so the state is set
 * up through the engine, the activity is recreated twice (turning the phone and
 * turning it back), and every part of the session is asserted afterwards:
 *
 * * the instrument and its captioned tuning,
 * * the stored chords,
 * * the marked position on the analyzer surface,
 * * the keyboard's marked keys,
 * * the tab the user was on,
 * * an open tuning draft **with an unapplied edit**, and
 * * the committed tuning, which the draft edit never touched.
 *
 * This test is the behavioural half of the regression: it fails against the code
 * where the session lived in `remember` (the recreated composition comes back
 * empty) and passes only while the session is held outside the composition.
 *
 * It runs with the x86_64 engine on the API 37 emulator.
 */
@RunWith(AndroidJUnit4::class)
class RotationStateTest {

    // The v1 rule is the one whose execution model this test was written
    // against. `androidx.compose.ui.test.junit4.v2` replaces it with a rule that
    // queues compositions on a `StandardTestDispatcher`; the migration is a
    // device-run change, not a compile-time one, so it is left for the machine
    // that runs this test (the deprecated call is a warning, not a failure).
    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    @Test
    fun theFrettedSessionSurvivesRotatingAndRotatingBack() {
        awaitSession()

        // An instrument switch, there and back: the boundary is the engine's and
        // the session must come out of it intact, not reset. The picker is a
        // dropdown, so the menu is opened before its entry is clicked.
        compose.onNodeWithTag("instrument-anchor").performClick()
        compose.onNodeWithTag("instrument-option-PIANO").performClick()
        awaitTag("keyboard-surface")
        compose.onNodeWithTag("instrument-anchor").performClick()
        compose.onNodeWithTag("instrument-option-GUITAR").performClick()
        awaitTag("fretboard-surface")
        awaitSession()

        // A chord, stored by the engine and drawn from its answer.
        compose.onNodeWithTag("add-chord").performClick()
        awaitTag("chord-card-0")
        val chordsBefore = headerText()

        // A marked position on the analyzer.
        compose.onNodeWithTag("tab-analyzer").performClick()
        awaitTag("analyzer-surface")
        val openStrings = openStringLabels()
        assertTrue("the engine answered open strings: $openStrings", openStrings.isNotEmpty())
        val marked = openStrings.first()
        compose.onNodeWithContentDescription(marked).performClick()
        awaitTag("clear-notes")
        compose.onNodeWithContentDescription(marked).assertIsSelected()

        // A tuning draft with an edit that has not been applied.
        val captionBefore = captionText()
        compose.onNodeWithTag("open-tuning").performClick()
        awaitTag("tuning-sheet")
        val committedRows = draftDropdownValues()
        assertTrue("the sheet drew its string rows: $committedRows", committedRows.isNotEmpty())
        val committedNote = committedRows.first()
        val wireName = listOf("C", "C#", "D", "D#", "E").first { name -> name !in committedNote }
        compose.onNodeWithTag("tuning-string-0-dropdown").performClick()
        compose.onNodeWithTag("tuning-string-0-option-$wireName").performClick()
        compose.waitUntil(TIMEOUT_MS) { draftDropdownValues() != committedRows }
        val editedRows = draftDropdownValues()
        assertNotEquals("the draft carries the engine's answer", committedRows, editedRows)

        rotate()

        // The sheet is still open, still holding the unapplied edit.
        compose.onNodeWithTag("tuning-sheet").assertExists()
        assertEquals("the unapplied edit survives the rotation", editedRows, draftDropdownValues())
        // The committed page is the engine's and the draft never touched it.
        assertEquals("the committed tuning is untouched", captionBefore, captionText())
        // The tab, the chord and the mark are all still there.
        compose.onNodeWithTag("analyzer-surface").assertExists()
        assertEquals("the stored chords survive", chordsBefore, headerText())
        compose.onNodeWithTag("clear-notes").assertExists()
        compose.onNodeWithContentDescription(marked).assertIsSelected()
    }

    @Test
    fun theKeyboardsMarkedKeysSurviveRotatingAndRotatingBack() {
        awaitSession()

        // A preceding device test may have persisted the analyzer tab. Return to
        // the visualizer explicitly so this test owns its starting surface.
        compose.onNodeWithTag("tab-visualizer").performClick()
        compose.onNodeWithTag("instrument-anchor").performClick()
        compose.onNodeWithTag("instrument-option-PIANO").performClick()
        awaitTag("keyboard-surface")
        val keyboardCaption = captionText()

        compose.onNodeWithTag("tab-analyzer").performClick()
        awaitTag("keyboard-analyzer-surface")
        val key = firstKeyTag()
        compose.onNodeWithTag(key).performClick()
        awaitTag("clear-notes")
        compose.onNodeWithTag(key).assertIsSelected()

        rotate()

        assertEquals("the instrument is still the keyboard", keyboardCaption, captionText())
        compose.onNodeWithTag("keyboard-analyzer-surface").assertExists()
        compose.onNodeWithTag("clear-notes").assertExists()
        compose.onNodeWithTag(key).assertIsSelected()
    }

    // ------------------------------------------------------------- plumbing

    /** Turn the phone and turn it back: the two recreations the report describes. */
    private fun rotate() {
        compose.activityRule.scenario.recreate()
        compose.waitForIdle()
        compose.activityRule.scenario.recreate()
        compose.waitForIdle()
    }

    /** The session is on screen: the engine answered and named the instrument. */
    private fun awaitSession() {
        awaitTag("session-caption")
        assertTrue(
            "the engine answered no session on this device",
            nodes(hasTestTag("engine-error")).isEmpty(),
        )
    }

    private fun captionText(): String = textOf(hasTestTag("session-caption"))

    /** The header's `N frets, N active chords` line, which the engine's page drives. */
    private fun headerText(): String = textOf(hasText("active chords", substring = true))

    /** The spoken labels of the analyzer surface's open-string cells. */
    private fun openStringLabels(): List<String> =
        nodes(hasContentDescription("open string", substring = true))
            .mapNotNull { node -> node.config.getOrNull(SemanticsProperties.ContentDescription)?.firstOrNull() }
            .sorted()

    /** The selected values of the six string dropdowns, in physical order. */
    private fun draftDropdownValues(): List<String> =
        (0 until 6).map { index ->
            val tag = "tuning-string-$index-dropdown"
            val node = nodes(hasTestTag(tag)).single()
            node.config.getOrNull(SemanticsProperties.EditableText)?.text
                ?: error("the string dropdown carries no selected value: $tag")
        }

    /** The tag of the keyboard's first key, which is the engine's own first key. */
    private fun firstKeyTag(): String =
        nodes(hasContentDescription("Key ", substring = true))
            .firstOrNull()
            ?.config
            ?.getOrNull(SemanticsProperties.TestTag)
            ?: error("the engine answered no keyboard keys")

    /** Wait until a tagged node is on screen: the engine's answers are asynchronous. */
    private fun awaitTag(tag: String) {
        compose.waitUntil(TIMEOUT_MS) {
            compose.onAllNodes(hasTestTag(tag)).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun nodes(matcher: SemanticsMatcher): List<SemanticsNode> =
        compose.onAllNodes(matcher).fetchSemanticsNodes()

    private fun textOf(matcher: SemanticsMatcher): String {
        val node = nodes(matcher).firstOrNull() ?: error("no node matched: $matcher")
        return textOfNode(node) ?: error("the node carries no text: $matcher")
    }

    private fun textOfNode(node: SemanticsNode): String? =
        node.config.getOrNull(SemanticsProperties.Text)?.joinToString("") { text -> text.text }

    private companion object {
        const val TIMEOUT_MS: Long = 20_000
    }
}
