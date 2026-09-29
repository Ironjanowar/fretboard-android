package dev.ironjanowar.fretboard.links

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What one delivered intent gives the application (task `A19`).
 *
 * The parser is pure: no Android type reaches it, so every rule is pinned here on the
 * JVM, where the engine's arm64 library cannot be loaded and where the *routing*
 * decision is the only thing under test. Nothing below asserts what a link means —
 * that is the engine's (`import_legacy_url`), and the text that crosses is compared as
 * text.
 *
 * The rules: only `ACTION_SEND`, only `text/plain`, a text must be there and be
 * non-blank, the first of several candidates wins, and an oversized text is refused
 * before anything reads it. `ACTION_VIEW` is deliberately *not* handled — the plan
 * asks for the `SEND` filter only, and serving `ACTION_VIEW` would mean claiming a web
 * origin this application has no approved one of (`DEC-07`).
 */
class IncomingIntentParserTest {

    private fun send(
        type: String? = IMPORTABLE_TYPE,
        texts: List<String> = listOf("https://any.test/?chords=Cmaj"),
    ) = DeliveredIntent(action = "android.intent.action.SEND", type = type, texts = texts)

    @Test
    fun `a shared plain text is what this application imports`() {
        val delivered = parseDeliveredIntent(send())

        assertEquals(DeliveredText.Text("https://any.test/?chords=Cmaj"), delivered)
    }

    @Test
    fun `a plain launch carries no session, so it is not a delivery at all`() {
        val launch = DeliveredIntent(action = "android.intent.action.MAIN", type = null, texts = emptyList())

        assertEquals(DeliveredText.Nothing, parseDeliveredIntent(launch))
    }

    @Test
    fun `only SEND is served, never a guessed VIEW`() {
        // A VIEW delivery would name a host, and no host has been approved for this
        // application to serve (DEC-07). It is not read, not refused as text.
        val view = DeliveredIntent(
            action = "android.intent.action.VIEW",
            type = null,
            texts = listOf("https://any.test/?chords=Cmaj"),
        )

        assertEquals(DeliveredText.Nothing, parseDeliveredIntent(view))
    }

    @Test
    fun `a delivery that is not plain text is refused in English`() {
        val delivered = parseDeliveredIntent(send(type = "image/png"))

        assertTrue("a refusal is not a silence", delivered is DeliveredText.Refused)
        assertTrue(
            (delivered as DeliveredText.Refused).reason.contains("image/png"),
        )
    }

    @Test
    fun `a delivery with no declared type is refused`() {
        val delivered = parseDeliveredIntent(send(type = null))

        assertTrue(delivered is DeliveredText.Refused)
    }

    @Test
    fun `a delivery with no text is refused rather than ignored`() {
        val delivered = parseDeliveredIntent(send(texts = emptyList()))

        assertTrue(delivered is DeliveredText.Refused)
        assertTrue((delivered as DeliveredText.Refused).reason.contains("no text"))
    }

    @Test
    fun `blank text is refused`() {
        for (blank in listOf("", "   ", " \n\t ")) {
            val delivered = parseDeliveredIntent(send(texts = listOf(blank)))

            assertTrue("$blank was accepted", delivered is DeliveredText.Refused)
        }
    }

    @Test
    fun `the first candidate wins when a delivery carries several`() {
        val delivered = parseDeliveredIntent(
            send(texts = listOf("https://first.test/?chords=Cmaj", "https://second.test/?chords=Gmaj")),
        )

        assertEquals(DeliveredText.Text("https://first.test/?chords=Cmaj"), delivered)
    }

    @Test
    fun `surrounding whitespace is trimmed away`() {
        val delivered = parseDeliveredIntent(send(texts = listOf("  https://any.test/?chords=Cmaj\n")))

        assertEquals(DeliveredText.Text("https://any.test/?chords=Cmaj"), delivered)
    }

    @Test
    fun `an oversized text is refused before anything reads it`() {
        val tooLong = "h".repeat(MAX_INCOMING_CHARS + 1)

        val delivered = parseDeliveredIntent(send(texts = listOf(tooLong)))

        assertTrue(delivered is DeliveredText.Refused)
        assertTrue(
            "the sentence says what the limit is",
            (delivered as DeliveredText.Refused).reason.contains(MAX_INCOMING_CHARS.toString()),
        )
    }

    @Test
    fun `a text exactly at the limit is imported`() {
        val atLimit = "h".repeat(MAX_INCOMING_CHARS)

        assertEquals(DeliveredText.Text(atLimit), parseDeliveredIntent(send(texts = listOf(atLimit))))
    }

    @Test
    fun `the same delivery read twice gives the same answer`() {
        // The lifecycle can deliver an intent more than once (a cold start and then
        // `onNewIntent`, for instance); the parser is a pure function of what arrived,
        // so it can only answer the same thing — what the *application* does about a
        // repeated delivery belongs to the coordinator above it.
        val intent = send()

        assertEquals(parseDeliveredIntent(intent), parseDeliveredIntent(intent))
    }
}
