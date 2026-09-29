package dev.ironjanowar.fretboard.session

import dev.ironjanowar.fretboard.core.AdapterException
import dev.ironjanowar.fretboard.core.ChordDetailsDto
import dev.ironjanowar.fretboard.core.FrettedSurfaceDto
import dev.ironjanowar.fretboard.core.InstrumentDefinitionDto
import dev.ironjanowar.fretboard.core.InstrumentKindDto
import dev.ironjanowar.fretboard.core.InstrumentStateDto
import dev.ironjanowar.fretboard.core.PageEventDto
import dev.ironjanowar.fretboard.core.PageStateDto
import dev.ironjanowar.fretboard.core.QualityGroupDto
import dev.ironjanowar.fretboard.core.TuningDto
import dev.ironjanowar.fretboard.core.applyPageEvent
import dev.ironjanowar.fretboard.core.chordColorSlots
import dev.ironjanowar.fretboard.core.chordDetails
import dev.ironjanowar.fretboard.core.defaultState
import dev.ironjanowar.fretboard.core.frettedSurface
import dev.ironjanowar.fretboard.core.instruments
import dev.ironjanowar.fretboard.core.qualityGroups
import dev.ironjanowar.fretboard.core.validateState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The catalog token every fretted instrument's default preset carries.
 *
 * `defaultState()` answers it for the guitar and the engine refuses a state
 * whose tuning reference is not one of its own presets, so this constant is the
 * one catalog string the client needs to switch instrument. Every value the
 * client sends is validated by the engine before it is shown.
 */
const val STANDARD_REFERENCE: String = "Standard"

/**
 * Everything the visualizer screen needs to draw one committed session.
 *
 * Nothing here is computed by Kotlin: `state`, `details`, `slots` and `surface`
 * are the engine's own answers.
 */
data class SessionView(
    val state: PageStateDto,
    val instruments: List<InstrumentDefinitionDto>,
    val qualityGroups: List<QualityGroupDto>,
    val details: List<ChordDetailsDto>,
    val slots: List<ULong>,
    /** The fretted surface, or `null` for the keyboard (its surface is P4). */
    val surface: FrettedSurfaceDto?,
)

/** The result of one engine round trip: either a session or an explicit reason. */
sealed interface SessionLoad {
    data class Ready(val view: SessionView) : SessionLoad
    data class Failed(val reason: String) : SessionLoad
}

/**
 * The English sentence shown for a failed engine call.
 *
 * A failure is never dressed up as a plausible answer: the screen says the
 * engine refused and names the variant and the sentence the binding carried.
 */
fun engineFailure(error: Throwable): String = when (error) {
    is AdapterException ->
        "The engine rejected the request (${error::class.simpleName ?: "AdapterError"}): " +
            "${error.message}"
    else ->
        "The engine could not be loaded: ${error.message ?: error.toString()}"
}

/** Ask the engine for every answer the screen shows, off the main thread. */
private fun derive(
    state: PageStateDto,
    instruments: List<InstrumentDefinitionDto>,
    qualityGroups: List<QualityGroupDto>,
): SessionView = SessionView(
    state = state,
    instruments = instruments,
    qualityGroups = qualityGroups,
    details = state.chords.map { chord -> chordDetails(chord) },
    slots = chordColorSlots(state),
    surface = when (state.instrument) {
        is InstrumentStateDto.Fretted -> frettedSurface(state)
        is InstrumentStateDto.Piano -> null
    },
)

/** The fresh session: the engine's default state plus the two frozen catalogs. */
suspend fun startSession(): SessionLoad = withContext(Dispatchers.Default) {
    try {
        SessionLoad.Ready(derive(defaultState(), instruments(), qualityGroups()))
    } catch (error: Throwable) {
        SessionLoad.Failed(engineFailure(error))
    }
}

/**
 * Apply one page event and re-derive the view from whatever the engine returned.
 *
 * The reducer, not the client, decides what an event does: a no-op returns the
 * page unchanged, the highlight toggles by identity and a removal keeps the
 * highlight while another occurrence of the same chord remains.
 */
suspend fun applyEvent(view: SessionView, event: PageEventDto): SessionLoad =
    withContext(Dispatchers.Default) {
        try {
            SessionLoad.Ready(
                derive(
                    applyPageEvent(view.state, event),
                    view.instruments,
                    view.qualityGroups,
                ),
            )
        } catch (error: Throwable) {
            SessionLoad.Failed(engineFailure(error))
        }
    }

/**
 * Switch to another catalog instrument.
 *
 * The engine exposes no instrument event, so the client assembles the candidate
 * state from the engine's own catalog data — the target definition's
 * `standardPitches`, its own string count — and hands it to `validateState`:
 * the engine either accepts the whole state or the switch is reported as a
 * failure, never shown as if it had happened. Positions that do not exist on the
 * target are dropped and the highlight is cleared; the chord list is preserved.
 *
 * Selecting the current instrument is a no-op: the state it was given comes back
 * untouched.
 */
suspend fun selectInstrument(
    view: SessionView,
    target: InstrumentDefinitionDto,
): SessionLoad = withContext(Dispatchers.Default) {
    try {
        if (target.instrument == view.state.instrument.instrumentId()) {
            SessionLoad.Ready(view)
        } else {
            val candidate = stateWithInstrument(view.state, target)
            SessionLoad.Ready(
                derive(validateState(candidate), view.instruments, view.qualityGroups),
            )
        }
    } catch (error: Throwable) {
        SessionLoad.Failed(engineFailure(error))
    }
}

/** The instrument id the current state names, whichever kind it is. */
private fun InstrumentStateDto.instrumentId(): dev.ironjanowar.fretboard.core.InstrumentDto =
    when (this) {
        is InstrumentStateDto.Fretted -> instrument
        // The keyboard carries no id in the typed state; `PIANO` is the catalog's
        // own keyboard entry, and this only ever decides whether a tap was a
        // no-op.
        is InstrumentStateDto.Piano -> dev.ironjanowar.fretboard.core.InstrumentDto.PIANO
    }

/** The candidate state for switching to [target], before the engine validates it. */
private fun stateWithInstrument(
    state: PageStateDto,
    target: InstrumentDefinitionDto,
): PageStateDto {
    val instrument = when (target.kind) {
        InstrumentKindDto.FRETTED -> InstrumentStateDto.Fretted(
            instrument = target.instrument,
            tuning = TuningDto(
                reference = STANDARD_REFERENCE,
                pitches = target.standardPitches,
            ),
            // Only positions the target actually has survive the switch; the
            // engine rejects the rest, so they are dropped, not reinterpreted.
            selected = when (val current = state.instrument) {
                is InstrumentStateDto.Fretted ->
                    current.selected.filter { position ->
                        position.string.toInt() < target.strings.toInt()
                    }
                is InstrumentStateDto.Piano -> emptyList()
            },
        )
        InstrumentKindDto.KEYBOARD -> InstrumentStateDto.Piano(selected = byteArrayOf())
    }
    return state.copy(instrument = instrument, highlight = null)
}

/**
 * The last fret of the current fretted instrument, from the engine's own
 * catalog definition — never a hardcoded 24. `null` for the keyboard, whose
 * surface is a later phase.
 */
fun SessionView.lastFret(): Int? {
    val fretted = state.instrument as? InstrumentStateDto.Fretted ?: return null
    val definition = instruments.firstOrNull { it.instrument == fretted.instrument } ?: return null
    return definition.frets?.toInt()
}
