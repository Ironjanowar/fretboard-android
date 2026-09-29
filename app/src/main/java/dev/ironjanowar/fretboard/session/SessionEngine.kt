package dev.ironjanowar.fretboard.session

import dev.ironjanowar.fretboard.core.InstrumentDefinitionDto
import dev.ironjanowar.fretboard.core.InstrumentDto
import dev.ironjanowar.fretboard.core.PageEventDto
import dev.ironjanowar.fretboard.core.PageStateDto

/**
 * Every engine round trip the session screen makes, as one port.
 *
 * The state holder that keeps a session across a configuration change needs the
 * engine's answers, but it must not need the engine itself: this port is the
 * seam that lets the holder's transitions be pinned on the JVM, where the arm64
 * native library cannot be loaded. Nothing here decides anything — each method
 * is one call the session coordinator already makes, with the engine's own
 * answer handed straight back.
 *
 * The production implementation is [BindingSessionEngine]; a test hands the
 * holder a scripted one, which is exactly the point: an assertion about a
 * musical value can only ever be an assertion about the scripted answer.
 */
interface SessionEngine {

    /** The fresh session: the engine's default state plus its two catalogs. */
    suspend fun start(): SessionLoad

    /**
     * The engine's own view of one page state the client already holds.
     *
     * A stored session comes back as the page it was written as, and a page on its
     * own is not a screen: the surfaces, the chord details, the colour slots and
     * the analysis are the engine's answers and are asked for again here, exactly
     * as they are for a fresh session. Nothing about a restored page is assembled
     * by the client.
     */
    suspend fun restore(page: PageStateDto): SessionLoad

    /** One page event, answered by the engine's own reducer. */
    suspend fun apply(view: SessionView, event: PageEventDto): SessionLoad

    /** Switch the committed page to another catalog instrument. */
    suspend fun switch(view: SessionView, target: InstrumentDefinitionDto): SessionLoad

    /** The engine's ordered preset names for an instrument, in the engine's order. */
    suspend fun presetNames(instrument: InstrumentDto): List<String>

    /** The draft a committed page opens, or `null` on the keyboard. */
    suspend fun openDraft(view: SessionView): TuningDraft?

    /** A preset applied to the draft: the engine's answer for the whole draft. */
    suspend fun selectPreset(draft: TuningDraft, preset: String): TuningDraft

    /** One string's note edited in the draft: the engine resolves the pitch. */
    suspend fun changeString(draft: TuningDraft, stringIndex: Int, note: String): TuningDraft

    /** The draft committed through the engine's own event, or an explicit refusal. */
    suspend fun commit(view: SessionView, draft: TuningDraft): SessionLoad
}

/**
 * The production port: the pinned bindings, called as they are.
 *
 * Every method is one of the session coordinator's own calls, so no path through
 * this file is a second music path: the values on screen remain the engine's.
 */
object BindingSessionEngine : SessionEngine {

    override suspend fun start(): SessionLoad = startSession()

    override suspend fun restore(page: PageStateDto): SessionLoad = restoreSession(page)

    override suspend fun apply(view: SessionView, event: PageEventDto): SessionLoad =
        applyEvent(view, event)

    override suspend fun switch(
        view: SessionView,
        target: InstrumentDefinitionDto,
    ): SessionLoad = selectInstrument(view, target)

    override suspend fun presetNames(instrument: InstrumentDto): List<String> =
        instrumentPresets(instrument)

    override suspend fun openDraft(view: SessionView): TuningDraft? = openTuningDraft(view)

    override suspend fun selectPreset(draft: TuningDraft, preset: String): TuningDraft =
        selectTuningPreset(draft, preset)

    override suspend fun changeString(
        draft: TuningDraft,
        stringIndex: Int,
        note: String,
    ): TuningDraft = changeTuningString(draft, stringIndex, note)

    override suspend fun commit(view: SessionView, draft: TuningDraft): SessionLoad =
        applyTuningDraft(view, draft)
}
