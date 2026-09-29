package dev.ironjanowar.fretboard.ui.controls

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.ironjanowar.fretboard.core.InstrumentDefinitionDto
import dev.ironjanowar.fretboard.core.InstrumentDto
import dev.ironjanowar.fretboard.ui.common.CatalogDropdown
import dev.ironjanowar.fretboard.ui.common.PickerGroup
import dev.ironjanowar.fretboard.ui.common.PickerOption

/**
 * The instrument picker: a dropdown over the engine's own catalog.
 *
 * Built from `instruments()`: all five of the engine's catalog instruments, in
 * the order the engine sent them, with the engine's own display name — so the
 * ukulele shows as "Ukulele" while the wire id stays `UKELELE`. The client adds
 * no instrument and hides none.
 *
 * It is a dropdown rather than a row of pills, mirroring the web's
 * `instrument-select`; the field shows the active instrument, and the menu lists
 * the whole catalog.
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
    val byId = instruments.associateBy { definition -> definition.instrument.name }
    CatalogDropdown(
        groups = listOf(
            PickerGroup(
                title = "",
                options = instruments.map { definition ->
                    PickerOption(id = definition.instrument.name, label = definition.name)
                },
            ),
        ),
        selectedId = selected.name,
        onSelect = { id -> byId[id]?.let(onSelect) },
        modifier = modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        enabled = enabled,
        tagPrefix = "instrument",
    )
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
