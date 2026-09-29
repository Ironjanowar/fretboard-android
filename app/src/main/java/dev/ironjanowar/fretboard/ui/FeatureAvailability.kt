package dev.ironjanowar.fretboard.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * An explicit English placeholder for a surface this build does not have yet.
 *
 * The plan's A06 asks for the not-yet-built piano visualizer and the analyzer to
 * say so rather than fall back to another kind's UI (which would either crash or
 * mislead on the new state). The engine may already answer for them; the screen
 * still says plainly that it does not draw them.
 */
@Composable
fun FeatureAvailability(
    title: String,
    detail: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
            .padding(16.dp)
            .testTag("feature-placeholder"),
    ) {
        Text(
            text = title,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.titleSmall,
        )
        Text(
            text = detail,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}
