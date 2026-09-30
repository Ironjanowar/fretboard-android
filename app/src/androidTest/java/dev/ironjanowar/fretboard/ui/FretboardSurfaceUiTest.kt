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
import dev.ironjanowar.fretboard.ui.surface.SurfacePalette
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

    private data class PaintedCell(
        val string: Int,
        val fret: Int,
        val memberships: List<ULong>,
        val fill: NoteFillDto,
    )

    private fun cell(
        string: Int,
        fret: Int,
        paint: PaintedCell? = null,
    ) = SurfaceCellDto(
        fret = fret.toUByte(),
        note = "S${string}F$fret",
        memberships = paint?.memberships.orEmpty(),
        fill = paint?.fill ?: NoteFillDto.Overlap,
    )

    private fun singlePaint(string: Int, fret: Int, slot: ULong = 0uL) =
        PaintedCell(string, fret, listOf(slot), NoteFillDto.Slot(slot))

    private fun overlapPaint(string: Int, fret: Int) =
        PaintedCell(string, fret, listOf(0uL, 1uL), NoteFillDto.Overlap)

    private fun surface(
        vararg painted: PaintedCell,
        stringCount: Int = 2,
    ): FrettedSurfaceDto {
        val active = painted.associateBy { it.string to it.fret }
        return FrettedSurfaceDto(
            rows = (0 until stringCount).map { string ->
                SurfaceRowDto(
                    cells = (0..lastFret).map { fret ->
                        cell(string, fret, active[string to fret])
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
        show(surface(singlePaint(0, 3)))

        compose.onNodeWithText("S0F3").assertExists()
        compose.onNodeWithText("S0F4").assertDoesNotExist()
        compose.onNodeWithText("S1F3").assertDoesNotExist()
    }

    @Test
    fun visualizerOpenStringUsesTheSameSingleChordPaintAsAStoppedNote() {
        val slot = 1uL
        val expected = Color(SurfacePalette.colorForSlot(slot))
        show(surface(singlePaint(0, 0, slot), singlePaint(0, 3, slot)))

        assertStoppedPainted(string = 0, fret = 3, expected)
        assertPainted("tuning-note-0", expected)
    }

    @Test
    fun visualizerOpenStringUsesTheSameOverlapPaintAsAStoppedNote() {
        val expected = Color(SurfacePalette.overlapArgb)
        show(surface(overlapPaint(0, 0), overlapPaint(0, 3)))

        assertStoppedPainted(string = 0, fret = 3, expected)
        assertPainted("tuning-note-0", expected)
    }

    @Test
    fun visualizerUnrelatedOpenStringHasNoChordPaint() {
        show(surface(singlePaint(0, 3)))

        val openString = compose.onNodeWithTag("tuning-note-0")
        SurfacePalette.argb.forEach { argb ->
            assertTrue(
                "an unrelated open string must not have a chord-coloured note circle ${Color(argb)}",
                openString.paintRingColorFraction(Color(argb)) < 0.15f,
            )
        }
        assertTrue(
            "an unrelated open string must not have an overlap-coloured note circle",
            openString.paintRingColorFraction(Color(SurfacePalette.overlapArgb)) < 0.15f,
        )
        assertTrue(
            "the stopped member verifies that chord paint is active in this visualizer fixture",
            compose.onNodeWithContentDescription(positionDescription(0, 3))
                .paintRingColorFraction(Color(SurfacePalette.colorForSlot(0uL))) > 0.45f,
        )
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
            surface = surface(singlePaint(0, 0, slot = 1uL)),
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
        assertFalse(
            "analyzer mode must not replace cyan selection with the cell's chord colour",
            compose.onNodeWithTag("tuning-note-0")
                .containsColor(Color(SurfacePalette.colorForSlot(1uL))),
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

    private fun assertPainted(tag: String, expected: Color) {
        val note = compose.onNodeWithTag(tag)
        assertTrue(
            "$tag must visibly use $expected across its note circle",
            note.paintRingColorFraction(expected) > 0.45f,
        )
        assertTrue("$tag must use the dark painted-note text", note.containsColor(BoardColors.ink))
    }

    private fun assertStoppedPainted(string: Int, fret: Int, expected: Color) {
        val note = compose.onNodeWithContentDescription(positionDescription(string, fret))
        assertTrue(
            "stopped note must visibly use $expected across its note circle",
            note.paintRingColorFraction(expected) > 0.45f,
        )
        assertTrue("stopped note must use the dark painted-note text", note.containsColor(BoardColors.ink))
    }

    private fun positionDescription(string: Int, fret: Int, stringCount: Int = 2): String =
        "String ${stringCount - string}, fret $fret, S${string}F$fret"

    private fun androidx.compose.ui.test.SemanticsNodeInteraction.containsColor(expected: Color): Boolean {
        val pixels = captureToImage().toPixelMap()
        for (y in 0 until pixels.height) {
            for (x in 0 until pixels.width) {
                val actual = pixels[x, y]
                if (actual.matches(expected)) {
                    return true
                }
            }
        }
        return false
    }

    /**
     * Samples the body of the 30dp note circle rather than any one pixel. This
     * stays independent of screen density and cannot mistake antialiased text
     * for an overlap-grey fill.
     */
    private fun androidx.compose.ui.test.SemanticsNodeInteraction.paintRingColorFraction(
        expected: Color,
    ): Float {
        val pixels = captureToImage().toPixelMap()
        val centerX = (pixels.width - 1) / 2f
        val centerY = (pixels.height - 1) / 2f
        val scale = minOf(pixels.width, pixels.height).toFloat()
        var matching = 0
        var sampled = 0
        for (y in 0 until pixels.height) {
            for (x in 0 until pixels.width) {
                val dx = (x - centerX) / scale
                val dy = (y - centerY) / scale
                val radiusSquared = dx * dx + dy * dy
                if (radiusSquared in 0.14f * 0.14f..0.27f * 0.27f) {
                    sampled += 1
                    if (pixels[x, y].matches(expected)) matching += 1
                }
            }
        }
        return matching.toFloat() / sampled
    }

    private fun Color.matches(expected: Color): Boolean =
        kotlin.math.abs(red - expected.red) < 0.03f &&
            kotlin.math.abs(green - expected.green) < 0.03f &&
            kotlin.math.abs(blue - expected.blue) < 0.03f
}
