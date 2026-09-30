package dev.ironjanowar.fretboard.links

/**
 * The canonical web origin this application would send sessions to, or nothing (tasks
 * `A20`/`A21`).
 *
 * **Nothing, today.** A link this application emits is only meaningful against the origin
 * its web app actually runs on, and no origin has been approved (`DEC-07`): writing one
 * here would mean inventing a host and shipping links that point nowhere. The plan says so
 * plainly — *if the host is unresolved, do not invent it, disable sharing with an explicit
 * explanation* — and this is where that decision lives, in one value, so that approving an
 * origin later is a one-line change with a test behind it.
 *
 * Importing is unaffected: a pasted or shared text goes through [pastedText], and the
 * engine reads the link without comparing any origin.
 */
object ShareConfig {

    /**
     * The approved HTTPS base, or `null` while there is none.
     *
     * HTTPS only: a web origin that cannot be verified is not one this application will
     * send a session to, and the App Link association (`A21`) needs the same domain.
     */
    val approvedBase: String? = null

    /** What to show where a share control would be, or `null` when sharing is available. */
    val unavailableReason: String? =
        if (approvedBase == null) {
            "Sharing is not available in this build: no canonical web origin has been " +
                "approved, so there is no link this application could produce. Importing " +
                "still works — paste a link, or share a text into this app."
        } else {
            null
        }
}

/** What this application can do with a session's link. */
sealed interface ShareOutcome {

    /** The link a client would send or copy. */
    data class Link(val url: String) : ShareOutcome

    /** No link can be produced, in English. */
    data class Unavailable(val reason: String) : ShareOutcome
}

/**
 * Turns the query the engine wrote into the link this application would share, or says why
 * it cannot (task `A20`).
 *
 * The engine writes the query and nothing else — it has no opinion about any origin, and
 * `encode_page_query` deliberately carries no base. Joining it to the approved origin is the
 * only thing that happens here, and while [ShareConfig.approvedBase] is `null` this refuses
 * with a sentence instead of guessing a host. No musical value and no URL parsing on either
 * side of that.
 */
class ShareLauncher(private val base: String? = ShareConfig.approvedBase) {

    /** The link for one page's query, or the reason there is none. */
    fun linkFor(query: String): ShareOutcome {
        val approved = base ?: return ShareOutcome.Unavailable(REASON)

        return if (approved.startsWith("https://")) {
            ShareOutcome.Link(joined(approved, query))
        } else {
            ShareOutcome.Unavailable(REASON)
        }
    }

    /**
     * The base with the query appended.
     *
     * The page route is always named, even when the query is empty: the engine serves only
     * `/`, so a link that dropped it would name a path nobody serves. The query itself is
     * appended only when there is one, so no stray separator is emitted.
     */
    private fun joined(approved: String, query: String): String {
        val trimmed = approved.trimEnd('/')
        return if (query.isEmpty()) "$trimmed/" else "$trimmed/?$query"
    }

    private companion object {
        /** One sentence, wherever sharing is refused. */
        const val REASON: String =
            "Sharing is not available in this build: no canonical web origin has been " +
                "approved, so there is no link this application could produce. Importing " +
                "still works — paste a link, or share a text into this app."
    }
}
