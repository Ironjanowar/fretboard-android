package dev.ironjanowar.fretboard.ui.common

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * A grouped catalog as a single-select dropdown.
 *
 * The sheets' catalogs — the progression categories and the scale groups — are
 * long enough that a row of pills per group has to scroll sideways, which on a
 * phone hides most of the list and clips the entries at the edge. Material 3's
 * `ExposedDropdownMenuBox` is the component for exactly this case: one compact
 * field showing the current selection, and a vertically scrolling menu that
 * lists every group and every entry.
 *
 * Every group and every option is still rendered, in the engine's own order, and
 * each option keeps its stable tag (`<prefix>-option-<id>`) so a test addresses
 * the engine's identifier rather than a label. The menu lives in its own popup,
 * so an option node exists only while the menu is open — a test opens the
 * anchor (`<prefix>-anchor`) first.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CatalogDropdown(
    groups: List<PickerGroup>,
    selectedId: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    tagPrefix: String = "catalog",
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedLabel = groups
        .firstNotNullOfOrNull { group -> group.options.firstOrNull { it.id == selectedId }?.label }
        ?: selectedId

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { open -> if (enabled) expanded = open },
        modifier = modifier.fillMaxWidth(),
    ) {
        OutlinedTextField(
            value = selectedLabel,
            onValueChange = {},
            readOnly = true,
            enabled = enabled,
            singleLine = true,
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .menuAnchor(MenuAnchorType.PrimaryNotEditable, enabled)
                .fillMaxWidth()
                .testTag("$tagPrefix-anchor"),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            groups.forEach { group ->
                // An ungrouped list (the twelve roots, the instrument catalog)
                // carries a blank title and gets no heading of its own.
                if (group.title.isNotBlank()) {
                    Text(
                        text = group.title,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                            .testTag("$tagPrefix-group-${group.title}"),
                    )
                }
                group.options.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(option.label) },
                        onClick = {
                            onSelect(option.id)
                            expanded = false
                        },
                        modifier = Modifier.testTag("$tagPrefix-option-${option.id}"),
                    )
                }
            }
        }
    }
}
