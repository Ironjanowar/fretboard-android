package dev.ironjanowar.fretboard.session

import dev.ironjanowar.fretboard.core.AdapterException
import dev.ironjanowar.fretboard.core.AnalysisDto
import dev.ironjanowar.fretboard.core.ChordDetailsDto
import dev.ironjanowar.fretboard.core.FrettedSurfaceDto
import dev.ironjanowar.fretboard.core.InstrumentDefinitionDto
import dev.ironjanowar.fretboard.core.InstrumentDto
import dev.ironjanowar.fretboard.core.InstrumentKindDto
import dev.ironjanowar.fretboard.core.InstrumentStateDto
import dev.ironjanowar.fretboard.core.PageEventDto
import dev.ironjanowar.fretboard.core.PageStateDto
import dev.ironjanowar.fretboard.core.QualityGroupDto
import dev.ironjanowar.fretboard.core.TuningDto
import dev.ironjanowar.fretboard.core.analyzePage
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

/** The English sentence shown when a draft no longer belongs to the page. */
const val STALE_DRAFT_MESSAGE: String =
    "The tuning draft no longer matches this page: the instrument changed. Reopen Tuning."

/**
 * The engine's analysis slot of one page.
 *
 * Three different things, kept apart: `Absent` is the engine answering *no*
 * analysis for this page at all (the visualizer tab), `Answer` is its typed
 * result — an empty selection included — and `Failed` is a refused or
 * unreachable call. None of them is turned into another, and a refusal never
 * takes the rest of the page down with it.
 */
sealed interface SessionAnalysis {
    /** The engine answers no analysis for this page: the visualizer tab. */
    data object Absent : SessionAnalysis

    /** The engine's typed answer. */
    data class Answer(val analysis: AnalysisDto) : SessionAnalysis

    /** The engine refused the analysis, or could not be reached for it. */
    data class Failed(val error: Throwable) : SessionAnalysis
}

/**
 * Everything the screen needs to draw one committed session.
 *
 * Nothing here is computed by Kotlin: `state`, `details`, `slots`, `surface` and
 * `analysis` are the engine's own answers.
 */
data class SessionView(
    val state: PageStateDto,
    val instruments: List<InstrumentDefinitionDto>,
    val qualityGroups: List<QualityGroupDto>,
    val details: List<ChordDetailsDto>,
    val slots: List<ULong>,
    /** The fretted surface, or `null` for the keyboard (its surface is P4). */
    val surface: FrettedSurfaceDto?,
    /** The engine's analysis of the committed selection, or why there is none. */
    val analysis: SessionAnalysis,
)

/** The result of one engine round trip: either a session or an explicit reason. */
sealed interface SessionLoad {
    data class Ready(val view: SessionView) : SessionLoad
    data class Failed(val reason: String) : SessionLoad
}

/**
 * The engine's own English sentence for a typed refusal.
 *
 * The generated binding renders an error variant as `sentence=…, field=…` in
 * its `message`; the sentence the domain wrote is what the screen should show,
 * so it is read from the variant itself. The `when` is exhaustive on purpose:
 * a variant added by a later engine revision breaks this build rather than
 * silently rendering its field dump.
 */
fun adapterSentence(error: AdapterException): String = when (error) {
    is AdapterException.InvalidState -> error.sentence
    is AdapterException.UnknownIdentifier -> error.sentence
    is AdapterException.OutOfRange -> error.sentence
    is AdapterException.InvalidAction -> error.sentence
    is AdapterException.InvalidUrl -> error.sentence
    is AdapterException.UnsupportedOrigin -> error.sentence
    is AdapterException.InputTooLarge -> error.sentence
    is AdapterException.InvalidSnapshot -> error.sentence
    is AdapterException.UnsupportedSchemaVersion -> error.sentence
    is AdapterException.UnsupportedCapability -> error.sentence
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
            adapterSentence(error)
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
    analysis = analyzeQuietly(state),
)

/**
 * The engine's analysis, kept a step apart from the page.
 *
 * A refused analysis must not take the committed page down with it: the
 * analyzer tab then says what the engine answered — a pending capability has its
 * own state — while the surface, the chords and the tuning keep working.
 */
