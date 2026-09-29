package dev.ironjanowar.fretboard.ui.analyzer

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * The analyzer's own reading of the engine's answer.
 *
 * Every branch renders what the engine said and nothing else. An absent
 * analysis, a computed empty one, an identification that matched nothing and a
 * capability the engine reports as pending are four different English states,
 * never one placeholder: showing "nothing selected" when the engine answers no
 * analysis at all would be a claim the engine did not make.
 *
 * The copy follows the pinned web screen with the platform's own verb ("Tap"
 * for "Click"); the musical strings — the slash label, the note names, the
 * interval labels, the bass and the inversion wording — are the engine's.
 */
@Composable
fun AnalysisCards(view: AnalysisView, modifier: Modifier = Modifier) {
    Column(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .testTag("analyzer-results"),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        when (view) {
            AnalysisView.Absent -> Message(
                text = "The Visualizer tab has no analysis: the engine answers the " +
                    "analyzer from the Analyzer tab's own selection.",
                tag = "analysis-absent",
            )

            AnalysisView.Empty -> Message(
                text = "Tap notes on the fretboard to identify a chord.",
                tag = "analysis-empty",
            )

            AnalysisView.NoMatch -> Message(
                text = "No chord found for these notes.",
                tag = "analysis-no-match",
            )

            is AnalysisView.Single -> Message(
                text = "Note: ${view.note}",
                tag = "analysis-single",
            )

            is AnalysisView.Interval -> Message(
                text = "Interval: ${view.low}-${view.high} (${view.label})",
                tag = "analysis-interval",
            )

            is AnalysisView.Results -> view.cards.forEach { card ->
                AnalysisCard(card)
            }

            is AnalysisView.Pending -> Message(
                text = "Not available in this engine build: ${view.reason}",
                tag = "analysis-pending",
            )

            is AnalysisView.Unavailable -> Message(
                text = "The engine did not answer: ${view.reason}",
                tag = "analysis-unavailable",
            )
        }
    }
}

/** One line of the analyzer's answer. */
@Composable
private fun Message(text: String, tag: String) {
    Text(
        text = text,
        color = MaterialTheme.colorScheme.onSurface,
        style = MaterialTheme.typography.bodyMedium,
        modifier = Modifier.testTag(tag),
    )
}

/** One identification: its label, its badge, its notes, its intervals, its bass. */
@Composable
private fun AnalysisCard(card: AnalysisCardModel) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(14.dp))
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
                shape = RoundedCornerShape(14.dp),
            )
            .padding(12.dp)
            .testTag("analysis-card"),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = card.label,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.testTag("analysis-label"),
            )
            Badge(card.badge)
        }

        NotePairs(card)
        IntervalRow(card)

        card.inversion?.let { inversion ->
            Text(
                text = inversion,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.testTag("analysis-inversion"),
            )
        }

        Text(
            text = "Bass: ${card.bass}",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.testTag("analysis-bass"),
        )
    }
}

/** The identification's own two flags, in the baseline's own order. */
@Composable
private fun Badge(text: String) {
    val color = when (text) {
        "exact" -> MaterialTheme.colorScheme.primary
        "incomplete" -> MaterialTheme.colorScheme.secondary
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Text(
        text = text,
        color = color,
        style = MaterialTheme.typography.labelSmall,
        modifier = Modifier
            .background(color.copy(alpha = 0.12f), RoundedCornerShape(6.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp)
            .testTag("analysis-badge"),
    )
}

/**
 * The engine's note/interval pairs.
 *
 * A pair the engine reports as missing from the input keeps its place and its
 * note name and says so in words, not by colour alone: the chip is outlined and
 * its spoken label carries "missing from the input". Nothing is filled in.
 */
@Composable
private fun NotePairs(card: AnalysisCardModel) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.testTag("analysis-notes"),
    ) {
        items(card.notes) { pair ->
            val color = if (pair.missing) {
                MaterialTheme.colorScheme.error
            } else {
                MaterialTheme.colorScheme.primary
            }
            Row(
                modifier = Modifier
                    .border(
                        width = if (pair.missing) 2.dp else 1.dp,
                        color = color,
                        shape = RoundedCornerShape(8.dp),
                    )
                    .padding(horizontal = 8.dp, vertical = 4.dp)
                    .semantics {
                        contentDescription = if (pair.missing) {
                            "${pair.note} (${pair.interval}), missing from the input"
                        } else {
                            "${pair.note} (${pair.interval})"
                        }
                    }
                    .testTag(if (pair.missing) "analysis-note-missing" else "analysis-note"),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = pair.note,
                    color = color,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.bodyMedium,
                )
                if (pair.missing) {
                    Text(
                        text = "missing",
                        color = color,
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                    )
                }
            }
        }
    }
}

/** The engine's interval labels, in the engine's own order. */
@Composable
private fun IntervalRow(card: AnalysisCardModel) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.testTag("analysis-intervals"),
    ) {
        items(card.intervals) { interval ->
            Text(
                text = interval,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelMedium,
            )
        }
    }
}
