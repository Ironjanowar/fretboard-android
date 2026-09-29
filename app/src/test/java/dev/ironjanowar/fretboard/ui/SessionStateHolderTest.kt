package dev.ironjanowar.fretboard.ui

import dev.ironjanowar.fretboard.core.ChordDto
import dev.ironjanowar.fretboard.core.InstrumentDefinitionDto
import dev.ironjanowar.fretboard.core.InstrumentDto
import dev.ironjanowar.fretboard.core.InstrumentKindDto
import dev.ironjanowar.fretboard.core.InstrumentStateDto
import dev.ironjanowar.fretboard.core.PageEventDto
import dev.ironjanowar.fretboard.core.PageStateDto
import dev.ironjanowar.fretboard.core.PositionDto
import dev.ironjanowar.fretboard.core.QualityDto
import dev.ironjanowar.fretboard.core.QualityGroupDto
import dev.ironjanowar.fretboard.core.TabDto
import dev.ironjanowar.fretboard.core.TuningDto
import dev.ironjanowar.fretboard.session.SessionAnalysis
import dev.ironjanowar.fretboard.session.SessionEngine
import dev.ironjanowar.fretboard.session.SessionLoad
import dev.ironjanowar.fretboard.session.SessionView
import dev.ironjanowar.fretboard.session.STALE_DRAFT_MESSAGE
import dev.ironjanowar.fretboard.session.TuningDraft
import dev.ironjanowar.fretboard.session.markedFrets
import dev.ironjanowar.fretboard.session.markedPitches
import dev.ironjanowar.fretboard.storage.support.FakeSessionStore
import dev.ironjanowar.fretboard.ui.tuning.PresetPickerState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The session's survival semantics, as a pure state machine over a scripted engine.
 *
 * The reported bug is a rotation: the activity — and with it the composition —
 * is destroyed and recreated, every value a composable held in `remember` is
 * initialised again, and the whole session is gone. A JVM test cannot rotate a
 * phone; what it *can* pin is the rule the fix depends on, which is where the
 * session lives and what happens to it when the screen asks again: the session
 * is held by this holder (not by a composition), the engine is asked for it once,
 * and a second ask returns the session already held instead of a fresh one.
 *
 * Every musical value below is scripted. The engine's arm64 library cannot load
 * on this host, so the port is a fake that answers exactly what a test hands it:
 * an assertion about a note, a mark, a label or a preset can therefore only ever
 * be an assertion about the scripted answer, which is what makes the holder's own
 * behaviour — and nothing else — the subject of these tests.
 *
 * The behavioural half of the regression is instrumented
 * (`app/src/androidTest/.../RotationStateTest.kt`), where `ActivityScenario`
 * really does recreate the activity.
 */
class SessionStateHolderTest {

    private val guitarStandard = TuningDto(
        pitches = byteArrayOf(40, 45, 50, 55, 59, 64),
        reference = "Standard",
    )
    private val guitarOpenG = TuningDto(
        pitches = byteArrayOf(38, 45, 50, 55, 59, 62),
        reference = "Open G",
    )

    private val qualityGroups = listOf(
        QualityGroupDto(
            group = "Triads",
            qualities = listOf(
                QualityDto(quality = "major", label = "maj"),
                QualityDto(quality = "minor", label = "m"),
            ),
        ),
    )

    private val guitarDefinition = InstrumentDefinitionDto(
        instrument = InstrumentDto.GUITAR,
        name = "Guitar",
        kind = InstrumentKindDto.FRETTED,
        strings = 6u,
        frets = 24u,
        standardPitches = byteArrayOf(40, 45, 50, 55, 59, 64),
    )

    private fun frettedPage(
        selected: List<PositionDto> = emptyList(),
        chords: List<ChordDto> = emptyList(),
        highlight: ChordDto? = null,
        tab: TabDto = TabDto.VISUALIZER,
        tuning: TuningDto = guitarStandard,
    ) = PageStateDto(
        instrument = InstrumentStateDto.Fretted(
            instrument = InstrumentDto.GUITAR,
            tuning = tuning,
            selected = selected,
        ),
        chords = chords,
        highlight = highlight,
        tab = tab,
    )

