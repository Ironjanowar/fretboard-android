package dev.ironjanowar.fretboard.storage

import androidx.core.util.AtomicFile
import dev.ironjanowar.fretboard.core.PageStateDto
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** The file the last session is kept in, inside the application's own directory. */
const val LAST_SESSION_FILE: String = "last-session"

/** The file an unreadable session is kept aside as, before a new one replaces it. */
const val REJECTED_SESSION_FILE: String = "$LAST_SESSION_FILE.rejected"

/**
 * The application's last-session store, over one file in [filesDir].
 *
 * It is the whole production wiring of A17: a private file inside the app's own
 * directory, which no other application and no user can write.
 */
fun lastSessionStore(filesDir: File): SessionStore =
    AtomicFileSessionStore(File(filesDir, LAST_SESSION_FILE))

/**
 * The last session on disk, written atomically.
 *
 * * A successful write goes to a temporary file and is renamed over the previous
 *   one, so a reader sees one session or the other and never half of either.
 * * A write that fails leaves the previous session exactly as it was.
 * * A page whose envelope is already stored is not written again, so a no-op action
 *   is not a disk write.
 * * A write not newer than the stored revision is refused, so two writes that raced
 *   leave the newer session on disk.
 * * Unreadable stored bytes are refused, and are copied aside before a new session
 *   replaces them — and if that copy cannot be made, nothing is written at all.
 *
 * [io] is the dispatcher both calls run on: file work never happens on the thread
 * that asked.
 */
class AtomicFileSessionStore(
    private val file: File,
    private val serializer: SessionSnapshotSerializer = SessionSnapshotSerializer(),
    private val io: CoroutineDispatcher = Dispatchers.IO,
) : SessionStore {

    override suspend fun read(): StoredSession = withContext(io) { stored() }

    override suspend fun write(revision: Long, page: PageStateDto): StoreOutcome =
        withContext(io) { store(revision, page) }

    private fun store(revision: Long, page: PageStateDto): StoreOutcome {
        val envelope = try {
            serializer.envelope(page)
        } catch (error: Throwable) {
            return StoreOutcome.Failed(saveFailure(said(error)))
        }
        val stored = stored()
        if (stored is StoredSession.Restored) {
            if (stored.envelope == envelope) return StoreOutcome.Unchanged(stored.revision)
            if (revision <= stored.revision) return StoreOutcome.Superseded(stored.revision)
        }
        if (stored is StoredSession.Refused && !keepAside()) {
            return StoreOutcome.Failed(
                saveFailure("the unreadable stored session could not be kept aside"),
            )
        }
        return writeAtomically(serializer.container(revision, envelope))
    }

    /** What is on disk now, never throwing: unreadable bytes are a refusal. */
    private fun stored(): StoredSession = try {
        if (file.exists()) serializer.decode(file.readBytes()) else StoredSession.None
    } catch (error: Throwable) {
        StoredSession.Refused(
            "The stored session could not be read: ${said(error)}. Its bytes were kept.",
        )
    }

    /**
     * Keep the unreadable bytes beside the session that is about to replace them.
     *
     * `false` when the copy cannot be made, and then nothing is written: losing a
     * session the user had, even an unreadable one, is worse than not saving.
     */
    private fun keepAside(): Boolean = try {
        rejected().writeBytes(file.readBytes())
        true
    } catch (error: Throwable) {
        false
    }

    /** One all-or-nothing write: the temporary file is only renamed once it is whole. */
    private fun writeAtomically(text: String): StoreOutcome {
        val atomic = AtomicFile(file)
        val output = try {
            atomic.startWrite()
        } catch (error: Throwable) {
            return StoreOutcome.Failed(saveFailure(said(error)))
        }
        return try {
            output.write(text.toByteArray(Charsets.UTF_8))
            atomic.finishWrite(output)
            StoreOutcome.Written
        } catch (error: Throwable) {
            atomic.failWrite(output)
            StoreOutcome.Failed(saveFailure(said(error)))
        }
    }

    private fun rejected(): File = File(file.parentFile, REJECTED_SESSION_FILE)

    private fun said(error: Throwable): String = error.message ?: error.toString()
}
