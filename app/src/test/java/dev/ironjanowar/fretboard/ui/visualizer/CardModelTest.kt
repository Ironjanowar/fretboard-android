package dev.ironjanowar.fretboard.ui.visualizer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The card's own model: the note–interval pairs and the occurrence slots.
 *
 * A08 asks the cards to render every note with its interval role, not only the
 * title, so the pairing is pinned here — positionally, in the engine's own order.
 */
class CardModelTest {

    @Test
    fun `a triad pairs every note with its interval label`() {
        val pairs = noteIntervals(
            notes = listOf("C", "E", "G"),
            intervals = listOf("Root", "Major 3rd", "Perfect 5th"),
        )
        assertEquals(
            listOf(
                NoteInterval("C", "Root"),
                NoteInterval("E", "Major 3rd"),
                NoteInterval("G", "Perfect 5th"),
            ),
            pairs,
        )
    }

    @Test
    fun `the pairing keeps the engine's order, it does not sort`() {
        // A C9's notes and interval labels are not in ascending order; pairing
        // them positionally is what the wire means, and a client-side sort would
        // silently mismatch every pair.
        val pairs = noteIntervals(
            notes = listOf("C", "D", "G", "A#", "E"),
            intervals = listOf("Root", "Major 2nd", "Perfect 5th", "Minor 7th", "Major 3rd"),
        )
        assertEquals(NoteInterval("D", "Major 2nd"), pairs[1])
        assertEquals(NoteInterval("A#", "Minor 7th"), pairs[3])
        assertEquals(NoteInterval("E", "Major 3rd"), pairs[4])
    }

    @Test
    fun `a note without an interval label is still rendered`() {
        val pairs = noteIntervals(notes = listOf("C", "E"), intervals = listOf("Root"))
        assertEquals(listOf(NoteInterval("C", "Root"), NoteInterval("E", "")), pairs)
    }

    @Test
    fun `an interval label without a note is still rendered`() {
        val pairs = noteIntervals(notes = listOf("C"), intervals = listOf("Root", "Major 3rd"))
        assertEquals(listOf(NoteInterval("C", "Root"), NoteInterval("", "Major 3rd")), pairs)
    }

    @Test
    fun `no chord details render no pairs`() {
        assertEquals(emptyList<NoteInterval>(), noteIntervals(emptyList(), emptyList()))
    }

    @Test
    fun `a slot is read per occurrence index`() {
        val slots = listOf(0uL, 1uL, 0uL)
        assertEquals(0uL, slotFor(slots, 0))
        assertEquals(1uL, slotFor(slots, 1))
        // The repeated occurrence shares the first occurrence's slot.
        assertEquals(0uL, slotFor(slots, 2))
    }

    @Test
    fun `an occurrence with no slot answers null instead of a wrong index`() {
        assertNull(slotFor(listOf(0uL), 1))
        assertNull(slotFor(emptyList(), 0))
    }
}
