package dev.ironjanowar.fretboard.session

import dev.ironjanowar.fretboard.core.ChordDto
import dev.ironjanowar.fretboard.core.InstrumentDto
import dev.ironjanowar.fretboard.core.InstrumentStateDto
import dev.ironjanowar.fretboard.core.PageEventDto
import dev.ironjanowar.fretboard.core.PageStateDto
import dev.ironjanowar.fretboard.core.PositionDto
import dev.ironjanowar.fretboard.core.TabDto
import dev.ironjanowar.fretboard.core.TuningDto
import dev.ironjanowar.fretboard.ui.frozenKeyboard
import dev.ironjanowar.fretboard.ui.surface.PianoGeometry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The keyboard's tap routing and the instrument boundary.
 *
 * The client's whole share of a piano tap is deciding which key it landed on and
 * sending the engine that key's own pitch; the reducer decides add, remove and
 * an out-of-range refusal. Likewise, crossing between the keyboard and a fretted
 * instrument is the engine's own `SetInstrument`: the client sends one event and
 * returns exactly the page the engine answered — it converts no string position
 * into a key, carries no selection across, and keeps the chords and the tab only
 * because the engine's answer did.
 *
 * The engine's reducer cannot run on this host (its arm64 library does not
 * load), so the boundary is scripted: each test hands the port the page it wants
 * and asserts that the coordinator carries that page unchanged and sent exactly
 * one event. What the engine *does* with the event is pinned by the engine's own
 * tests and by the device gate.
 */
class PianoTransitionsTest {

    private val layout = PianoGeometry.layout(frozenKeyboard())

    /** A scripted reducer: the answers a test hands it, and the events it saw. */
    private class ScriptedPageEngine(private val answer: PageStateDto) : PageEngine {
        val events = mutableListOf<PageEventDto>()
        override fun apply(state: PageStateDto, event: PageEventDto): PageStateDto {
            events += event
            return answer
        }
    }

    private fun frettedPage(
        selected: List<PositionDto>,
        chords: List<ChordDto> = emptyList(),
        highlight: ChordDto? = null,
        tab: TabDto = TabDto.VISUALIZER,
    ) = PageStateDto(
        instrument = InstrumentStateDto.Fretted(
            instrument = InstrumentDto.GUITAR,
            tuning = TuningDto(byteArrayOf(40, 45, 50, 55, 59, 64), reference = "Standard"),
            selected = selected,
        ),
        chords = chords,
        highlight = highlight,
        tab = tab,
    )

    private fun pianoPage(
        selected: ByteArray,
        chords: List<ChordDto> = emptyList(),
        highlight: ChordDto? = null,
        tab: TabDto = TabDto.VISUALIZER,
    ) = PageStateDto(
        instrument = InstrumentStateDto.Piano(selected = selected),
        chords = chords,
        highlight = highlight,
        tab = tab,
    )

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

    // ---------------------------------------------------------------- routing

    @Test
    fun `a tap on a white key routes exactly one event with the tapped pitch`() {
        val c3 = layout.first()
        val centre = c3.leftDp + c3.widthDp / 2f

        assertEquals(
            PageEventDto.TogglePianoKey(48u),
            PianoGeometry.tapEvent(layout, centre, 1f),
        )
    }

    @Test
    fun `a tap on a black key routes that black key's own pitch`() {
        val cSharp = layout.first { it.note == "C#" }
        val centre = cSharp.leftDp + cSharp.widthDp / 2f

        assertEquals(
            PageEventDto.TogglePianoKey(49u),
            PianoGeometry.tapEvent(layout, centre, 1f),
        )
    }

    @Test
    fun `the same pitch class in another octave is its own event`() {
        val c3 = layout.first { it.pitch == 48 }
        val c4 = layout.first { it.pitch == 60 }

        val onC3 = PianoGeometry.tapEvent(layout, c3.leftDp + 1f, 1f)
        val onC4 = PianoGeometry.tapEvent(layout, c4.leftDp + 1f, 1f)

        assertEquals(PageEventDto.TogglePianoKey(48u), onC3)
        assertEquals(PageEventDto.TogglePianoKey(60u), onC4)
        assertTrue("the octaves are independent, not one shared key", onC3 != onC4)
    }

    @Test
    fun `a tap that owns no key routes nothing at all`() {
        val outside = listOf(
            PianoGeometry.tapEvent(layout, -1f, 10f),
            PianoGeometry.tapEvent(layout, 10f, -1f),
            PianoGeometry.tapEvent(layout, PianoGeometry.surfaceWidthDp(layout), 10f),
            PianoGeometry.tapEvent(layout, 10f, PianoGeometry.WHITE_HEIGHT_DP),
            PianoGeometry.tapEvent(emptyList(), 10f, 10f),
        )

        assertTrue("no key, no event, no dispatch: $outside", outside.all { it == null })
    }

