package dev.ironjanowar.fretboard.storage

import dev.ironjanowar.fretboard.core.PageStateDto

/**
 * The last accepted session, durably.
 *
 * The session the user is looking at lives in memory and is derived entirely by
 * the engine. This port is about the *other* half: writing down the committed
 * page so the next launch reopens it, and reading it back without trusting a
 * single byte.
 *
 * What is stored is the engine's own **opaque snapshot** — the
 * `{"schema_version": 1, "page": …}` envelope `encodeSnapshot` produces and
 * `decodeSnapshot` reads — wrapped in a minimal container that carries the
 * container's own schema and the write revision. Nothing else: no second musical
 * serializer, no drafts, no derived surfaces, no card models. A client that kept
 * its own copy of the music would be a second engine, and the two would drift.
 *
 * Both calls are `suspend` and both answer with a *value* rather than throwing:
 * a storage failure is a sentence the screen can show beside a session that keeps
 * working, and unreadable stored bytes are a refusal that keeps those bytes, never
 * an exception at the top of the launch path.
 */
interface SessionStore {

    /**
     * The stored session, or why it cannot be used.
     *
     * Never throws: a missing file, unreadable bytes and a snapshot the engine
     * refuses all arrive as a [StoredSession] variant.
     */
    suspend fun read(): StoredSession

    /**
     * Store [page] as revision [revision].
     *
     * The write is all-or-nothing: it either replaces the stored session
     * atomically or leaves what was there untouched. A [revision] not newer than
     * the stored one is refused rather than written, so two writes that raced
     * cannot leave an older session on disk.
     */
    suspend fun write(revision: Long, page: PageStateDto): StoreOutcome
}

/** What one read of the stored session found. */
sealed interface StoredSession {

    /** Nothing has been stored yet: the first launch of a fresh install. */
    data object None : StoredSession

    /**
     * The stored session: the revision it was written as, the page the engine
     * decoded from it, and the exact envelope those bytes carried.
     *
     * The envelope is kept because it is the engine's canonical form of the page:
     * two pages are the same session when the engine says the same bytes, never
     * because two Kotlin values look alike.
     */
    data class Restored(
        val revision: Long,
        val page: PageStateDto,
        val envelope: String,
    ) : StoredSession

    /**
     * Stored bytes that cannot be used, with the English sentence saying why.
     *
     * The bytes themselves are kept: an unreadable session is evidence about a
     * past release, not garbage to be swept up silently.
     */
    data class Refused(val reason: String) : StoredSession
}

/** What one write did. */
sealed interface StoreOutcome {

    /** The session is durable. */
    data object Written : StoreOutcome

    /** The very same session is already stored under this revision. */
    data class Unchanged(val storedRevision: Long) : StoreOutcome

    /** A newer revision is already stored, so this older write was dropped. */
    data class Superseded(val storedRevision: Long) : StoreOutcome

    /** The write did not happen; whatever was stored before is still there. */
    data class Failed(val reason: String) : StoreOutcome
}

/** The revision a store that has never been written is treated as carrying. */
const val NO_STORED_REVISION: Long = 0L

/**
 * The English sentence for a write that could not be completed.
 *
 * It says what happened and what did *not*: the session on screen is the engine's
 * own answer and a full disk does not take it away.
 */
fun saveFailure(reason: String): String =
    "The last session could not be saved: $reason. The session on screen is unaffected."
