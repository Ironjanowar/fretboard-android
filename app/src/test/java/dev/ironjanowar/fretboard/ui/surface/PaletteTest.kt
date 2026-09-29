package dev.ironjanowar.fretboard.ui.surface

import dev.ironjanowar.fretboard.core.NoteFillDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The client palette and the note paint.
 *
 * The palette is the core repository's frozen oracle fixture
 * (`fixtures/oracle/surfaces.jsonl`), so these assertions are the pinned hexes
 * themselves, and the wrap is the baseline's `chord_color/2` behaviour: slot 8
 * is slot 0's colour again (fixture `chord_color/8`).
 */
class PaletteTest {

    @Test
    fun `the palette is the eight frozen chord colours in order`() {
        assertEquals(8, SurfacePalette.argb.size)
        assertEquals(
            listOf(
                0xFF4FC3F7.toInt(),
                0xFFFF8A65.toInt(),
                0xFF81C784.toInt(),
                0xFFBA68C8.toInt(),
                0xFFFFD54F.toInt(),
                0xFF4DB6AC.toInt(),
                0xFFF06292.toInt(),
                0xFF7986CB.toInt(),
            ),
            SurfacePalette.argb.toList(),
        )
        assertEquals(0xFF9E9E9E.toInt(), SurfacePalette.overlapArgb)
    }

    @Test
    fun `every slot of the first cycle indexes its own colour`() {
        for (slot in 0 until SurfacePalette.argb.size) {
            assertEquals(
                SurfacePalette.argb[slot],
                SurfacePalette.colorForSlot(slot.toULong()),
            )
        }
    }

    @Test
    fun `a slot past the palette length wraps instead of failing`() {
        // chord_color/8 -> #4FC3F7 and chord_color/9 -> #FF8A65 in the fixture.
        assertEquals(SurfacePalette.argb[0], SurfacePalette.colorForSlot(8u))
        assertEquals(SurfacePalette.argb[1], SurfacePalette.colorForSlot(9u))
        assertEquals(SurfacePalette.argb[0], SurfacePalette.colorForSlot(16u))
    }

    @Test
    fun `an unclaimed note is painted nothing, not the overlap grey`() {
        // The contract answers Overlap for "no membership" as well as for several
        // distinct memberships (`note_fill/no-memberships` in the fixture), so the
        // empty membership list is what separates the two cases here.
        assertEquals(NotePaint.None, notePaint(emptyList(), NoteFillDto.Overlap))
    }

    @Test
    fun `a note claimed by one chord takes that chord's slot colour`() {
        val paint = notePaint(listOf(0u), NoteFillDto.Slot(0u))
        assertEquals(NotePaint.Colored(SurfacePalette.argb[0]), paint)
    }

    @Test
    fun `a repeated occurrence keeps the first occurrence's colour`() {
        // chordColorSlots gives both copies of one chord the same slot, so two
        // memberships that are one identity paint that identity's colour.
        val paint = notePaint(listOf(0u, 0u), NoteFillDto.Slot(0u))
        assertEquals(NotePaint.Colored(SurfacePalette.argb[0]), paint)
    }

    @Test
    fun `a note claimed by several distinct chords is the overlap grey`() {
        val paint = notePaint(listOf(0u, 1u), NoteFillDto.Overlap)
        assertEquals(NotePaint.Overlap(SurfacePalette.overlapArgb), paint)
    }

    @Test
    fun `a slot past the palette length still paints that wrapped colour`() {
        val paint = notePaint(listOf(8u), NoteFillDto.Slot(8u))
        assertEquals(NotePaint.Colored(SurfacePalette.argb[0]), paint)
    }

    @Test
    fun `an overlap answer with many memberships never becomes a slot colour`() {
        val paint = notePaint(listOf(0u, 1u, 0u), NoteFillDto.Overlap)
        assertTrue(paint is NotePaint.Overlap)
    }
}
