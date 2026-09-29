package dev.ironjanowar.fretboard.ui.tuning

import dev.ironjanowar.fretboard.core.AdapterException
import dev.ironjanowar.fretboard.session.engineFailure
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The tuning sheet's preset picker.
 *
 * The names are the engine's own ordered catalog (`presets(instrument)`), and
 * the client's whole share is wrapping the answer: it keeps the engine's order,
 * it never sorts or filters, it treats the engine's empty answer (the keyboard,
 * which has no tuning) as *not applicable*, and it shows a refused call as a
 * refusal instead of falling back to a list of its own.
 *
 * The binding itself cannot run on this host, so the answers are scripted here;
 * the model is the seam that the JVM can pin.
 */
class PresetPickerTest {

    @Test
    fun `the names are the engine's own, in the engine's own order`() {
        val fromTheEngine = listOf("Standard", "Drop D", "Half Step Down")

        val state = presetPicker(fromTheEngine)

        assertEquals(PresetPickerState.Ready(fromTheEngine), state)
        assertEquals(
            "the engine's order is kept, never sorted",
            fromTheEngine,
            (state as PresetPickerState.Ready).names,
        )
    }

    @Test
    fun `the picker keeps duplicates and odd names exactly as the engine sent them`() {
        val fromTheEngine = listOf("Standard", "Standard", "Custom")

        val state = presetPicker(fromTheEngine) as PresetPickerState.Ready

        assertEquals(fromTheEngine, state.names)
        assertEquals(3, state.names.size)
    }

    @Test
    fun `the keyboard's empty catalog is not applicable`() {
        // The engine answers no presets for a keyboard: it has no tuning. That is
        // shown as not applicable, not as an empty control.
        assertEquals(PresetPickerState.NotApplicable, presetPicker(emptyList()))
    }

    @Test
    fun `a refusal is shown as a refusal, with the engine's own sentence`() {
        val refusal = AdapterException.InvalidState(
            sentence = "the instrument has no presets",
            field = "instrument",
        )

        val state = presetPickerRefusal(refusal)

        assertTrue("a refusal is its own state", state is PresetPickerState.Refused)
        assertEquals(
            "The engine rejected the request (InvalidState): the instrument has no presets",
            (state as PresetPickerState.Refused).reason,
        )
    }

    @Test
    fun `an engine that cannot be reached is also a refusal, never a guessed list`() {
        val failure = IllegalStateException("no library")
        val state = presetPickerRefusal(failure)

        assertTrue(state is PresetPickerState.Refused)
        assertEquals(
            // The wiring, not the wording: `engineFailure`'s own sentence is pinned
            // in `session/EngineFailureTest.kt`, so this assertion cannot drift from
            // it and does not repeat it.
            engineFailure(failure),
            (state as PresetPickerState.Refused).reason,
        )
    }
}
