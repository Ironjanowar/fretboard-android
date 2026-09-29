package dev.ironjanowar.fretboard.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.ironjanowar.fretboard.core.ChordDto
import dev.ironjanowar.fretboard.ui.surface.SurfacePalette

/**
 * The engine's chord list of a draft, as wrapping, coloured pills.
 *
 * The chords come straight from the engine (`diatonic_chords`,
 * `progression_chords`) as root-and-quality pairs; the client renders those two
 * fields as they arrived and never renames or re-derives them. The list is a
 * `FlowRow`, not a sideways-scrolling row: a four-chord progression fits a phone
 * on one or two lines instead of being clipped at the edge.
 *
 * The colours tie the preview to the cards: each identity is painted with the
 * palette colour of its slot, assigned by first appearance
 * ([chordPreviewSlots]).
 */
@Composable
fun ChordPreview(
    chords: List<ChordDto>,
    modifier: Modifier = Modifier,
    tag: String = "chord-preview",
) {
    if (chords.isEmpty()) {
        Text(
            text = "No chords from the engine for this draft.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.labelSmall,
            modifier = modifier.testTag(tag),
        )
        return
    }
    Column(modifier.testTag(tag), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = "${chords.size} chords from the engine",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
        )
        ChordPreviewRow(chords = chords)
    }
}

/** The preview chords as one wrapping row of coloured pills. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ChordPreviewRow(
    chords: List<ChordDto>,
    modifier: Modifier = Modifier,
) {
    val slots = chordPreviewSlots(chords)
    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        chords.forEachIndexed { index, chord ->
            PreviewChip(
                text = "${chord.root} ${chord.quality}",
                argb = SurfacePalette.colorForSlot(slots[index]),
                modifier = Modifier.testTag("preview-chord-${chord.root}-${chord.quality}"),
            )
        }
    }
}

/**
 * The palette slot of every chord of a *draft preview*, by first appearance.
 *
 * The engine owns slot assignment for a committed page (`chordColorSlots`), and
 * there is no engine entry point for a draft that has not been committed, so a
 * preview's colours are projected here with the engine's own rule: the first
 * occurrence of an identity takes the next slot and later occurrences of the
 * same identity share it. The *palette* is the client's either way
 * ([SurfacePalette]), so the preview's colours match the cards' colours for the
 * same chords. When the engine exposes draft slots this projection is deleted.
 */
fun chordPreviewSlots(chords: List<ChordDto>): List<ULong> {
    val assigned = LinkedHashMap<ChordDto, ULong>()
    return chords.map { chord -> assigned.getOrPut(chord) { assigned.size.toULong() } }
}

/**
 * One preview chord: the engine's root and quality, painted with the chord's
 * palette colour so the preview reads like the cards.
 */
@Composable
fun PreviewChip(text: String, argb: Int, modifier: Modifier = Modifier) {
    Text(
        text = text,
        color = Color(0xFF10131A),
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        modifier = modifier
            .padding(vertical = 2.dp)
            .clip(RoundedCornerShape(50))
            .background(Color(argb))
            .padding(horizontal = 14.dp, vertical = 7.dp),
    )
}
