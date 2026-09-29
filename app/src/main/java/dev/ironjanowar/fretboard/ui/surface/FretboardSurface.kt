package dev.ironjanowar.fretboard.ui.surface

import androidx.compose.foundation.background
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.ironjanowar.fretboard.core.FrettedSurfaceDto
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
 */
@Composable
fun FretboardSurface(
    surface: FrettedSurfaceDto,
    lastFret: Int,
    modifier: Modifier = Modifier,
) {
    val scrollState = rememberScrollState()
    Column(
        modifier
            .horizontalScroll(scrollState)
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
            PositionCell(cell = cellsByFret[fret], fret = fret)
        }
    }
}

/** One position: the engine's note, painted with the engine's fill. */
@Composable
private fun PositionCell(cell: SurfaceCellDto?, fret: Int) {
    val paint = if (cell == null) NotePaint.None else notePaint(cell.memberships, cell.fill)
    val fillColor = when (paint) {
        is NotePaint.Colored -> Color(paint.argb)
        is NotePaint.Overlap -> Color(paint.argb)
        NotePaint.None -> null
    }
    Box(
        modifier = Modifier
            .width(FretboardGeometry.columnWidthDp(fret).dp)
            .fillMaxHeight()
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
            text = cell?.note ?: "",
            color = if (fillColor != null) BoardColors.ink else Color(0x59FFFFFF),
            fontWeight = if (fillColor != null) FontWeight.Bold else FontWeight.Normal,
            style = MaterialTheme.typography.labelMedium.copy(fontSize = 13.sp),
        )
    }
}
