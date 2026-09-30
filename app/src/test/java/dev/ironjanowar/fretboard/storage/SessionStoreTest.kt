package dev.ironjanowar.fretboard.storage

import dev.ironjanowar.fretboard.core.InstrumentDefinitionDto
import dev.ironjanowar.fretboard.core.InstrumentDto
import dev.ironjanowar.fretboard.core.InstrumentStateDto
import dev.ironjanowar.fretboard.core.PageEventDto
import dev.ironjanowar.fretboard.core.PageStateDto
import dev.ironjanowar.fretboard.core.TabDto
import dev.ironjanowar.fretboard.core.TuningDto
import dev.ironjanowar.fretboard.session.SessionAnalysis
import dev.ironjanowar.fretboard.session.SessionEngine
import dev.ironjanowar.fretboard.session.SessionLoad
import dev.ironjanowar.fretboard.session.SessionView
import dev.ironjanowar.fretboard.session.TuningDraft
import dev.ironjanowar.fretboard.storage.support.FakeSessionStore
import dev.ironjanowar.fretboard.ui.SessionStateHolder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/** The engine's envelope, as a scripted codec emits it: opaque to the client. */
private const val ENVELOPE = """{"schema_version":1,"page":{"tab":"visualizer"}}"""

/** The container header one stored session starts with. */
private const val HEADER = "fretboard-session/1"

/** The envelope the scripted codec emits for a page: opaque, and distinct per page. */
private fun envelopeOf(page: PageStateDto): String = "envelope-of-${page.tab}"

/** The engine's own view of one page state, with no musical value in between. */
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

/**
 * A scripted engine for the holder: the answers a test hands it, and a count of how
 * often it was asked for a *fresh* session rather than the stored one.
 */
private class ScriptedEngine(private val freshPage: PageStateDto) : SessionEngine {
    var startCalls: Int = 0
    val restored = mutableListOf<PageStateDto>()
    var draft: TuningDraft? = null
    var applyAnswer: ((PageStateDto) -> PageStateDto)? = null

    override suspend fun start(): SessionLoad {
        startCalls += 1
        return SessionLoad.Ready(viewOf(freshPage))
    }

    override suspend fun restore(page: PageStateDto): SessionLoad {
        restored += page
        return SessionLoad.Ready(viewOf(page))
    }

    override suspend fun apply(view: SessionView, event: PageEventDto): SessionLoad =
        SessionLoad.Ready(viewOf(applyAnswer?.invoke(view.state) ?: view.state))

    override suspend fun switch(
        view: SessionView,
        target: InstrumentDefinitionDto,
    ): SessionLoad = SessionLoad.Failed("not used by this test")

    override suspend fun presetNames(instrument: InstrumentDto): List<String> = emptyList()

    override suspend fun openDraft(view: SessionView): TuningDraft? = draft

    override suspend fun selectPreset(draft: TuningDraft, preset: String): TuningDraft = draft

    override suspend fun changeString(
        draft: TuningDraft,
        stringIndex: Int,
        note: String,
    ): TuningDraft = draft

    override suspend fun commit(view: SessionView, draft: TuningDraft): SessionLoad =
        SessionLoad.Failed("not used by this test")
}

/**
 * A scripted snapshot codec: the engine's envelope and its refusals, as a test hands
 * them over, with no music in between.
 */
private class ScriptedCodec(
    var encodedFor: (PageStateDto) -> String = { page -> envelopeOf(page) },
    var decoded: (String) -> SnapshotDecode = { SnapshotDecode.Refused("no page scripted") },
    var refuseEncode: Throwable? = null,
) : SnapshotCodec {
    override fun encode(page: PageStateDto): String {
        refuseEncode?.let { failure -> throw failure }
        return encodedFor(page)
    }

    override fun decode(snapshot: String): SnapshotDecode = decoded(snapshot)
}

/**
 * The last session, as a durable artifact and as a session transition (task A17).
 *
 * Two questions, kept apart on purpose:
 *
 * * **Does the stored session survive what a phone does to it?** That is asked of the
 *   *real* [AtomicFileSessionStore] against a temporary file: a stored revision
 *   written atomically, an older write that cannot overwrite a newer one, a failed
 *   write that leaves the previous session exactly as it was, and stored bytes that
 *   are unreadable without being erased.
 * * **Does the session holder use it?** That is asked against a scripted
 *   [FakeSessionStore], because the holder's own behaviour — reopening the stored
 *   page, writing every accepted transition, never writing a draft, reporting a write
 *   that failed without taking the session away — is what is under test there, not
 *   the file system.
 *
 * The engine is scripted in both halves: its arm64 library cannot be loaded on the
 * host these tests run on, so no test below asserts a musical value. What the pinned
 * engine's own snapshot codec does with a real envelope is exercised on a device
 * (`app/src/androidTest/.../storage/LastSessionDeviceTest.kt`).
 */
