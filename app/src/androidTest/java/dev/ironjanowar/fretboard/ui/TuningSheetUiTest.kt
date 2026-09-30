package dev.ironjanowar.fretboard.ui

import android.content.pm.ActivityInfo
import android.content.res.Configuration
import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.ironjanowar.fretboard.core.InstrumentDto
import dev.ironjanowar.fretboard.core.TuningDto
import dev.ironjanowar.fretboard.session.TuningDraft
import dev.ironjanowar.fretboard.ui.tuning.PresetPickerState
import dev.ironjanowar.fretboard.ui.tuning.TuningSheet
import dev.ironjanowar.fretboard.ui.visualizer.ROOT_WIRE_NAMES
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Behavioural contract for the compact tuning editor.
 *
 * The tagged nodes are the controls and layout regions a user interacts with:
 * one dropdown for the engine's preset catalog, one per physical string, a
 * scrollable editing body, and an action bar that remains outside that body.
 */
@RunWith(AndroidJUnit4::class)
class TuningSheetUiTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val presetNames = listOf("Standard", "Open C", "DADGAD")
    private val draft = TuningDraft(
        instrument = InstrumentDto.GUITAR,
        tuning = TuningDto(
            pitches = byteArrayOf(40, 45, 50, 55, 59, 64),
            reference = "Standard",
        ),
        preset = "Standard",
        notes = listOf("E", "A", "D", "G", "B", "E"),
    )

    @Test
    fun presetAndStringEditorsAreDropdownsWithOrderedEngineValues() {
        portrait()
        val selectedPresets = mutableListOf<String>()
        val changedStrings = mutableListOf<Pair<Int, String>>()
        show(
            onSelectPreset = selectedPresets::add,
            onChangeString = { index, note -> changedStrings += index to note },
        )

        compose.onNodeWithTag("tuning-preset-dropdown").performClick()
        assertOptionOrder("tuning-preset-option-", presetNames)
        compose.onNodeWithTag("tuning-preset-option-Open C").performClick()
        assertEquals(listOf("Open C"), selectedPresets)

        compose.onNodeWithTag("tuning-string-0-dropdown").performClick()
        assertOptionOrder("tuning-string-0-option-", ROOT_WIRE_NAMES)
        compose.onNodeWithTag("tuning-string-0-option-C#").performClick()
        assertEquals(listOf(0 to "C#"), changedStrings)
    }

    @Test
    fun disabledDropdownsDoNotOfferEdits() {
        portrait()
        val selectedPresets = mutableListOf<String>()
        val changedStrings = mutableListOf<Pair<Int, String>>()
        show(
            enabled = false,
            onSelectPreset = selectedPresets::add,
            onChangeString = { index, note -> changedStrings += index to note },
        )

        compose.onNodeWithTag("tuning-preset-dropdown")
            .assertIsNotEnabled()
            .performClick()
        compose.onNodeWithTag("tuning-string-0-dropdown")
            .assertIsNotEnabled()
            .performClick()

        compose.onNodeWithTag("tuning-preset-option-Standard").assertDoesNotExist()
        compose.onNodeWithTag("tuning-string-0-option-C").assertDoesNotExist()
        assertTrue(selectedPresets.isEmpty())
        assertTrue(changedStrings.isEmpty())
    }

    @Test
    fun disablingAnOpenDropdownPreventsItsPendingSelection() {
        portrait()
        val enabled = mutableStateOf(true)
        val selectedPresets = mutableListOf<String>()
        compose.setContent {
            MaterialTheme {
                TuningSheet(
                    draft = draft,
                    stringCount = 6,
                    presets = PresetPickerState.Ready(presetNames),
                    onSelectPreset = selectedPresets::add,
                    onChangeString = { _, _ -> },
                    onApply = {},
                    onCancel = {},
                    enabled = enabled.value,
                )
            }
        }
        compose.waitForIdle()

        compose.onNodeWithTag("tuning-preset-dropdown").performClick()
        compose.onNodeWithTag("tuning-preset-option-Open C").assertIsDisplayed()

        compose.runOnIdle { enabled.value = false }
        compose.waitForIdle()
        compose.onNodeWithTag("tuning-preset-dropdown").assertIsNotEnabled()

        val pendingOptionTag = "tuning-preset-option-Open C"
        if (compose.onAllNodes(hasTestTag(pendingOptionTag)).fetchSemanticsNodes().isNotEmpty()) {
            compose.onNodeWithTag(pendingOptionTag).performClick()
        }
        assertTrue("a disabled sheet must reject a pending dropdown selection", selectedPresets.isEmpty())
    }

    @Test
    fun portraitUsesOnePhysicalStringColumnAndKeepsActionsVisible() {
        portrait()
        show()

        val strings = stringBounds()
        strings.zipWithNext().forEachIndexed { index, (upper, lower) ->
            assertNear(upper.center.x, lower.center.x, "portrait string columns at ${index + 1}")
            assertTrue("physical strings remain ordered 6 to 1", upper.top < lower.top)
        }
        assertPinnedActions()
    }

    @Test
    fun landscapeUsesTwoColumnsOfThreeAndKeepsActionsVisible() {
        landscape()
        show()

        val strings = stringBounds()
        listOf(0 to 1, 2 to 3, 4 to 5).forEach { (left, right) ->
            assertTrue("the left string control precedes the right", strings[left].center.x < strings[right].center.x)
            assertNear(strings[left].center.y, strings[right].center.y, "landscape row ${left / 2 + 1}")
        }
        listOf(0, 2, 4).zipWithNext().forEach { (upper, lower) ->
            assertTrue("landscape rows retain physical order", strings[upper].top < strings[lower].top)
        }
        assertPinnedActions()
    }

    private fun show(
        enabled: Boolean = true,
        onSelectPreset: (String) -> Unit = {},
        onChangeString: (Int, String) -> Unit = { _, _ -> },
    ) {
        compose.setContent {
            MaterialTheme {
                TuningSheet(
                    draft = draft,
                    stringCount = 6,
                    presets = PresetPickerState.Ready(presetNames),
                    onSelectPreset = onSelectPreset,
                    onChangeString = onChangeString,
                    onApply = {},
                    onCancel = {},
                    enabled = enabled,
                )
            }
        }
        compose.waitForIdle()
    }

    private fun portrait() = setOrientation(
        requested = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT,
        expected = Configuration.ORIENTATION_PORTRAIT,
    )

    private fun landscape() = setOrientation(
        requested = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE,
        expected = Configuration.ORIENTATION_LANDSCAPE,
    )

    private fun setOrientation(requested: Int, expected: Int) {
        compose.activity.requestedOrientation = requested
        compose.waitUntil(TIMEOUT_MS) {
            compose.activity.resources.configuration.orientation == expected
        }
        compose.waitForIdle()
    }

    private fun stringBounds(): List<Rect> =
        (0 until 6).map { index -> bounds("tuning-string-$index-dropdown") }

    private fun assertPinnedActions() {
        compose.onNodeWithTag("tuning-body").assert(hasScrollAction())
        compose.onNodeWithTag("tuning-cancel").assertIsDisplayed()
        compose.onNodeWithTag("tuning-apply").assertIsDisplayed()

        val body = bounds("tuning-body")
        val actions = bounds("tuning-actions")
        val sheet = bounds("tuning-sheet")
        assertTrue("the action bar is below and outside the scrollable body", actions.top >= body.bottom - 1f)
        assertTrue("the action bar remains inside the sheet", actions.bottom <= sheet.bottom + 1f)
    }

    private fun assertOptionOrder(prefix: String, expected: List<String>) {
        val actual = compose.onAllNodes(tagStartsWith(prefix))
            .fetchSemanticsNodes()
            .map { node -> node.config.getOrNull(SemanticsProperties.TestTag).orEmpty().removePrefix(prefix) }
        assertEquals("dropdown options preserve their source order", expected, actual)
    }

    private fun tagStartsWith(prefix: String) = SemanticsMatcher("test tag starts with $prefix") { node ->
        node.config.getOrNull(SemanticsProperties.TestTag)?.startsWith(prefix) == true
    }

    private fun bounds(tag: String): Rect =
        compose.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot

    private fun assertNear(expected: Float, actual: Float, label: String) {
        assertTrue("$label: expected $expected, got $actual", kotlin.math.abs(expected - actual) <= 2f)
    }

    private companion object {
        const val TIMEOUT_MS = 20_000L
    }
}
