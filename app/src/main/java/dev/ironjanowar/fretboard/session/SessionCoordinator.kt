package dev.ironjanowar.fretboard.session

import dev.ironjanowar.fretboard.core.AdapterException
import dev.ironjanowar.fretboard.core.AnalysisDto
import dev.ironjanowar.fretboard.core.ChordDetailsDto
import dev.ironjanowar.fretboard.core.FrettedSurfaceDto
import dev.ironjanowar.fretboard.core.InstrumentDefinitionDto
import dev.ironjanowar.fretboard.core.InstrumentDto
import dev.ironjanowar.fretboard.core.InstrumentStateDto
import dev.ironjanowar.fretboard.core.KeyboardSurfaceDto
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
import dev.ironjanowar.fretboard.core.keyboardSurface
import dev.ironjanowar.fretboard.core.presets
import dev.ironjanowar.fretboard.core.qualityGroups
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

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
 * Nothing here is computed by Kotlin: `state`, `details`, `slots`, `surface`,
 * `keyboard` and `analysis` are the engine's own answers.
 */
data class SessionView(
    val state: PageStateDto,
    val instruments: List<InstrumentDefinitionDto>,
    val qualityGroups: List<QualityGroupDto>,
    val details: List<ChordDetailsDto>,
    val slots: List<ULong>,
    /** The fretted surface, or `null` for the keyboard. */
    val surface: FrettedSurfaceDto?,
    /** The keyboard surface, or `null` for a fretted instrument. */
    val keyboard: KeyboardSurfaceDto?,
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
    keyboard = when (state.instrument) {
        is InstrumentStateDto.Fretted -> null
        is InstrumentStateDto.Piano -> keyboardSurface(state)
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
suspend fun startSession(): SessionLoad = restoreSession(defaultState())

/**
 * The engine's own view of one page state the client already holds.
 *
 * This is the same derivation a fresh session goes through — the surfaces, the
 * chord details, the colour slots, the analysis and the catalogs are all asked for
 * again — so a page read back from disk cannot arrive on screen as a half-built
 * session, and no client-side reconstruction of a stored page exists to drift from
 * the engine's own.
 */
suspend fun restoreSession(state: PageStateDto): SessionLoad = withContext(Dispatchers.Default) {
    try {
        SessionLoad.Ready(derive(state, instruments(), qualityGroups()))
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
 * The page reducer, as the session uses it.
 *
 * Every committed transition is the engine's: the client hands it the state it
 * holds and the event, and takes the page it answers with. This exists as a port
 * only so the boundary between the kinds can be pinned on the JVM, where the
 * arm64 native library cannot be loaded; the production implementation is
 * [BindingPageEngine] and decides nothing.
 */
interface PageEngine {
    fun apply(state: PageStateDto, event: PageEventDto): PageStateDto
}

/** The production port: the pinned binding, called as it is. */
object BindingPageEngine : PageEngine {
    override fun apply(state: PageStateDto, event: PageEventDto): PageStateDto =
        applyPageEvent(state, event)
}

/**
 * The event that switches to [target], or `null` when it is already active.
 *
 * A selection of the current instrument is a no-op and sends nothing, so it can
 * never produce a spurious revision. Otherwise the one event is the engine's
 * `SetInstrument`; the client assembles no page and carries no value across the
 * boundary.
 */
fun instrumentSwitchEvent(current: InstrumentDto, target: InstrumentDto): PageEventDto? =
    if (current == target) null else PageEventDto.SetInstrument(target)

/**
 * Switch the committed page to [target] through the engine, or return it
 * untouched when the target is already active.
 *
 * Crossing the fretted/piano boundary is the engine's own rule: it clears the
 * selection entirely (a string position is never a key and a key is never a
 * string position), keeps the chords and the tab, clears the highlight and
 * resets the target's tuning. The client sends one `SetInstrument` event and
 * returns exactly the page the engine answered — never a page it assembled
 * itself, and never the old selection converted.
 */
fun switchInstrument(
    state: PageStateDto,
    current: InstrumentDto,
    target: InstrumentDto,
    engine: PageEngine,
): PageStateDto {
    val event = instrumentSwitchEvent(current, target) ?: return state
    return engine.apply(state, event)
}

/**
 * Switch to another catalog instrument.
 *
 * The switch is the engine's own `SetInstrument` event, for a fretted instrument
 * and for the keyboard alike: it resets the target's Standard tuning, keeps only
 * the marks whose string still exists, keeps the chords and the tab and clears
 * the highlight. The client asks and reads the answer; it never assembles the
 * target state itself.
 *
 * Selecting the current instrument is a no-op: no event is sent and the state
 * comes back untouched.
 */
suspend fun selectInstrument(
    view: SessionView,
    target: InstrumentDefinitionDto,
    engine: PageEngine = BindingPageEngine,
): SessionLoad = withContext(Dispatchers.Default) {
    try {
        val switched = switchInstrument(
            state = view.state,
            current = view.state.instrumentId(),
            target = target.instrument,
            engine = engine,
        )
        SessionLoad.Ready(derive(switched, view.instruments, view.qualityGroups))
    } catch (error: Throwable) {
        SessionLoad.Failed(engineFailure(error))
    }
}

/**
 * The engine's ordered preset names for [instrument], off the main thread.
 *
 * This is the enumeration the tuning sheet's picker reads: the frozen catalog's
 * own names in the frozen order, and an empty list for the keyboard, which has
 * no tuning. The client carries no list of its own.
 */
suspend fun instrumentPresets(instrument: InstrumentDto): List<String> =
    withContext(Dispatchers.Default) { presets(instrument) }

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

/**
 * The last fret of the current fretted instrument, from the engine's own
 * catalog definition — never a hardcoded 24. `null` for the keyboard.
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

/**
 * The committed keys of a keyboard page, by absolute pitch.
 *
 * The engine holds the piano's canonical selection as the exact pitches it was
 * given, unique and ascending; the client reads it and never derives it from
 * anything else. The bytes are the generated binding's signed view of the Rust
 * `Vec<u8>`, so every one is read unsigned.
 */
fun SessionView.markedPitches(): Set<Int> {
    val piano = state.instrument as? InstrumentStateDto.Piano ?: return emptySet()
    return piano.selected.map { byte -> byte.toInt() and 0xFF }.toSet()
}

/** The committed tuning of a fretted page, or `null` on the keyboard. */
fun SessionView.committedTuning(): TuningDto? =
    (state.instrument as? InstrumentStateDto.Fretted)?.tuning
