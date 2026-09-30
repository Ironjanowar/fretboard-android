package dev.ironjanowar.fretboard.links

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What this application does about sharing a session (task `A20`).
 *
 * The plan is explicit about the interim: *if the host is unresolved, do not invent it,
 * disable sharing with an explicit explanation*. These tests are that decision, and they are
 * what keeps a host from being guessed into the application later without anyone noticing.
 *
 * The query below is hand-written text: what the engine writes is pinned in the core
 * (`crates/domain/tests/query_emission.rs` and the adapter's `p7_query_emission.rs`).
 */
class ShareLauncherTest {

    @Test
    fun `sharing is off in this build, with a sentence that says why`() {
        val outcome = ShareLauncher(base = null).linkFor("chords=Cmaj")

        assertTrue(outcome is ShareOutcome.Unavailable)
        assertTrue((outcome as ShareOutcome.Unavailable).reason.contains("no canonical web origin"))
    }

    @Test
    fun `no host is invented when there is none`() {
        // The rule that outlives the approved origin: with no base, no query becomes a link.
        for (query in listOf("", "chords=Cmaj", "chords=Cmaj&marked=5-2")) {
            val outcome = ShareLauncher(base = null).linkFor(query)

            assertTrue("$query produced a link", outcome is ShareOutcome.Unavailable)
        }
    }

    @Test
    fun `the shipped configuration is the approved origin`() {
        // The production default, not a parameter: this is the value the APK carries, and it
        // is the origin the web application actually serves.
        assertEquals("https://cuwano.gramos.me/", ShareConfig.approvedBase)
        assertEquals("sharing is on, so nothing explains why it would be off", null, ShareConfig.unavailableReason)
    }

    @Test
    fun `the link the application would send names the approved origin and the page route`() {
        assertEquals(
            ShareOutcome.Link("https://cuwano.gramos.me/?chords=Cmaj"),
            ShareLauncher().linkFor("chords=Cmaj"),
        )
    }

    @Test
    fun `an approved origin produces the link, and the query is the engine plus nothing`() {
        val outcome = ShareLauncher(base = "https://fretboard.test/").linkFor("chords=Cmaj")

        assertEquals(ShareOutcome.Link("https://fretboard.test/?chords=Cmaj"), outcome)
    }

    @Test
    fun `a trailing slash on the origin does not double up`() {
        assertEquals(
            ShareOutcome.Link("https://fretboard.test/?chords=Cmaj"),
            ShareLauncher(base = "https://fretboard.test").linkFor("chords=Cmaj"),
        )
    }

    @Test
    fun `an empty query is the bare origin, with no stray question mark`() {
        assertEquals(
            ShareOutcome.Link("https://fretboard.test/"),
            ShareLauncher(base = "https://fretboard.test/").linkFor(""),
        )
    }

    @Test
    fun `a base that is not HTTPS is refused like no base at all`() {
        // The plan asks for a confirmed HTTPS origin; anything else is not one, and a
        // downgrade is not a link this application sends a session to.
        for (base in listOf("http://fretboard.test/", "fretboard.test", "ftp://fretboard.test/")) {
            assertTrue(
                "$base was used as an origin",
                ShareLauncher(base = base).linkFor("chords=Cmaj") is ShareOutcome.Unavailable,
            )
        }
    }
}
