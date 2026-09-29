package dev.ironjanowar.fretboard.links

import android.content.Intent

/**
 * The largest incoming text this application hands to the engine.
 *
 * It is the *client's* own guard for an untrusted input, not a music rule and not the
 * engine's cap: concrete resource limits are still an open decision in the core
 * (`DEC-06`), and inventing one there would be a decision this client does not own.
 * What it buys is that a pathological extra is refused with an English sentence
 * before anything is parsed.
 */
const val MAX_INCOMING_CHARS: Int = 4096

/** The MIME type this application imports. */
const val IMPORTABLE_TYPE: String = "text/plain"

/**
 * One delivered intent, as the parser needs it.
 *
 * No Android type crosses this boundary, so the whole decision below is pinned on the
 * JVM: the platform's own `Intent` is read into this shape by [asDeliveredIntent], in
 * one place, with nothing to decide there.
 */
data class DeliveredIntent(
    /** The platform action, e.g. `android.intent.action.SEND`. */
    val action: String?,
    /** The delivered MIME type, e.g. `text/plain`. */
    val type: String?,
    /** Every text the delivery carried, in the order it carried them. */
    val texts: List<String>,
)

/** What one delivery gave this application. */
sealed interface DeliveredText {

    /** The text to import. The engine decides what it means, never this class. */
    data class Text(val value: String) : DeliveredText

    /** The delivery carried nothing this application imports. */
    data object Nothing : DeliveredText

    /** The delivery carried something this application refuses, in English. */
    data class Refused(val reason: String) : DeliveredText
}

/**
 * What one delivered intent gives this application (task `A19`).
 *
 * This is the *routing* half of A19 and nothing else: which deliveries are read, and
 * what text they yield. The link itself is read by the engine
 * (`import_legacy_url`), never here — no URL is split, no parameter is decoded and
 * nothing is fetched.
 *
 * The rules, each one a test:
 *
 * * only an `ACTION_SEND` delivery is read. A launch with any other action carries no
 *   session, which is what lets the stored session win (`A18`). `ACTION_VIEW` is
 *   deliberately **not** handled: serving it would mean claiming a web origin this
 *   application does not have an approved one of (`DEC-07`), and the plan says to add
 *   the `SEND` filter only.
 * * a delivery that is not `text/plain` is refused in English rather than guessed at.
 * * a delivery that arrived with no text, or with blank text, is refused.
 * * **the first candidate wins** when a delivery carries several. Only one session can
 *   be opened, and taking a later one would make the outcome depend on how the sender
 *   happened to order its extras.
 * * text longer than [MAX_INCOMING_CHARS] is refused before anything reads it.
 */
fun parseDeliveredIntent(intent: DeliveredIntent): DeliveredText = when {
    intent.action != Intent.ACTION_SEND ->
        DeliveredText.Nothing

    intent.type != IMPORTABLE_TYPE ->
        DeliveredText.Refused(
            "The shared content is ${intent.type ?: "of no declared type"}, not plain text, " +
                "so it was not imported.",
        )

    intent.texts.isEmpty() ->
        DeliveredText.Refused(
            "The shared content carried no text, so there was nothing to import.",
        )

    else -> {
        val candidate = intent.texts.first().trim()
        when {
            candidate.isEmpty() -> DeliveredText.Refused(
                "The shared text is empty, so there was nothing to import.",
            )

            candidate.length > MAX_INCOMING_CHARS -> DeliveredText.Refused(
                "The shared text is longer than this application imports " +
                    "($MAX_INCOMING_CHARS characters), so it was not imported.",
            )

            else -> DeliveredText.Text(candidate)
        }
    }
}

/**
 * Read one pasted text as the delivery it is (task `A19`).
 *
 * A paste is a delivery the user made by hand, so it goes through the same rules as a
 * shared one — blank text and an oversized text are refused with the same sentences —
 * and there is no second, laxer way into the engine. `null` is an empty clipboard, which
 * is a delivery that carried no text.
 *
 * Nothing is read from the clipboard here: the caller reads it, on an explicit action,
 * and this only decides what the text is worth. The application never scrapes it.
 */
fun pastedText(text: String?): DeliveredText = parseDeliveredIntent(
    DeliveredIntent(
        action = Intent.ACTION_SEND,
        type = IMPORTABLE_TYPE,
        texts = listOfNotNull(text),
    ),
)

/** Read the platform's own `Intent` into the shape the parser decides on. */
fun Intent.asDeliveredIntent(): DeliveredIntent = DeliveredIntent(
    action = action,
    type = type,
    // The extra first, then whatever clip data came with it: a sender that puts its
    // text in a clip is as ordinary as one that uses the extra, and the parser's
    // "first candidate wins" rule is what orders them.
    texts = buildList {
        getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString()?.let(::add)
        clipData?.let { clip ->
            for (index in 0 until clip.itemCount) {
                clip.getItemAt(index).text?.toString()?.let(::add)
            }
        }
    },
)
