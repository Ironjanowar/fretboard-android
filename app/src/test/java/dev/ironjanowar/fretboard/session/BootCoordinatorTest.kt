package dev.ironjanowar.fretboard.session

import dev.ironjanowar.fretboard.core.InstrumentDefinitionDto
import dev.ironjanowar.fretboard.core.InstrumentDto
import dev.ironjanowar.fretboard.core.InstrumentStateDto
import dev.ironjanowar.fretboard.core.PageEventDto
import dev.ironjanowar.fretboard.core.PageStateDto
import dev.ironjanowar.fretboard.core.TabDto
import dev.ironjanowar.fretboard.core.TuningDto
import dev.ironjanowar.fretboard.storage.SessionStore
import dev.ironjanowar.fretboard.storage.StoreOutcome
import dev.ironjanowar.fretboard.storage.StoredSession
import dev.ironjanowar.fretboard.ui.SessionStateHolder
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The engine's own view of one page, with no musical value in between. */
private fun viewOf(state: PageStateDto) = SessionView(
    state = state,
    instruments = emptyList(),
    qualityGroups = emptyList(),
    details = emptyList(),
    slots = emptyList(),
    surface = null,
    keyboard = null,
    analysis = SessionAnalysis.Absent,
)

/** The page one decision opens. */
private fun openedPage(decision: BootDecision): PageStateDto =
    ((decision as BootDecision.Open).load as SessionLoad.Ready).view.state

/** A scripted engine: the answers a test hands it, and how often each was asked for. */
private class ScriptedEngine(private val defaults: PageStateDto) : SessionEngine {
    var startCalls: Int = 0
    val restored = mutableListOf<PageStateDto>()

    override suspend fun start(): SessionLoad {
        startCalls += 1
        return SessionLoad.Ready(viewOf(defaults))
    }

    override suspend fun restore(page: PageStateDto): SessionLoad {
        restored += page
        return SessionLoad.Ready(viewOf(page))
    }

    override suspend fun apply(view: SessionView, event: PageEventDto): SessionLoad =
        SessionLoad.Failed("not used by this test")

    override suspend fun switch(
        view: SessionView,
        target: InstrumentDefinitionDto,
    ): SessionLoad = SessionLoad.Failed("not used by this test")

    override suspend fun presetNames(instrument: InstrumentDto): List<String> = emptyList()

    override suspend fun openDraft(view: SessionView): TuningDraft? = null

    override suspend fun selectPreset(draft: TuningDraft, preset: String): TuningDraft = draft

    override suspend fun changeString(
        draft: TuningDraft,
        stringIndex: Int,
        note: String,
    ): TuningDraft = draft

    override suspend fun commit(view: SessionView, draft: TuningDraft): SessionLoad =
        SessionLoad.Failed("not used by this test")
}

/** A store that answers with what a test stored, and nothing else. */
private class ScriptedStore(private val stored: StoredSession) : SessionStore {
    override suspend fun read(): StoredSession = stored

    override suspend fun write(revision: Long, page: PageStateDto): StoreOutcome =
        StoreOutcome.Written
}

/** A store whose read hangs until the test releases it: the slow read. */
private class GatedStore(private val stored: StoredSession) : SessionStore {
    val gate = CompletableDeferred<Unit>()

    override suspend fun read(): StoredSession {
        gate.await()
        return stored
    }

    override suspend fun write(revision: Long, page: PageStateDto): StoreOutcome =
        StoreOutcome.Written
}

/** A delivery that hands out one candidate per call, in order. */
private class ScriptedDeliveries(private val answers: List<BootCandidate?>) : SessionCandidateSource {
    private var next = 0

    override suspend fun delivered(): BootCandidate? =
        answers.getOrNull(next++)
}

/**
 * Which session wins at startup (task A18).
 *
 * The plan's order, one assertion each: an incoming candidate the engine accepted,
 * then the stored session, then the engine's own defaults — with a rejected delivery
 * never taking a session away, and the newest decision always beating an older one.
 */
