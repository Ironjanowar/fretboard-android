package dev.ironjanowar.fretboard.ui.surface

import dev.ironjanowar.fretboard.core.NoteFillDto

/**
 * The frozen client palette.
 *
 * Where it comes from: the core repository's frozen oracle fixture
 * `fixtures/oracle/surfaces.jsonl` — the `colors` array of its `note_fill` and
 * `chord_color` records, which is the pinned Elixir baseline's own palette:
 * `#4FC3F7 #FF8A65 #81C784 #BA68C8 #FFD54F #4DB6AC #F06292 #7986CB`, plus the
 * neutral overlap grey `#9E9E9E`.
 *
 * Kotlin owns the colour and the wrap, not the music. The engine answers with
 * raw occurrence *slots* (a `ULong` per chord occurrence, repeats sharing their
 * first occurrence's slot), and the baseline's `chord_color/2` is
 * `Enum.at(colors, index)` i.e. `index rem palette length`. Indexing and
 * wrapping are therefore the client's job: a slot equal to or greater than the
 * palette length is not an error, it wraps.
 */
object SurfacePalette {

    /** The eight frozen chord colours, in catalog order. */
    val argb: IntArray = intArrayOf(
        0xFF4FC3F7.toInt(),
        0xFFFF8A65.toInt(),
        0xFF81C784.toInt(),
        0xFFBA68C8.toInt(),
        0xFFFFD54F.toInt(),
        0xFF4DB6AC.toInt(),
        0xFFF06292.toInt(),
        0xFF7986CB.toInt(),
    )

    /** The neutral colour a note claimed by more than one distinct chord gets. */
    val overlapArgb: Int = 0xFF9E9E9E.toInt()

    /**
     * The colour of one engine slot, wrapping around the palette.
     *
     * `slot rem length`: slot 8 is the same colour as slot 0, slot 9 the same as
     * slot 1, exactly as the fixture's `chord_color/8` and `chord_color/9` pin.
     */
    fun colorForSlot(slot: ULong): Int = argb[(slot % argb.size.toULong()).toInt()]
}

/**
 * How a position or key is painted.
 *
 * The engine's `fill` alone cannot distinguish "no chord claims this note" from
 * "several distinct chords claim it": the contract answers `Overlap` for **both**
 * (fixture `note_fill/no-memberships`). The distinction is recoverable because
 * the surface also carries `memberships`, so the client paints nothing for an
 * empty membership list and the overlap grey only when the note really is
 * claimed by more than one distinct chord.
 */
sealed interface NotePaint {
    /** No active chord claims the note: leave the position plain. */
    data object None : NotePaint

    /** Exactly one chord claims the note: that chord's palette colour. */
    data class Colored(val argb: Int) : NotePaint

    /** Several distinct chords claim the note (or a highlighted one does not): grey. */
    data class Overlap(val argb: Int) : NotePaint
}

/**
 * Resolve what colour a surface position gets.
 *
 * [memberships] is the engine's own list, in active order with repeats kept.
 */
fun notePaint(memberships: List<ULong>, fill: NoteFillDto): NotePaint = when {
    memberships.isEmpty() -> NotePaint.None
    fill is NoteFillDto.Slot -> NotePaint.Colored(SurfacePalette.colorForSlot(fill.slot))
    else -> NotePaint.Overlap(SurfacePalette.overlapArgb)
}
