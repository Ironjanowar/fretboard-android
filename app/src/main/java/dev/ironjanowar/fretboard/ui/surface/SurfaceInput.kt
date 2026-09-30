package dev.ironjanowar.fretboard.ui.surface

import dev.ironjanowar.fretboard.core.PageEventDto
import dev.ironjanowar.fretboard.core.PositionDto

/**
 * Where a tap on the fretted surface lands.
 *
 * The surface is drawn as a `FretboardGeometry` grid — the fret-number header,
 * then one row per physical string, reversed so the instrument's first string
 * is at the bottom — and this is the same grid read
 * backwards: one function decides which cell a point owns, and drawing, pointer
 * input and the accessibility bounds all use it. Nothing here is musical: a cell
 * is a (string, fret) pair, and the note it carries is the engine's.
 *
 * Ownership is half-open on both axes: the left and top edges belong to the
 * cell, the right and bottom edges belong to the next one. A point outside the
 * grid owns nothing and produces no action at all.
 */
object SurfaceInput {

    /** Distance from the top of the surface to the first string row. */
    val ROWS_TOP_DP: Float = FretboardGeometry.HEADER_HEIGHT_DP

    /** The minimum touch target A10 pins; every cell is at least this wide and tall. */
    const val MIN_TARGET_DP: Float = 48f

    /** The fret column a horizontal offset owns, or `null` outside the columns. */
    fun fretAt(xDp: Float, lastFret: Int): Int? {
        if (lastFret < 0 || xDp < 0f) return null
        for (fret in 0..lastFret) {
            val left = FretboardGeometry.columnLeftDp(fret)
            val right = left + FretboardGeometry.columnWidthDp(fret)
            if (xDp >= left && xDp < right) return fret
        }
        return null
    }

    /** The visual row (0 at the top) a vertical offset owns, or `null` outside. */
    fun visualRowAt(yDp: Float, stringCount: Int): Int? {
        if (stringCount <= 0 || yDp < ROWS_TOP_DP) return null
        val row = ((yDp - ROWS_TOP_DP) / FretboardGeometry.ROW_HEIGHT_DP).toInt()
        return if (row in 0 until stringCount) row else null
    }

    /**
     * The position a point owns, in the engine's own coordinates.
     *
     * The visual rows are the engine's rows reversed (the drawing does the same),
     * so visual row 0 is physical string `stringCount - 1`.
     */
    fun positionAt(
        xDp: Float,
        yDp: Float,
        lastFret: Int,
        stringCount: Int,
    ): PositionDto? {
        if (stringCount <= 0) return null
        val fret = fretAt(xDp, lastFret) ?: return null
        val row = visualRowAt(yDp, stringCount) ?: return null
        val stringIndex = stringCount - 1 - row
        if (stringIndex < 0 || stringIndex >= stringCount) return null
        return PositionDto(string = stringIndex.toUByte(), fret = fret.toUByte())
    }

    /**
     * The event one tap produces, or `null` when the tap owns no cell.
     *
     * A tap on a marked cell and a tap on another fret of the same string both
     * send the same event: the engine's reducer decides whether that clears the
     * mark (same fret) or moves it (another fret). The client never decides.
     */
    fun tapEvent(
        xDp: Float,
        yDp: Float,
        lastFret: Int,
        stringCount: Int,
    ): PageEventDto? = positionAt(xDp, yDp, lastFret, stringCount)?.let(PageEventDto::ToggleNote)

    /**
     * The event for one position, used by the accessibility action.
     *
     * An index the instrument does not have produces no event: the tap is a
     * no-op rather than an impossible position sent to the engine.
     */
    fun toggleEvent(position: PositionDto, stringCount: Int, lastFret: Int): PageEventDto? {
        val string = position.string.toInt()
        val fret = position.fret.toInt()
        if (string !in 0 until stringCount) return null
        if (fret !in 0..lastFret) return null
        return PageEventDto.ToggleNote(position)
    }
}