class BootCoordinatorTest {

    private val defaultPage = page(TabDto.VISUALIZER, "Standard")
    private val storedPage = page(TabDto.ANALYZER, "Stored")
    private val incomingPage = page(TabDto.VISUALIZER, "Incoming")

    private fun page(tab: TabDto, reference: String) = PageStateDto(
        instrument = InstrumentStateDto.Fretted(
            instrument = InstrumentDto.GUITAR,
            tuning = TuningDto(pitches = byteArrayOf(40, 45, 50, 55, 59, 64), reference = reference),
            selected = emptyList(),
        ),
        chords = emptyList(),
        highlight = null,
        tab = tab,
    )

    private fun stored(revision: Long, page: PageStateDto) =
        StoredSession.Restored(revision, page, envelope = "envelope-of-$revision")

    // ------------------------------------------------------------- precedence

    @Test
    fun `an incoming candidate the engine accepted wins over the stored session`() = runTest {
        val engine = ScriptedEngine(defaultPage)
        val coordinator = BootCoordinator(
            engine = engine,
            store = ScriptedStore(stored(4, storedPage)),
            delivered = ScriptedDeliveries(listOf(BootCandidate.Valid(incomingPage))),
        )

        val decision = coordinator.open(holding = false)

        assertTrue(decision is BootDecision.Open)
        decision as BootDecision.Open
        assertEquals(BootSource.INCOMING, decision.source)
        assertEquals(incomingPage, openedPage(decision))
        assertEquals("the engine was asked for its own view of the candidate", listOf(incomingPage), engine.restored)
        assertEquals("the engine's defaults are never opened over a candidate", 0, engine.startCalls)
    }

    @Test
    fun `a candidate still continues the stored revision`() = runTest {
        val coordinator = BootCoordinator(
            engine = ScriptedEngine(defaultPage),
            store = ScriptedStore(stored(7, storedPage)),
            delivered = ScriptedDeliveries(listOf(BootCandidate.Valid(incomingPage))),
        )

        val decision = coordinator.open(holding = false) as BootDecision.Open

        assertEquals(
            "an incoming session is the newest one, not a first write the store would refuse",
            7,
            decision.storedRevision,
        )
    }

    @Test
    fun `the stored session wins when the launch carried nothing`() = runTest {
        val engine = ScriptedEngine(defaultPage)
        val coordinator = BootCoordinator(
            engine = engine,
            store = ScriptedStore(stored(4, storedPage)),
        )

        val decision = coordinator.open(holding = false) as BootDecision.Open

        assertEquals(BootSource.STORED, decision.source)
        assertEquals(storedPage, openedPage(decision))
        assertEquals(4, decision.storedRevision)
    }

    @Test
    fun `the engine's own defaults open when there is nothing to open`() = runTest {
        val engine = ScriptedEngine(defaultPage)
        val coordinator = BootCoordinator(engine = engine, store = ScriptedStore(StoredSession.None))

        val decision = coordinator.open(holding = false) as BootDecision.Open

        assertEquals(BootSource.DEFAULTS, decision.source)
        assertEquals(defaultPage, openedPage(decision))
        assertEquals(1, engine.startCalls)
        assertNull("a plain start has nothing to say", decision.notice)
    }

    // ------------------------------------------------------------ rejections

    @Test
    fun `a rejected delivery on a cold start opens the defaults with the reason`() = runTest {
        val engine = ScriptedEngine(defaultPage)
        val coordinator = BootCoordinator(
            engine = engine,
            store = ScriptedStore(StoredSession.None),
            delivered = ScriptedDeliveries(listOf(BootCandidate.Rejected("the link is not a session"))),
        )

        val decision = coordinator.open(holding = false) as BootDecision.Open

        assertEquals("a rejection is not a session", BootSource.DEFAULTS, decision.source)
        assertEquals(defaultPage, openedPage(decision))
        assertEquals("the reason travels beside the session", "the link is not a session", decision.notice)
    }

