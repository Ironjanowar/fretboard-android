package dev.ironjanowar.fretboard.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.click
import androidx.compose.ui.unit.dp
import dev.ironjanowar.fretboard.core.FrettedSurfaceDto
import dev.ironjanowar.fretboard.core.NoteFillDto
import dev.ironjanowar.fretboard.core.PositionDto
import dev.ironjanowar.fretboard.core.SurfaceCellDto
import dev.ironjanowar.fretboard.core.SurfaceRowDto
import dev.ironjanowar.fretboard.ui.surface.BoardColors
import dev.ironjanowar.fretboard.ui.surface.FretboardSurface
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * Behavioral contract for the mobile fretboard's information hierarchy.
 *
 * The tags asserted here are public test/accessibility seams rather than layout
 * implementation details:
 *
 * * `fretboard-board` is the wooden, stopped-fret area;
 * * `tuning-note-N` is physical string N's note outside that area;
 * * `fret-number-N` is the number above stopped fret N; and
 * * `inlay-marker-N` tags each individual dot inside stopped fret N.
 */
class FretboardSurfaceUiTest {

    @get:Rule
    val compose = createComposeRule()

    private val lastFret = 24

    private fun cell(
        string: Int,
        fret: Int,
        painted: Boolean = false,
    ) = SurfaceCellDto(
        fret = fret.toUByte(),
        note = "S${string}F$fret",
        memberships = if (painted) listOf(0uL) else emptyList(),
        fill = if (painted) NoteFillDto.Slot(0uL) else NoteFillDto.Overlap,
    )

    private fun surface(
        vararg painted: Pair<Int, Int>,
        stringCount: Int = 2,
    ): FrettedSurfaceDto {
        val active = painted.toSet()
        return FrettedSurfaceDto(
            rows = (0 until stringCount).map { string ->
                SurfaceRowDto(
                    cells = (0..lastFret).map { fret ->
                        cell(string, fret, (string to fret) in active)
                    },
                )
            },
        )
    }

    private fun show(
        surface: FrettedSurfaceDto,
        marked: Map<Int, Int> = emptyMap(),
        paintChords: Boolean = true,
        onPositionTap: ((PositionDto) -> Unit)? = null,
    ) {
        compose.setContent {
            MaterialTheme {
                FretboardSurface(
                    surface = surface,
                    lastFret = lastFret,
                    marked = marked,
                    paintChords = paintChords,
                    onPositionTap = onPositionTap,
                    modifier = Modifier,
                )
            }
        }
    }

