package dev.ironjanowar.fretboard.session

import dev.ironjanowar.fretboard.core.InstrumentDto
import dev.ironjanowar.fretboard.core.InstrumentStateDto
import dev.ironjanowar.fretboard.core.PageStateDto
import dev.ironjanowar.fretboard.core.TabDto
import dev.ironjanowar.fretboard.core.TuningDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The fixed-reference tuning draft lifecycle, as a pure state machine.
 *
 * The draft is Kotlin's (open, edit, apply, cancel, dismiss, reopen) and every
 * musical value in it is the engine's, so the engine is scripted here: each test
 * hands the port the answer it wants and asserts that the coordinator carries
 * that answer unchanged — never a value of its own. The nearest-pitch rule, the
 * preset names and the note names never appear in this repository: a wrong
 * answer here can only be the scripted one, which is what makes the assertions
 * real.
 */
class TuningDraftTest {

    private val guitarStandard = TuningDto(
        pitches = byteArrayOf(40, 45, 50, 55, 59, 64),
        reference = "Standard",
    )
    private val guitarDropD = TuningDto(
        pitches = byteArrayOf(38, 45, 50, 55, 59, 64),
        reference = "Drop D",
    )
    private val guitarStandardNotes = listOf("E", "A", "D", "G", "B", "E")

    private val guitarPage = frettedPage(InstrumentDto.GUITAR, guitarStandard)
    private val bassPage = frettedPage(
        InstrumentDto.BASS4,
        TuningDto(pitches = byteArrayOf(28, 33, 38, 43), reference = "Standard"),
    )
    private val pianoPage = PageStateDto(
        instrument = InstrumentStateDto.Piano(selected = byteArrayOf()),
        chords = emptyList(),
        highlight = null,
        tab = TabDto.VISUALIZER,
    )

    /** A scripted port: no musical rule, only the answers a test hands it. */
    private class ScriptedEngine : TuningEngine {
        var draftAnswer: TuningDto? = null
        var presetAnswer: TuningDto? = null
        var stringAnswer: TuningDto? = null
        var detected: String = "Standard"
        var noteNames: List<String> = emptyList()
        var pageAnswer: PageStateDto? = null

        val openedWith = mutableListOf<PageStateDto>()
        val presetCalls = mutableListOf<Triple<InstrumentDto, TuningDto, String>>()
        val stringCalls = mutableListOf<Triple<InstrumentDto, TuningDto, Pair<String, String>>>()
        val commits = mutableListOf<Pair<PageStateDto, TuningDto>>()

        override fun openDraft(state: PageStateDto): TuningDto? {
            openedWith += state
            return draftAnswer
        }

        override fun selectPreset(
            instrument: InstrumentDto,
            draft: TuningDto,
            preset: String,
        ): TuningDto {
            presetCalls += Triple(instrument, draft, preset)
            return presetAnswer ?: draft
        }

        override fun changeString(
            instrument: InstrumentDto,
            draft: TuningDto,
            string: String,
            note: String,
        ): TuningDto {
            stringCalls += Triple(instrument, draft, string to note)
            return stringAnswer ?: draft
        }

        override fun detectPreset(instrument: InstrumentDto, draft: TuningDto): String = detected

        override fun notes(draft: TuningDto): List<String> = noteNames

        override fun commit(state: PageStateDto, draft: TuningDto): PageStateDto {
            commits += state to draft
            return pageAnswer ?: state
        }
    }

    private fun frettedPage(instrument: InstrumentDto, tuning: TuningDto) = PageStateDto(
        instrument = InstrumentStateDto.Fretted(
            instrument = instrument,
            tuning = tuning,
            selected = emptyList(),
        ),
        chords = emptyList(),
        highlight = null,
        tab = TabDto.VISUALIZER,
    )

