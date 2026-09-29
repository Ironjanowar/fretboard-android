package dev.ironjanowar.fretboard.ui.progressions

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
import dev.ironjanowar.fretboard.core.ProgressionGroupDto
import dev.ironjanowar.fretboard.session.ProgressionCatalogState
import dev.ironjanowar.fretboard.session.ProgressionDraft
import dev.ironjanowar.fretboard.ui.common.CatalogDropdown
import dev.ironjanowar.fretboard.ui.common.ChordPreview
import dev.ironjanowar.fretboard.ui.common.EvaluationPending
import dev.ironjanowar.fretboard.ui.common.PickerGroup
import dev.ironjanowar.fretboard.ui.common.PickerOption
import dev.ironjanowar.fretboard.ui.common.PillChip
import dev.ironjanowar.fretboard.ui.visualizer.ROOT_WIRE_NAMES

/**
 * The engine's grouped progression catalog, as the dropdown's own groups.
 *
 * Every group and every entry the engine sent is carried across, in the engine's
 * order: the client neither curates the catalog nor reorders its categories.
 */
fun progressionPickerGroups(groups: List<ProgressionGroupDto>): List<PickerGroup> =
    groups.map { group ->
        PickerGroup(
            title = group.category,
            options = group.progressions.map { progression ->
                PickerOption(id = progression.id, label = progression.name)
            },
        )
    }

/**
 * The progression draft sheet.
 *
 * It edits a draft and nothing else: the committed page changes only when Apply
 * hands the engine its own `CommitProgression` event, and Cancel or a dismissal
 * drops the draft entirely, so reopening always starts from the plan's fresh
 * `pop_i_v_vi_iv` in C. The catalog above the preview is the engine's own grouped
 * catalog (`progressions()`); the preview is the engine's `progression_chords`
 * answer for the draft's tonic and identifier, so a progression that repeats a
 * chord shows every occurrence.
 *
 * The catalog is a dropdown, not one sideways-scrolling pill row per category:
 * a category's entries are often wider than the phone, so a horizontal row hid
 * most of them. The dropdown shows the current selection in one field and lists
 * every group and every progression in a vertically scrolling menu.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ProgressionSheet(
    draft: ProgressionDraft,
    catalog: ProgressionCatalogState,
    onTonicChange: (String) -> Unit,
    onSelectProgression: (String) -> Unit,
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
                .testTag("progression-sheet"),
        ) {
            Column(
                modifier = Modifier
                    .heightIn(max = 620.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    text = "Progression",
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
                            modifier = Modifier.testTag("progression-tonic-$name"),
                        )
                    }
                }

                when (catalog) {
                    is ProgressionCatalogState.Ready -> {
                        Text(
                            text = "${catalog.groups.size} catalog groups from the engine",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                        )
                        CatalogDropdown(
                            groups = progressionPickerGroups(catalog.groups),
                            selectedId = draft.progression,
                            onSelect = onSelectProgression,
                            enabled = enabled,
                            tagPrefix = "progression",
                        )
                    }

                    ProgressionCatalogState.NotApplicable -> Text(
                        text = "The engine lists no progressions.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.testTag("progression-catalog-none"),
                    )

                    is ProgressionCatalogState.Refused -> Text(
                        text = "The engine refused the progression catalog: ${catalog.reason}",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.testTag("progression-catalog-refused"),
                    )
                }

                Text(
                    text = "Preview — the engine's own chords for this tonic and progression",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                if (previewPending) {
                    EvaluationPending(
                        label = "Asking the engine for this progression's chords…",
                        tag = "progression-preview-pending",
                    )
                } else {
                    ChordPreview(chords = draft.preview, tag = "progression-preview")
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.End),
                ) {
                    TextButton(
                        onClick = onCancel,
                        enabled = enabled,
                        modifier = Modifier.testTag("progression-cancel"),
                    ) {
                        Text("Cancel")
                    }
                    Button(
                        onClick = onApply,
                        enabled = enabled,
                        modifier = Modifier.testTag("progression-apply"),
                    ) {
                        Text("Apply")
                    }
                }
            }
        }
    }
}
