package dev.ironjanowar.fretboard.ui

import dev.ironjanowar.fretboard.core.PageEventDto
import dev.ironjanowar.fretboard.core.PositionDto
import dev.ironjanowar.fretboard.ui.surface.FretboardGeometry
import dev.ironjanowar.fretboard.ui.surface.SurfaceInput
import dev.ironjanowar.fretboard.ui.surface.positionLabel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The fretted surface's hit geometry.
 *
 * The same grid the drawing uses decides which cell a point owns, so this is the
 * test that keeps drawing, pointer input and the accessibility bounds from
 * drifting apart: 25 columns per string, a row per physical string in the
 * engine's own (reversed for drawing) order, half-open boundaries, and nothing
 * at all outside the board.
 *
 * A10's floor is also pinned here: every cell is at least 48dp wide and tall, so
 * an analyzer tap has a real target rather than a note-sized dot.
 */
class SurfaceInputTest {

    private val lastFret = 24
    private val stringCount = 6

    private fun columnCentre(fret: Int): Float =
        FretboardGeometry.columnLeftDp(fret) + FretboardGeometry.columnWidthDp(fret) / 2f

    private fun rowCentre(visualRow: Int): Float =
        SurfaceInput.ROWS_TOP_DP +
            FretboardGeometry.ROW_HEIGHT_DP * visualRow +
            FretboardGeometry.ROW_HEIGHT_DP / 2f

    @Test
    fun `every cell is at least a 48dp target`() {
        assertTrue(SurfaceInput.MIN_TARGET_DP >= 48f)
        assertTrue(FretboardGeometry.CELL_WIDTH_DP >= SurfaceInput.MIN_TARGET_DP)
        assertTrue(FretboardGeometry.OPEN_CELL_WIDTH_DP >= SurfaceInput.MIN_TARGET_DP)
        assertTrue(FretboardGeometry.ROW_HEIGHT_DP >= SurfaceInput.MIN_TARGET_DP)
    }

    @Test
    fun `the first and last column are owned by their own cells`() {
        assertEquals(0, SurfaceInput.fretAt(0f, lastFret))
        assertEquals(0, SurfaceInput.fretAt(FretboardGeometry.OPEN_CELL_WIDTH_DP - 0.01f, lastFret))
        assertEquals(1, SurfaceInput.fretAt(FretboardGeometry.OPEN_CELL_WIDTH_DP, lastFret))

        val lastLeft = FretboardGeometry.columnLeftDp(lastFret)
        assertEquals(lastFret, SurfaceInput.fretAt(lastLeft, lastFret))
        assertEquals(
            lastFret,
            SurfaceInput.fretAt(FretboardGeometry.surfaceWidthDp(lastFret) - 0.01f, lastFret),
        )
    }

    @Test
    fun `the boundaries are half-open, so exactly one cell owns an edge`() {
        // The right edge of fret 2 is the left edge of fret 3 and belongs to fret 3.
        val boundary = FretboardGeometry.columnLeftDp(3)
        assertEquals(2, SurfaceInput.fretAt(boundary - 0.01f, lastFret))
        assertEquals(3, SurfaceInput.fretAt(boundary, lastFret))

        val rowBoundary = SurfaceInput.ROWS_TOP_DP + FretboardGeometry.ROW_HEIGHT_DP
        assertEquals(0, SurfaceInput.visualRowAt(rowBoundary - 0.01f, stringCount))
        assertEquals(1, SurfaceInput.visualRowAt(rowBoundary, stringCount))
    }

    @Test
    fun `the open column is its own cell, not fret 1`() {
        assertEquals(0, SurfaceInput.fretAt(1f, lastFret))
        assertTrue(FretboardGeometry.columnWidthDp(0) > FretboardGeometry.columnWidthDp(1))
    }

    @Test
    fun `nothing owns a point outside the board`() {
        assertNull(SurfaceInput.fretAt(-1f, lastFret))
        assertNull(SurfaceInput.fretAt(FretboardGeometry.surfaceWidthDp(lastFret), lastFret))
        assertNull(SurfaceInput.fretAt(columnCentre(0), -1))
        assertNull(SurfaceInput.visualRowAt(SurfaceInput.ROWS_TOP_DP - 0.01f, stringCount))
        assertNull(SurfaceInput.visualRowAt(rowCentre(stringCount), stringCount))
        assertNull(SurfaceInput.visualRowAt(rowCentre(0), 0))
    }

