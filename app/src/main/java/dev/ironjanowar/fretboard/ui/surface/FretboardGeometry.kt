package dev.ironjanowar.fretboard.ui.surface

/**
 * The fretted surface's geometry, in density-independent units.
 *
 * Kotlin owns where a position sits; the engine owns which note it carries. A
 * position is a (string row, fret column) pair. Column 0 is the open string and
 * is deliberately wider than every stopped column, so it reads as its own column
 * rather than as a fret: the nut is drawn on the boundary between the open
 * column and fret 1.
 *
 * The numbers are chosen so that a 24-fret instrument is legible and scrollable
 * on a phone (25 columns do not fit), never scaled down until the positions
 * become untouchable: every cell is at least `SurfaceInput.MIN_TARGET_DP` wide
 * and tall, so the analyzer's whole cells are valid touch targets (A10).
 */
object FretboardGeometry {

    /** Width of a stopped column (fret 1..last); an analyzer cell, so at least 48dp. */
    const val CELL_WIDTH_DP: Float = 48f

    /** Width of the open-string column: wider, so it is visibly not a fret. */
    const val OPEN_CELL_WIDTH_DP: Float = 66f

    /** Height of one string row; an analyzer cell, so at least 48dp. */
    const val ROW_HEIGHT_DP: Float = 48f

    /** Height of the fret-number header above the first string. */
    const val HEADER_HEIGHT_DP: Float = 26f

    /** Stroke width of the nut, at the open column's right edge. */
    const val NUT_WIDTH_DP: Float = 8f

    /** Stroke width of an ordinary fret wire. */
    const val FRET_WIRE_WIDTH_DP: Float = 2f

    /** Fret wires carrying one inlay dot. */
    val MARKER_FRETS: Set<Int> = setOf(3, 5, 7, 9, 15, 17, 19, 21)

    /** Fret wires carrying two inlay dots. */
    val DOUBLE_MARKER_FRETS: Set<Int> = setOf(12, 24)

    /** Columns on a string: the open string plus one per fret. */
    fun columnCount(lastFret: Int): Int = lastFret + 1

    /** The width of the column for [fret]; fret 0 is the open column. */
    fun columnWidthDp(fret: Int): Float =
        if (isOpenColumn(fret)) OPEN_CELL_WIDTH_DP else CELL_WIDTH_DP

    /** Left edge of the column for [fret], from the surface's left edge. */
    fun columnLeftDp(fret: Int): Float {
        var left = 0f
        for (column in 0 until fret) {
            left += columnWidthDp(column)
        }
        return left
    }

    /** Total scrollable width of a surface whose last fret is [lastFret]. */
    fun surfaceWidthDp(lastFret: Int): Float = columnLeftDp(lastFret) + columnWidthDp(lastFret)

    /** Total height of the wooden board for [stringCount] string rows. */
    fun boardHeightDp(stringCount: Int): Float = stringCount * ROW_HEIGHT_DP

    /** True for the open-string column, which is not a fret. */
    fun isOpenColumn(fret: Int): Boolean = fret == 0

    /** True when the fret wire at [fret] carries one inlay dot. */
    fun hasMarker(fret: Int): Boolean = fret in MARKER_FRETS

    /** True when the fret wire at [fret] carries two inlay dots. */
    fun hasDoubleMarker(fret: Int): Boolean = fret in DOUBLE_MARKER_FRETS
}
