package dev.ironjanowar.fretboard.ui.visualizer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import dev.ironjanowar.fretboard.core.FrettedSurfaceDto
import dev.ironjanowar.fretboard.core.KeyRowDto
import dev.ironjanowar.fretboard.core.KeySuggestionDto
import dev.ironjanowar.fretboard.core.MultiKeyGroupDto
import dev.ironjanowar.fretboard.session.EvaluationState
import dev.ironjanowar.fretboard.session.SessionView
import dev.ironjanowar.fretboard.session.lastFret
import dev.ironjanowar.fretboard.ui.FeatureAvailability
import dev.ironjanowar.fretboard.ui.controls.ChordEditor
import dev.ironjanowar.fretboard.ui.keys.KeySuggestionsPanel
import dev.ironjanowar.fretboard.ui.keys.MultiKeySuggestionsPanel
import dev.ironjanowar.fretboard.ui.surface.FretboardSurface
import dev.ironjanowar.fretboard.ui.surface.PianoSurface
import dev.ironjanowar.fretboard.ui.surface.SurfacePalette

/**
 * How many positions of the fretted surface a slot claims.
 *
 * Read straight off the engine's own `memberships` (a note keeps a membership
 * once per occurrence), so the position set is the engine's answer and the
 * client only counts it.
 */
fun claimedPositions(surface: FrettedSurfaceDto, slot: ULong): Int =
    surface.rows.sumOf { row -> row.cells.count { cell -> slot in cell.memberships } }

/**
 * The Visualizer tab.
 *
 * The order is the one the design fixes: the controls, then the surface, then
 * the active chords. Every musical fact on screen — the note of a position, its
 * fill, the chord label, its notes and their interval roles, the colour slots —
 * is the engine's; Kotlin draws it.
 *
 * The visualizer is informative, not a selection surface: tapping the board does
 * nothing here, and the analyzer's own selection never colours these positions.
 */
@Composable
fun VisualizerScreen(
    view: SessionView,
    root: String,
    quality: String,
    onRootChange: (String) -> Unit,
    onQualityChange: (String) -> Unit,
    onAdd: () -> Unit,
    onRemove: (Int) -> Unit,
    onToggleHighlight: (Int) -> Unit,
    onClearAll: () -> Unit,
    keyRows: EvaluationState<List<KeyRowDto>>,
    multiKeyGroups: EvaluationState<List<MultiKeyGroupDto>>,
    keyExpanded: Boolean,
    onToggleKeyExpansion: () -> Unit,
    onApplySuggestion: (KeySuggestionDto) -> Unit,
    onRetryKeys: () -> Unit,
    onOpenKey: () -> Unit,
    onOpenProgression: () -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        ChordEditor(
            qualityGroups = view.qualityGroups,
            root = root,
            quality = quality,
            onRootChange = onRootChange,
            onQualityChange = onQualityChange,
            onAdd = onAdd,
            enabled = enabled,
        )

        // The two replacement actions, visualizer-only like the Add control: the
        // key and progression sheets open on the plan's fresh drafts.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            TextButton(
                onClick = onOpenKey,
                enabled = enabled,
                modifier = Modifier.testTag("open-key"),
            ) {
                Text("Key")
            }
            TextButton(
                onClick = onOpenProgression,
                enabled = enabled,
                modifier = Modifier.testTag("open-progression"),
            ) {
                Text("Progressions")
            }
        }

        val keyboard = view.keyboard
        val lastFret = view.lastFret()
        val surface = view.surface
        if (keyboard != null) {
            // The keyboard: the same informative surface family, one key per
            // pitch the engine answers, with the engine's chord marks painted.
            PianoSurface(
                surface = keyboard,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("keyboard-surface"),
            )
            Text(
                text = "Swipe the keyboard sideways: the engine's keys, note names and " +
                    "the active chords' markers.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        } else if (surface == null || lastFret == null) {
            FeatureAvailability(
                title = "No surface for this instrument",
                detail =
                    "The engine answered neither a fretted surface nor a keyboard " +
                        "for this page, so there is nothing to draw.",
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        } else {
            FretboardSurface(
                surface = surface,
                lastFret = lastFret,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("fretboard-surface"),
            )
            Text(
                text = "Swipe the frets sideways up to $lastFret; the tuning notes stay fixed.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        }

        ActiveChords(
            view = view,
            onRemove = onRemove,
            onToggleHighlight = onToggleHighlight,
            onClearAll = onClearAll,
            enabled = enabled,
        )

        // A15's two panels: the engine's own key rows and, when it answered no
        // rows, its multi-key groups. Both render exactly what the engine sent.
        KeySuggestionsPanel(
            chordCount = view.state.chords.size,
            state = keyRows,
            expanded = keyExpanded,
            onToggleExpanded = onToggleKeyExpansion,
            onApplySuggestion = onApplySuggestion,
            onRetry = onRetryKeys,
            enabled = enabled,
        )
        MultiKeySuggestionsPanel(
            groups = multiKeyGroups,
            onRetry = onRetryKeys,
            enabled = enabled,
        )
    }
}

/** The clear action and the ordered occurrence cards. */
@Composable
private fun ActiveChords(
    view: SessionView,
    onRemove: (Int) -> Unit,
    onToggleHighlight: (Int) -> Unit,
    onClearAll: () -> Unit,
    enabled: Boolean,
) {
    val chords = view.state.chords
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = if (chords.isEmpty()) "Active chords" else "Active chords (${chords.size})",
            color = MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.titleMedium,
        )
        if (chords.isNotEmpty()) {
            TextButton(
                onClick = onClearAll,
                enabled = enabled,
                modifier = Modifier.testTag("clear-chords"),
            ) {
                Text("Clear chords")
            }
        }
    }

    if (chords.isEmpty()) {
        Text(
            text = "No chords yet. Pick a root and a quality above, then Add.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        return
    }

    LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("chord-cards"),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(horizontal = 16.dp),
    ) {
        itemsIndexed(chords) { index, chord ->
            val details = view.details.getOrNull(index)
            val slot = slotFor(view.slots, index)
            ChordCard(
                index = index,
                label = details?.label ?: "?",
                notes = noteIntervals(
                    notes = details?.notes ?: emptyList(),
                    intervals = details?.intervalLabels ?: emptyList(),
                ),
                positions = view.surface?.let { surface ->
                    slot?.let { claimedPositions(surface, it) }
                },
                // The engine's slot, wrapped by the client palette. A card the
                // engine gave no slot for is painted the overlap grey rather than
                // crashing or inventing a colour.
                argb = if (slot == null) {
                    SurfacePalette.overlapArgb
                } else {
                    SurfacePalette.colorForSlot(slot)
                },
                highlighted = view.state.highlight == chord,
                onToggleHighlight = { onToggleHighlight(index) },
                onRemove = { onRemove(index) },
            )
        }
    }
}
