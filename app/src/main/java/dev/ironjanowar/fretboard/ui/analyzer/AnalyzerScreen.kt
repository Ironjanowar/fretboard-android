package dev.ironjanowar.fretboard.ui.analyzer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import dev.ironjanowar.fretboard.core.PositionDto
import dev.ironjanowar.fretboard.session.SessionView
import dev.ironjanowar.fretboard.session.lastFret
import dev.ironjanowar.fretboard.session.markedFrets
import dev.ironjanowar.fretboard.ui.FeatureAvailability
import dev.ironjanowar.fretboard.ui.analyzer.asView
import dev.ironjanowar.fretboard.ui.surface.FretboardSurface

/**
 * The Analyzer tab.
 *
 * The surface is the analyzer's own control: one tap marks a cell through the
 * engine's `ToggleNote` event, which clears the mark when it is the same fret of
 * the same string and moves it when it is another one. The engine holds at most
 * one marked fret per physical string, and *Clear notes* is its own event, so it
 * never disturbs the chords or the tuning.
 *
 * The chords the visualizer holds are kept — the tab does not lose them — but
 * they are not painted here: analyzer marks are cyan and independent of any
 * chord colour, and the analysis itself is derived from the selection, never
 * from the stored chords.
 *
 * The keyboard's analyzer is a later phase, and this screen says so: the engine
 * already answers the keyboard surface, but nothing here would draw 36 keys
 * correctly, and a fretted board drawn under a piano selection would be a wrong
 * musical answer.
 */
@Composable
fun AnalyzerScreen(
    view: SessionView,
    onTapPosition: (PositionDto) -> Unit,
    onClearSelection: () -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier,
) {
    val marked = view.markedFrets()
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = if (marked.isEmpty()) {
                    "Analyzer"
                } else {
                    "Analyzer (${marked.size} marked)"
                },
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.titleMedium,
            )
            if (marked.isNotEmpty()) {
                TextButton(
                    onClick = onClearSelection,
                    enabled = enabled,
                    modifier = Modifier.testTag("clear-notes"),
                ) {
                    Text("✕ Clear notes")
                }
            }
        }

        val lastFret = view.lastFret()
        val surface = view.surface
        if (surface == null || lastFret == null) {
            FeatureAvailability(
                title = "Keyboard analyzer is not built yet",
                detail =
                    "This build marks the fretted surface. The engine already answers the " +
                        "keyboard surface and its analysis; the screen for the 36 keys " +
                        "arrives in the next phase.",
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        } else {
            FretboardSurface(
                surface = surface,
                lastFret = lastFret,
                marked = marked,
                // The analyzer's marks are its own; the visualizer's chord colours
                // stay on that tab.
                paintChords = false,
                onPositionTap = onTapPosition,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("analyzer-surface"),
            )
            Text(
                text = "Tap a position to mark it; tap it again to clear it. One fret per string.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier
                    .padding(horizontal = 16.dp)
                    .testTag("analyzer-instruction"),
            )
        }

        AnalysisCards(view = view.analysis.asView())
    }
}
