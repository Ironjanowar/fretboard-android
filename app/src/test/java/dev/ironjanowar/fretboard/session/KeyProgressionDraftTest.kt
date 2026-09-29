package dev.ironjanowar.fretboard.session

import dev.ironjanowar.fretboard.core.ChordDto
import dev.ironjanowar.fretboard.core.ChordModeDto
import dev.ironjanowar.fretboard.core.InstrumentDefinitionDto
import dev.ironjanowar.fretboard.core.InstrumentDto
import dev.ironjanowar.fretboard.core.InstrumentKindDto
import dev.ironjanowar.fretboard.core.InstrumentStateDto
import dev.ironjanowar.fretboard.core.KeySuggestionDto
import dev.ironjanowar.fretboard.core.PageEventDto
import dev.ironjanowar.fretboard.core.PageStateDto
import dev.ironjanowar.fretboard.core.PositionDto
import dev.ironjanowar.fretboard.core.ProgressionDto
import dev.ironjanowar.fretboard.core.ProgressionGroupDto
import dev.ironjanowar.fretboard.core.QualityDto
import dev.ironjanowar.fretboard.core.QualityGroupDto
import dev.ironjanowar.fretboard.core.TabDto
import dev.ironjanowar.fretboard.core.TuningDto
import dev.ironjanowar.fretboard.ui.SessionStateHolder
import dev.ironjanowar.fretboard.storage.support.FakeSessionStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The A14 key and progression draft lifecycle, as a pure state machine over a
 * scripted engine.
 *
 * The drafts are Kotlin's (open, edit, preview, apply, cancel, reopen) and every
 * musical value in them is the engine's, so the engine is scripted: each test
 * hands the port the answer it wants and asserts that the coordinator carries
 * that answer unchanged — never a chord of its own. The replacement rule itself
 * (replace rather than append, clear the highlight, keep the instrument, its
 * tuning, the selection and the tab) is the engine's own `CommitKeys` /
 * `CommitProgression` reducer and is pinned by the core's fixtures; what is
 * under test here is that the client sends that event, adopts the page the
 * engine answers with, and never assembles a page of its own.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class KeyProgressionDraftTest {

    private val guitarStandard = TuningDto(
        pitches = byteArrayOf(40, 45, 50, 55, 59, 64),
        reference = "Standard",
    )

    private fun frettedPage(
        chords: List<ChordDto> = emptyList(),
        highlight: ChordDto? = null,
        tab: TabDto = TabDto.VISUALIZER,
        selected: List<PositionDto> = emptyList(),
    ) = PageStateDto(
        instrument = InstrumentStateDto.Fretted(
            instrument = InstrumentDto.GUITAR,
            tuning = guitarStandard,
            selected = selected,
        ),
        chords = chords,
        highlight = highlight,
        tab = tab,
    )

    private val guitarDefinition = InstrumentDefinitionDto(
        instrument = InstrumentDto.GUITAR,
        name = "Guitar",
        kind = InstrumentKindDto.FRETTED,
        strings = 6u,
        frets = 24u,
        standardPitches = byteArrayOf(40, 45, 50, 55, 59, 64),
    )

    private val qualityGroups = listOf(
        QualityGroupDto(
            group = "Triads",
            qualities = listOf(QualityDto(quality = "major", label = "maj")),
        ),
    )

    private fun viewOf(state: PageStateDto) = SessionView(
        state = state,
        instruments = listOf(guitarDefinition),
        qualityGroups = qualityGroups,
        details = emptyList(),
        slots = emptyList(),
        surface = null,
        keyboard = null,
        analysis = SessionAnalysis.Absent,
    )

    private fun chord(root: String, quality: String) = ChordDto(root, quality)

    /** A scripted progression engine: no musical rule, only the answers a test hands it. */
    private class ScriptedKeyProgression : KeyProgressionEngine {
        var keyAnswer: List<ChordDto> = emptyList()
        var progressionAnswer: List<ChordDto> = emptyList()
        var catalogAnswer: List<ProgressionGroupDto> = emptyList()
        var catalogRefusal: Throwable? = null

        val keyCalls = mutableListOf<Triple<String, String, ChordModeDto>>()
        val progressionCalls = mutableListOf<Pair<String, String>>()
        var catalogCalls: Int = 0

        override suspend fun keyPreview(
            tonic: String,
            scale: String,
            mode: ChordModeDto,
        ): List<ChordDto> {
            keyCalls += Triple(tonic, scale, mode)
            return keyAnswer
        }

        override suspend fun progressionPreview(tonic: String, progression: String): List<ChordDto> {
            progressionCalls += tonic to progression
            return progressionAnswer
        }

        override suspend fun progressionCatalog(): List<ProgressionGroupDto> {
            catalogCalls += 1
            catalogRefusal?.let { refusal -> throw refusal }
            return catalogAnswer
        }
    }

    /** A scripted session engine: the holder's own port, answered by the test. */
    private class ScriptedSession : SessionEngine {
        var startAnswer: SessionLoad = SessionLoad.Failed("no session was scripted")
        var applyAnswer: SessionLoad? = null
        val applied = mutableListOf<PageEventDto>()

        override suspend fun start(): SessionLoad = startAnswer

        /**
         * The engine's own view of a page the client already holds (A17). Nothing in
         * this suite stores a session, so the holder never routes through it.
         */
        override suspend fun restore(page: PageStateDto): SessionLoad = startAnswer

        override suspend fun apply(view: SessionView, event: PageEventDto): SessionLoad {
            applied += event
            return applyAnswer ?: SessionLoad.Ready(view)
        }

        override suspend fun switch(
            view: SessionView,
            target: InstrumentDefinitionDto,
        ): SessionLoad = SessionLoad.Failed("unused")

        override suspend fun presetNames(instrument: InstrumentDto): List<String> = emptyList()

        override suspend fun openDraft(view: SessionView): TuningDraft? = null

        override suspend fun selectPreset(draft: TuningDraft, preset: String): TuningDraft = draft

        override suspend fun changeString(
            draft: TuningDraft,
            stringIndex: Int,
            note: String,
        ): TuningDraft = draft

        override suspend fun commit(view: SessionView, draft: TuningDraft): SessionLoad =
            SessionLoad.Failed("unused")
    }

    // ------------------------------------------------------ fresh defaults

    @Test
    fun `the fresh key draft is the plan's C major triad with the engine's preview`() = runTest {
        val preview = listOf(chord("C", "major"), chord("D", "minor"), chord("B", "dim"))
        val engine = ScriptedKeyProgression().apply { keyAnswer = preview }
        val drafts = KeyProgressionDrafts(engine)

        val draft = drafts.openKey()

        assertEquals("C", draft.tonic)
        assertEquals("major", draft.scale)
        assertEquals(ChordModeDto.TRIAD, draft.mode)
        assertEquals("the preview is the engine's, not the client's", preview, draft.preview)
        assertEquals(
            "the engine is asked for exactly the draft's fields",
            Triple("C", "major", ChordModeDto.TRIAD),
            engine.keyCalls.single(),
        )
        assertTrue("nothing is committed by opening", engine.progressionCalls.isEmpty())
    }

    @Test
    fun `the fresh progression draft is the plan's pop_i_v_vi_iv in C`() = runTest {
        val preview = listOf(
            chord("C", "major"),
            chord("G", "major"),
            chord("A", "minor"),
            chord("F", "major"),
        )
        val engine = ScriptedKeyProgression().apply { progressionAnswer = preview }
        val drafts = KeyProgressionDrafts(engine)

        val draft = drafts.openProgression()

        assertEquals("C", draft.tonic)
        assertEquals(FRESH_PROGRESSION, draft.progression)
        assertEquals("pop_i_v_vi_iv", draft.progression)
        assertEquals(preview, draft.preview)
        assertEquals("C" to "pop_i_v_vi_iv", engine.progressionCalls.single())
    }

    @Test
    fun `a progression that repeats a chord keeps every occurrence`() = runTest {
        val repeated = listOf(
            chord("C", "major"),
            chord("G", "major"),
            chord("A", "minor"),
            chord("F", "major"),
            chord("C", "major"),
        )
        val engine = ScriptedKeyProgression().apply { progressionAnswer = repeated }
        val drafts = KeyProgressionDrafts(engine)

        val draft = drafts.openProgression()

        assertEquals("it is the engine's list, in the engine's order", repeated, draft.preview)
        assertEquals("both C copies are kept", 2, draft.preview.count { it == chord("C", "major") })
    }

    // ---------------------------------------------------------- editing

    @Test
    fun `editing asks the engine for the new fields and never commits`() = runTest {
        val engine = ScriptedKeyProgression().apply { keyAnswer = listOf(chord("C", "major")) }
        val drafts = KeyProgressionDrafts(engine)
        val opened = drafts.openKey()

        engine.keyAnswer = listOf(chord("A", "minor"), chord("B", "dim"))
        val seventh = drafts.previewKey(tonic = "A", scale = "minor", mode = ChordModeDto.SEVENTH)

        assertEquals("A", seventh.tonic)
        assertEquals("minor", seventh.scale)
        assertEquals(ChordModeDto.SEVENTH, seventh.mode)
        assertEquals(listOf(chord("A", "minor"), chord("B", "dim")), seventh.preview)
        assertEquals(
            Triple("A", "minor", ChordModeDto.SEVENTH),
            engine.keyCalls.last(),
        )
        assertNotEquals("the edit is a new draft", opened, seventh)
        assertTrue("no event is built by an edit", engine.catalogCalls == 0)
    }

    @Test
    fun `reopening resets to the plan's fresh draft`() = runTest {
        val engine = ScriptedKeyProgression().apply { keyAnswer = listOf(chord("C", "major")) }
        val drafts = KeyProgressionDrafts(engine)
        drafts.openKey()
        engine.keyAnswer = listOf(chord("E", "minor"))
        val edited = drafts.previewKey(tonic = "E", scale = "minor", mode = ChordModeDto.SEVENTH)
        assertEquals(ChordModeDto.SEVENTH, edited.mode)

        // Cancel drops the draft; the next open starts from the plan's own fresh
        // C major triad, never from the abandoned edit.
        engine.keyAnswer = listOf(chord("C", "major"))
        val reopened = drafts.openKey()

        assertEquals("C", reopened.tonic)
        assertEquals("major", reopened.scale)
        assertEquals(ChordModeDto.TRIAD, reopened.mode)
        assertNotEquals(edited, reopened)
    }

    // ------------------------------------------------------------ events

    @Test
    fun `apply hands over the engine's own commit events`() = runTest {
        val engine = ScriptedKeyProgression().apply { keyAnswer = listOf(chord("C", "major")) }
        val drafts = KeyProgressionDrafts(engine)

        val key = drafts.openKey()
        assertEquals(
            PageEventDto.CommitKeys(tonic = "C", scale = "major", mode = ChordModeDto.TRIAD),
            drafts.keyEvent(key),
        )

        val draft = ProgressionDraft("G", "pop_i_v_vi_iv", emptyList())
        assertEquals(
            PageEventDto.CommitProgression(tonic = "G", progression = "pop_i_v_vi_iv"),
            drafts.progressionEvent(draft),
        )
    }

    @Test
    fun `an applied suggested key never carries the sheet's previous mode`() = runTest {
        // A key draft left open in seventh mode must not influence a suggestion
        // apply: the event is the mode-free CommitSuggestedKeys, so the engine
        // infers the mode from the page's own chords.
        val engine = ScriptedKeyProgression()
        val drafts = KeyProgressionDrafts(engine)
        drafts.previewKey(tonic = "C", scale = "major", mode = ChordModeDto.SEVENTH)

        val event = drafts.suggestedKeyEvent(KeySuggestionDto("D", "major", 3u, 4u))

        assertTrue("the engine infers the mode", event is PageEventDto.CommitSuggestedKeys)
        val suggested = event as PageEventDto.CommitSuggestedKeys
        assertEquals("D", suggested.tonic)
        assertEquals("major", suggested.scale)
    }

    // ------------------------------------------------------------ catalog

    @Test
    fun `the catalog is the engine's own groups, in its own order`() = runTest {
        val groups = listOf(
            ProgressionGroupDto("Pop", listOf(ProgressionDto("pop_i_v_vi_iv", "I–V–vi–IV", "Pop", "", "", "C", "major", emptyList(), emptyList()))),
            ProgressionGroupDto("Jazz", listOf(ProgressionDto("jazz_ii_v_i", "ii–V–I", "Jazz", "", "", "C", "major", emptyList(), emptyList()))),
        )
        val engine = ScriptedKeyProgression().apply { catalogAnswer = groups }
        val drafts = KeyProgressionDrafts(engine)

        val catalog = drafts.catalog()

        assertEquals(ProgressionCatalogState.Ready(groups), catalog)
    }

    @Test
    fun `an empty catalog is the engine's own none and a refusal is a refusal`() = runTest {
        val none = ScriptedKeyProgression().apply { catalogAnswer = emptyList() }
        assertEquals(ProgressionCatalogState.NotApplicable, KeyProgressionDrafts(none).catalog())

        val refused = ScriptedKeyProgression().apply {
            catalogRefusal = IllegalStateException("the engine is unreachable")
        }
        val catalog = KeyProgressionDrafts(refused).catalog()
        assertTrue("a refusal is shown as a refusal: $catalog", catalog is ProgressionCatalogState.Refused)
    }

    // ------------------------------------------------- holder transitions

    private fun holder(
        engine: SessionEngine,
        keyEngine: KeyProgressionEngine,
        scope: CoroutineScope,
    ) = SessionStateHolder(scope, engine, keyEngine, store = FakeSessionStore())

    @Test
    fun `opening the key sheet shows the fresh draft at once and fills the preview`() = runTest {
        val preview = listOf(chord("C", "major"), chord("D", "minor"))
        val keyEngine = ScriptedKeyProgression().apply { keyAnswer = preview }
        val holder = holder(ScriptedSession(), keyEngine, this)

        holder.openKey()
        assertEquals("the plan's fresh key opens immediately", "C", holder.state.keyDraft?.tonic)
        assertEquals("major", holder.state.keyDraft?.scale)
        assertEquals(ChordModeDto.TRIAD, holder.state.keyDraft?.mode)
        assertTrue("the preview is on its way", holder.state.keyPreviewPending)

        advanceUntilIdle()
        assertEquals("the preview is the engine's", preview, holder.state.keyDraft?.preview)
        assertEquals(false, holder.state.keyPreviewPending)
    }

    @Test
    fun `a dismissed key draft is not reopened by a late preview`() = runTest {
        val keyEngine = ScriptedKeyProgression().apply { keyAnswer = listOf(chord("C", "major")) }
        val holder = holder(ScriptedSession(), keyEngine, this)

        holder.openKey()
        holder.cancelKeyDraft()
        assertNull(holder.state.keyDraft)

        // The preview lands after the dismissal; it must be dropped, not reopen
        // the sheet the user closed.
        advanceUntilIdle()
        assertNull("the dismissed draft stays dismissed", holder.state.keyDraft)
        assertEquals(false, holder.state.keyPreviewPending)
    }

    @Test
    fun `applying the key draft sends the engine's event and adopts its page`() = runTest {
        val page = frettedPage(
            chords = listOf(chord("C", "major")),
            highlight = chord("C", "major"),
            tab = TabDto.ANALYZER,
            selected = listOf(PositionDto(string = 1u, fret = 3u)),
        )
        val session = ScriptedSession().apply { startAnswer = SessionLoad.Ready(viewOf(page)) }
        // The engine's own replacement: new chords, highlight cleared, and the
        // instrument, its tuning, the selection and the tab kept.
        val replaced = replacementPage()
        val replacedView = viewOf(replaced)
        session.applyAnswer = SessionLoad.Ready(replacedView)
        val keyEngine = ScriptedKeyProgression().apply { keyAnswer = listOf(chord("G", "major")) }
        val holder = holder(session, keyEngine, this)
        holder.start()
        holder.openKey()
        advanceUntilIdle()

        holder.applyKeyDraft()
        advanceUntilIdle()

        assertNull("the sheet is closed by the apply", holder.state.keyDraft)
        assertSame("the page is the engine's, not assembled here", replacedView, holder.state.view)
        assertEquals(
            "the engine's own CommitKeys event carries the draft's three fields",
            listOf(PageEventDto.CommitKeys("C", "major", ChordModeDto.TRIAD)),
            session.applied,
        )
        val committed = holder.state.view?.state ?: error("a page")
        assertEquals("the chords are the engine's replacement", listOf(chord("C", "major"), chord("G", "major")), committed.chords)
        assertNull("the engine cleared the highlight", committed.highlight)
    }

    @Test
    fun `applying a progression draft sends the engine's event and keeps repeats`() = runTest {
        val page = frettedPage(chords = listOf(chord("C", "major")))
        val session = ScriptedSession().apply { startAnswer = SessionLoad.Ready(viewOf(page)) }
        val repeated = listOf(
            chord("C", "major"),
            chord("G", "major"),
            chord("A", "minor"),
            chord("F", "major"),
            chord("C", "major"),
        )
        session.applyAnswer = SessionLoad.Ready(viewOf(frettedPage(chords = repeated)))
        val keyEngine = ScriptedKeyProgression().apply { progressionAnswer = repeated }
        val holder = holder(session, keyEngine, this)
        holder.start()
        holder.openProgression()
        advanceUntilIdle()

        holder.applyProgressionDraft()
        advanceUntilIdle()

        assertNull(holder.state.progressionDraft)
        assertEquals(
            listOf(PageEventDto.CommitProgression("C", FRESH_PROGRESSION)),
            session.applied,
        )
        assertEquals(
            "every occurrence the engine committed is kept",
            2,
            holder.state.view?.state?.chords?.count { it == chord("C", "major") },
        )
    }

    @Test
    fun `applying a suggested key sends the mode-free event to the engine`() = runTest {
        val session = ScriptedSession().apply {
            startAnswer = SessionLoad.Ready(viewOf(frettedPage(chords = listOf(chord("C", "major"), chord("G", "major")))))
        }
        val holder = holder(session, ScriptedKeyProgression(), this)
        holder.start()
        advanceUntilIdle()

        holder.applySuggestedKey(KeySuggestionDto("D", "major", 2u, 2u))
        advanceUntilIdle()

        assertEquals(
            listOf(PageEventDto.CommitSuggestedKeys("D", "major")),
            session.applied,
        )
    }

    /** The engine's own replacement answer for the apply test. */
    private fun replacementPage(): PageStateDto = PageStateDto(
        instrument = InstrumentStateDto.Fretted(
            instrument = InstrumentDto.GUITAR,
            tuning = guitarStandard,
            selected = listOf(PositionDto(string = 1u, fret = 3u)),
        ),
        chords = listOf(chord("C", "major"), chord("G", "major")),
        highlight = null,
        tab = TabDto.ANALYZER,
    )
}
