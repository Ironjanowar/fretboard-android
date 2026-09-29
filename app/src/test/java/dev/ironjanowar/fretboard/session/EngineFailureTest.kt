package dev.ironjanowar.fretboard.session

import dev.ironjanowar.fretboard.core.AdapterException
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The sentence a failed engine call is shown with.
 *
 * Two failures that look alike on screen are different things, and this is what
 * tells them apart:
 *
 * * the **engine refused** — it answered, with a typed variant and the domain's own
 *   sentence, so the sentence carries the variant's name;
 * * the **call never completed** — the failure is the host's (a missing library, a
 *   refused native thread, a memory failure), and its message alone is not enough
 *   to attribute it: the platform's own text, such as "stack size 4109KB", appears
 *   in no artifact of this application. So the exception's class is named too, and
 *   a screenshot or a report is enough to say *what* failed.
 *
 * This is deliberately a small, exact test: the wording is what a device report is
 * read through.
 */
class EngineFailureTest {

    @Test
    fun `a typed refusal names the engine's own variant and repeats its sentence`() {
        val refusal = AdapterException.InvalidState(
            sentence = "the instrument has no presets",
            field = "instrument",
        )

        assertEquals(
            "The engine rejected the request (InvalidState): the instrument has no presets",
            engineFailure(refusal),
        )
    }

    @Test
    fun `a host failure is attributed to its own class, not to the engine`() {
        val failure = OutOfMemoryError("stack size 4109KB")

        assertEquals(
            "The engine call did not complete (OutOfMemoryError): stack size 4109KB",
            engineFailure(failure),
        )
    }

    @Test
    fun `a host failure with no message still names its class`() {
        val failure = UnsupportedOperationException()

        assertEquals(
            "The engine call did not complete (UnsupportedOperationException): " +
                "java.lang.UnsupportedOperationException",
            engineFailure(failure),
        )
    }
}
