package dev.ironjanowar.fretboard.ui.keys

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import dev.ironjanowar.fretboard.core.ChordModeDto
import dev.ironjanowar.fretboard.session.CHORD_MODES
import dev.ironjanowar.fretboard.session.KeyDraft
import dev.ironjanowar.fretboard.session.chordModeLabel
import dev.ironjanowar.fretboard.ui.common.CatalogDropdown
import dev.ironjanowar.fretboard.ui.common.ChordPreview
import dev.ironjanowar.fretboard.ui.common.EvaluationPending
import dev.ironjanowar.fretboard.ui.common.PickerGroup
import dev.ironjanowar.fretboard.ui.common.PickerOption
import dev.ironjanowar.fretboard.ui.common.PillChip
import dev.ironjanowar.fretboard.ui.visualizer.ROOT_WIRE_NAMES

/**
 * The fifteen scale types of the frozen catalog, as wire tokens and labels.
 *
 * This is a *wire token list*, not musical logic — the same kind of list
 * [ROOT_WIRE_NAMES] is: the engine's `diatonic_chords` parses these identifiers
 * and rejects any other (`UnknownIdentifier`), so the client offers exactly the
 * identifiers the engine accepts and computes nothing from them. The engine
 * exports no scale enumeration of its own (the progression catalog and the key
 * suggestions carry only the scales they happen to mention), so the sheet's
 * choices are the frozen catalog's identifiers, grouped the way the pinned
 * catalog groups them.
 */
val SCALE_WIRE_GROUPS: List<PickerGroup> = listOf(
    PickerGroup("Standard", listOf(PickerOption("major", "Major"), PickerOption("minor", "Minor"))),
    PickerGroup(
        "Minor Variants",
        listOf(
            PickerOption("harmonic_minor", "Harmonic Minor"),
            PickerOption("melodic_minor", "Melodic Minor"),
        ),
    ),
    PickerGroup(
        "Pentatonic",
        listOf(
            PickerOption("pentatonic_major", "Major Pentatonic"),
            PickerOption("pentatonic_minor", "Minor Pentatonic"),
        ),
    ),
    PickerGroup("Blues", listOf(PickerOption("blues", "Blues"))),
    PickerGroup(
        "Modes",
        listOf(
            PickerOption("dorian", "Dorian"),
            PickerOption("phrygian", "Phrygian"),
            PickerOption("lydian", "Lydian"),
            PickerOption("mixolydian", "Mixolydian"),
            PickerOption("locrian", "Locrian"),
        ),
    ),
    PickerGroup(
        "Exotic",
        listOf(
            PickerOption("phrygian_dominant", "Phrygian Dominant"),
            PickerOption("whole_tone", "Whole Tone"),
        ),
    ),
    PickerGroup("Other", listOf(PickerOption("chromatic", "Chromatic"))),
)

/** The display label of a scale identifier, or `null` when the list has none. */
fun scaleLabel(id: String): String? =
    SCALE_WIRE_GROUPS.firstNotNullOfOrNull { group ->
        group.options.firstOrNull { option -> option.id == id }?.label
    }

/**
 * The key draft sheet.
 *
 * It edits a draft and nothing else: the committed page changes only when Apply
 * hands the engine its own `CommitKeys` event, and Cancel or a dismissal drops
 * the draft entirely, so reopening always starts from the plan's fresh C major
 * triad. The preview under the pickers is the engine's `diatonic_chords` answer
 * for the draft's exact three fields, so the sheet shows the chords the engine
 * names for the key it holds — in the triad or seventh mode the sheet chose.
 *
 * The twelve tonics wrap onto two lines instead of scrolling sideways, and the
 * scale list is a dropdown rather than one sideways-scrolling row per group, so
 * every entry is reachable without hunting for a hidden edge.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun KeySheet(
    draft: KeyDraft,
    onTonicChange: (String) -> Unit,
    onScaleChange: (String) -> Unit,
    onModeChange: (ChordModeDto) -> Unit,
    onApply: () -> Unit,
    onCancel: () -> Unit,
    enabled: Boolean = true,
    previewPending: Boolean = false,
) {
    Dialog(onDismissRequest = onCancel) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("key-sheet"),
        ) {
            Column(
                modifier = Modifier
                    .heightIn(max = 620.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    text = "Key",
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleLarge,
                )

                Text(
                    text = "Tonic",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    ROOT_WIRE_NAMES.forEach { name ->
                        PillChip(
                            text = name,
                            selected = name == draft.tonic,
                            enabled = enabled,
                            onClick = { onTonicChange(name) },
                            modifier = Modifier.testTag("key-tonic-$name"),
                        )
                    }
                }

                Text(
                    text = "Scale",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                CatalogDropdown(
                    groups = SCALE_WIRE_GROUPS,
                    selectedId = draft.scale,
                    onSelect = onScaleChange,
                    enabled = enabled,
                    tagPrefix = "key-scale",
                )

                Text(
                    text = "Chord mode",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    CHORD_MODES.forEach { mode ->
                        PillChip(
                            text = chordModeLabel(mode),
                            selected = mode == draft.mode,
                            enabled = enabled,
                            onClick = { onModeChange(mode) },
                            modifier = Modifier.testTag("key-mode-${mode.name.lowercase()}"),
                        )
                    }
                }

                Text(
                    text = "Preview — the engine's own chords for this key and mode",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                if (previewPending) {
                    EvaluationPending(
                        label = "Asking the engine for this key's chords…",
                        tag = "key-preview-pending",
                    )
                } else {
                    ChordPreview(chords = draft.preview, tag = "key-preview")
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.End),
                ) {
                    TextButton(
                        onClick = onCancel,
                        enabled = enabled,
                        modifier = Modifier.testTag("key-cancel"),
                    ) {
                        Text("Cancel")
                    }
                    Button(
                        onClick = onApply,
                        enabled = enabled,
                        modifier = Modifier.testTag("key-apply"),
                    ) {
                        Text("Apply")
                    }
                }
            }
        }
    }
}
