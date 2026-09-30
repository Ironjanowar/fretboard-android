package dev.ironjanowar.fretboard.ui.surface

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.platform.testTag
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
    val nut = Color(0xFFF5F5F5)
    val wire = Color(0xFF8D6E63)
    val marker = Color(0xFFBCAAA4)
    val string = Color(0xFFE0E0E0)
    val ink = Color(0xFF1B1210)

    /** The analyzer's selection marker: cyan, the palette's first colour. */
    val selection = Color(0xFF4FC3F7)
}

/**
 * The fretted surface.
 *
 * Tuning notes stay fixed to the left of the wooden board while the stopped
 * frets and their number header share one horizontal scroll position. The
 * engine supplies notes and chord paint; this composable only decides which of
 * those notes the current presentation mode makes visible.
 *
 * Engine rows are reversed for drawing, so its first physical string is at the
 * bottom. In analyzer mode every cell remains a full-size accessible control;
 * only its visible note label is conditional on selection.
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
    val rows = surface.rows.reversed()
    val stringCount = rows.size
    val boardHeight = FretboardGeometry.boardHeightDp(stringCount).dp
    val density = LocalDensity.current.density
    val currentTap by rememberUpdatedState(onPositionTap)
    val boardTaps = if (onPositionTap == null) {
        Modifier
    } else {
        Modifier.pointerInput(lastFret, stringCount) {
            detectTapGestures { offset ->
                SurfaceInput.positionAt(
                    xDp = offset.x / density + FretboardGeometry.OPEN_CELL_WIDTH_DP,
                    yDp = offset.y / density + FretboardGeometry.HEADER_HEIGHT_DP,
                    lastFret = lastFret,
                    stringCount = stringCount,
                )?.let { position -> currentTap?.invoke(position) }
            }
        }
    }

    Column(modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth()) {
            Box(
                Modifier
                    .width(FretboardGeometry.OPEN_CELL_WIDTH_DP.dp)
                    .height(FretboardGeometry.HEADER_HEIGHT_DP.dp),
            )
            FretNumberHeader(
                lastFret = lastFret,
                modifier = Modifier
                    .weight(1f)
                    .horizontalScroll(scrollState, enabled = false),
            )
        }
        Row(Modifier.fillMaxWidth()) {
            TuningNotes(
                rows = rows,
                stringCount = stringCount,
                marked = marked,
                onPositionTap = onPositionTap,
            )
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(boardHeight)
                    .horizontalScroll(scrollState)
                    .testTag("fretboard-board")
                    .background(BoardColors.board),
            ) {
                Board(
                    rows = rows,
                    lastFret = lastFret,
                    stringCount = stringCount,
                    marked = marked,
                    paintChords = paintChords,
                    onPositionTap = onPositionTap,
                    modifier = boardTaps,
                )
            }
        }
    }
}

/** The fret numbers, aligned above the stopped columns. */
@Composable
private fun FretNumberHeader(lastFret: Int, modifier: Modifier = Modifier) {
    Row(modifier.height(FretboardGeometry.HEADER_HEIGHT_DP.dp)) {
        for (fret in 1..lastFret) {
            Box(
                modifier = Modifier
                    .width(FretboardGeometry.CELL_WIDTH_DP.dp)
                    .fillMaxHeight()
                    .testTag("fret-number-$fret"),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = fret.toString(),
                    color = MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.labelMedium,
                )
            }
        }
    }
}

/** Fixed open-string labels, one per physical string. */
@Composable
private fun TuningNotes(
    rows: List<SurfaceRowDto>,
    stringCount: Int,
    marked: Map<Int, Int>,
    onPositionTap: ((PositionDto) -> Unit)?,
) {
    Column(Modifier.width(FretboardGeometry.OPEN_CELL_WIDTH_DP.dp)) {
        rows.forEachIndexed { visualIndex, row ->
            val stringIndex = stringCount - 1 - visualIndex
            TuningNote(
                cell = row.cells.firstOrNull { it.fret.toInt() == 0 },
                stringIndex = stringIndex,
                stringCount = stringCount,
                marked = marked[stringIndex] == 0,
                onPositionTap = onPositionTap,
            )
        }
    }
}

