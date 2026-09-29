package dev.ironjanowar.fretboard.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * The distinct states of an asynchronous engine answer, shared by the keys
 * panels.
 *
 * A16 asks for pending, empty, failed and Retry to be distinct, honest states
 * rather than one vague placeholder: a calculation still running says so, an
 * engine that answered nothing says *that*, and a refused call says what the
 * engine said and offers the Retry action. None of them is dressed up as an
 * answer, and none is an empty box.
 */

/** A calculation is in flight. */
@Composable
fun EvaluationPending(
    label: String,
    modifier: Modifier = Modifier,
    tag: String = "evaluation-pending",
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
            .padding(14.dp)
            .testTag(tag),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.padding(2.dp))
        Text(
            text = label,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

/** The engine answered, and its answer was empty. */
@Composable
fun EvaluationEmpty(
    label: String,
    modifier: Modifier = Modifier,
    tag: String = "evaluation-empty",
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
            .padding(14.dp)
            .testTag(tag),
    ) {
        Text(
            text = "Nothing to suggest",
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.titleSmall,
        )
        Text(
            text = label,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}

/** The engine refused the call, or could not be reached for it. */
@Composable
fun EvaluationFailed(
    reason: String,
    onRetry: () -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    tag: String = "evaluation-failed",
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.error.copy(alpha = 0.15f))
            .padding(14.dp)
            .testTag(tag),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            // Not the "not built yet" placeholder's wording: this panel is a call
            // that was made and came back with nothing, which is a different
            // statement, and the reason below says which kind it was.
            text = "The engine did not answer",
            color = MaterialTheme.colorScheme.error,
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.titleSmall,
        )
        Text(
            text = reason,
            color = MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.bodySmall,
        )
        OutlinedButton(
            onClick = onRetry,
            enabled = enabled,
            modifier = Modifier.testTag("evaluation-retry"),
        ) {
            Text("Try again")
        }
    }
}
