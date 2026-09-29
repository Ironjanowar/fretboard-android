package dev.ironjanowar.fretboard.links

import dev.ironjanowar.fretboard.session.BootCandidate
import dev.ironjanowar.fretboard.session.SessionCandidateSource
import dev.ironjanowar.fretboard.session.readLegacyLink

/**
 * The delivery this process was handed, read exactly once (task `A19`).
 *
 * This is the seam `A18` left open: [SessionCandidateSource] is where the incoming side
 * reaches the startup arbitration, and until now the production source was
 * `NoDeliveredCandidate`, the honest reading of an app with no incoming path. Now the
 * activity hands its delivery here, the parser has already decided whether it is a
 * text, and this turns it into the candidate the arbitration orders.
 *
 * Two rules hold it together:
 *
 * * **the text is read by the engine, never here.** [readLink] defaults to the binding
 *   call (`import_legacy_url`) and is a parameter only so that the *decision above it*
 *   — a text becomes a page, a refusal crosses as a refusal, nothing crosses as
 *   nothing — can be pinned on the JVM without every scripted session engine growing a
 *   method it does not use. No URL is split and no musical value is computed in this
 *   file.
 * * **one delivery is read once.** The arbitration runs again after every composition —
 *   a rotation goes through it — and a delivery that stayed "pending" would open the
 *   same link again over whatever the user had since done. Consuming it makes a second
 *   read answer "nothing", which is exactly what a launch that carried nothing
 *   answers.
 */
class IncomingSessionSource(
    private val readLink: suspend (String) -> BootCandidate = ::readLegacyLink,
) : SessionCandidateSource {

    /** The delivery waiting to be read, or `null` when there is none. */
    private var pending: DeliveredText? = null

    /**
     * Hand one delivery over.
     *
     * Called from the activity's own callbacks, which run on the main thread, as does
     * the arbitration that reads it back: no lock is needed and none is pretended.
     */
    fun deliver(delivered: DeliveredText) {
        pending = delivered
    }

    override suspend fun delivered(): BootCandidate? {
        val delivered = pending ?: return null
        pending = null

        return when (delivered) {
            DeliveredText.Nothing -> null

            is DeliveredText.Refused -> BootCandidate.Rejected(delivered.reason)

            is DeliveredText.Text -> readLink(delivered.value)
        }
    }
}