class SessionStoreTest {

    @get:Rule
    val folder = TemporaryFolder()

    // ------------------------------------------------------------ fixtures

    private val standard = TuningDto(
        pitches = byteArrayOf(40, 45, 50, 55, 59, 64),
        reference = "Standard",
    )

    private fun page(
        tab: TabDto = TabDto.VISUALIZER,
        reference: String = "Standard",
    ) = PageStateDto(
        instrument = InstrumentStateDto.Fretted(
            instrument = InstrumentDto.GUITAR,
            tuning = standard.copy(reference = reference),
            selected = emptyList(),
        ),
        chords = emptyList(),
        highlight = null,
        tab = tab,
    )

    private val defaultPage = page()
    private val otherPage = page(tab = TabDto.ANALYZER, reference = "Open G")

    private fun storeIn(file: File, codec: ScriptedCodec) =
        AtomicFileSessionStore(file, SessionSnapshotSerializer(codec), Dispatchers.Unconfined)

    private fun lastSession() = File(folder.root, LAST_SESSION_FILE)
    private fun rejectedSession() = File(folder.root, REJECTED_SESSION_FILE)

    private fun contentOf(file: File): String = file.readText()

    // ------------------------------------------------- the stored session

    @Test
    fun `nothing stored yet is an empty result, not a failure`() = runBlocking {
        val store = storeIn(lastSession(), ScriptedCodec())

        assertEquals(StoredSession.None, store.read())
    }

    @Test
    fun `the engine's snapshot is stored verbatim in the container, and read back`() = runBlocking {
        val codec = ScriptedCodec(
            encodedFor = { ENVELOPE },
            decoded = { SnapshotDecode.Decoded(otherPage) },
        )
        val store = storeIn(lastSession(), codec)

        assertEquals(StoreOutcome.Written, store.write(7, otherPage))
        assertEquals(StoredSession.Restored(7, otherPage, ENVELOPE), store.read())

        // The container is the client's; the envelope between its lines is the
        // engine's, carried verbatim: nothing here re-serialises the music.
        assertEquals("$HEADER\nrevision=7\n$ENVELOPE", contentOf(lastSession()))
        // A successful write leaves no temporary file behind: the session appears
        // atomically, in one piece.
        assertEquals(listOf(LAST_SESSION_FILE), folder.root.list()?.sorted())
    }

    @Test
    fun `an older write cannot overwrite a newer stored revision`() = runBlocking {
        val store = storeIn(
            lastSession(),
            ScriptedCodec(decoded = { SnapshotDecode.Decoded(defaultPage) }),
        )
        store.write(7, defaultPage)

        assertEquals(StoreOutcome.Superseded(7), store.write(6, otherPage))
        assertEquals(
            StoredSession.Restored(7, defaultPage, envelopeOf(defaultPage)),
            store.read(),
        )
    }

    @Test
    fun `a write that fails leaves the previously stored session readable`() = runBlocking {
        val codec = ScriptedCodec(decoded = { SnapshotDecode.Decoded(defaultPage) })
        val store = storeIn(lastSession(), codec)
        store.write(7, defaultPage)
        val storedBytes = lastSession().readBytes()

        codec.refuseEncode = IllegalStateException("the page cannot be encoded")
        val outcome = store.write(8, otherPage)

        assertTrue("a failed write is a value, not an exception", outcome is StoreOutcome.Failed)
        assertTrue(
            "the failure says the session could not be saved",
            (outcome as StoreOutcome.Failed).reason.contains("could not be saved"),
        )
        assertEquals(
            "the previous session is untouched, byte for byte",
            storedBytes.toList(),
            lastSession().readBytes().toList(),
        )
        assertEquals(
            StoredSession.Restored(7, defaultPage, envelopeOf(defaultPage)),
            store.read(),
        )
    }

    @Test
    fun `the same session under a newer revision is not written again`() = runBlocking {
        val store = storeIn(
            lastSession(),
            ScriptedCodec(decoded = { SnapshotDecode.Decoded(defaultPage) }),
        )
        store.write(7, defaultPage)
        val storedBytes = lastSession().readBytes()

        assertEquals(StoreOutcome.Unchanged(7), store.write(8, defaultPage))

        assertEquals(
            "a no-op action is not a disk write",
            storedBytes.toList(),
            lastSession().readBytes().toList(),
        )
        assertEquals("the revision is not burned by a write that never happened", 7, store.read().let {
            (it as StoredSession.Restored).revision
        })
    }

