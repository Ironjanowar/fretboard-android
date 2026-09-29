package dev.ironjanowar.fretboard.storage

import dev.ironjanowar.fretboard.core.PageStateDto
import dev.ironjanowar.fretboard.core.decodeSnapshot
import dev.ironjanowar.fretboard.core.encodeSnapshot
import dev.ironjanowar.fretboard.session.engineFailure

/**
 * The pinned binding, called as it is.
 *
 * `encodeSnapshot` validates the page before writing it and `decodeSnapshot` reads
 * the envelope's own `schema_version` before the page, so both refusals happen in
 * the engine and neither is re-implemented here.
 */
object BindingSnapshotCodec : SnapshotCodec {

    override fun encode(page: PageStateDto): String = encodeSnapshot(page)

    override fun decode(snapshot: String): SnapshotDecode = try {
        SnapshotDecode.Decoded(decodeSnapshot(snapshot))
    } catch (error: Throwable) {
        SnapshotDecode.Refused(engineFailure(error))
    }
}

/**
 * The bytes of one stored session: the engine's envelope inside a minimal container.
 *
 * The container is a three-line text document, not a second musical format:
 *
 * ```
 * fretboard-session/1
 * revision=7
 * {"schema_version":1,"page":{…}}
 * ```
 *
 * The first line is the *container's* schema — this client's, refusable when a later
 * release writes a shape this build does not know — and the third is the engine's
 * envelope, verbatim, from its own codec. Nothing here parses, rewrites or
 * interprets that envelope: it is carried as an opaque tail and handed back to the
 * engine, which is the only thing that owns the music. The revision is the write
 * counter the store uses to refuse a write that lost a race.
 *
 * Deliberately no JSON dependency: the only document the client would have to parse
 * is its own, and a text container says exactly what it means.
 */
class SessionSnapshotSerializer(
    private val codec: SnapshotCodec = BindingSnapshotCodec,
) {

    /** The engine's own envelope for [page]. */
    fun envelope(page: PageStateDto): String = codec.encode(page)

    /** The container text carrying [envelope] as revision [revision]. */
    fun container(revision: Long, envelope: String): String =
        "$CONTAINER_HEADER\n$REVISION_PREFIX$revision\n$envelope"

    /** The session a container carries, or why it cannot be used. */
    fun decode(bytes: ByteArray): StoredSession {
        val text = String(bytes, Charsets.UTF_8)
        val headerEnd = text.indexOf('\n')
        if (headerEnd < 0) return StoredSession.Refused(unreadable())
        val header = text.substring(0, headerEnd)
        if (header != CONTAINER_HEADER) return StoredSession.Refused(unknownContainer(header))
        val revisionEnd = text.indexOf('\n', headerEnd + 1)
        if (revisionEnd < 0) return StoredSession.Refused(unreadable())
        val revision = text.substring(headerEnd + 1, revisionEnd)
        if (!revision.startsWith(REVISION_PREFIX)) return StoredSession.Refused(unreadable())
        val stored = revision.removePrefix(REVISION_PREFIX).toLongOrNull()
            ?: return StoredSession.Refused(unreadable())
        val envelope = text.substring(revisionEnd + 1)
        return when (val decoded = codec.decode(envelope)) {
            is SnapshotDecode.Decoded -> StoredSession.Restored(stored, decoded.page, envelope)
            is SnapshotDecode.Refused -> StoredSession.Refused(refused(decoded.reason))
        }
    }

    private fun unreadable(): String =
        "The stored session could not be read: its container is not readable. " +
            "Its bytes were kept."

    private fun unknownContainer(header: String): String =
        "The stored session could not be read: it uses the container " +
            "'${header.ifBlank { "with no header" }}', which this release does not know. " +
            "Its bytes were kept."

    private fun refused(reason: String): String =
        "The stored session could not be reopened: $reason. Its bytes were kept."
}

/** The container's own schema line. */
private const val CONTAINER_HEADER = "fretboard-session/1"

/** How the stored revision is introduced inside the container. */
private const val REVISION_PREFIX = "revision="
