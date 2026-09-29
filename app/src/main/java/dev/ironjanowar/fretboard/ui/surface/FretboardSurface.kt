package dev.ironjanowar.fretboard.ui.surface

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.ironjanowar.fretboard.core.FrettedSurfaceDto
import dev.ironjanowar.fretboard.core.PositionDto
import dev.ironjanowar.fretboard.core.SurfaceCellDto
import dev.ironjanowar.fretboard.core.SurfaceRowDto

/** The fretboard's own colours: a dark timber board with pale hardware. */
object BoardColors {
    val board = Color(0xFF3E2723)
    val openTint = Color(0xFF4E342E)
    val nut = Color(0xFFF5F5F5)
    val wire = Color(0xFF8D6E63)
    val marker = Color(0xFFBCAAA4)
    val string = Color(0xFFE0E0E0)
    val ink = Color(0xFF1B1210)

    /** The analyzer's selection marker: cyan, the palette's first colour. */
    val selection = Color(0xFF4FC3F7)

    /** A note the engine painted nothing for is still readable, never invisible. */
    val unstruckNote = Color(0x59FFFFFF)
}

/**
 * The fretted surface.
 *
 * One row per physical string and one position per fret from the open string to
 * the instrument's last fret. The engine supplies the note and the fill of every
 * position; the client supplies the geometry and the palette.
 *
 * The engine's rows are in physical string order (row 0 is the instrument's own
 * first string — its lowest-pitched one for a non-reentrant tuning, its G string
 * for a ukulele). A real fretboard diagram draws that first string at the
 * **bottom**, which is what the pinned web surface does (`string_lines/1`:
 * `visual_row = string_count - 1 - string_index`), so the rows are reversed for
 * drawing and the string lines get thicker towards the bottom.
 *
 * The open column is wider than the stopped columns and tinted, and the nut is
 * drawn on the boundary between it and fret 1, so the open string is never
 * mistaken for fret 1. Everything is scrollable horizontally: 25 positions do
 * not fit a phone, and shrinking them would be worse than scrolling.
 *
 * The visualizer is informative, so it passes no [onPositionTap] and the board
 * is not a control. The analyzer passes one, and then the whole board is one
 * half-open grid of [SurfaceInput] cells: a tap that lands outside every cell
 * does nothing, a scroll cancels the pending tap, and a tap marks its cell on
 * release. [marked] is the engine's committed selection; [paintChords] is off on
 * the analyzer so the visualizer's chords never colour the analyzer's marks.
 */
@Composable
fun FretboardSurface(
    surface: FrettedSurfaceDto,
    lastFret: Int,
    modifier: Modifier = Modifier,
    marked: Map<Int, Int> = emptyMap(),
    paintChords: Boolean = true,
    onPositionTap: ((PositionDto) -> Unit)? = null,
) {
    val scrollState = rememberScrollState()
    val stringCount = surface.rows.size
    val density = LocalDensity.current.density
    // The gesture block below outlives a recomposition (it only restarts when its
    // keys change), so the callback is read through the updated state: a tap
    // always sends its position to the handler of the *current* session, never to
    // one captured when the gesture was first installed.
    val currentTap by rememberUpdatedState(onPositionTap)
    val taps = if (onPositionTap == null) {
        Modifier
    } else {
        Modifier.pointerInput(lastFret, stringCount) {
            detectTapGestures { offset ->
                SurfaceInput.positionAt(
                    xDp = offset.x / density,
                    yDp = offset.y / density,
                    lastFret = lastFret,
                    stringCount = stringCount,
                )?.let { position -> currentTap?.invoke(position) }
            }
        }
    }

    Column(
        modifier
            .horizontalScroll(scrollState)
            .then(taps)
            .background(BoardColors.board),
    ) {
        FretNumberHeader(lastFret)
        MarkerStrip(lastFret)
        val rows = surface.rows.reversed()
        rows.forEachIndexed { visualIndex, row ->
            StringRow(
                row = row,
                lastFret = lastFret,
                visualIndex = visualIndex,
                stringIndex = stringCount - 1 - visualIndex,
                stringCount = stringCount,
                marked = marked,
                paintChords = paintChords,
                onPositionTap = onPositionTap,
            )
        }
    }
}

