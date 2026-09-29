package dev.ironjanowar.fretboard.ui.tuning

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import dev.ironjanowar.fretboard.session.TuningDraft
import dev.ironjanowar.fretboard.session.TuningRow
import dev.ironjanowar.fretboard.session.tuningRows
import dev.ironjanowar.fretboard.ui.common.PillChip
import dev.ironjanowar.fretboard.ui.visualizer.ROOT_WIRE_NAMES

/**
 * The tuning draft sheet.
 *
 * It edits a draft and nothing else: the committed page changes only when Apply
 * commits the draft through the engine's own event, and Cancel or a dismissal
 * drops the draft entirely, so reopening always starts from the committed
 * tuning.
 *
 * The strings are edited in the baseline's own order — physical order, labelled
 * `String N` down to `String 1` — and each row's note list is the twelve sharp
 * wire names the engine accepts, a wire token list rather than musical logic.
 * The pitch each choice resolves to is the engine's: it resolves the note
 * against the draft's fixed reference preset, never against the string's current
 * pitch.
 *
 * The preset the sheet shows is the engine's own detection for the draft's exact
 * pitches (its `Custom` included), and the picker above it is the engine's own
 * ordered catalog of preset names (`presets(instrument)`) — never a list the
 * client wrote. An instrument the engine names no presets for (the keyboard)
 * says so plainly, and a refused list is shown as a refusal.
 */
@Composable
fun TuningSheet(
    draft: TuningDraft,
    stringCount: Int,
    presets: PresetPickerState,
    onSelectPreset: (String) -> Unit,
    onChangeString: (Int, String) -> Unit,
    onApply: () -> Unit,
    onCancel: () -> Unit,
    enabled: Boolean = true,
) {
    Dialog(onDismissRequest = onCancel) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("tuning-sheet"),
        ) {
            Column(
                modifier = Modifier
                    .heightIn(max = 560.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    text = "Tuning",
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleLarge,
                )

                Text(
                    text = "Preset: ${draft.preset} — reference ${draft.tuning.reference}",
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.testTag("tuning-preset"),
                )

                PresetPicker(
                    state = presets,
                    selected = draft.preset,
                    enabled = enabled,
                    onSelect = onSelectPreset,
                )

                tuningRows(stringCount).forEach { row ->
                    StringNoteRow(
                        row = row,
                        note = draft.notes.getOrNull(row.stringIndex) ?: "",
                        enabled = enabled,
                        onChange = { note -> onChangeString(row.stringIndex, note) },
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.End),
                ) {
                    TextButton(
                        onClick = onCancel,
                        enabled = enabled,
                        modifier = Modifier.testTag("tuning-cancel"),
                    ) {
                        Text("Cancel")
                    }
                    Button(
                        onClick = onApply,
                        enabled = enabled,
                        modifier = Modifier.testTag("tuning-apply"),
                    ) {
                        Text("Apply")
                    }
                }
            }
        }
    }
}

/**
 * One string row: its label, the note the engine currently names, and the twelve
 * wire note names to choose from.
 *
 * The chips are the control the chord editor already ships, in their own
 * horizontal scroll, so a row of twelve never squeezes the sheet or pushes Apply
 * out of reach on a phone.
 */
@Composable
private fun StringNoteRow(
    row: TuningRow,
    note: String,
    enabled: Boolean,
    onChange: (String) -> Unit,
) {
    Column(Modifier.fillMaxWidth()) {
        Text(
            text = if (note.isEmpty()) {
                "${row.label} — the engine named no note"
            } else {
                "${row.label} — $note"
            },
            color = MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(vertical = 2.dp)
                .testTag("tuning-string-${row.stringIndex}"),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            ROOT_WIRE_NAMES.forEach { name ->
                PillChip(
                    text = name,
                    selected = name == note,
                    enabled = enabled,
                    onClick = { onChange(name) },
                    modifier = Modifier.testTag("tuning-string-${row.stringIndex}-$name"),
                )
            }
        }
        if (note.isEmpty()) {
            Text(
                text = "the engine named no note for this string",
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier
                    .background(
                        MaterialTheme.colorScheme.error.copy(alpha = 0.12f),
                        RoundedCornerShape(6.dp),
                    )
                    .padding(horizontal = 6.dp, vertical = 2.dp),
            )
        }
    }
}

/**
 * The preset picker: the engine's own ordered names, or its own "none", or its
 * own refusal.
 *
 * It renders the names exactly as the engine sent them — same order, nothing
 * filtered — and the chip whose name equals the engine's detected label of the
 * draft is the selected one. Picking a name sends it back to the engine, which
 * answers the whole draft; the sheet never assumes the selection took.
 */
@Composable
private fun PresetPicker(
    state: PresetPickerState,
    selected: String,
    enabled: Boolean,
    onSelect: (String) -> Unit,
) {
    when (state) {
        is PresetPickerState.Ready -> {
            ControlCaptionFor(presets = state.names)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(vertical = 2.dp)
                    .testTag("tuning-preset-picker"),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                state.names.forEach { name ->
                    PillChip(
                        text = name,
                        selected = name == selected,
                        enabled = enabled,
                        onClick = { onSelect(name) },
                        modifier = Modifier.testTag("tuning-preset-option-$name"),
                    )
                }
            }
        }

        PresetPickerState.NotApplicable -> Text(
            text = "The engine lists no presets for this instrument: the keyboard has no tuning.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.testTag("tuning-preset-not-applicable"),
        )

        is PresetPickerState.Refused -> Text(
            text = "The engine refused the preset list: ${state.reason}",
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.testTag("tuning-preset-refused"),
        )
    }
}

/** A caption naming the count of the engine's own names. */
@Composable
private fun ControlCaptionFor(presets: List<String>) {
    Text(
        text = "${presets.size} presets from the engine",
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.SemiBold,
    )
}