private fun analyzeQuietly(state: PageStateDto): SessionAnalysis = try {
    analyzePage(state)?.let(SessionAnalysis::Answer) ?: SessionAnalysis.Absent
} catch (error: Throwable) {
    SessionAnalysis.Failed(error)
}

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
 * page unchanged, a tap toggles or replaces a string's mark, the highlight
 * toggles by identity and a removal keeps the highlight while another occurrence
 * of the same chord remains.
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
 * The switch is the engine's own `SetInstrument` event: it resets the target's
 * Standard tuning, keeps only the marks whose string still exists, keeps the
 * chords and the tab and clears the highlight. The client asks and reads the
 * answer; it never assembles the target state itself.
 *
 * One boundary is honest to name: the pinned reducer's `SetInstrument` has no
 * keyboard target yet (its `Standard` preset lookup fails for the piano), so a
 * switch to the keyboard still goes through the client-assembled candidate page
 * and `validateState`, which is what the P2 build shipped. The fretted targets
 * never take that path.
 *
 * Selecting the current instrument is a no-op: the state comes back untouched.
 */
suspend fun selectInstrument(
    view: SessionView,
    target: InstrumentDefinitionDto,
): SessionLoad = withContext(Dispatchers.Default) {
    try {
        if (target.instrument == view.state.instrumentId()) {
            SessionLoad.Ready(view)
        } else {
            val switched = applyPageEvent(
                view.state,
                PageEventDto.SetInstrument(target.instrument),
            )
            val next = if (switched.instrumentId() == target.instrument) {
                switched
            } else {
                validateState(stateWithInstrument(view.state, target))
            }
            SessionLoad.Ready(derive(next, view.instruments, view.qualityGroups))
        }
    } catch (error: Throwable) {
        SessionLoad.Failed(engineFailure(error))
    }
}

/**
 * Open the tuning draft of the committed page, off the main thread.
 *
 * `null` means the page opens no draft: the piano has no tuning.
 */
suspend fun openTuningDraft(view: SessionView): TuningDraft? =
    withContext(Dispatchers.Default) { TUNING_DRAFTS.open(view.state) }

/** Select a preset in the draft: the engine's own answer for the whole draft. */
suspend fun selectTuningPreset(draft: TuningDraft, preset: String): TuningDraft =
    withContext(Dispatchers.Default) { TUNING_DRAFTS.selectPreset(draft, preset) }

/** Edit one string's note in the draft: the engine resolves the pitch against the reference. */
suspend fun changeTuningString(draft: TuningDraft, stringIndex: Int, note: String): TuningDraft =
    withContext(Dispatchers.Default) { TUNING_DRAFTS.changeString(draft, stringIndex, note) }

/**
 * Apply the draft through the engine's own `CommitTuning` event.
 *
 * A draft whose instrument no longer matches the page is not committed: the
 * failure is explicit English, never a silent write into another instrument.
 */
suspend fun applyTuningDraft(view: SessionView, draft: TuningDraft): SessionLoad =
    withContext(Dispatchers.Default) {
        try {
            val committed = TUNING_DRAFTS.apply(view.state, draft)
            if (committed == null) {
                SessionLoad.Failed(STALE_DRAFT_MESSAGE)
            } else {
                SessionLoad.Ready(derive(committed, view.instruments, view.qualityGroups))
            }
        } catch (error: Throwable) {
            SessionLoad.Failed(engineFailure(error))
        }
    }

/** The instrument id the current state names, whichever kind it is. */
fun PageStateDto.instrumentId(): InstrumentDto = when (val current = instrument) {
    is InstrumentStateDto.Fretted -> current.instrument
    // The keyboard carries no id in the typed state; `PIANO` is the catalog's
    // own keyboard entry, and this only ever decides whether a tap was a no-op.
    is InstrumentStateDto.Piano -> InstrumentDto.PIANO
}

/** The candidate state for switching to [target], when the engine's event cannot. */
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

/** The current instrument's catalog entry, the engine's own definition. */
fun SessionView.instrumentDefinition(): InstrumentDefinitionDto? {
    val id = state.instrumentId()
    return instruments.firstOrNull { it.instrument == id }
}

/** The committed marks of a fretted page: one fret per physical string, the engine's own. */
fun SessionView.markedFrets(): Map<Int, Int> {
    val fretted = state.instrument as? InstrumentStateDto.Fretted ?: return emptyMap()
    return fretted.selected.associate { position -> position.string.toInt() to position.fret.toInt() }
}

/** The committed tuning of a fretted page, or `null` on the keyboard. */
fun SessionView.committedTuning(): TuningDto? =
    (state.instrument as? InstrumentStateDto.Fretted)?.tuning
