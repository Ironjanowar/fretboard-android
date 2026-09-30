package dev.ironjanowar.fretboard.links

/**
 * The canonical web origin this application sends sessions to (tasks `A20`/`A21`).
 *
 * **Approved, and verified live.** The origin is the user's own web application, confirmed by
 * them and checked from here before it was written down: it answers `200` on the page route,
 * and it answers `200` on the legacy query this codec emits — including a percent-encoded
 * sharp (`?chords=C%23maj`), which is the one spelling a raw `#` would break.
 *
 * It was `null` while the origin was unresolved (`DEC-07`), because a link is only meaningful
 * against the origin its web app actually runs on and inventing a host would have shipped
 * links pointing nowhere. The value is here, in one place, so a change is one line with its
 * test beside it — and the note the application showed while sharing was off disappears with
 * it, because it is [unavailableReason] that drove that note.
 */
object ShareConfig {

    /**
     * The approved HTTPS base, or `null` while there is none.
     *
     * HTTPS only: a web origin that cannot be verified is not one this application will
     * send a session to, and the App Link association (`A21`) needs the same domain.
     */
    val approvedBase: String? = "https://cuwano.gramos.me/"

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