    private fun viewOf(
        state: PageStateDto,
        groups: List<QualityGroupDto> = qualityGroups,
    ) = SessionView(
        state = state,
        instruments = listOf(guitarDefinition),
        qualityGroups = groups,
        details = emptyList(),
        slots = emptyList(),
        surface = null,
        keyboard = null,
        analysis = SessionAnalysis.Absent,
    )

    private fun draftOf(tuning: TuningDto, preset: String = "Standard", notes: List<String>) =
        TuningDraft(
            instrument = InstrumentDto.GUITAR,
            tuning = tuning,
            preset = preset,
            notes = notes,
        )

    /** A scripted port: no musical rule, only the answers a test hands it. */
    private class ScriptedEngine : SessionEngine {
        var startAnswer: SessionLoad = SessionLoad.Failed("no session was scripted")
        var restoreAnswer: SessionLoad? = null
        var applyAnswer: SessionLoad? = null
        var switchAnswer: SessionLoad? = null
        var presetNames: List<String> = emptyList()
        var presetRefusal: Throwable? = null
        var draftAnswer: TuningDraft? = null
        var presetAnswer: TuningDraft? = null
        var stringAnswer: TuningDraft? = null
        var commitAnswer: SessionLoad = SessionLoad.Failed("no commit was scripted")

        var startCalls: Int = 0
        val applied = mutableListOf<PageEventDto>()
        val restored = mutableListOf<PageStateDto>()
        val switched = mutableListOf<InstrumentDefinitionDto>()
        val presetRequests = mutableListOf<InstrumentDto>()
        val openedFor = mutableListOf<SessionView>()
        val edits = mutableListOf<Pair<Int, String>>()
        var committed: Pair<SessionView, TuningDraft>? = null

        override suspend fun start(): SessionLoad {
            startCalls += 1
            return startAnswer
        }

        /**
         * The engine's own view of a page the client already holds (A17). This test
         * has no store, so the holder never routes through it; it is scripted all
         * the same, because a port whose only implementation is the binding would
         * let a store-less path look like a store-based one.
         */
        override suspend fun restore(page: PageStateDto): SessionLoad {
            restored += page
            return restoreAnswer ?: startAnswer
        }

        override suspend fun apply(view: SessionView, event: PageEventDto): SessionLoad {
            applied += event
            return applyAnswer ?: SessionLoad.Ready(view)
        }

        override suspend fun switch(
            view: SessionView,
            target: InstrumentDefinitionDto,
        ): SessionLoad {
            switched += target
            return switchAnswer ?: SessionLoad.Ready(view)
        }

        override suspend fun presetNames(instrument: InstrumentDto): List<String> {
            presetRequests += instrument
            presetRefusal?.let { refusal -> throw refusal }
            return presetNames
        }

        override suspend fun openDraft(view: SessionView): TuningDraft? {
            openedFor += view
            return draftAnswer
        }

        override suspend fun selectPreset(draft: TuningDraft, preset: String): TuningDraft =
            presetAnswer ?: draft

        override suspend fun changeString(
            draft: TuningDraft,
            stringIndex: Int,
            note: String,
        ): TuningDraft {
            edits += stringIndex to note
            return stringAnswer ?: draft
        }

        override suspend fun commit(view: SessionView, draft: TuningDraft): SessionLoad {
            committed = view to draft
            return commitAnswer
        }
    }