    // ----------------------------------------- stored bytes that are not usable

    @Test
    fun `bytes that are not a container are refused and kept`() = runBlocking {
        val store = storeIn(lastSession(), ScriptedCodec())
        lastSession().writeBytes("this is not a session".toByteArray())

        val refused = store.read()

        assertTrue("unreadable bytes are a refusal", refused is StoredSession.Refused)
        assertTrue((refused as StoredSession.Refused).reason.isNotBlank())
        assertEquals(
            "the bytes are kept, never erased",
            "this is not a session",
            contentOf(lastSession()),
        )
    }

    @Test
    fun `a container from a newer release is refused and kept`() = runBlocking {
        val store = storeIn(lastSession(), ScriptedCodec())
        val future = "fretboard-session/2\nrevision=3\n$ENVELOPE"
        lastSession().writeText(future)

        val refused = store.read()

        assertTrue(refused is StoredSession.Refused)
        assertEquals(
            "the bytes are kept, never erased",
            future,
            contentOf(lastSession()),
        )
    }

    @Test
    fun `a snapshot the engine refuses is refused in the engine's own words, and kept`() =
        runBlocking {
            val codec = ScriptedCodec(
                decoded = {
                    SnapshotDecode.Refused(
                        "the engine rejected the request (UnsupportedSchemaVersion): " +
                            "the snapshot schema version is not supported",
                    )
                },
            )
            val store = storeIn(lastSession(), codec)
            val stored = "$HEADER\nrevision=3\n$ENVELOPE"
            lastSession().writeText(stored)

            val refused = store.read()

            assertTrue(refused is StoredSession.Refused)
            assertTrue(
                "the engine's own sentence is carried through",
                (refused as StoredSession.Refused).reason.contains("UnsupportedSchemaVersion"),
            )
            assertEquals(
                "the bytes are kept, never erased",
                stored,
                contentOf(lastSession()),
            )
        }

    @Test
    fun `an unreadable session is kept aside before a new one replaces it`() = runBlocking {
        val store = storeIn(
            lastSession(),
            ScriptedCodec(decoded = { SnapshotDecode.Decoded(defaultPage) }),
        )
        lastSession().writeText("this is not a session")
        assertTrue(store.read() is StoredSession.Refused)

        assertEquals(StoreOutcome.Written, store.write(1, defaultPage))

        assertEquals(
            "the original bytes survive the new session",
            "this is not a session",
            contentOf(rejectedSession()),
        )
        assertEquals(
            StoredSession.Restored(1, defaultPage, envelopeOf(defaultPage)),
            store.read(),
        )
    }

    @Test
    fun `nothing is written when the unreadable session cannot be kept aside`() = runBlocking {
        val store = storeIn(
            lastSession(),
            ScriptedCodec(decoded = { SnapshotDecode.Decoded(defaultPage) }),
        )
        lastSession().writeText("this is not a session")
        // A directory where the kept-aside file should go: the copy cannot be made.
        assertTrue(rejectedSession().mkdir())

        val outcome = store.write(1, defaultPage)

        assertTrue("losing the original is not an option", outcome is StoreOutcome.Failed)
        assertEquals("this is not a session", contentOf(lastSession()))
    }

    // -------------------------------------------------------- session holder

    private fun holder(engine: ScriptedEngine, store: FakeSessionStore) =
        SessionStateHolder(CoroutineScope(Dispatchers.Unconfined), engine, store = store)

    @Test
    fun `the stored session is reopened through the engine, not replaced by a fresh one`() {
        val store = FakeSessionStore(StoredSession.Restored(4, otherPage, envelopeOf(otherPage)))
        val engine = ScriptedEngine(defaultPage)

        val session = holder(engine, store)
        session.start()

        assertEquals(otherPage, session.state.view?.state)
        assertEquals("the engine's own view of the stored page", listOf(otherPage), engine.restored)
        assertEquals("a stored session is never replaced by a fresh one", 0, engine.startCalls)
        assertEquals(
            "the next write follows the restored revision, so the store can order it",
            listOf(5L to otherPage),
            store.writes,
        )
    }

    @Test
    fun `every accepted transition is stored, and the next revision follows the stored one`() {
        val store = FakeSessionStore(
            StoredSession.Restored(7, defaultPage, envelopeOf(defaultPage)),
        )
        val engine = ScriptedEngine(defaultPage)
        engine.applyAnswer = { otherPage }

        val session = holder(engine, store)
        session.start()
        session.applyEvent(PageEventDto.SetTab(TabDto.ANALYZER))

        assertEquals(otherPage, session.state.view?.state)
        assertEquals(
            "the restored page is asked for as revision 8 — the store answers `unchanged` " +
                "for it — and the accepted transition follows as revision 9",
            listOf(8L to defaultPage, 9L to otherPage),
            store.writes,
        )
    }

