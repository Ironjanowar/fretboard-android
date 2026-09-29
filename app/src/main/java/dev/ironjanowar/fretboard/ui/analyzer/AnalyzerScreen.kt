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
import dev.ironjanowar.fretboard.session.markedPitches
import dev.ironjanowar.fretboard.ui.FeatureAvailability
import dev.ironjanowar.fretboard.ui.analyzer.asView
import dev.ironjanowar.fretboard.ui.surface.FretboardSurface
import dev.ironjanowar.fretboard.ui.surface.PianoSurface

/**
 * The Analyzer tab.
 *
 * The surface is the analyzer's own control, whichever kind it is: on the
 * fretted board one tap marks a cell through the engine's `ToggleNote` event,
 * and on the keyboard one tap marks a key through the engine's `TogglePianoKey`
 * event. The engine holds at most one marked fret per physical string and a set
 * of exact keys, and *Clear notes* is its own event, so it never disturbs the
 * chords or the tuning.
 *
 * The chords the visualizer holds are kept — the tab does not lose them — but
 * they are not painted here: analyzer marks are cyan and independent of any
 * chord colour, and the analysis itself is derived from the selection, never
 * from the stored chords.
 *
 * The keyboard's markers are the engine's committed selection, read from the
 * piano state's own keys; the tap carries back exactly the tapped key's pitch
 * and nothing else. The engine decides add, remove and any out-of-range refusal.
 */
@Composable
fun AnalyzerScreen(
    view: SessionView,
    onTapPosition: (PositionDto) -> Unit,
    onTapPitch: (Int) -> Unit,
    onClearSelection: () -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier,
) {
    val marked = view.markedFrets()
    val markedKeys = view.markedPitches()
    val keyboard = view.keyboard
    val markedCount = if (keyboard != null) markedKeys.size else marked.size
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = if (markedCount == 0) {
                    "Analyzer"
                } else {
                    "Analyzer ($markedCount marked)"
                },
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.titleMedium,
            )
            if (markedCount > 0) {
                TextButton(
                    onClick = onClearSelection,
                    enabled = enabled,
                    modifier = Modifier.testTag("clear-notes"),
                ) {
                    Text("✕ Clear notes")
                }
            }
        }

        if (keyboard != null) {
            PianoSurface(
                surface = keyboard,
                markedPitches = markedKeys,
                // The analyzer's marks are its own; the visualizer's chord
                // colours stay on that tab.
                paintChords = false,
                onPitchTap = onTapPitch,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("keyboard-analyzer-surface"),
            )
            Text(
                text = "Tap a key to mark it; tap it again to clear it. Same pitch class in " +
                    "another octave is a different key.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier
                    .padding(horizontal = 16.dp)
                    .testTag("analyzer-instruction"),
            )
        } else {
            val lastFret = view.lastFret()
            val surface = view.surface
            if (surface == null || lastFret == null) {
                FeatureAvailability(
                    title = "No surface for this instrument",
                    detail =
                        "The engine answered neither a fretted surface nor a keyboard " +
                            "for this page, so there is nothing to mark.",
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
        }

        AnalysisCards(view = view.analysis.asView())
    }
}
