package dev.ironjanowar.fretboard.ui.keys

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.ironjanowar.fretboard.core.KeyRowDto
import dev.ironjanowar.fretboard.core.KeySuggestionDto
import dev.ironjanowar.fretboard.session.EvaluationState
import dev.ironjanowar.fretboard.ui.NotApplicable
import dev.ironjanowar.fretboard.ui.common.EvaluationEmpty
import dev.ironjanowar.fretboard.ui.common.EvaluationFailed
import dev.ironjanowar.fretboard.ui.common.EvaluationPending

/**
 * The keys panel on the visualizer.
 *
 * It renders exactly the engine's [KeyRowDto] rows, in the engine's order: a
 * collapsed group shows its prominent pair (the major and its relative minor)
 * with the other five modes behind one shared expansion, and a `Single` row
 * stays an independent card. The imperfect branch is likewise the engine's own
 * first three rows. A15's rule is that Android performs *no* grouping, coverage
 * or sorting here — every row, its score and its order arrive from
 * `key_suggestions`, and the client only draws them.
 *
 * Below two chords the panel is absent, because the engine's own gate answers no
 * rows: the section says so with the existing placeholder instead of drawing an
 * empty box.
 */
@Composable
fun KeySuggestionsPanel(
    chordCount: Int,
    state: EvaluationState<List<KeyRowDto>>,
    expanded: Boolean,
    onToggleExpanded: () -> Unit,
    onApplySuggestion: (KeySuggestionDto) -> Unit,
    onRetry: () -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier,
) {
    if (chordCount < 2) {
        // The engine ranks keys from the active chords; with fewer than two it
        // answers nothing at all, so there is no panel to draw.
        NotApplicable(
            title = "Key suggestions need two chords",
            detail =
                "The engine ranks keys from the active chords. Add at least two chords and " +
                    "its own suggestions appear here.",
            modifier = modifier.padding(horizontal = 16.dp),
            tag = "key-suggestions-gate",
        )
        return
    }

    when (state) {
        EvaluationState.Absent -> Unit
        EvaluationState.Pending -> EvaluationPending(
            label = "Ranking keys…",
            modifier = modifier,
            tag = "key-suggestions-pending",
        )

        is EvaluationState.Failed -> EvaluationFailed(
            reason = state.reason,
            onRetry = onRetry,
            enabled = enabled,
            modifier = modifier,
            tag = "key-suggestions-failed",
        )

        is EvaluationState.Answer -> {
            if (state.value.isEmpty()) {
                EvaluationEmpty(
                    label = "The engine found no key that fits these chords.",
                    modifier = modifier,
                    tag = "key-suggestions-empty",
                )
            } else {
                Column(modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Suggested keys",
                        color = MaterialTheme.colorScheme.onSurface,
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                    state.value.forEachIndexed { index, row ->
                        when (row) {
                            is KeyRowDto.Single -> KeySuggestionCard(
                                suggestion = row.item,
                                tag = "key-row-single-$index-${row.item.tonic}-${row.item.scale}",
                                enabled = enabled,
                                onApply = onApplySuggestion,
                            )

                            is KeyRowDto.Group -> KeyGroupCard(
                                group = row,
                                tag = "key-row-group-$index",
                                expanded = expanded,
                                enabled = enabled,
                                onToggleExpanded = onToggleExpanded,
                                onApplySuggestion = onApplySuggestion,
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * A collapsed group of relative modes: the prominent pair, then the rest behind
 * one expansion shared across every group.
 */
@Composable
private fun KeyGroupCard(
    group: KeyRowDto.Group,
    tag: String,
    expanded: Boolean,
    enabled: Boolean,
    onToggleExpanded: () -> Unit,
    onApplySuggestion: (KeySuggestionDto) -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .testTag(tag),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "Relative modes — the engine grouped these",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
            )
            // The prominent pair: the major and the relative minor, in the
            // engine's own order.
            group.prominent.forEach { suggestion ->
                KeySuggestionCard(
                    suggestion = suggestion,
                    enabled = enabled,
                    onApply = onApplySuggestion,
                )
            }
            if (group.others.isNotEmpty()) {
                TextButton(
                    onClick = onToggleExpanded,
                    enabled = enabled,
                    modifier = Modifier.testTag("key-modes-toggle"),
                ) {
                    Text(
                        if (expanded) {
                            "Hide ${group.others.size} other modes"
                        } else {
                            "${group.others.size} other modes"
                        },
                    )
                }
                if (expanded) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        group.others.forEach { suggestion ->
                            KeySuggestionCard(
                                suggestion = suggestion,
                                enabled = enabled,
                                onApply = onApplySuggestion,
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * One key suggestion, shown as its tonic, its scale and the engine's own score
 * out of the engine's own total.
 */
@Composable
private fun KeySuggestionCard(
    suggestion: KeySuggestionDto,
    enabled: Boolean,
    onApply: (KeySuggestionDto) -> Unit,
    tag: String = keyRowTag(suggestion),
) {
    val label = "${suggestion.tonic} ${scaleLabel(suggestion.scale) ?: suggestion.scale}"
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier
            .fillMaxWidth()
            .testTag(tag)
            .clickable(enabled = enabled) { onApply(suggestion) },
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = label,
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.titleSmall,
            )
            Text(
                // The engine's score and total, exactly as they arrived.
                text = "${suggestion.score}/${suggestion.total}",
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

/** The panel's own semantic key, so an instrumented test can read a row's scale. */
fun keyRowTag(suggestion: KeySuggestionDto): String =
    "key-suggestion-${suggestion.tonic}-${suggestion.scale}"