    @Test
    fun `a pitch the engine did not answer as a key produces no event`() {
        assertNull("below the frozen range", PianoGeometry.toggleEvent(layout, 47))
        assertNull("above the frozen range", PianoGeometry.toggleEvent(layout, 84))
        assertEquals(
            "the range's own last key is a key",
            PageEventDto.TogglePianoKey(83u),
            PianoGeometry.toggleEvent(layout, 83),
        )
    }

    // ------------------------------------------------------------- selection

    @Test
    fun `the keyboard's marks are the engine's committed keys`() {
        val view = viewOf(pianoPage(selected = byteArrayOf(60, 72)))

        assertEquals(setOf(60, 72), view.markedPitches())
        assertTrue("a keyboard page has no fretted marks", view.markedFrets().isEmpty())
    }

    @Test
    fun `a fretted page has no keyboard keys to mark`() {
        val view = viewOf(frettedPage(selected = listOf(PositionDto(string = 0u, fret = 3u))))

        assertTrue(view.markedPitches().isEmpty())
        assertEquals(mapOf(0 to 3), view.markedFrets())
    }

    @Test
    fun `a pitch stored high in the byte range is read unsigned`() {
        // The binding carries the piano's keys as a signed view of a Rust
        // `Vec<u8>`; a key of 200 (outside the range, and impossible today) must
        // still not read as -56.
        val view = viewOf(pianoPage(selected = byteArrayOf(200.toByte())))

        assertEquals(setOf(200), view.markedPitches())
    }

    // ------------------------------------------------------------ boundary

    @Test
    fun `switching to the keyboard is one SetInstrument event and the engine's own page`() {
        val guitar = frettedPage(
            selected = listOf(PositionDto(string = 0u, fret = 3u), PositionDto(string = 4u, fret = 12u)),
            chords = listOf(ChordDto("C", "major"), ChordDto("A", "minor")),
            highlight = ChordDto("C", "major"),
            tab = TabDto.ANALYZER,
        )
        // What the engine answers for that switch: the selection cleared, the
        // chords and the tab kept, the highlight cleared.
        val pianoAnswer = pianoPage(
            selected = byteArrayOf(),
            chords = guitar.chords,
            highlight = null,
            tab = TabDto.ANALYZER,
        )
        val engine = ScriptedPageEngine(pianoAnswer)

        val switched = switchInstrument(guitar, InstrumentDto.GUITAR, InstrumentDto.PIANO, engine)

        assertSame("the client returns the engine's page itself, never one it built", pianoAnswer, switched)
        assertEquals(listOf(PageEventDto.SetInstrument(InstrumentDto.PIANO)), engine.events)
        // Nothing of the fretted selection survives into the keyboard.
        assertTrue(viewOf(switched).markedFrets().isEmpty())
        assertTrue(viewOf(switched).markedPitches().isEmpty())
        // The chords and the tab are the engine's answer; the highlight is gone.
        assertEquals(guitar.chords, switched.chords)
        assertEquals(TabDto.ANALYZER, switched.tab)
        assertNull(switched.highlight)
    }

    @Test
    fun `switching from the keyboard converts no key into a string position`() {
        val piano = pianoPage(
            selected = byteArrayOf(60, 72),
            chords = listOf(ChordDto("C", "major")),
            highlight = ChordDto("C", "major"),
            tab = TabDto.ANALYZER,
        )
        val guitarAnswer = frettedPage(
            selected = emptyList(),
            chords = piano.chords,
            highlight = null,
            tab = TabDto.ANALYZER,
        )
        val engine = ScriptedPageEngine(guitarAnswer)

        val switched = switchInstrument(piano, InstrumentDto.PIANO, InstrumentDto.GUITAR, engine)

        assertSame(guitarAnswer, switched)
        assertEquals(listOf(PageEventDto.SetInstrument(InstrumentDto.GUITAR)), engine.events)
        assertTrue("no key is carried across as a mark", viewOf(switched).markedPitches().isEmpty())
        assertTrue(viewOf(switched).markedFrets().isEmpty())
        assertEquals(piano.chords, switched.chords)
    }

    @Test
    fun `selecting the instrument that is already active sends nothing`() {
        assertNull(instrumentSwitchEvent(InstrumentDto.PIANO, InstrumentDto.PIANO))
        assertNull(instrumentSwitchEvent(InstrumentDto.GUITAR, InstrumentDto.GUITAR))

        val piano = pianoPage(selected = byteArrayOf(60))
        val engine = ScriptedPageEngine(frettedPage(selected = emptyList()))

        val page = switchInstrument(piano, InstrumentDto.PIANO, InstrumentDto.PIANO, engine)

        assertSame("a no-op returns the page it was given", piano, page)
        assertTrue("no event, no revision", engine.events.isEmpty())
    }

    @Test
    fun `the switch event carries the target instrument and nothing else`() {
        assertEquals(
            PageEventDto.SetInstrument(InstrumentDto.PIANO),
            instrumentSwitchEvent(InstrumentDto.GUITAR, InstrumentDto.PIANO),
        )
        assertEquals(
            PageEventDto.SetInstrument(InstrumentDto.UKELELE),
            instrumentSwitchEvent(InstrumentDto.GUITAR, InstrumentDto.UKELELE),
        )
    }
}
