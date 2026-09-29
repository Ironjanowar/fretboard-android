package dev.ironjanowar.fretboard.ui.tuning

import dev.ironjanowar.fretboard.session.engineFailure

/**
 * What the tuning sheet's preset picker shows.
 *
 * The names are never a client list: they are the engine's own ordered catalog
 * (`presets(instrument)`), which answers the frozen preset names in the frozen
 * order, and an empty list for the keyboard, which has no tuning. The picker
 * therefore has three states and no fourth: the engine's names, the engine's
 * "none", or the engine's refusal.
 */
sealed interface PresetPickerState {

    /** The engine names no presets at all: the keyboard has no tuning. */
    data object NotApplicable : PresetPickerState

    /** The engine's own names, in the engine's own order, unfiltered and unsorted. */
    data class Ready(val names: List<String>) : PresetPickerState

    /** The engine refused or could not be reached; the reason is shown, not hidden. */
    data class Refused(val reason: String) : PresetPickerState
}

/**
 * Wrap the engine's answer.
 *
 * An empty answer is the engine's own statement that this instrument has no
 * presets — the keyboard's tuning does not exist — so it is shown as *not
 * applicable* rather than as an empty control. A non-empty answer keeps the
 * engine's order exactly: the client neither sorts nor filters it.
 */
fun presetPicker(names: List<String>): PresetPickerState =
    if (names.isEmpty()) PresetPickerState.NotApplicable else PresetPickerState.Ready(names)

/**
 * Wrap a refused preset call.
 *
 * A refusal is a refusal: the sheet says the engine refused and repeats the
 * engine's own sentence, and never falls back to a list the client wrote.
 */
fun presetPickerRefusal(error: Throwable): PresetPickerState =
    PresetPickerState.Refused(engineFailure(error))
