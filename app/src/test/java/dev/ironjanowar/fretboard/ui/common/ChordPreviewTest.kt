package dev.ironjanowar.fretboard.ui.common

import dev.ironjanowar.fretboard.core.ChordDto
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The draft preview's colour projection.
 *
 * A draft is not a committed page, so the engine exposes no slots for it; the
 * preview therefore projects them with the engine's own committed-page rule
 * (first appearance takes the next slot, later occurrences of the same identity
 * share it) and the client's palette. These tests pin the projection, not any
 * musical value: every chord below is a hand-written pair.
 */
class ChordPreviewTest {

    @Test
    fun `an identity shares the slot of its first appearance`() {
        val chords = listOf(
            ChordDto("C", "major"),
            ChordDto("G", "major"),
            ChordDto("C", "major"),
            ChordDto("F", "major"),
            ChordDto("G", "major"),
        )

        assertEquals(listOf(0uL, 1uL, 0uL, 2uL, 1uL), chordPreviewSlots(chords))
    }

    @Test
    fun `distinct identities take the next slot in the engine's order`() {
        val chords = listOf(
            ChordDto("C", "major"),
            ChordDto("D", "minor"),
            ChordDto("B", "dim"),
        )

        assertEquals(listOf(0uL, 1uL, 2uL), chordPreviewSlots(chords))
    }

    @Test
    fun `equal notes under different identities are different slots`() {
        // `C6` and `Amin7` name the same notes but are different chords, so the
        // projection keys on the identity pair, not on a pitch set.
        val chords = listOf(ChordDto("C", "6"), ChordDto("A", "min7"))

        assertEquals(listOf(0uL, 1uL), chordPreviewSlots(chords))
    }

    @Test
    fun `an empty preview has no slots`() {
        assertEquals(emptyList<ULong>(), chordPreviewSlots(emptyList()))
    }
}
