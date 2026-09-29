package dev.ironjanowar.fretboard.ui.visualizer

/**
 * The frozen wire spelling of a chord root: the twelve sharp names the engine's
 * `PitchClass::from_str` accepts on every wire surface (core decision
 * `CORE-D06`).
 *
 * This is a wire token list, not musical logic: the client never computes a
 * pitch from it, it only sends the token back through `ChordDto(root, quality)`
 * and the engine decides whether it is valid.
 */
val ROOT_WIRE_NAMES: List<String> = listOf(
    "C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B",
)

/** One note a chord names, with the engine's interval label for it. */
data class NoteInterval(val note: String, val interval: String)

/**
 * Pair the engine's notes with its interval labels.
 *
 * A08 asks the cards to render every note–interval pair, not only the title. The
 * pairing is positional because that is what the wire carries: `note_fill`'s
 * sibling `chordDetails` answers two lists of the same length whose *order is
 * the engine's*, and the client must not re-sort one against the other (a `C9`'s
 * notes and interval labels are not in the same order as a naive sort would
 * give). A missing label on either side renders as an empty string rather than
 * silently dropping a note.
 */
fun noteIntervals(notes: List<String>, intervals: List<String>): List<NoteInterval> {
    val count = maxOf(notes.size, intervals.size)
    return (0 until count).map { index ->
        NoteInterval(
            note = notes.getOrElse(index) { "" },
            interval = intervals.getOrElse(index) { "" },
        )
    }
}

/**
 * The palette slot of the occurrence at [index], or `null` when the engine sent
 * no slot for it.
 *
 * `chordColorSlots` answers one slot per active occurrence; a card whose slot is
 * missing is painted neutrally rather than crashing the screen.
 */
fun slotFor(slots: List<ULong>, index: Int): ULong? = slots.getOrNull(index)
