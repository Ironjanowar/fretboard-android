package dev.ironjanowar.fretboard.ui.controls

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.ironjanowar.fretboard.core.InstrumentDefinitionDto
import dev.ironjanowar.fretboard.core.InstrumentDto
import dev.ironjanowar.fretboard.ui.common.PillChip

/**
 * The instrument picker.
 *
 * Built from `instruments()`: all five of the engine's catalog instruments, in
 * the order the engine sent them, with the engine's own display name — so the
 * ukulele shows as "Ukulele" while the wire id stays `UKELELE`. The client adds
 * no instrument and hides none.
 *
 * Selecting the instrument that is already active is a no-op, which the session
 * coordinator enforces before it touches the state.
 */
@Composable
fun InstrumentPicker(
    instruments: List<InstrumentDefinitionDto>,
    selected: InstrumentDto,
    onSelect: (InstrumentDefinitionDto) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Row(
        modifier = modifier
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        instruments.forEach { definition ->
            PillChip(
                text = definition.name,
                selected = definition.instrument == selected,
                enabled = enabled,
                accent = MaterialTheme.colorScheme.secondary,
                onClick = { onSelect(definition) },
                modifier = Modifier.testTag("instrument-${definition.instrument.name}"),
            )
        }
    }
}

/** A small caption used above a control. */
@Composable
fun ControlCaption(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.SemiBold,
        modifier = modifier,
    )
}
