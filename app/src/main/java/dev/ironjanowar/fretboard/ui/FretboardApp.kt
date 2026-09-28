package dev.ironjanowar.fretboard.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.ironjanowar.fretboard.core.AdapterException
import dev.ironjanowar.fretboard.core.ChordDto
import dev.ironjanowar.fretboard.core.InstrumentStateDto
import dev.ironjanowar.fretboard.core.chordDetails
import dev.ironjanowar.fretboard.core.defaultState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * What the first real engine call produced.
 *
 * The application shows an explicit English state for each case and never a
 * plausible fake answer: a failure is a failure, not an empty chord.
 */
sealed interface EngineOutcome {
    /** The call has not finished yet. */
    data object Loading : EngineOutcome

    /** The engine answered: these strings are the engine's own values. */
    data class Ready(
        val instrument: String,
        val tuning: String,
        val chordLabel: String,
        val notes: List<String>,
    ) : EngineOutcome

    /** The engine could not answer, for a reason that is shown to the user. */
    data class Failed(val reason: String) : EngineOutcome
}

/**
 * The display text of a tuning state: its reference and the exact pitches, in
 * physical string order.
 *
 * The generated binding carries the pitches as a signed `ByteArray` (the Rust
 * `Vec<u8>`), so every byte must be read as unsigned: a pitch of 127 would
 * otherwise show as -1. This is the only transformation the application does,
 * and it is pinned by a unit test.
 */
fun describeTuning(reference: String, pitches: ByteArray): String =
    "$reference ${pitches.map { byte -> byte.toInt() and 0xFF }.joinToString("-")}"

/**
 * Ask the engine for the default state and for C major.
 *
 * This is deliberately the whole "session" of the first milestone: it proves the
 * generated bindings load, call across UniFFI and come back with the engine's
 * own values. The native call runs off the main thread.
 */
suspend fun loadEngineOutcome(): EngineOutcome = withContext(Dispatchers.Default) {
    try {
        val state = defaultState()
        val (instrument, tuning) = when (val value = state.instrument) {
            is InstrumentStateDto.Fretted ->
                value.instrument.name to describeTuning(value.tuning.reference, value.tuning.pitches)
            is InstrumentStateDto.Piano -> "PIANO" to "keys"
        }

        val details = chordDetails(ChordDto("C", "major"))
        EngineOutcome.Ready(
            instrument = instrument,
            tuning = tuning,
            chordLabel = details.label,
            notes = details.notes,
        )
    } catch (error: AdapterException) {
        // The sealed hierarchy names the failure; the sentence and the field live
        // on each variant, while the base type only renders them into `message`.
        // A Kotlin-facing accessor on the base type belongs to the binding work,
        // not to the screen.
        val variant = error::class.simpleName ?: "AdapterError"
        EngineOutcome.Failed("The engine rejected the request ($variant): ${error.message}")
    } catch (error: Throwable) {
        EngineOutcome.Failed("The engine could not be loaded: ${error.message ?: error.toString()}")
    }
}

/** The first screen: the application's name and the engine's real answer. */
@Composable
fun FretboardApp() {
    MaterialTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            var outcome by remember { mutableStateOf<EngineOutcome>(EngineOutcome.Loading) }
            LaunchedEffect(Unit) { outcome = loadEngineOutcome() }

            Column(modifier = Modifier.padding(24.dp)) {
                Text("Fretboard", style = MaterialTheme.typography.headlineMedium)
                Spacer(Modifier.height(16.dp))
                when (val current = outcome) {
                    EngineOutcome.Loading -> CircularProgressIndicator()
                    is EngineOutcome.Ready -> {
                        Text("Engine answered", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(8.dp))
                        Text("Instrument: ${current.instrument}")
                        Text("Tuning: ${current.tuning}")
                        Text("C major: ${current.chordLabel}")
                        Text("Notes: ${current.notes.joinToString(", ")}")
                    }
                    is EngineOutcome.Failed -> {
                        Text("Not available", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(8.dp))
                        Text(current.reason)
                    }
                }
            }
        }
    }
}
