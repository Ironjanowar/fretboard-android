package dev.ironjanowar.fretboard.session

import dev.ironjanowar.fretboard.ui.describeTuning
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The client half of the parity replay (task `A23`).
 *
 * The six assets in `app/src/test/resources/parity/` are the frozen corpus, copied verbatim and
 * with their provenance, and `docs/parity-matrix.md` says what belongs where: an **engine
 * value** can only be replayed where the engine is loaded (the instrumented side), while the
 * **client's own transformation of those values** is what a JVM test may assert. A JVM test
 * that claimed an engine value would be a test computing its own expectation.
 *
 * So this asserts the one transformation the client really performs on engine data — the tuning
 * text on screen — over every case of the two families that carry a tuning: `page-params` (104
 * cases) and `query-transport` (36). The expectation is the corpus's own numbers, read case by
 * case; nothing is computed by the code under test.
 *
 * **No JSON library is on the test classpath** (and one is not added for this: the offline
 * cache decides what can be built here, not convenience). The two fields the replay needs are
 * read by pattern, and the reading is itself asserted: a case whose tuning cannot be read is a
 * **failure**, never a silent skip, and the two cases the corpus *refuses* — the non-UTF-8
 * escape and the 404 — are counted and required to be exactly two.
 */
class FullSessionRegressionTest {

    /** What one case pins, or `null` when the corpus refuses to give that case a page. */
    private data class Tuning(val reference: String, val pitches: List<Int>)

    private fun cases(asset: String): List<String> {
        val file = File("src/test/resources/parity/$asset")
        assertTrue("$asset is not in the test resources: the corpus is required", file.exists())
        return file.readLines().filter { it.isNotBlank() }
    }

    /** The case id, so a failure says which frozen case it is. */
    private fun caseId(line: String): String =
        Regex("\"case_id\"\\s*:\\s*\"([^\"]+)\"").find(line)?.groupValues?.get(1) ?: "<unreadable case>"

    /**
     * The tuning one line pins, `null` when the corpus refuses the case a page at all.
     *
     * A line the pattern cannot read is a failure: `null` is reserved for the two refusals,
     * which the caller counts.
     */
    private fun tuningOf(line: String): Tuning? {
        // The two refused cases carry no page at all, so no tuning either.
        if (!line.contains("\"tuning_state\"")) return null

        val reference = Regex("\"reference\"\\s*:\\s*\"([^\"]*)\"").find(line)?.groupValues?.get(1)
        val pitches = Regex("\"pitches\"\\s*:\\s*\\[([^\\]]*)\\]").find(line)?.groupValues?.get(1)

        if (reference == null || pitches == null) {
            // The keyboard carries no tuning, and the corpus says which instrument it is: a case
            // with no readable tuning that is *not* the piano is a case this replay cannot read,
            // and that is a failure rather than a skip.
            assertTrue(
                "${caseId(line)} carries a tuning the replay cannot read and is not the keyboard",
                line.contains("\"instrument\":\"piano\""),
            )
            return null
        }

        return Tuning(
            reference = reference!!,
            pitches = pitches!!.split(',').mapNotNull { it.trim().toIntOrNull() },
        )
    }

    /** The text the corpus's own numbers have to produce. */
    private fun expected(tuning: Tuning): String =
        "${tuning.reference} ${tuning.pitches.joinToString("-")}"

    @Test
    fun `every frozen case that pins a tuning renders as the corpus says`() {
        var checked = 0
        var withoutTuning = 0

        for (asset in listOf("page-params.jsonl", "query-transport.jsonl")) {
            for (line in cases(asset)) {
                val tuning = tuningOf(line)
                if (tuning == null) {
                    // Either the corpus refused the case a page (the non-UTF-8 escape, the 404) or
                    // it is a keyboard case, which has no tuning to render. Both are counted.
                    withoutTuning += 1
                    continue
                }

                val bytes = ByteArray(tuning.pitches.size) { index -> tuning.pitches[index].toByte() }

                assertEquals(
                    "${caseId(line)} ($asset) rendered differently",
                    expected(tuning),
                    describeTuning(tuning.reference, bytes),
                )
                checked += 1
            }
        }

        assertEquals("every case of the two families is accounted for", 140, checked + withoutTuning)
        assertEquals("15 keyboard cases plus the 2 refused ones carry no tuning", 17, withoutTuning)
        assertEquals(123, checked)
    }

    @Test
    fun `every pitch the corpus pins is one a byte can carry`() {
        // The rendering reads each pitch as an unsigned byte, so a corpus pitch outside 0..127
        // would be silently reinterpreted. The corpus is known to stay inside the range, and
        // this is what makes that a checked fact instead of an assumption.
        var pitches = 0

        for (asset in listOf("page-params.jsonl", "query-transport.jsonl")) {
            for (line in cases(asset)) {
                val tuning = tuningOf(line) ?: continue
                for (pitch in tuning.pitches) {
                    assertTrue(
                        "${caseId(line)} pins $pitch, which a signed byte cannot carry",
                        pitch in 0..127,
                    )
                    pitches += 1
                }
            }
        }

        assertTrue("the corpus pins pitches at all", pitches > 0)
    }

    @Test
    fun `the corpus is the one the provenance file describes`() {
        val provenance = File("src/test/resources/parity/provenance.json").readText()

        for (asset in listOf("page-params.jsonl", "query-transport.jsonl", "keys.jsonl")) {
            assertTrue("$asset is not described by the provenance file", provenance.contains(asset))
        }
        assertTrue(
            "the provenance file must name the commit the corpus came from",
            provenance.contains("source_commit"),
        )
    }
}