/** The fret numbers, one per stopped column (the open column carries none). */
@Composable
private fun FretNumberHeader(lastFret: Int) {
    Row(Modifier.height(FretboardGeometry.HEADER_HEIGHT_DP.dp)) {
        for (fret in 0..lastFret) {
            Box(
                modifier = Modifier
                    .width(FretboardGeometry.columnWidthDp(fret).dp)
                    .fillMaxHeight(),
                contentAlignment = Alignment.Center,
            ) {
                if (!FretboardGeometry.isOpenColumn(fret)) {
                    Text(
                        text = fret.toString(),
                        color = Color(0xFFFFF3E0),
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
            }
        }
    }
}

/** The inlay markers: one dot on most marked frets, two on 12 and 24. */
@Composable
private fun MarkerStrip(lastFret: Int) {
    Row(Modifier.height(FretboardGeometry.MARKER_HEIGHT_DP.dp)) {
        for (fret in 0..lastFret) {
            val dots = when {
                FretboardGeometry.hasDoubleMarker(fret) -> 2
                FretboardGeometry.hasMarker(fret) -> 1
                else -> 0
            }
            Box(
                modifier = Modifier
                    .width(FretboardGeometry.columnWidthDp(fret).dp)
                    .fillMaxHeight(),
                contentAlignment = Alignment.Center,
            ) {
                Row {
                    repeat(dots) {
                        Box(
                            Modifier
                                .size(7.dp)
                                .background(BoardColors.marker, CircleShape),
                        )
                    }
                }
            }
        }
    }
}

/** One string: its line, and every position from the open string to the last fret. */
@Composable
private fun StringRow(
    row: SurfaceRowDto,
    lastFret: Int,
    visualIndex: Int,
    stringIndex: Int,
    stringCount: Int,
    marked: Map<Int, Int>,
    paintChords: Boolean,
    onPositionTap: ((PositionDto) -> Unit)?,
) {
    val cellsByFret = row.cells.associateBy { it.fret.toInt() }
    // Thicker towards the bottom, as the pinned web surface draws them
    // (`stroke-width = 1.5 + s * 0.3` for visual row `s` from the top).
    val strokeDp = 1.5f + visualIndex * 0.3f
    Row(
        modifier = Modifier
            .height(FretboardGeometry.ROW_HEIGHT_DP.dp)
            .drawBehind {
                val y = size.height / 2f
                drawLine(
                    color = BoardColors.string,
                    start = Offset(0f, y),
                    end = Offset(size.width, y),
                    strokeWidth = strokeDp.dp.toPx(),
                    cap = StrokeCap.Round,
                )
            },
    ) {
        for (fret in 0..lastFret) {
            PositionCell(
                cell = cellsByFret[fret],
                fret = fret,
                stringIndex = stringIndex,
                stringCount = stringCount,
                marked = marked[stringIndex] == fret,
                paintChords = paintChords,
                onPositionTap = onPositionTap,
            )
        }
    }
}

/** One position: the engine's note, painted with the engine's fill. */
@Composable
private fun PositionCell(
    cell: SurfaceCellDto?,
    fret: Int,
    stringIndex: Int,
    stringCount: Int,
    marked: Boolean,
    paintChords: Boolean,
    onPositionTap: ((PositionDto) -> Unit)?,
) {
    val paint = if (cell == null || !paintChords) {
        NotePaint.None
    } else {
        notePaint(cell.memberships, cell.fill)
    }
    val fillColor = when (paint) {
        is NotePaint.Colored -> Color(paint.argb)
        is NotePaint.Overlap -> Color(paint.argb)
        NotePaint.None -> null
    }
    val note = cell?.note ?: ""
    val label = positionLabel(
        stringIndex = stringIndex,
        stringCount = stringCount,
        fret = fret,
        note = note,
    )
    val semantics = if (onPositionTap == null) {
        Modifier.semantics { contentDescription = label }
    } else {
        Modifier.semantics {
            contentDescription = label
            selected = marked
            role = Role.Button
            onClick(label) {
                onPositionTap(positionOf(stringIndex, fret))
                true
            }
        }
    }
    Box(
        modifier = Modifier
            .width(FretboardGeometry.columnWidthDp(fret).dp)
            .fillMaxHeight()
            .then(semantics)
            .drawBehind {
                when {
                    // The nut: the boundary between the open column and fret 1.
                    fret == 1 -> drawLine(
                        color = BoardColors.nut,
                        start = Offset(0f, 0f),
                        end = Offset(0f, size.height),
                        strokeWidth = FretboardGeometry.NUT_WIDTH_DP.dp.toPx(),
                    )
                    fret > 1 -> drawLine(
                        color = BoardColors.wire,
                        start = Offset(0f, 0f),
                        end = Offset(0f, size.height),
                        strokeWidth = FretboardGeometry.FRET_WIRE_WIDTH_DP.dp.toPx(),
                    )
                }
                if (marked) {
                    // The analyzer's own mark: a cyan ring, independent of any
                    // chord colour, and never conveyed by colour alone — the
                    // cell's semantics carry the selected state.
                    val radius = size.minDimension / 2f - 2.dp.toPx()
                    drawCircle(
                        color = BoardColors.selection,
                        radius = radius,
                        center = Offset(size.width / 2f, size.height / 2f),
                        style = androidx.compose.ui.graphics.drawscope.Stroke(
                            width = 3.dp.toPx(),
                        ),
                    )
                }
            }
            .background(if (FretboardGeometry.isOpenColumn(fret)) BoardColors.openTint else Color.Transparent),
        contentAlignment = Alignment.Center,
    ) {
        if (fillColor != null) {
            Box(
                Modifier
                    .size(30.dp)
                    .background(fillColor, CircleShape),
            )
        }
        Text(
            text = note,
            color = if (fillColor != null) BoardColors.ink else BoardColors.unstruckNote,
            fontWeight = if (fillColor != null) FontWeight.Bold else FontWeight.Normal,
            style = MaterialTheme.typography.labelMedium.copy(fontSize = 13.sp),
        )
    }
}

/** The engine's position type, built from the two indices the cell already holds. */
private fun positionOf(stringIndex: Int, fret: Int): PositionDto =
    PositionDto(string = stringIndex.toUByte(), fret = fret.toUByte())
