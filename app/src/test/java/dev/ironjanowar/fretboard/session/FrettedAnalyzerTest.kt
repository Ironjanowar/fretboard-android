package dev.ironjanowar.fretboard.session

import dev.ironjanowar.fretboard.core.InstrumentDto
import dev.ironjanowar.fretboard.core.InstrumentStateDto
import dev.ironjanowar.fretboard.core.PageEventDto
import dev.ironjanowar.fretboard.core.PageStateDto
import dev.ironjanowar.fretboard.core.PositionDto
import dev.ironjanowar.fretboard.core.TabDto
import dev.ironjanowar.fretboard.core.TuningDto
import dev.ironjanowar.fretboard.ui.surface.FretboardGeometry
import dev.ironjanowar.fretboard.ui.surface.SurfaceInput
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The fretted analyzer's routing.
 *
 * The client's whole share of a tap is deciding *which* cell it landed on and
 * sending the engine that position; the reducer decides the rest — the same
 * fret of the same string clears the mark, another fret of that string replaces
 * it, another string is untouched. That reducer rule is the engine's and is
 * pinned by its own tests and by the device gate; what this class pins is the
 * routing that feeds it: exactly one event per tap, the tapped position and
 * nothing else, and no event at all for a tap that owns no cell.
 */
class FrettedAnalyzerTest {

    private val guitar = PageStateDto(
        instrument = InstrumentStateDto.Fretted(
            instrument = InstrumentDto.GUITAR,
            tuning = TuningDto(byteArrayOf(45, 50, 55, 59, 64, 69), reference = "Standard"),
            selected = listOf(
                PositionDto(string = 0u, fret = 3u),
                PositionDto(string = 4u, fret = 12u),
            ),
        ),
        chords = emptyList(),
        highlight = null,
        tab = TabDto.ANALYZER,
    )
    private val piano = PageStateDto(
        instrument = InstrumentStateDto.Piano(selected = byteArrayOf()),
        chords = emptyList(),
        highlight = null,
        tab = TabDto.ANALYZER,
    )

    private val lastFret = 24
    private val stringCount = 6

    @Test
    fun `a tap on a cell routes the engine's own position`() {
        val event = SurfaceInput.tapEvent(
            xDp = openColumnCentre(),
            yDp = rowCentre(stringIndex = 0),
            lastFret = lastFret,
            stringCount = stringCount,
        )

        assertEquals(PageEventDto.ToggleNote(PositionDto(string = 0u, fret = 0u)), event)
    }

    @Test
    fun `another fret of the same string routes that fret, never the old one`() {
        // The engine replaces a string's mark when the fret differs, so the
        // route is the tapped cell and only it.
        val onFretFour = SurfaceInput.tapEvent(
            xDp = columnCentre(fret = 4),
            yDp = rowCentre(stringIndex = 0),
            lastFret = lastFret,
            stringCount = stringCount,
        )
        val onFretFive = SurfaceInput.tapEvent(
            xDp = columnCentre(fret = 5),
            yDp = rowCentre(stringIndex = 0),
            lastFret = lastFret,
            stringCount = stringCount,
        )

        assertEquals(PositionDto(string = 0u, fret = 4u), (onFretFour as PageEventDto.ToggleNote).position)
        assertEquals(PositionDto(string = 0u, fret = 5u), (onFretFive as PageEventDto.ToggleNote).position)
        assertTrue("a replacement carries no history", onFretFour != onFretFive)
    }

    @Test
    fun `the same cell always routes the same event, which is what makes a tap a toggle`() {
        val first = SurfaceInput.tapEvent(
            xDp = columnCentre(fret = 7),
            yDp = rowCentre(stringIndex = 2),
            lastFret = lastFret,
            stringCount = stringCount,
        )
        val second = SurfaceInput.tapEvent(
            xDp = columnCentre(fret = 7),
            yDp = rowCentre(stringIndex = 2),
            lastFret = lastFret,
            stringCount = stringCount,
        )

        // The client sends the same event; the engine answers the cleared
        // selection, because the mark it already holds is that same cell.
        assertEquals(first, second)
        assertEquals(
            PageEventDto.ToggleNote(PositionDto(string = 2u, fret = 7u)),
            second,
        )
    }

