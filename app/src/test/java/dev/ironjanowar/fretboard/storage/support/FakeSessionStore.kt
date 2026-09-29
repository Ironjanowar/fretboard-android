package dev.ironjanowar.fretboard.storage.support

import dev.ironjanowar.fretboard.core.PageStateDto
import dev.ironjanowar.fretboard.storage.SessionStore
import dev.ironjanowar.fretboard.storage.StoreOutcome
import dev.ironjanowar.fretboard.storage.StoredSession

/**
 * A scripted store: no file, no rule, only the answers a test hands it.
 *
 * The session holder's own behaviour is what the tests that use this fake are
 * about — that every accepted transition is written down, that a refusal is
 * reported without taking the session away, that a restored revision is continued
 * rather than restarted. Whether the durable store really keeps its bytes is a
 * different question, and it is asked of the real [SessionStore] implementation
 * against a temporary file, never of this fake.
 */
class FakeSessionStore(
    /** What the next read answers. */
    var stored: StoredSession = StoredSession.None,
    /** What the next write answers, or `null` for a successful write. */
    var outcome: StoreOutcome? = null,
) : SessionStore {

    /** Every write the holder made, in the order it made them. */
    val writes: MutableList<Pair<Long, PageStateDto>> = mutableListOf()

    /** Every read the holder made. */
    var reads: Int = 0
        private set

    override suspend fun read(): StoredSession {
        reads += 1
        return stored
    }

    override suspend fun write(revision: Long, page: PageStateDto): StoreOutcome {
        writes += revision to page
        return outcome ?: StoreOutcome.Written
    }
}
