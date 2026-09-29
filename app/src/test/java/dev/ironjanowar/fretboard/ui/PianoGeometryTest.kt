package dev.ironjanowar.fretboard.ui

import dev.ironjanowar.fretboard.ui.surface.PianoGeometry
import dev.ironjanowar.fretboard.ui.surface.pianoKeyLabel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The keyboard's layout and its one hit grid.
 *
 * The same grid the drawing uses decides which key a point owns, so this is the
 * test that keeps drawing, pointer input and the accessibility bounds from
 * drifting apart: the engine's own key order left to right, 21 white and 15
 * black keys, the pinned proportional black offsets, half-open boundaries,
 * black-over-white ownership, nothing at all outside the keyboard, and a 48dp
 * floor on every key.
 *
 * The musical facts are the engine's and are fed in as its own surface
 * ([frozenKeyboard]); what is asserted here is the client's layout and the
 * client's reading of the engine's note spelling.
 */
class PianoGeometryTest {

    private val surface = frozenKeyboard()
    private val layout = PianoGeometry.layout(surface)

    private val whiteWidth = PianoGeometry.WHITE_WIDTH_DP
    private val blackHeight = PianoGeometry.BLACK_HEIGHT_DP
    private val whiteHeight = PianoGeometry.WHITE_HEIGHT_DP

    /** A y inside the whites but below every black key, where only a white can own. */
    private val belowTheBlacks = blackHeight + 1f

    @Test
    fun `the layout keeps the engine's own key order and count`() {
        // 36 keys, one per pitch of the engine's frozen range, in the engine's
        // ascending order — the order is the drawing order, never re-sorted and
        // never reversed.
        assertEquals(36, layout.size)
        assertEquals(KEYBOARD_RANGE.toList(), layout.map { it.pitch })
        assertEquals(
            KEYBOARD_RANGE.map { pitch -> SHARP_NOTE_NAMES[(pitch - 48) % 12] },
            layout.map { it.note },
        )
    }

    @Test
    fun `the keyboard is 21 white keys and 15 black keys`() {
        assertEquals(21, layout.count { !it.black })
        assertEquals(15, layout.count { it.black })
    }

    @Test
    fun `the engine's first key is the leftmost white key and its last the rightmost`() {
        assertEquals(48, layout.first().pitch)
        assertFalse("the engine's range starts on a white key", layout.first().black)
        assertEquals(83, layout.last().pitch)

        val whites = layout.filter { !it.black }
        assertEquals(48, whites.first().pitch)
        assertEquals(83, whites.last().pitch)
        // The last white key ends exactly at the surface's own width.
        assertEquals(PianoGeometry.surfaceWidthDp(layout), whites.last().leftDp + whiteWidth, 0.001f)
    }

    @Test
    fun `white keys are laid out left to right without a gap or an overlap`() {
        val whites = layout.filter { !it.black }
        whites.forEachIndexed { ordinal, key ->
            assertEquals("white key $ordinal", ordinal * whiteWidth, key.leftDp, 0.001f)
        }
    }

    @Test
    fun `the black offsets are the pinned web fractions`() {
        // `piano_keyboard.ex`'s `@black_offsets`, by the engine's own note name.
        assertEquals(0.62f, PianoGeometry.BLACK_OFFSETS.getValue("C#"), 0.0001f)
        assertEquals(0.81f, PianoGeometry.BLACK_OFFSETS.getValue("D#"), 0.0001f)
        assertEquals(0.58f, PianoGeometry.BLACK_OFFSETS.getValue("F#"), 0.0001f)
        assertEquals(0.71f, PianoGeometry.BLACK_OFFSETS.getValue("G#"), 0.0001f)
        assertEquals(0.86f, PianoGeometry.BLACK_OFFSETS.getValue("A#"), 0.0001f)
        assertEquals(5, PianoGeometry.BLACK_OFFSETS.size)
    }

    @Test
    fun `a black key sits at the engine's own offset over its lower white key`() {
        layout.forEachIndexed { index, key ->
            if (!key.black) return@forEachIndexed
            val lowerWhite = layout.take(index).count { !it.black } - 1
            val offset = PianoGeometry.BLACK_OFFSETS.getValue(key.note)
            assertEquals(
                "the ${key.note} key is placed at the pinned web offset",
                (lowerWhite + offset) * whiteWidth,
                key.leftDp,
                0.001f,
            )
        }
    }

    @Test
    fun `the named black keys land on the pinned positions`() {
        // The first octave's black keys, at absolute positions, so the layout
        // formula cannot be satisfied by a self-consistent wrong offset table.
        val cSharp = layout.first { it.note == "C#" }
        val dSharp = layout.first { it.note == "D#" }
        val fSharp = layout.first { it.note == "F#" }

        assertEquals(0.62f * whiteWidth, cSharp.leftDp, 0.001f) // over the first C
        assertEquals(1.81f * whiteWidth, dSharp.leftDp, 0.001f) // over the first D
        assertEquals(3.58f * whiteWidth, fSharp.leftDp, 0.001f) // over the first F
    }