    @Test
    fun `a tap that owns no cell routes nothing at all`() {
        // Above the first string row, below the last one, left of the open
        // column and past the last fret: four ways to own no cell.
        val outside = listOf(
            SurfaceInput.tapEvent(10f, 0f, lastFret, stringCount),
            SurfaceInput.tapEvent(10f, 10_000f, lastFret, stringCount),
            SurfaceInput.tapEvent(-1f, rowCentre(0), lastFret, stringCount),
            SurfaceInput.tapEvent(10_000f, rowCentre(0), lastFret, stringCount),
        )

        assertTrue("no cell, no event, no dispatch: $outside", outside.all { it == null })
    }

    @Test
    fun `an out-of-range position produces no event`() {
        assertNull(
            "a string the instrument does not have is a no-op",
            SurfaceInput.toggleEvent(PositionDto(string = 6u, fret = 3u), stringCount, lastFret),
        )
        assertNull(
            "a fret past the instrument's last one is a no-op",
            SurfaceInput.toggleEvent(PositionDto(string = 0u, fret = 25u), stringCount, lastFret),
        )
        assertEquals(
            PageEventDto.ToggleNote(PositionDto(string = 5u, fret = 24u)),
            SurfaceInput.toggleEvent(PositionDto(string = 5u, fret = 24u), stringCount, lastFret),
        )
    }

    @Test
    fun `the marked cells are the engine's committed selection, one fret per string`() {
        val view = SessionView(
            state = guitar,
            instruments = emptyList(),
            qualityGroups = emptyList(),
            details = emptyList(),
            slots = emptyList(),
            surface = null,
            analysis = SessionAnalysis.Absent,
        )

        assertEquals(mapOf(0 to 3, 4 to 12), view.markedFrets())
        // The engine holds at most one fret per string; the map cannot lose a
        // string, whatever order the engine answered in.
        assertEquals(2, view.markedFrets().size)
    }

    @Test
    fun `the keyboard has no marked frets to draw`() {
        val view = SessionView(
            state = piano,
            instruments = emptyList(),
            qualityGroups = emptyList(),
            details = emptyList(),
            slots = emptyList(),
            surface = null,
            analysis = SessionAnalysis.Absent,
        )

        assertTrue(view.markedFrets().isEmpty())
    }

    @Test
    fun `every analyzer cell is a valid touch target`() {
        assertTrue(SurfaceInput.MIN_TARGET_DP >= 48f)
        assertTrue(FretboardGeometry.CELL_WIDTH_DP >= SurfaceInput.MIN_TARGET_DP)
        assertTrue(FretboardGeometry.OPEN_CELL_WIDTH_DP >= SurfaceInput.MIN_TARGET_DP)
        assertTrue(FretboardGeometry.ROW_HEIGHT_DP >= SurfaceInput.MIN_TARGET_DP)
    }

    /** The centre of a stopped column. */
    private fun columnCentre(fret: Int): Float =
        FretboardGeometry.columnLeftDp(fret) + FretboardGeometry.columnWidthDp(fret) / 2f

    private fun openColumnCentre(): Float =
        FretboardGeometry.columnWidthDp(0) / 2f

    /** The centre of the row of a physical string. */
    private fun rowCentre(stringIndex: Int): Float {
        val visualRow = stringCount - 1 - stringIndex
        return SurfaceInput.ROWS_TOP_DP +
            FretboardGeometry.ROW_HEIGHT_DP * visualRow +
            FretboardGeometry.ROW_HEIGHT_DP / 2f
    }
}
