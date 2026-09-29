package dev.ironjanowar.fretboard.session

import dev.ironjanowar.fretboard.core.PageStateDto
import dev.ironjanowar.fretboard.storage.NO_STORED_REVISION
import dev.ironjanowar.fretboard.storage.SessionStore
import dev.ironjanowar.fretboard.storage.StoredSession

/** One incoming session the engine already judged: a page, or why there is none. */
sealed interface BootCandidate {

    /** A page the engine accepted from an incoming delivery. */
    data class Valid(val page: PageStateDto) : BootCandidate

    /** An incoming delivery the engine refused, in its own words. */
    data class Rejected(val reason: String) : BootCandidate
}

/**
 * The session a launch carried, if it carried one (task `A18`; `A19` fills it).
 *
 * This is the seam the incoming side reaches the arbitration through: a pasted
 * link, an `ACTION_SEND` text, or nothing at all. It is deliberately a port and not
 * a call into the activity, because the *decision* — candidate, then stored session,
 * then defaults — is what needs pinning on the JVM, and it must not need an Android
 * `Intent` to be exercised.
 */
interface SessionCandidateSource {

    /** The delivered candidate, or `null` when this launch carried nothing. */
    suspend fun delivered(): BootCandidate?
}

/**
 * A launch that carried nothing.
 *
 * Every launch carries nothing until `A19` reads the intent, so this is the
 * production source today: not a placeholder that quietly answers "valid", but the
 * honest reading of an app that has no incoming path yet.
 */
object NoDeliveredCandidate : SessionCandidateSource {
    override suspend fun delivered(): BootCandidate? = null
}

/** Where the session that opens came from. */
enum class BootSource {
    /** An incoming delivery the engine accepted. */
    INCOMING,

    /** The last session this device stored. */
    STORED,

    /** The engine's own defaults: nothing to restore. */
    DEFAULTS,
}

/** What the app should open at startup, and why. */
sealed interface BootDecision {

    /**
     * Open this session.
     *
     * [storedRevision] is the revision the stored session was written as, whichever
     * source wins: a candidate still continues the stored numbering, so the store
     * cannot read a newer session as an older write. [notice] is the English
     * sentence to show beside the session, or `null`.
     */
    data class Open(
        val load: SessionLoad,
        val source: BootSource,
        val storedRevision: Long = NO_STORED_REVISION,
        val notice: String? = null,
    ) : BootDecision

    /** Keep the session already on screen, and show [notice] beside it. */
    data class KeepCurrent(val notice: String) : BootDecision

    /** A newer decision won: this answer must not be published. */
    data object Superseded : BootDecision
}

/**
 * Which session wins at startup (task `A18`).
 *
 * The order is the plan's: **an incoming candidate the engine accepted** beats **the
 * last stored session**, which beats **the engine's own defaults**. Two rules hold it
 * together:
 *
 * * a rejected incoming delivery never takes a session away — on a cold start the
 *   engine's defaults open with the reason beside them, and a session already on
 *   screen simply stays;
 * * the *newest* decision wins. Every call to [open] takes a generation, and an
 *   answer whose generation is stale is [BootDecision.Superseded] rather than an
 *   answer: a stored session read slowly, or an incoming delivery that arrived while
 *   a read was in flight, can never overwrite the newer intent.
 */
class BootCoordinator(
    private val engine: SessionEngine,
    private val store: SessionStore,
    private val delivered: SessionCandidateSource = NoDeliveredCandidate,
) {

    /**
     * The generation of the newest decision.
     *
     * Every [open] bumps it. An answer produced under an older generation is
     * discarded, which is what makes the arbitration safe against a slow read and a
     * late delivery alike.
     */
    private var generation: Long = 0

    /**
     * The session to open, or the reason not to.
     *
     * [holding] says whether a session is already on screen: a rejected delivery then
     * leaves it alone instead of replacing it.
     */
    suspend fun open(holding: Boolean): BootDecision {
        generation += 1
        val token = generation

        val stored = store.read()
        if (token != generation) return BootDecision.Superseded

        val candidate = delivered.delivered()
        if (token != generation) return BootDecision.Superseded

        return when (candidate) {
            null -> fromStored(stored)

            is BootCandidate.Valid -> BootDecision.Open(
                load = engine.restore(candidate.page),
                source = BootSource.INCOMING,
                storedRevision = revisionOf(stored),
            )

            is BootCandidate.Rejected -> if (holding) {
                BootDecision.KeepCurrent(candidate.reason)
            } else {
                fromStored(stored).withNotice(candidate.reason)
            }
        }
    }

    /**
     * The stored session when it can be used, the engine's own defaults otherwise.
     *
     * An unusable stored session is not an error to open with: the reason travels
     * beside a session that works, and the bytes stay on disk.
     */
    private suspend fun fromStored(stored: StoredSession): BootDecision = when (stored) {
        is StoredSession.Restored -> BootDecision.Open(
            load = engine.restore(stored.page),
            source = BootSource.STORED,
            storedRevision = stored.revision,
        )

        is StoredSession.None -> BootDecision.Open(engine.start(), BootSource.DEFAULTS)

        is StoredSession.Refused ->
            BootDecision.Open(engine.start(), BootSource.DEFAULTS, notice = stored.reason)
    }

    /** The same decision, with the refusal shown beside the session it opens. */
    private fun BootDecision.withNotice(notice: String): BootDecision =
        if (this is BootDecision.Open) copy(notice = notice) else this

    /** The revision a stored session continues from, whichever source wins. */
    private fun revisionOf(stored: StoredSession): Long =
        (stored as? StoredSession.Restored)?.revision ?: NO_STORED_REVISION
}