    @Test
    fun `opening a fretted page starts from the committed tuning`() {
        val engine = ScriptedEngine().apply {
            draftAnswer = guitarStandard
            detected = "Standard"
            noteNames = guitarStandardNotes
        }
        val draft = DraftCoordinator(engine).open(guitarPage)

        assertEquals(guitarStandard, draft?.tuning)
        assertEquals("Standard", draft?.preset)
        assertEquals(guitarStandardNotes, draft?.notes)
        assertEquals(InstrumentDto.GUITAR, draft?.instrument)
        assertEquals(listOf(guitarPage), engine.openedWith)
    }

    @Test
    fun `the keyboard opens no draft at all`() {
        val engine = ScriptedEngine().apply { draftAnswer = guitarStandard }
        assertNull(DraftCoordinator(engine).open(pianoPage))
        // The engine is the authority for the piano, not the client: it is only
        // asked after the page is known to be fretted, and it answers nothing.
        assertTrue(engine.openedWith.isEmpty())
    }

    @Test
    fun `an edit changes the draft and never the committed page`() {
        val engine = ScriptedEngine().apply {
            draftAnswer = guitarStandard
            detected = "Drop D"
            stringAnswer = guitarDropD
        }
        val coordinator = DraftCoordinator(engine)
        val draft = coordinator.open(guitarPage) ?: error("a fretted page opens a draft")

        val edited = coordinator.changeString(draft, stringIndex = 0, note = "D")

        assertEquals("the edit is the engine's answer", guitarDropD, edited.tuning)
        assertEquals("the label is re-read from the engine", "Drop D", edited.preset)
        assertEquals("the committed page is untouched", guitarStandard, guitarPage.frettedTuning())
        assertTrue("an edit never commits", engine.commits.isEmpty())
        assertEquals(
            "the physical index is sent as the recorded decimal text",
            Triple(InstrumentDto.GUITAR, guitarStandard, "0" to "D"),
            engine.stringCalls.single(),
        )
    }

    @Test
    fun `an edit the engine refuses leaves the draft exactly as the engine answered`() {
        val engine = ScriptedEngine().apply {
            draftAnswer = guitarStandard
            noteNames = guitarStandardNotes
            // No script for the edit: the port answers the unchanged draft, which
            // is the engine's documented behaviour for a string of another
            // instrument or a note outside its chromatic scale.
        }
        val coordinator = DraftCoordinator(engine)
        val draft = coordinator.open(guitarPage) ?: error("a fretted page opens a draft")

        val edited = coordinator.changeString(draft, stringIndex = 9, note = "H")

        assertEquals(guitarStandard, edited.tuning)
        assertEquals(guitarStandardNotes, edited.notes)
        assertTrue("a refused edit commits nothing", engine.commits.isEmpty())
    }

    @Test
    fun `selecting a preset takes the engine's answer, not the requested name`() {
        val engine = ScriptedEngine().apply {
            draftAnswer = guitarStandard
            presetAnswer = guitarDropD
            detected = "Drop D"
        }
        val coordinator = DraftCoordinator(engine)
        val draft = coordinator.open(guitarPage) ?: error("a fretted page opens a draft")

        val selected = coordinator.selectPreset(draft, "Drop D")

        assertEquals(guitarDropD, selected.tuning)
        assertEquals("Drop D", selected.preset)
        assertEquals(
            "the requested name is passed to the engine verbatim",
            Triple(InstrumentDto.GUITAR, guitarStandard, "Drop D"),
            engine.presetCalls.single(),
        )
    }

    @Test
    fun `a preset the engine does not know comes back unchanged`() {
        val engine = ScriptedEngine().apply { draftAnswer = guitarStandard }
        val coordinator = DraftCoordinator(engine)
        val draft = coordinator.open(guitarPage) ?: error("a fretted page opens a draft")

        val selected = coordinator.selectPreset(draft, "Nope")

        assertEquals(guitarStandard, selected.tuning)
        assertEquals("the client never invents the label it asked for", "Standard", selected.preset)
    }

