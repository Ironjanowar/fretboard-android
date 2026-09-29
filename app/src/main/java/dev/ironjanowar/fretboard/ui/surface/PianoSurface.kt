package dev.ironjanowar.fretboard.ui.surface

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
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
import dev.ironjanowar.fretboard.core.KeyboardSurfaceDto

/** The keyboard's own colours: ivory whites over a near-black body. */
object PianoColors {
    val white = Color(0xFFF5F5F5)
    val black = Color(0xFF16110F)
    val body = Color(0xFF2A313A)
    val edge = Color(0xFF546E7A)

    /** The analyzer's selection marker: cyan, exactly as on the fretboard. */
    val selection = BoardColors.selection
}

/**
 * The keyboard surface.
 *
 * One key per pitch the engine answers, in the engine's own order — the
 * leftmost key is the first key the engine names. The whites are drawn as one
 * contiguous run and the blacks are drawn over them, so a black key covers the
 * whites it straddles, exactly as a real keyboard looks; the hit test reads the
 * same layout, black first, so the drawing and the touch agree at every edge.
 *
 * The engine supplies the key, its note and what claims it; the client supplies
 * the layout ([PianoGeometry]). Everything scrolls sideways, because 21 white
 * keys are wider than any phone and shrinking them below a 48dp target is worse
 * than scrolling.
 *
 * The visualizer is informative, so it passes no [onPitchTap] and the keyboard
 * is not a control. The analyzer passes one, and a tap then routes exactly one
 * `TogglePianoKey` for the tapped key's pitch. A scroll cancels the pending tap,
 * an extra pointer does nothing, and a tap outside every key produces no event.
 */
@Composable
fun PianoSurface(
    surface: KeyboardSurfaceDto,
    modifier: Modifier = Modifier,
    /** The engine's committed piano selection, by absolute pitch. */
    markedPitches: Set<Int> = emptySet(),
    /** Paint the engine's chord memberships; off on the analyzer. */
    paintChords: Boolean = true,
    onPitchTap: ((Int) -> Unit)? = null,
) {
    val layout = remember(surface) { PianoGeometry.layout(surface) }
    val scrollState = rememberScrollState()
    val density = LocalDensity.current.density
    val currentTap by rememberUpdatedState(onPitchTap)
    val taps = if (onPitchTap == null) {
        Modifier
    } else {
        Modifier.pointerInput(layout) {
            detectTapGestures { offset ->
                PianoGeometry.keyAt(layout, offset.x / density, offset.y / density)
                    ?.let { key -> currentTap?.invoke(key.pitch) }
            }
        }
    }

    Box(
        modifier
            .horizontalScroll(scrollState)
            .then(taps)
            .background(PianoColors.body),
    ) {
        Box(
            Modifier
                .width(PianoGeometry.surfaceWidthDp(layout).dp)
                .height(PianoGeometry.WHITE_HEIGHT_DP.dp),
        ) {
            // The whites first, then the blacks on top: the paint order the web
            // surface pins, and the order the hit test mirrors.
            layout.filterNot(PianoKey::black).forEach { key ->
                PianoKeyBody(key, markedPitches, paintChords, onPitchTap)
            }
            layout.filter(PianoKey::black).forEach { key ->
                PianoKeyBody(key, markedPitches, paintChords, onPitchTap)
            }
        }
    }
}

/** One key: the engine's mark on it, and its spoken label. */
@Composable
private fun PianoKeyBody(
    key: PianoKey,
    markedPitches: Set<Int>,
    paintChords: Boolean,
    onPitchTap: ((Int) -> Unit)?,
) {
    val marked = key.pitch in markedPitches
    // The analyzer's mark is its own cyan, independent of any chord colour; the
    // visualizer paints the engine's membership colour.
    val mark: Color? = when {
        marked -> PianoColors.selection
        paintChords && key.memberships.isNotEmpty() ->
            when (val paint = notePaint(key.memberships, key.fill)) {
                is NotePaint.Colored -> Color(paint.argb)
                is NotePaint.Overlap -> Color(paint.argb)
                NotePaint.None -> null
            }
        else -> null
    }
    val label = pianoKeyLabel(key.note, key.pitch)
    val semantics = if (onPitchTap == null) {
        Modifier.semantics { contentDescription = label }
    } else {
        Modifier.semantics {
            contentDescription = label
            selected = marked
            role = Role.Button
            onClick(label) {
                onPitchTap(key.pitch)
                true
            }
        }
    }

    Box(
        modifier = Modifier
            .offset(x = key.leftDp.dp)
            .width(key.widthDp.dp)
            .height(key.heightDp.dp)
            .then(semantics)
            .clip(RoundedCornerShape(bottomStart = 4.dp, bottomEnd = 4.dp))
            .background(if (key.black) PianoColors.black else PianoColors.white)
            .border(1.dp, PianoColors.edge.copy(alpha = 0.6f), RoundedCornerShape(bottomStart = 4.dp, bottomEnd = 4.dp))
            .padding(bottom = 8.dp)
            .testTag("piano-key-${key.pitch}"),
        contentAlignment = Alignment.BottomCenter,
    ) {
        if (mark != null) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Box(
                    Modifier
                        .size(22.dp)
                        .background(mark, CircleShape),
                )
                Text(
                    text = key.note,
                    color = if (key.black) PianoColors.white else PianoColors.black,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                )
            }
        }
    }
}
