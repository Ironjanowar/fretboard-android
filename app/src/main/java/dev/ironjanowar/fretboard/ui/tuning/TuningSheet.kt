package dev.ironjanowar.fretboard.ui.tuning

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import dev.ironjanowar.fretboard.session.TuningDraft
import dev.ironjanowar.fretboard.session.TuningRow
import dev.ironjanowar.fretboard.session.tuningRows
import dev.ironjanowar.fretboard.ui.visualizer.ROOT_WIRE_NAMES

/**
 * Modal editor for a tuning draft.
 *
 * The engine owns the preset names, their order, and every draft update. This
 * composable only renders that session state and forwards selections. Dismissal
 * and Cancel both drop the draft; Apply is the only commit path.
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
                .heightIn(max = 560.dp)
                .testTag("tuning-sheet"),
        ) {
            Column {
                Text(
                    text = "Tuning",
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(start = 18.dp, top = 18.dp, end = 18.dp),
                )

                TuningBody(
                    draft = draft,
                    rows = tuningRows(stringCount),
                    presets = presets,
                    enabled = enabled,
                    onSelectPreset = onSelectPreset,
                    onChangeString = onChangeString,
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 18.dp, vertical = 4.dp)
                        .testTag("tuning-body"),
                )

                TuningActions(
                    enabled = enabled,
                    onCancel = onCancel,
                    onApply = onApply,
                )
            }
        }
    }
}

@Composable
private fun TuningBody(
    draft: TuningDraft,
    rows: List<TuningRow>,
    presets: PresetPickerState,
    enabled: Boolean,
    onSelectPreset: (String) -> Unit,
    onChangeString: (Int, String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
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
        StringEditors(
            rows = rows,
            notes = draft.notes,
            enabled = enabled,
            onChange = onChangeString,
        )
    }
}

@Composable
private fun StringEditors(
    rows: List<TuningRow>,
    notes: List<String>,
    enabled: Boolean,
    onChange: (Int, String) -> Unit,
) {
    val columns = if (
        LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    ) {
        2
    } else {
        1
    }

    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        rows.chunked(columns).forEach { rowGroup ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                rowGroup.forEach { row ->
                    StringNoteDropdown(
                        row = row,
                        note = notes.getOrNull(row.stringIndex).orEmpty(),
                        enabled = enabled,
                        onChange = { note -> onChange(row.stringIndex, note) },
                        modifier = Modifier.weight(1f),
                    )
                }
                repeat(columns - rowGroup.size) {
                    Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun StringNoteDropdown(
    row: TuningRow,
    note: String,
    enabled: Boolean,
    onChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier) {
        TuningDropdown(
            selected = note,
            options = ROOT_WIRE_NAMES,
            label = row.label,
            enabled = enabled,
            tag = "tuning-string-${row.stringIndex}-dropdown",
            optionTag = { name -> "tuning-string-${row.stringIndex}-option-$name" },
            onSelect = onChange,
        )
        if (note.isEmpty()) {
            Text(
                text = "The engine named no note for this string",
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
            TuningDropdown(
                selected = selected,
                options = state.names,
                label = "Preset",
                enabled = enabled,
                tag = "tuning-preset-dropdown",
                optionTag = { name -> "tuning-preset-option-$name" },
                onSelect = onSelect,
            )
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TuningDropdown(
    selected: String,
    options: List<String>,
    label: String,
    enabled: Boolean,
    tag: String,
    optionTag: (String) -> String,
    onSelect: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val menuExpanded = expanded && enabled

    LaunchedEffect(enabled) {
        if (!enabled) expanded = false
    }

    ExposedDropdownMenuBox(
        expanded = menuExpanded,
        onExpandedChange = { open -> if (enabled) expanded = open },
        modifier = Modifier.fillMaxWidth(),
    ) {
        OutlinedTextField(
            value = selected,
            onValueChange = {},
            readOnly = true,
            enabled = enabled,
            singleLine = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = menuExpanded) },
            modifier = Modifier
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable, enabled)
                .fillMaxWidth()
                .testTag(tag),
        )
        ExposedDropdownMenu(
            expanded = menuExpanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.heightIn(max = 240.dp),
        ) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option) },
                    onClick = {
                        if (enabled) {
                            onSelect(option)
                            expanded = false
                        }
                    },
                    enabled = enabled,
                    modifier = Modifier.testTag(optionTag(option)),
                )
            }
        }
    }
}

@Composable
private fun TuningActions(
    enabled: Boolean,
    onCancel: () -> Unit,
    onApply: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 18.dp, end = 18.dp, bottom = 12.dp)
            .testTag("tuning-actions"),
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
