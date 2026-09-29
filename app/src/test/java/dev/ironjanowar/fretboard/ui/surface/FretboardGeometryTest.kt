package dev.ironjanowar.fretboard.ui.surface

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The fretted surface's geometry.
 *
 * A07's acceptance: 25 positions per string, the open column distinct from
 * fret 1, and the last fret (24) inside the surface rather than clipped, on a
 * surface wide enough that horizontal scroll is required on a phone.
 */
class FretboardGeometryTest {

    private val lastFret = 24

    @Test
    fun `a 24-fret instrument has one open position plus 24 frets`() {
        assertEquals(25, FretboardGeometry.columnCount(lastFret))
    }

    @Test
    fun `the open column is wider than a stopped column`() {
        assertTrue(
            "the open column must not look like fret 1",
            FretboardGeometry.columnWidthDp(0) > FretboardGeometry.columnWidthDp(1),
        )
        assertEquals(FretboardGeometry.CELL_WIDTH_DP, FretboardGeometry.columnWidthDp(1))
    }

    @Test
    fun `the open column starts at the left edge and is the only open column`() {
        assertEquals(0f, FretboardGeometry.columnLeftDp(0))
        assertTrue(FretboardGeometry.isOpenColumn(0))
        for (fret in 1..lastFret) {
            assertFalse(FretboardGeometry.isOpenColumn(fret))
        }
    }

    @Test
    fun `fret 1 starts after the open column`() {
        assertEquals(FretboardGeometry.OPEN_CELL_WIDTH_DP, FretboardGeometry.columnLeftDp(1))
    }

    @Test
    fun `columns are laid out left to right without a gap or an overlap`() {
        for (fret in 1..lastFret) {
            assertEquals(
                "fret $fret must follow fret ${fret - 1}",
                FretboardGeometry.columnLeftDp(fret - 1) + FretboardGeometry.columnWidthDp(fret - 1),
                FretboardGeometry.columnLeftDp(fret),
            )
        }
    }

    @Test
    fun `the last fret lies inside the surface`() {
        val width = FretboardGeometry.surfaceWidthDp(lastFret)
        val lastLeft = FretboardGeometry.columnLeftDp(lastFret)
        assertEquals(lastLeft + FretboardGeometry.CELL_WIDTH_DP, width)
        assertTrue(lastLeft < width)
        // 25 positions do not fit a phone: the surface is wider than any screen
        // the app targets, which is why it scrolls instead of shrinking.
        assertTrue("the surface must scroll on a phone", width > 1000f)
    }

    @Test
    fun `inlay markers are one dot on the ordinary frets and two on 12 and 24`() {
        for (fret in listOf(3, 5, 7, 9, 15, 17, 19, 21)) {
            assertTrue("fret $fret carries a dot", FretboardGeometry.hasMarker(fret))
            assertFalse("fret $fret is not a double marker", FretboardGeometry.hasDoubleMarker(fret))
        }
        for (fret in listOf(12, 24)) {
            assertTrue("fret $fret carries two dots", FretboardGeometry.hasDoubleMarker(fret))
            assertFalse("fret $fret never also single", FretboardGeometry.hasMarker(fret))
        }
    }

    @Test
    fun `frets without an inlay marker carry none`() {
        for (fret in listOf(0, 1, 2, 4, 6, 8, 10, 11, 13, 14, 16, 18, 20, 22, 23)) {
            assertFalse(FretboardGeometry.hasMarker(fret))
            assertFalse(FretboardGeometry.hasDoubleMarker(fret))
        }
    }
}
