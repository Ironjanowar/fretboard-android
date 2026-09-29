package dev.ironjanowar.fretboard.ui.surface

/** The open position, spelled as the engine's own fret `0`. */
private const val OPEN_FRET: Int = 0

/**
 * The spoken label of one fretted position.
 *
 * The design asks for the instrument string number, whether the position is the
 * open string or a stopped fret, and the engine's own note name — and for the
 * analyzer's cells to be real buttons with a selected state, so a TalkBack user
 * can mark a note without seeing the board.
 *
 * `stringIndex` is the engine's physical index (0 is the instrument's first
 * string), so its spoken number is `stringCount - index`, exactly as the tuning
 * sheet labels its rows. `note` is the engine's answer and is never omitted: an
 * empty one cannot happen, and if the engine sent none the label says so rather
 * than inventing a pitch.
 */
fun positionLabel(stringIndex: Int, stringCount: Int, fret: Int, note: String): String {
    val stringNumber = stringCount - stringIndex
    val position = if (fret <= OPEN_FRET) "open string" else "fret $fret"
    val sounding = note.ifEmpty { "an unnamed note" }
    return "String $stringNumber, $position, $sounding"
}

/**
 * The spoken label of one keyboard key.
 *
 * The design asks the piano's labels to be unique and to carry the engine's own
 * note; the engine's keyboard key names the note but not its octave (the
 * artifact's `KeyboardKeyDto` carries no octave-qualified or key-kind metadata),
 * so the label names the engine's note together with the engine's own pitch
 * number, which is what makes two same-named keys in different octaves
 * distinguishable without the client computing a register of its own.
 */
fun pianoKeyLabel(note: String, pitch: Int): String {
    val sounding = note.ifEmpty { "an unnamed note" }
    return "Key $sounding, pitch $pitch"
}