    /**
     * A holder whose engine calls run to completion before the call returns.
     *
     * `Dispatchers.Unconfined` is the test's, not the app's: the production holder
     * is handed `viewModelScope`. The scripted port never really suspends, so the
     * transitions below are complete when each action returns — the tests assert
     * the holder's transitions, never a thread's timing.
     *
     * The store is a scripted one with nothing in it. A17 gave the holder a store,
     * and this suite still asks the questions it asked before that — what the
     * *holder* does with a session — so storage gets a fake here and the holder's
     * storage behaviour is pinned against the real store in
     * `storage/SessionStoreTest.kt`.
     */
    private fun holder(engine: ScriptedEngine) =
        SessionStateHolder(
            CoroutineScope(Dispatchers.Unconfined),
            engine,
            store = FakeSessionStore(),
        )

    // ------------------------------------------------------------- rotation

    @Test
    fun `the engine is asked for the session once and a later frame reads it back`() {
        val page = viewOf(frettedPage())
        val engine = ScriptedEngine().apply { startAnswer = SessionLoad.Ready(page) }
        val holder = holder(engine)

        holder.start()
        val drawnByTheFirstComposition = holder.state
        // The composition runs again after a rotation, and again when the phone
        // is turned back: both ask for the session.
        holder.start()
        holder.start()

        assertEquals("the engine is asked exactly once", 1, engine.startCalls)
        assertSame(
            "the session the first composition drew is the one that comes back",
            page,
            holder.state.view,
        )
        assertEquals(drawnByTheFirstComposition, holder.state)
    }

    @Test
    fun `the marks, chords, highlight and tab are still there after a later frame`() {
        val marked = listOf(PositionDto(string = 1u, fret = 3u))
        val chords = listOf(ChordDto("C", "major"), ChordDto("A", "minor"))
        val page = viewOf(
            frettedPage(selected = marked, chords = chords, highlight = chords[1], tab = TabDto.ANALYZER),
        )
        val engine = ScriptedEngine().apply { startAnswer = SessionLoad.Ready(page) }
        val holder = holder(engine)
        holder.start()

        holder.start()

        val held = holder.state.view ?: error("the session is held")
        assertEquals(1, held.markedFrets().size)
        assertEquals(2, held.state.chords.size)
        assertEquals(TabDto.ANALYZER, held.state.tab)
        assertEquals(chords[1], held.state.highlight)
    }

    @Test
    fun `the piano's committed keys are read back unchanged`() {
        val page = SessionView(
            state = PageStateDto(
                instrument = InstrumentStateDto.Piano(selected = byteArrayOf(60, 72)),
                chords = emptyList(),
                highlight = null,
                tab = TabDto.ANALYZER,
            ),
            instruments = emptyList(),
            qualityGroups = qualityGroups,
            details = emptyList(),
            slots = emptyList(),
            surface = null,
            keyboard = null,
            analysis = SessionAnalysis.Absent,
        )
        val engine = ScriptedEngine().apply { startAnswer = SessionLoad.Ready(page) }
        val holder = holder(engine)

        holder.start()
        holder.start()

        assertEquals(setOf(60, 72), holder.state.view?.markedPitches())
    }

    // --------------------------------------------------------------- drafts

    @Test
    fun `a draft opened with an unapplied edit is still held, with its edits`() {
        val page = viewOf(frettedPage())
        val opened = draftOf(guitarStandard, notes = listOf("E", "A", "D", "G", "B", "E"))
        val edited = draftOf(guitarOpenG, preset = "Open G", notes = listOf("D", "G", "D", "G", "B", "D"))
        val engine = ScriptedEngine().apply {
            startAnswer = SessionLoad.Ready(page)
            draftAnswer = opened
            stringAnswer = edited
        }
        val holder = holder(engine)
        holder.start()

        holder.openTuning()
        holder.changeString(stringIndex = 0, note = "D")

        assertEquals("the edit is the engine's answer, held", edited, holder.state.draft)
        assertEquals(listOf(0 to "D"), engine.edits)

        // The screen comes back after a rotation and reads the holder.
        holder.start()
        assertEquals("the unapplied edit survives", edited, holder.state.draft)
    }

