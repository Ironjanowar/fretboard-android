package dev.ironjanowar.fretboard.links

import dev.ironjanowar.fretboard.core.InstrumentDto
import dev.ironjanowar.fretboard.core.InstrumentStateDto
import dev.ironjanowar.fretboard.core.PageStateDto
import dev.ironjanowar.fretboard.core.TabDto
import dev.ironjanowar.fretboard.core.TuningDto
import dev.ironjanowar.fretboard.session.BootCandidate
import dev.ironjanowar.fretboard.session.SessionCandidateSource
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What one delivery gives the startup arbitration (task `A19`).
 *
 * The link's own meaning is the engine's and is pinned in the core
 * (`crates/domain/tests/legacy_import.rs`, the adapter's `p6_legacy_import.rs`); what
 * is pinned here is the *decision above it*: a text becomes a page, a delivery the
 * parser refused crosses as a refusal and never reaches the engine, a launch that
 * carried nothing crosses as nothing — and a delivery is read exactly once.
 *
 * That last one is the rule that matters on a device. The arbitration runs again after
 * every composition, so a rotation goes through it; a delivery that stayed pending
 * would open the shared link a second time over whatever the user had since done.
 *
 * The reader is scripted, so no engine is loaded and no arm64 library is needed. Every
 * expectation below is hand-written.
 */
class IncomingSessionSourceTest {

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

    private fun share(text: String = "https://any.test/?chords=Cmaj") =
        DeliveredText.Text(text)

    @Test
    fun `a shared text is read by the engine and crosses as the page it names`() = runTest {
        val asked = mutableListOf<String>()
        val source = IncomingSessionSource { text ->
            asked += text
            BootCandidate.Valid(incomingPage)
        }

        source.deliver(share())

        assertEquals(BootCandidate.Valid(incomingPage), source.delivered())
        assertEquals(listOf("https://any.test/?chords=Cmaj"), asked)
    }

    @Test
    fun `a text the engine refuses crosses as the engine's own refusal`() = runTest {
        val engineSaid = "The engine rejected the request (InvalidUrl): the URL names no route."
        val source = IncomingSessionSource { BootCandidate.Rejected(engineSaid) }

        source.deliver(share())

        assertEquals(BootCandidate.Rejected(engineSaid), source.delivered())
    }

    @Test
    fun `a delivery the parser refused crosses as a refusal and never reaches the engine`() =
        runTest {
            var asked = 0
            val source = IncomingSessionSource { asked += 1; BootCandidate.Rejected("unused") }
            val parserSaid = "The shared content is image/png, not plain text, so it was not imported."

            source.deliver(DeliveredText.Refused(parserSaid))

            assertEquals(BootCandidate.Rejected(parserSaid), source.delivered())
            assertEquals("the engine is not asked about a delivery the parser refused", 0, asked)
        }

    @Test
    fun `a launch that carried nothing crosses as nothing`() = runTest {
        var asked = 0
        val source = IncomingSessionSource { asked += 1; BootCandidate.Valid(incomingPage) }

        source.deliver(DeliveredText.Nothing)

        assertNull(source.delivered())
        assertEquals(0, asked)
    }

    @Test
    fun `a source nothing was handed to answers nothing`() = runTest {
        var asked = 0
        val source = IncomingSessionSource { asked += 1; BootCandidate.Valid(incomingPage) }

        assertNull(source.delivered())
        assertEquals(0, asked)
    }

    @Test
    fun `a delivery is read exactly once, so a rotation cannot reopen it`() = runTest {
        var asked = 0
        val source = IncomingSessionSource { asked += 1; BootCandidate.Valid(incomingPage) }

        source.deliver(share())

        assertEquals(BootCandidate.Valid(incomingPage), source.delivered())
        assertNull("the same delivery is not a second delivery", source.delivered())
        assertNull(source.delivered())
        assertEquals(1, asked)
    }

    @Test
    fun `a second, genuinely different delivery is read after the first`() = runTest {
        val read = mutableListOf<String>()
        val source = IncomingSessionSource { text ->
            read += text
            BootCandidate.Valid(incomingPage)
        }

        source.deliver(share("https://first.test/?chords=Cmaj"))
        source.delivered()
        source.deliver(share("https://second.test/?chords=Gmaj"))

        assertEquals(BootCandidate.Valid(incomingPage), source.delivered())
        assertEquals(
            listOf("https://first.test/?chords=Cmaj", "https://second.test/?chords=Gmaj"),
            read,
        )
    }

    @Test
    fun `the source answers what the arbitration expects of a candidate source`() = runTest {
        // A spot check that this really is one: the decision `A18` orders is reachable
        // through the port it declares, with no knowledge of what is behind it.
        val source = IncomingSessionSource { BootCandidate.Valid(incomingPage) }

        source.deliver(share())

        val port: SessionCandidateSource = source
        assertTrue(port.delivered() is BootCandidate.Valid)
    }
}