    @Test
    fun `the rows are the engine's rows reversed, so the first string is at the bottom`() {
        // Visual row 0 is the top of the board and the instrument's *last*
        // physical string; the instrument's first string (index 0) is the last
        // visual row, as a real diagram draws it.
        val top = SurfaceInput.positionAt(
            columnCentre(1),
            rowCentre(0),
            lastFret,
            stringCount,
        )
        val bottom = SurfaceInput.positionAt(
            columnCentre(1),
            rowCentre(stringCount - 1),
            lastFret,
            stringCount,
        )

        assertEquals(PositionDto(string = 5u, fret = 1u), top)
        assertEquals(PositionDto(string = 0u, fret = 1u), bottom)
    }

    @Test
    fun `a short instrument uses only its own rows`() {
        // The ukulele has four strings and 15 frets: its own top row is physical
        // string 3, and there is no fifth row and no sixteenth fret.
        assertEquals(
            PositionDto(string = 3u, fret = 15u),
            SurfaceInput.positionAt(columnCentre(15), rowCentre(0), 15, 4),
        )
        assertEquals(
            PositionDto(string = 0u, fret = 15u),
            SurfaceInput.positionAt(columnCentre(15), rowCentre(3), 15, 4),
        )
        assertNull("there is no fifth row", SurfaceInput.positionAt(columnCentre(15), rowCentre(4), 15, 4))
        assertNull("and no sixteenth fret", SurfaceInput.positionAt(columnCentre(16), rowCentre(0), 15, 4))
    }

    @Test
    fun `a tap owns the whole cell, wherever inside it lands`() {
        val cell = PositionDto(string = 2u, fret = 1u)
        val visualRow = stringCount - 1 - cell.string.toInt()
        val top = SurfaceInput.ROWS_TOP_DP + FretboardGeometry.ROW_HEIGHT_DP * visualRow
        val left = FretboardGeometry.columnLeftDp(cell.fret.toInt())

        val corners = listOf(
            left + 0.01f to top + 0.01f,
            left + FretboardGeometry.CELL_WIDTH_DP - 0.01f to top + 0.01f,
            left + 0.01f to top + FretboardGeometry.ROW_HEIGHT_DP - 0.01f,
            left + FretboardGeometry.CELL_WIDTH_DP / 2f to top + FretboardGeometry.ROW_HEIGHT_DP / 2f,
        )

        corners.forEach { (x, y) ->
            assertEquals(
                "the whole cell is one target, not a note-sized dot ($x, $y)",
                PageEventDto.ToggleNote(cell),
                SurfaceInput.tapEvent(x, y, lastFret, stringCount),
            )
        }
    }

    @Test
    fun `the header and the marker strip own no cell`() {
        val fret = columnCentre(3)
        assertNull(SurfaceInput.tapEvent(fret, 0f, lastFret, stringCount))
        assertNull(
            SurfaceInput.tapEvent(
                fret,
                FretboardGeometry.HEADER_HEIGHT_DP + FretboardGeometry.MARKER_HEIGHT_DP - 0.01f,
                lastFret,
                stringCount,
            ),
        )
        assertEquals(
            PageEventDto.ToggleNote(PositionDto(string = 5u, fret = 3u)),
            SurfaceInput.tapEvent(fret, SurfaceInput.ROWS_TOP_DP + 1f, lastFret, stringCount),
        )
    }

    @Test
    fun `a spoken label names the string, the position and the engine's note`() {
        assertEquals(
            "String 6, open string, E",
            positionLabel(stringIndex = 0, stringCount = 6, fret = 0, note = "E"),
        )
        assertEquals(
            "String 3, fret 2, A",
            positionLabel(stringIndex = 3, stringCount = 6, fret = 2, note = "A"),
        )
        // The engine's own note, never a client-derived one; a missing note is
        // said out loud rather than left blank.
        assertEquals(
            "String 1, fret 24, an unnamed note",
            positionLabel(stringIndex = 5, stringCount = 6, fret = 24, note = ""),
        )
    }
}