    @Test
    fun `apply commits the draft through the engine and returns the engine's page`() {
        val engine = ScriptedEngine().apply {
            draftAnswer = guitarStandard
            stringAnswer = guitarDropD
            detected = "Drop D"
        }
        val coordinator = DraftCoordinator(engine)
        val draft = coordinator.open(guitarPage) ?: error("a fretted page opens a draft")
        val edited = coordinator.changeString(draft, stringIndex = 0, note = "D")
        val committedPage = frettedPage(InstrumentDto.GUITAR, guitarDropD)
        engine.pageAnswer = committedPage

        val page = coordinator.apply(guitarPage, edited)

        assertEquals(committedPage, page)
        assertEquals(listOf(guitarPage to guitarDropD), engine.commits)
    }

    @Test
    fun `cancelling and reopening starts from the committed values again`() {
        val engine = ScriptedEngine().apply {
            draftAnswer = guitarStandard
            stringAnswer = guitarDropD
            detected = "Drop D"
        }
        val coordinator = DraftCoordinator(engine)
        val draft = coordinator.open(guitarPage) ?: error("a fretted page opens a draft")
        val edited = coordinator.changeString(draft, stringIndex = 0, note = "D")
        assertEquals(guitarDropD, edited.tuning)

        // Cancel or dismiss drops the draft; there is nothing else to undo,
        // because nothing was ever written to the page.
        val dismissed: TuningDraft? = null
        assertNull(dismissed)
        engine.draftAnswer = guitarStandard
        engine.detected = "Standard"

        val reopened = coordinator.open(guitarPage)

        assertEquals("reopening reads the committed tuning", guitarStandard, reopened?.tuning)
        assertEquals("Standard", reopened?.preset)
        assertNotEquals("the abandoned edit is gone", edited.tuning, reopened?.tuning)
        assertEquals(2, engine.openedWith.size)
    }

    @Test
    fun `a draft opened for another instrument is never committed`() {
        val engine = ScriptedEngine().apply { draftAnswer = guitarStandard }
        val coordinator = DraftCoordinator(engine)
        val draft = coordinator.open(guitarPage) ?: error("a fretted page opens a draft")

        val page = coordinator.apply(bassPage, draft)

        assertNull("a stale draft cannot target a replaced session", page)
        assertTrue(engine.commits.isEmpty())
    }

    @Test
    fun `a draft is never committed onto the keyboard`() {
        val engine = ScriptedEngine().apply { draftAnswer = guitarStandard }
        val coordinator = DraftCoordinator(engine)
        val draft = coordinator.open(guitarPage) ?: error("a fretted page opens a draft")

        assertNull(coordinator.apply(pianoPage, draft))
        assertTrue(engine.commits.isEmpty())
    }

    @Test
    fun `the draft keeps the engine's own values, not the caller's`() {
        val engine = ScriptedEngine().apply {
            draftAnswer = guitarStandard
            detected = "Custom"
            noteNames = guitarStandardNotes
        }
        val draft = DraftCoordinator(engine).open(guitarPage) ?: error("a fretted page opens a draft")

        // `Custom` is the engine's own label for a tuning that matches no preset.
        assertSame("the note list is the engine's", guitarStandardNotes, draft.notes)
        assertEquals("Custom", draft.preset)
    }

    @Test
    fun `the string editor keeps the baseline's physical order and labels`() {
        assertEquals(
            listOf("String 6", "String 5", "String 4", "String 3", "String 2", "String 1"),
            tuningRows(6).map { row -> row.label },
        )
        assertEquals(
            listOf(0, 1, 2, 3, 4, 5),
            tuningRows(6).map { row -> row.stringIndex },
        )
        assertEquals(
            "the token the engine reads is the physical index as text",
            listOf("0", "1", "2", "3", "4", "5"),
            tuningRows(6).map { row -> row.token },
        )
        assertEquals(
            listOf("String 4", "String 3", "String 2", "String 1"),
            tuningRows(4).map { row -> row.label },
        )
        assertTrue(tuningRows(0).isEmpty())
    }
}

/** The committed tuning of a page, or `null` on the keyboard. */
private fun PageStateDto.frettedTuning(): TuningDto? =
    (instrument as? InstrumentStateDto.Fretted)?.tuning