    @Test
    fun `a rejected delivery never replaces a stored session`() = runTest {
        val coordinator = BootCoordinator(
            engine = ScriptedEngine(defaultPage),
            store = ScriptedStore(stored(4, storedPage)),
            delivered = ScriptedDeliveries(listOf(BootCandidate.Rejected("the link is not a session"))),
        )

        val decision = coordinator.open(holding = false) as BootDecision.Open

        assertEquals(BootSource.STORED, decision.source)
        assertEquals(storedPage, openedPage(decision))
        assertEquals("the reason travels beside the stored session", "the link is not a session", decision.notice)
    }

    @Test
    fun `a rejected delivery keeps the session already on screen`() = runTest {
        val engine = ScriptedEngine(defaultPage)
        val coordinator = BootCoordinator(
            engine = engine,
            store = ScriptedStore(stored(4, storedPage)),
            delivered = ScriptedDeliveries(listOf(BootCandidate.Rejected("the link is not a session"))),
        )

        val decision = coordinator.open(holding = true)

        assertEquals(BootDecision.KeepCurrent("the link is not a session"), decision)
        assertEquals("nothing was opened at all", emptyList<PageStateDto>(), engine.restored)
        assertEquals(0, engine.startCalls)
    }

    @Test
    fun `an unusable stored session opens the defaults and says why`() = runTest {
        val refusal = "The stored session could not be read: its container is not readable. Its bytes were kept."
        val coordinator = BootCoordinator(
            engine = ScriptedEngine(defaultPage),
            store = ScriptedStore(StoredSession.Refused(refusal)),
        )

        val decision = coordinator.open(holding = false) as BootDecision.Open

        assertEquals("a refusal is not a session to keep", BootSource.DEFAULTS, decision.source)
        assertEquals(defaultPage, openedPage(decision))
        assertEquals(refusal, decision.notice)
    }

    // ------------------------------------------------------------- ordering

    @Test
    fun `a stored read that finishes after a newer decision is discarded`() = runTest {
        val store = GatedStore(stored(4, storedPage))
        val coordinator = BootCoordinator(engine = ScriptedEngine(defaultPage), store = store)

        val slow = async { coordinator.open(holding = false) }
        runCurrent()
        val newer = async { coordinator.open(holding = false) }
        runCurrent()
        store.gate.complete(Unit)

        assertEquals("the slow answer must not be published", BootDecision.Superseded, slow.await())
        assertEquals(BootSource.STORED, (newer.await() as BootDecision.Open).source)
    }

    @Test
    fun `a delivery that arrives while a read is in flight is the one that opens`() = runTest {
        val store = GatedStore(StoredSession.None)
        val coordinator = BootCoordinator(
            engine = ScriptedEngine(defaultPage),
            store = store,
            delivered = ScriptedDeliveries(listOf(BootCandidate.Valid(incomingPage))),
        )

        val cold = async { coordinator.open(holding = false) }
        runCurrent()
        val delivered = async { coordinator.open(holding = false) }
        runCurrent()
        store.gate.complete(Unit)

        assertEquals(BootDecision.Superseded, cold.await())
        val decision = delivered.await() as BootDecision.Open
        assertEquals("the newest delivery wins over a read that was already running", BootSource.INCOMING, decision.source)
        assertEquals(incomingPage, openedPage(decision))
    }

    // ------------------------------------------------------- what the screen offers

    @Test
    fun `the session is not offered until the arbitration answers`() = runTest {
        val store = GatedStore(StoredSession.None)
        val holder = SessionStateHolder(this, ScriptedEngine(defaultPage), store = store)

        holder.start()
        runCurrent()

        assertNull("nothing is offered while the arbitration is in flight", holder.state.view)
        assertTrue("the controls stay disabled until it resolves", holder.state.busy)

        store.gate.complete(Unit)
        runCurrent()

        assertEquals("the session lands when the arbitration does", defaultPage, holder.state.view?.state)
        assertTrue("and the controls come back", !holder.state.busy)
    }
}
