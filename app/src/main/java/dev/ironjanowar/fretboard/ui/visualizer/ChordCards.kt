package dev.ironjanowar.fretboard.ui.visualizer

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Ink used on the pastel chord colours. */
private val CardInk = Color(0xFF14181D)

/**
 * One active chord occurrence, as a coloured chip.
 *
 * The colour is the client palette indexed by the occurrence's slot
 * (`chordColorSlots`, wrapped by the client); a repeated chord shares its first
 * occurrence's slot, which is why two identical chords get one colour while a
 * different chord gets the next one.
 *
 * The card carries the whole chord: its engine label **and** every note with the
 * engine's interval label for it (A08 asks for the pairs, not only the title).
 * The whole card is the highlight target; the remove control is a separate
 * target that never toggles the highlight.
 */
@Composable
fun ChordCard(
    index: Int,
    label: String,
    notes: List<NoteInterval>,
    positions: Int?,
    argb: Int,
    highlighted: Boolean,
    onToggleHighlight: () -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val color = Color(argb)
    Column(
        modifier = modifier
            .testTag("chord-card-$index")
            .shadow(
                elevation = if (highlighted) 12.dp else 3.dp,
                shape = RoundedCornerShape(18.dp),
            )
            .clip(RoundedCornerShape(18.dp))
            .background(color)
            .border(
                width = if (highlighted) 3.dp else 0.dp,
                color = if (highlighted) Color.White else Color.Transparent,
                shape = RoundedCornerShape(18.dp),
            )
            .clickable(onClick = onToggleHighlight)
            .padding(horizontal = 14.dp, vertical = 10.dp)
            .semantics {
                contentDescription =
                    if (highlighted) "$label highlighted, tap to clear" else "$label, tap to highlight"
            },
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = label,
                color = CardInk,
                fontWeight = FontWeight.Bold,
                fontSize = 22.sp,
            )
            if (highlighted) {
                Text(
                    text = "highlighted",
                    color = Color(0xFF10131A),
                    fontWeight = FontWeight.Bold,
                    fontSize = 10.sp,
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(Color.White.copy(alpha = 0.75f))
                        .padding(horizontal = 8.dp, vertical = 3.dp),
                )
            }
            RemoveButton(label = label, onRemove = onRemove)
        }

        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            notes.forEach { pair ->
                Column(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(CardInk.copy(alpha = 0.10f))
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = pair.note,
                        color = CardInk,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp,
                    )
                    Text(
                        text = pair.interval,
                        color = CardInk.copy(alpha = 0.7f),
                        fontSize = 10.sp,
                    )
                }
            }
        }

        if (positions != null) {
            Text(
                text = if (positions == 1) "on 1 position" else "on $positions positions",
                color = CardInk.copy(alpha = 0.65f),
                fontSize = 10.sp,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}

/** The card's own remove target: it never reaches the card's highlight tap. */
@Composable
private fun RemoveButton(label: String, onRemove: () -> Unit) {
    Box(
        modifier = Modifier
            .size(26.dp)
            .clip(CircleShape)
            .background(CardInk.copy(alpha = 0.16f))
            .clickable(onClick = onRemove)
            .semantics { contentDescription = "Remove $label" },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "✕",
            color = CardInk,
            fontWeight = FontWeight.Bold,
            fontSize = 13.sp,
        )
    }
}
