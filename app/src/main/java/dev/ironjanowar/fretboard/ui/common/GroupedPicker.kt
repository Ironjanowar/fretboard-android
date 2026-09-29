package dev.ironjanowar.fretboard.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** One selectable token of a picker: the engine's wire id and its display label. */
data class PickerOption(val id: String, val label: String)

/** A titled group of options, exactly as the engine grouped them. */
data class PickerGroup(val title: String, val options: List<PickerOption>)

/**
 * A pill.
 *
 * The selected pill is filled with the accent colour and carries an unselected
 * border; an unselected one is a translucent surface. Nothing here decides
 * anything about music — it only renders the option it is handed.
 */
@Composable
fun PillChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    accent: Color = MaterialTheme.colorScheme.primary,
) {
    val background = when {
        !enabled -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
        selected -> accent
        else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
    }
    val content = when {
        !enabled -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
        selected -> Color(0xFF10131A)
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Text(
        text = text,
        color = content,
        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
        fontSize = 14.sp,
        modifier = modifier
            .padding(vertical = 4.dp)
            .clip(RoundedCornerShape(50))
            .background(background)
            .border(
                width = if (selected) 0.dp else 1.dp,
                color = if (selected) accent else MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                shape = RoundedCornerShape(50),
            )
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 7.dp)
            .semantics { contentDescription = text },
    )
}

/**
 * A picker whose options are grouped under their headings, in the order the
 * engine sent them.
 *
 * Every group and every option the engine supplies is rendered: there is no
 * hand-curated subset. Each group scrolls horizontally on its own so a long
 * group cannot push the rest of the screen off a phone.
 */
@Composable
fun GroupedPicker(
    groups: List<PickerGroup>,
    selectedId: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    accent: Color = MaterialTheme.colorScheme.primary,
    tagPrefix: String = "picker",
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        groups.forEach { group ->
            Text(
                text = group.title,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.testTag("$tagPrefix-group-${group.title}"),
            )
            Row(
                modifier = Modifier
                    .horizontalScroll(rememberScrollState())
                    .padding(vertical = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                group.options.forEach { option ->
                    PillChip(
                        text = option.label,
                        selected = option.id == selectedId,
                        enabled = enabled,
                        accent = accent,
                        onClick = { onSelect(option.id) },
                        modifier = Modifier.testTag("$tagPrefix-option-${option.id}"),
                    )
                }
            }
        }
    }
}