    @Test
    fun `an edit never touches the committed page the engine answered`() {
        val page = viewOf(frettedPage())
        val engine = ScriptedEngine().apply {
            startAnswer = SessionLoad.Ready(page)
            draftAnswer = draftOf(guitarStandard, notes = listOf("E", "A", "D", "G", "B", "E"))
            stringAnswer = draftOf(guitarOpenG, preset = "Open G", notes = listOf("D", "G", "D", "G", "B", "D"))
        }
        val holder = holder(engine)
        holder.start()

        holder.openTuning()
        holder.changeString(stringIndex = 0, note = "D")

        assertSame("the committed page is the engine's, untouched", page, holder.state.view)
        assertNull("nothing was committed", engine.committed)
    }

    @Test
    fun `applying the draft commits the engine's answer and closes the sheet`() {
        val page = viewOf(frettedPage())
        val committedPage = viewOf(frettedPage(tuning = guitarOpenG))
        val engine = ScriptedEngine().apply {
            startAnswer = SessionLoad.Ready(page)
            draftAnswer = draftOf(guitarOpenG, preset = "Open G", notes = listOf("D", "G", "D", "G", "B", "D"))
            commitAnswer = SessionLoad.Ready(committedPage)
        }
        val holder = holder(engine)
        holder.start()
        holder.openTuning()

        holder.applyDraft()

        assertSame(committedPage, holder.state.view)
        assertNull("the sheet is closed by the commit", holder.state.draft)
        assertNull(holder.state.error)
        assertEquals(page, engine.committed?.first)
    }

    @Test
    fun `a stale draft is dropped, and the session it could not reach is kept`() {
        val page = viewOf(frettedPage())
        val engine = ScriptedEngine().apply {
            startAnswer = SessionLoad.Ready(page)
            draftAnswer = draftOf(guitarOpenG, preset = "Open G", notes = listOf("D", "G", "D", "G", "B", "D"))
            commitAnswer = SessionLoad.Failed(STALE_DRAFT_MESSAGE)
        }
        val holder = holder(engine)
        holder.start()
        holder.openTuning()

        holder.applyDraft()

        assertNull("a stale draft cannot be applied twice", holder.state.draft)
        assertEquals(STALE_DRAFT_MESSAGE, holder.state.error)
        assertSame("the session is not lost to a refused commit", page, holder.state.view)
    }

    @Test
    fun `cancelling drops the draft and never the session`() {
        val page = viewOf(frettedPage())
        val engine = ScriptedEngine().apply {
            startAnswer = SessionLoad.Ready(page)
            draftAnswer = draftOf(guitarStandard, notes = listOf("E", "A", "D", "G", "B", "E"))
        }
        val holder = holder(engine)
        holder.start()
        holder.openTuning()

        holder.cancelDraft()

        assertNull(holder.state.draft)
        assertSame(page, holder.state.view)
    }

    @Test
    fun `changing instrument drops the draft and the picker but keeps the session`() {
        val page = viewOf(frettedPage())
        val engine = ScriptedEngine().apply {
            startAnswer = SessionLoad.Ready(page)
            draftAnswer = draftOf(guitarStandard, notes = listOf("E", "A", "D", "G", "B", "E"))
            presetNames = listOf("Standard", "Drop D")
        }
        val holder = holder(engine)
        holder.start()
        holder.openTuning()
        assertEquals(PresetPickerState.Ready(listOf("Standard", "Drop D")), holder.state.presets)

        holder.selectInstrument(guitarDefinition)

        assertNull("a draft belonged to the instrument that was replaced", holder.state.draft)
        assertEquals(PresetPickerState.NotApplicable, holder.state.presets)
        assertEquals(listOf(guitarDefinition), engine.switched)
    }

    // -------------------------------------------------------------- pickers

