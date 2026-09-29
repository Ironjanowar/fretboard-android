package dev.ironjanowar.fretboard.ui.controls

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import dev.ironjanowar.fretboard.core.QualityGroupDto
import dev.ironjanowar.fretboard.ui.common.GroupedPicker
import dev.ironjanowar.fretboard.ui.common.PickerGroup
import dev.ironjanowar.fretboard.ui.common.PickerOption
import dev.ironjanowar.fretboard.ui.common.PillChip
import dev.ironjanowar.fretboard.ui.visualizer.ROOT_WIRE_NAMES

/**
 * The chord editor: a root, a quality and Add.
 *
 * Both pickers are catalog-driven. The root list is the twelve sharp wire names
 * the engine accepts on every wire surface (`CORE-D06`), which is a wire token
 * list, not musical logic. The qualities come from the engine's own
 * `qualityGroups()`, grouping and labels included: every group the engine sends
 * is rendered, with the engine's labels (`maj`, `m7`, `6`, …).
 *
 * Adding a chord whose exact root and quality are already active is a no-op: the
 * reducer refuses an exact duplicate while allowing the same pitch set under
 * another identity.
 */
@Composable
fun ChordEditor(
    qualityGroups: List<QualityGroupDto>,
    root: String,
    quality: String,
    onRootChange: (String) -> Unit,
    onQualityChange: (String) -> Unit,
    onAdd: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Column(modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        ControlCaption("Add a chord", Modifier.padding(bottom = 6.dp))

        ControlCaption("Root", Modifier.padding(top = 2.dp))
        Row(
            modifier = Modifier
                .horizontalScroll(rememberScrollState())
                .padding(vertical = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            ROOT_WIRE_NAMES.forEach { name ->
                PillChip(
                    text = name,
                    selected = name == root,
                    enabled = enabled,
                    onClick = { onRootChange(name) },
                    modifier = Modifier.testTag("root-$name"),
                )
            }
        }

        GroupedPicker(
            groups = qualityGroups.map { group ->
                PickerGroup(
                    title = group.group,
                    options = group.qualities.map { PickerOption(it.quality, it.label) },
                )
            },
            selectedId = quality,
            onSelect = onQualityChange,
            enabled = enabled,
            modifier = Modifier.padding(top = 6.dp),
            tagPrefix = "quality",
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Button(
                onClick = onAdd,
                enabled = enabled,
                modifier = Modifier.testTag("add-chord"),
            ) {
                Text("Add $root$quality")
            }
            Text(
                text = "Highlights and colours are the engine's answers.",
                style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
                color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
