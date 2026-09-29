package dev.ironjanowar.fretboard.ui.keys

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.ironjanowar.fretboard.core.MultiKeyGroupDto
import dev.ironjanowar.fretboard.session.EvaluationState
import dev.ironjanowar.fretboard.ui.common.EvaluationFailed
import dev.ironjanowar.fretboard.ui.common.EvaluationPending
import dev.ironjanowar.fretboard.ui.common.PillChip

/**
 * The multi-key panel on the visualizer.
 *
 * It renders the engine's own [MultiKeyGroupDto] groups, each with its *full*
 * displayed membership: a group's `chords` is every input occurrence whose notes
 * fit that key, so one chord can appear in two groups and both are shown. A
 * group whose `key` is absent is the engine's keyless "unmatched chords" group
 * and is shown as such. The panel only appears when the engine returns groups,
 * which happens only when the single-key operation answered none — the engine's
 * page rule, not the client's.
 */
@Composable
fun MultiKeySuggestionsPanel(
    groups: EvaluationState<List<MultiKeyGroupDto>>,
    onRetry: () -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier,
) {
    when (groups) {
        EvaluationState.Absent -> Unit
        EvaluationState.Pending -> EvaluationPending(
            label = "Grouping keys…",
            modifier = modifier,
            tag = "multi-key-pending",
        )

        is EvaluationState.Failed -> EvaluationFailed(
            reason = groups.reason,
            onRetry = onRetry,
            enabled = enabled,
            modifier = modifier,
            tag = "multi-key-failed",
        )

        is EvaluationState.Answer -> {
            // The panel appears only when the engine returned groups; an empty
            // answer is one of the single-key panel's own states, not this one.
            if (groups.value.isNotEmpty()) {
                Column(modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Keys that share these chords",
                        color = MaterialTheme.colorScheme.onSurface,
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                    groups.value.forEachIndexed { index, group ->
                        MultiKeyGroupCard(group = group, index = index)
                    }
                }
            }
        }
    }
}

/** One multi-key group: its key (or the engine's keyless group) and every member chord. */
@Composable
private fun MultiKeyGroupCard(group: MultiKeyGroupDto, index: Int) {
    val keyless = group.key == null
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .testTag(if (keyless) "multi-key-keyless" else "multi-key-group-$index"),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            val key = group.key
            Text(
                text = if (key == null) {
                    "Unmatched chords"
                } else {
                    "${key.tonic} ${scaleLabel(key.scale) ?: key.scale} — ${key.score}/${key.total}"
                },
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            // The group's full membership, in the engine's own order: every
            // occurrence that fits, duplicates included.
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                group.chords.forEachIndexed { chordIndex, chord ->
                    PillChip(
                        text = "${chord.root} ${chord.quality}",
                        selected = false,
                        onClick = {},
                        modifier = Modifier.testTag(
                            if (keyless) {
                                "multi-key-keyless-chord-$chordIndex"
                            } else {
                                "multi-key-group-$index-chord-$chordIndex"
                            },
                        ),
                    )
                }
            }
        }
    }
}