    @Test
    fun `a black key is narrower and shorter than a white key`() {
        assertTrue(PianoGeometry.BLACK_WIDTH_DP < whiteWidth)
        assertTrue(blackHeight < whiteHeight)
        assertTrue(PianoGeometry.BLACK_WIDTH_DP > 0f)
    }

    @Test
    fun `every key reaches the 48dp floor`() {
        assertTrue(PianoGeometry.MIN_TARGET_DP >= 48f)
        assertTrue(PianoGeometry.BLACK_WIDTH_DP >= PianoGeometry.MIN_TARGET_DP)
        assertTrue(PianoGeometry.WHITE_WIDTH_DP >= PianoGeometry.MIN_TARGET_DP)
        assertTrue(PianoGeometry.BLACK_HEIGHT_DP >= PianoGeometry.MIN_TARGET_DP)
        assertTrue(PianoGeometry.WHITE_HEIGHT_DP >= PianoGeometry.MIN_TARGET_DP)
        // The narrowest key is the black one, and it is exactly at the floor.
        assertEquals(48f, PianoGeometry.BLACK_WIDTH_DP, 0.001f)
    }

    @Test
    fun `the boundaries are half-open, so exactly one key owns an edge`() {
        // The right edge of the first white key is the left edge of the next.
        assertEquals(48, PianoGeometry.keyAt(layout, 0f, belowTheBlacks)?.pitch)
        assertEquals(48, PianoGeometry.keyAt(layout, whiteWidth - 0.01f, belowTheBlacks)?.pitch)
        assertEquals(50, PianoGeometry.keyAt(layout, whiteWidth, belowTheBlacks)?.pitch)
    }

    @Test
    fun `nothing owns a point outside the keyboard`() {
        assertNull(PianoGeometry.keyAt(layout, -1f, belowTheBlacks))
        assertNull(PianoGeometry.keyAt(layout, 10f, -1f))
        assertNull(PianoGeometry.keyAt(layout, PianoGeometry.surfaceWidthDp(layout), belowTheBlacks))
        assertNull(PianoGeometry.keyAt(layout, 10f, whiteHeight))
        assertNull(PianoGeometry.keyAt(emptyList(), 10f, 10f))
    }

    @Test
    fun `a black key owns its whole rectangle, over the white key beneath it`() {
        val cSharp = layout.first { it.note == "C#" }
        val centre = cSharp.leftDp + cSharp.widthDp / 2f

        assertEquals(49, PianoGeometry.keyAt(layout, centre, 0f)?.pitch)
        assertEquals(49, PianoGeometry.keyAt(layout, centre, blackHeight - 0.01f)?.pitch)
        // Below the black key's bottom edge the white it overlaps owns the point.
        val below = PianoGeometry.keyAt(layout, centre, blackHeight)
        assertNotNull(below)
        assertEquals("the white key under C# is C", 48, below?.pitch)
    }

    @Test
    fun `the white hit region excludes the black overlay`() {
        val dSharp = layout.first { it.note == "D#" }
        val centre = dSharp.leftDp + dSharp.widthDp / 2f
        // Inside the black key's vertical span the black key owns the point, not
        // the white key underneath it.
        assertEquals(51, PianoGeometry.keyAt(layout, centre, 1f)?.pitch)
        assertEquals(51, PianoGeometry.keyAt(layout, centre, blackHeight / 2f)?.pitch)
    }

    @Test
    fun `the whole black key is one target, wherever inside it lands`() {
        val fSharp = layout.first { it.note == "F#" }
        val corners = listOf(
            fSharp.leftDp + 0.01f to 0.01f,
            fSharp.leftDp + fSharp.widthDp - 0.01f to 0.01f,
            fSharp.leftDp + 0.01f to blackHeight - 0.01f,
            fSharp.leftDp + fSharp.widthDp / 2f to blackHeight / 2f,
        )
        corners.forEach { (x, y) ->
            assertEquals(
                "the whole key is one target ($x, $y)",
                fSharp.pitch,
                PianoGeometry.keyAt(layout, x, y)?.pitch,
            )
        }
    }

    @Test
    fun `a spoken label names the engine's note and its own pitch`() {
        assertEquals("Key C, pitch 48", pianoKeyLabel("C", 48))
        assertEquals("Key C#, pitch 49", pianoKeyLabel("C#", 49))
        // Two same-named keys in different octaves stay distinguishable without
        // the client inventing a register of its own.
        assertTrue(pianoKeyLabel("C", 60) != pianoKeyLabel("C", 48))
        assertEquals("Key an unnamed note, pitch 48", pianoKeyLabel("", 48))
    }
}