    @Test
    fun `the preset picker holds the engine's own ordered names`() {
        val page = viewOf(frettedPage())
        val engine = ScriptedEngine().apply {
            startAnswer = SessionLoad.Ready(page)
            draftAnswer = draftOf(guitarStandard, notes = listOf("E"))
            presetNames = listOf("Standard", "Drop D", "Open G")
        }
        val holder = holder(engine)
        holder.start()

        holder.openTuning()

        assertEquals(
            "the engine's order, unfiltered and unsorted",
            listOf("Standard", "Drop D", "Open G"),
            (holder.state.presets as PresetPickerState.Ready).names,
        )
        assertEquals(listOf(InstrumentDto.GUITAR), engine.presetRequests)
    }

    @Test
    fun `a refused preset list is a refusal beside a draft that opened`() {
        val page = viewOf(frettedPage())
        val refusal = IllegalStateException("the engine is unreachable")
        val engine = ScriptedEngine().apply {
            startAnswer = SessionLoad.Ready(page)
            draftAnswer = draftOf(guitarStandard, notes = listOf("E"))
            presetRefusal = refusal
        }
        val holder = holder(engine)
        holder.start()

        holder.openTuning()

        assertTrue(
            "a refusal is shown as a refusal: ${holder.state.presets}",
            holder.state.presets is PresetPickerState.Refused,
        )
        assertEquals("the draft still opened", holder.state.draft?.notes, listOf("E"))
    }

    @Test
    fun `the root and quality the user picked come back after a later frame`() {
        val page = viewOf(frettedPage())
        val engine = ScriptedEngine().apply { startAnswer = SessionLoad.Ready(page) }
        val holder = holder(engine)
        holder.start()

        holder.setRoot("A")
        holder.setQuality("minor")
        holder.start()

        assertEquals("A", holder.state.root)
        assertEquals("minor", holder.state.quality)
        assertEquals("no second session was asked for", 1, engine.startCalls)
    }

    @Test
    fun `a quality the engine does not list is replaced by the engine's own first`() {
        val page = viewOf(frettedPage())
        val engine = ScriptedEngine().apply { startAnswer = SessionLoad.Ready(page) }
        val holder = holder(engine)

        holder.start()

        assertEquals(
            "the default quality is never a claim, only a starting value",
            "major",
            holder.state.quality,
        )

        val second = ScriptedEngine().apply {
            startAnswer = SessionLoad.Ready(
                viewOf(
                    frettedPage(),
                    groups = listOf(
                        QualityGroupDto(
                            group = "Extended",
                            qualities = listOf(QualityDto(quality = "maj7", label = "maj7")),
                        ),
                    ),
                ),
            )
        }
        val other = holder(second)
        other.start()

        assertEquals("the engine's own first quality", "maj7", other.state.quality)
    }

    // --------------------------------------------------------------- errors

    @Test
    fun `a refused action keeps the session and shows the engine's sentence`() {
        val page = viewOf(frettedPage(selected = listOf(PositionDto(string = 1u, fret = 3u))))
        val engine = ScriptedEngine().apply {
            startAnswer = SessionLoad.Ready(page)
            applyAnswer = SessionLoad.Failed("The engine rejected the request: out of range.")
        }
        val holder = holder(engine)
        holder.start()

        holder.applyEvent(PageEventDto.ToggleNote(PositionDto(string = 9u, fret = 99u)))

        assertSame("the last valid session stays on screen", page, holder.state.view)
        assertEquals(
            "The engine rejected the request: out of range.",
            holder.state.error,
        )
        assertEquals("the action is not busy any more", false, holder.state.busy)
    }

    @Test
    fun `a failed session load is a state, and the retry asks the engine again`() {
        val engine = ScriptedEngine().apply { startAnswer = SessionLoad.Failed("The engine could not be loaded.") }
        val holder = holder(engine)

        holder.start()
        assertNull(holder.state.view)
        assertEquals("The engine could not be loaded.", holder.state.error)

        val page = viewOf(frettedPage())
        engine.startAnswer = SessionLoad.Ready(page)
        holder.reload()

        assertSame(page, holder.state.view)
        assertNull(holder.state.error)
        assertEquals(2, engine.startCalls)
    }
}