    private fun showInProductionScroll(surface: FrettedSurfaceDto) {
        compose.setContent {
            MaterialTheme {
                Column(
                    Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState()),
                ) {
                    FretboardSurface(
                        surface = surface,
                        lastFret = lastFret,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }

    @Test
    fun visualizerHidesStoppedLabelsWhenNoChordPaintExists() {
        show(surface())

        compose.onNodeWithText("S0F0").assertExists()
        compose.onNodeWithText("S0F3").assertDoesNotExist()
        compose.onNodeWithText("S1F12").assertDoesNotExist()
    }

    @Test
    fun visualizerShowsOnlyStoppedLabelsWithActiveChordPaint() {
        show(surface(0 to 3))

        compose.onNodeWithText("S0F3").assertExists()
        compose.onNodeWithText("S0F4").assertDoesNotExist()
        compose.onNodeWithText("S1F3").assertDoesNotExist()
    }

    @Test
    fun analyzerShowsSelectedStoppedLabelAndHidesUnselectedLabels() {
        show(
            surface = surface(),
            marked = mapOf(0 to 3),
            paintChords = false,
            onPositionTap = {},
        )

        compose.onNodeWithText("S0F3").assertExists()
        compose.onNodeWithText("S0F4").assertDoesNotExist()
        compose.onNodeWithText("S1F3").assertDoesNotExist()
    }

    @Test
    fun tuningNotesStayOutsideTheBoardAndFretNumbersStayAboveIt() {
        show(surface())

        val board = compose.onNodeWithTag("fretboard-board").fetchSemanticsNode().boundsInRoot
        val topTuning = compose.onNodeWithTag("tuning-note-1").fetchSemanticsNode().boundsInRoot
        val bottomTuning = compose.onNodeWithTag("tuning-note-0").fetchSemanticsNode().boundsInRoot
        val fretNumber = compose.onNodeWithTag("fret-number-3").fetchSemanticsNode().boundsInRoot

        assertTrue("top tuning note must end before the wooden board", topTuning.right <= board.left)
        assertTrue("bottom tuning note must end before the wooden board", bottomTuning.right <= board.left)
        assertTrue("fret numbers remain above the wooden board", fretNumber.bottom <= board.top)
    }

    @Test
    fun analyzerTuningNoteIsA48DpFretZeroControl() {
        var tapped: PositionDto? = null
        show(
            surface = surface(),
            paintChords = false,
            onPositionTap = { tapped = it },
        )

        compose.onNodeWithTag("tuning-note-0")
            .assertWidthIsAtLeast(48.dp)
            .assertHeightIsAtLeast(48.dp)
            .performClick()

        assertEquals(PositionDto(string = 0u, fret = 0u), tapped)
    }

    @Test
    fun inlayDotsAreRenderedInTheirColumnsAndCenteredVertically() {
        show(surface())

        val singles = listOf(3, 5, 7, 9, 15, 17, 19, 21)
        val doubles = listOf(12, 24)

        singles.forEach { fret ->
            compose.onAllNodesWithTag("inlay-marker-$fret", useUnmergedTree = true)
                .assertCountEquals(1)
        }
        doubles.forEach { fret ->
            compose.onAllNodesWithTag("inlay-marker-$fret", useUnmergedTree = true)
                .assertCountEquals(2)
        }

        (singles + doubles).forEach { fret ->
            scrollFretIntoView(fret)

            val board = bounds("fretboard-board")
            val cell = compose.onNodeWithContentDescription(positionDescription(0, fret))
                .fetchSemanticsNode().boundsInRoot
            val header = bounds("fret-number-$fret")
            val markerNodes = compose.onAllNodesWithTag("inlay-marker-$fret", useUnmergedTree = true)
            val markers = markerNodes.fetchSemanticsNodes()
                .map { it.boundsInRoot }
                .sortedBy { it.center.y }

            assertAlignedHorizontally(header, cell, "fret $fret header")
            markers.forEachIndexed { index, marker ->
                assertTrue(
                    "fret $fret marker $index tag must be on a visibly rendered dot",
                    markerNodes[index].containsColor(BoardColors.marker),
                )
                assertInside(marker, board, fret)
                assertAlignedHorizontally(marker, cell, "fret $fret marker")
            }
            if (fret in singles) {
                assertNear(board.center.y, markers.single().center.y, "fret $fret single marker is centered")
            } else {
                assertTrue("fret $fret upper marker is above center", markers[0].center.y < board.center.y)
                assertTrue("fret $fret lower marker is below center", markers[1].center.y > board.center.y)
                assertNear(
                    board.center.y,
                    (markers[0].center.y + markers[1].center.y) / 2f,
                    "fret $fret double markers straddle center evenly",
                )
            }
        }
    }

    @Test
    fun sixStringSingleInlayIsCenteredWhenMeasuredInsideTheProductionScroll() {
        showInProductionScroll(surface(stringCount = 6))

        val board = bounds("fretboard-board")
        val single = bounds("inlay-marker-3")

        assertNear(
            board.center.y,
            single.center.y,
            "fret 3 single marker is centered on the six-string board",
        )
    }

    @Test
    fun sixStringDoubleInlaysStraddleCenterWhenMeasuredInsideTheProductionScroll() {
        showInProductionScroll(surface(stringCount = 6))
        scrollFretIntoView(12, stringCount = 6)

        val board = bounds("fretboard-board")
        val doubles = compose.onAllNodesWithTag("inlay-marker-12", useUnmergedTree = true)
            .fetchSemanticsNodes()
            .map { it.boundsInRoot }
            .sortedBy { it.center.y }

        assertTrue("fret 12 upper marker is above center", doubles[0].center.y < board.center.y)
        assertTrue("fret 12 lower marker is below center", doubles[1].center.y > board.center.y)
        assertNear(
            board.center.y,
            (doubles[0].center.y + doubles[1].center.y) / 2f,
            "fret 12 double markers straddle the six-string board center evenly",
        )
    }

    @Test
    fun selectedTuningControlHasAVisibleIndicatorAndUnselectedControlDoesNot() {
        show(
            surface = surface(),
            marked = mapOf(0 to 0),
            paintChords = false,
            onPositionTap = {},
        )

        compose.onNodeWithTag("tuning-note-0").assertIsSelected()
        compose.onNodeWithTag("tuning-note-1").assertIsNotSelected()

        assertTrue(
            "selected fret-zero control must visibly use the selection colour",
            compose.onNodeWithTag("tuning-note-0").containsColor(BoardColors.selection),
        )
        assertFalse(
            "unselected fret-zero control must not show the selection indicator",
            compose.onNodeWithTag("tuning-note-1").containsColor(BoardColors.selection),
        )
    }

    @Test
    fun realPointerTouchesEmitTheStoppedPositionBeforeAndAfterScrolling() {
        val tapped = mutableListOf<PositionDto>()
        show(
            surface = surface(),
            paintChords = false,
            onPositionTap = tapped::add,
        )

        compose.onNodeWithContentDescription(positionDescription(0, 3))
            .performTouchInput { click() }
        compose.waitForIdle()

        scrollFretIntoView(21)
        compose.onNodeWithContentDescription(positionDescription(1, 21))
            .performTouchInput { click() }
        compose.waitForIdle()

        assertEquals(
            listOf(
                PositionDto(string = 0u, fret = 3u),
                PositionDto(string = 1u, fret = 21u),
            ),
            tapped,
        )
    }

    @Test
    fun fretNumberHeaderStaysAlignedWithItsStoppedColumnWhenScrolled() {
        show(surface())

        assertHeaderAlignedWithCell(3)
        scrollFretIntoView(21)
        assertHeaderAlignedWithCell(21)
    }

    @Test
    fun surfaceRemainsHorizontallyScrollable() {
        show(surface())

        compose.onNodeWithTag("fretboard-board").assert(hasScrollAction())
    }

    private fun assertInside(marker: Rect, board: Rect, fret: Int) {
        assertTrue("fret $fret marker starts inside the board horizontally", marker.left >= board.left)
        assertTrue("fret $fret marker ends inside the board horizontally", marker.right <= board.right)
        assertTrue("fret $fret marker starts inside the board", marker.top >= board.top)
        assertTrue("fret $fret marker ends inside the board", marker.bottom <= board.bottom)
    }

    private fun scrollFretIntoView(fret: Int, stringCount: Int = 2) {
        compose.onNodeWithTag("fretboard-board").performScrollToNode(
            hasContentDescription(positionDescription(0, fret, stringCount)),
        )
        compose.waitForIdle()
    }

    private fun assertHeaderAlignedWithCell(fret: Int) {
        val header = bounds("fret-number-$fret")
        val cell = compose.onNodeWithContentDescription(positionDescription(0, fret))
            .fetchSemanticsNode().boundsInRoot
        assertAlignedHorizontally(header, cell, "fret $fret header")
    }

    private fun assertAlignedHorizontally(actual: Rect, column: Rect, label: String) {
        assertNear(column.center.x, actual.center.x, "$label is centered over its stopped-fret column")
    }

    private fun assertNear(expected: Float, actual: Float, message: String) {
        assertTrue("$message: expected $expected, got $actual", kotlin.math.abs(expected - actual) <= 1.5f)
    }

    private fun bounds(tag: String): Rect =
        compose.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot

    private fun positionDescription(string: Int, fret: Int, stringCount: Int = 2): String =
        "String ${stringCount - string}, fret $fret, S${string}F$fret"

    private fun androidx.compose.ui.test.SemanticsNodeInteraction.containsColor(expected: Color): Boolean {
        val pixels = captureToImage().toPixelMap()
        for (y in 0 until pixels.height) {
            for (x in 0 until pixels.width) {
                val actual = pixels[x, y]
                if (
                    kotlin.math.abs(actual.red - expected.red) < 0.03f &&
                    kotlin.math.abs(actual.green - expected.green) < 0.03f &&
                    kotlin.math.abs(actual.blue - expected.blue) < 0.03f
                ) {
                    return true
                }
            }
        }
        return false
    }
}