/** One always-visible tuning note; in analyzer mode it is the fret-zero control. */
@Composable
private fun TuningNote(
    cell: SurfaceCellDto?,
    stringIndex: Int,
    stringCount: Int,
    marked: Boolean,
    onPositionTap: ((PositionDto) -> Unit)?,
) {
    val note = cell?.note.orEmpty()
    val position = positionOf(stringIndex, 0)
    val label = positionLabel(stringIndex, stringCount, 0, note)
    val input = if (onPositionTap == null) {
        Modifier.semantics { contentDescription = label }
    } else {
        Modifier
            .pointerInput(stringIndex, onPositionTap) {
                detectTapGestures { onPositionTap(position) }
            }
            .semantics {
                contentDescription = label
                selected = marked
                role = Role.Button
                onClick(label) {
                    onPositionTap(position)
                    true
                }
            }
    }
    Box(
        modifier = Modifier
            .width(FretboardGeometry.OPEN_CELL_WIDTH_DP.dp)
            .height(FretboardGeometry.ROW_HEIGHT_DP.dp)
            .testTag("tuning-note-$stringIndex")
            .then(input)
            .drawBehind {
                if (marked) {
                    drawCircle(
                        color = BoardColors.selection,
                        radius = 17.dp.toPx(),
                        center = Offset(size.width / 2f, size.height / 2f),
                    )
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = note,
            color = if (marked) BoardColors.ink else MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.labelMedium.copy(fontSize = 13.sp),
        )
    }
}

/** The wooden stopped-fret area, including inlays and string rows. */
@Composable
private fun Board(
    rows: List<SurfaceRowDto>,
    lastFret: Int,
    stringCount: Int,
    marked: Map<Int, Int>,
    paintChords: Boolean,
    onPositionTap: ((PositionDto) -> Unit)?,
    modifier: Modifier = Modifier,
) {
    Box(modifier) {
        InlayMarkers(lastFret, stringCount)
        Column {
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
}

/** Inlay dots occupy the board itself rather than a separate vertical strip. */
@Composable
private fun InlayMarkers(lastFret: Int, stringCount: Int) {
    val boardHeight = FretboardGeometry.boardHeightDp(stringCount).dp
    Row(Modifier.height(boardHeight)) {
        for (fret in 1..lastFret) {
            val dots = markerCount(fret)
            Column(
                modifier = Modifier
                    .width(FretboardGeometry.CELL_WIDTH_DP.dp)
                    .height(boardHeight),
                verticalArrangement = Arrangement.SpaceEvenly,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                repeat(dots) {
                    Box(
                        Modifier
                            .size(7.dp)
                            .background(BoardColors.marker, CircleShape)
                            .testTag("inlay-marker-$fret"),
                    )
                }
            }
        }
    }
}

private fun markerCount(fret: Int): Int = when {
    FretboardGeometry.hasDoubleMarker(fret) -> 2
    FretboardGeometry.hasMarker(fret) -> 1
    else -> 0
}

/** One string and every stopped position up to the last fret. */
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
        for (fret in 1..lastFret) {
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

/** One stopped position, showing a note only when the current mode calls for it. */
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
    val note = cell?.note.orEmpty()
    val label = positionLabel(stringIndex, stringCount, fret, note)
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
            .width(FretboardGeometry.CELL_WIDTH_DP.dp)
            .fillMaxHeight()
            .then(semantics)
            .drawBehind {
                if (fret == 1) {
                    drawLine(
                        color = BoardColors.nut,
                        start = Offset(0f, 0f),
                        end = Offset(0f, size.height),
                        strokeWidth = FretboardGeometry.NUT_WIDTH_DP.dp.toPx(),
                    )
                } else {
                    drawLine(
                        color = BoardColors.wire,
                        start = Offset(0f, 0f),
                        end = Offset(0f, size.height),
                        strokeWidth = FretboardGeometry.FRET_WIRE_WIDTH_DP.dp.toPx(),
                    )
                }
                if (marked) {
                    drawCircle(
                        color = BoardColors.selection,
                        radius = 17.dp.toPx(),
                        center = Offset(size.width / 2f, size.height / 2f),
                    )
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        if (fillColor != null) {
            Box(
                Modifier
                    .size(30.dp)
                    .background(fillColor, CircleShape),
            )
        }
        if ((paintChords && fillColor != null) || (!paintChords && marked)) {
            Text(
                text = note,
                color = BoardColors.ink,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.labelMedium.copy(fontSize = 13.sp),
            )
        }
    }
}

/** The engine's position type, built from the two indices the cell already holds. */
private fun positionOf(stringIndex: Int, fret: Int): PositionDto =
    PositionDto(string = stringIndex.toUByte(), fret = fret.toUByte())