    @Test
    fun `a fresh session is stored as the first revision`() {
        val store = FakeSessionStore()
        val engine = ScriptedEngine(defaultPage)

        val session = holder(engine, store)
        session.start()

        assertEquals(listOf(1L to defaultPage), store.writes)
        assertEquals(1, engine.startCalls)
    }

    @Test
    fun `a draft or a picker choice is never stored`() {
        val store = FakeSessionStore()
        val engine = ScriptedEngine(defaultPage)
        val open = TuningDraft(
            instrument = InstrumentDto.GUITAR,
            tuning = standard,
            preset = "Standard",
            notes = listOf("E", "A", "D", "G", "B", "E"),
        )
        engine.draft = open

        val session = holder(engine, store)
        session.start()
        val writtenByTheSession = store.writes.toList()

        session.setRoot("D")
        session.setQuality("minor")
        session.openTuning()
        assertEquals("the draft is on screen", open, session.state.draft)
        session.changeString(0, "D")
        session.cancelDraft()

        assertEquals("only committed pages are stored", writtenByTheSession, store.writes)
        assertEquals(
            "the stored page is the committed one, never a draft's",
            listOf(1L to defaultPage),
            store.writes,
        )
    }

    @Test
    fun `an accepted transition that changes nothing is not a new revision`() {
        // The store is the one that knows the page is already there: an identical
        // envelope is answered `Unchanged` and nothing reaches the disk. That rule is
        // pinned against a real file in the storage half above. Here the division of
        // responsibility is what the test states: the holder asks for every accepted
        // transition, the store decides whether asking was a write, and a no-op
        // transition neither replaces the session nor reports a failure.
        val store = FakeSessionStore(outcome = StoreOutcome.Unchanged(1))
        val engine = ScriptedEngine(defaultPage)
        engine.applyAnswer = { page -> page }

        val session = holder(engine, store)
        session.start()
        session.applyEvent(PageEventDto.SetTab(TabDto.VISUALIZER))

        assertEquals(defaultPage, session.state.view?.state)
        assertNull("an unchanged action reports nothing", session.state.error)
        assertEquals(
            "the holder asks for every accepted transition",
            listOf(1L to defaultPage, 2L to defaultPage),
            store.writes,
        )
    }

    @Test
    fun `a write that fails is reported without taking the session away`() {
        val store = FakeSessionStore(
            outcome = StoreOutcome.Failed(saveFailure("the device has no space left")),
        )
        val engine = ScriptedEngine(defaultPage)

        val session = holder(engine, store)
        session.start()

        assertEquals("the session on screen is the engine's", defaultPage, session.state.view?.state)
        assertNotNull(session.state.error)
        assertTrue(
            "the failure says it could not be saved",
            session.state.error!!.contains("could not be saved"),
        )
        assertTrue(
            "and what went wrong is on screen",
            session.state.error!!.contains("no space left"),
        )
    }

    @Test
    fun `a refused stored session opens the engine's own, says why, and is replaced`() {
        val refusal =
            "The stored session could not be reopened: it was written by a newer release. " +
                "Its bytes were kept."
        val store = FakeSessionStore(StoredSession.Refused(refusal))
        val engine = ScriptedEngine(defaultPage)

        val session = holder(engine, store)
        session.start()

        assertEquals("the session still works", defaultPage, session.state.view?.state)
        assertEquals("the engine's own fresh session", 1, engine.startCalls)
        // The reason an unusable stored session was not used is an *explanation*, not a session
        // failure: the bytes stay on disk and this session works, so it is the message the user
        // dismisses rather than the banner with its Retry. The behaviour is unchanged; where the
        // sentence is kept changed, because a refusal that cannot be dismissed covers a session
        // it never damaged.
        assertEquals(refusal, session.state.notice)
        assertEquals("a refusal to *read* is not a failure of the session", null, session.state.error)
        assertEquals(
            "the refused bytes are moved aside by the store, so the new session is durable",
            listOf(1L to defaultPage),
            store.writes,
        )
        assertFalse(
            "a refusal is not a write failure",
            session.state.notice!!.contains("could not be saved"),
        )
    }

    @Test
    fun `the holder asks the store only for the first session, never after a rotation`() {
        val store = FakeSessionStore()
        val engine = ScriptedEngine(defaultPage)

        val session = holder(engine, store)
        session.start()
        session.start()
        session.start()

        assertEquals("one read for the first session", 1, store.reads)
        assertEquals(1, store.writes.size)
    }
}
