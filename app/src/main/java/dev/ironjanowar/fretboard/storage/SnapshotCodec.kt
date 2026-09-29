package dev.ironjanowar.fretboard.storage

import dev.ironjanowar.fretboard.core.PageStateDto

/**
 * The engine's snapshot codec, as the store uses it.
 *
 * A port, for the same reason the session screen has one: the arm64 library
 * cannot be loaded on the host the unit tests run on, so the store's own rules —
 * revisions, atomic writes, refusals — are pinned against a scripted codec. The
 * production implementation is [BindingSnapshotCodec], and it decides nothing:
 * it hands the engine a page and returns the envelope it answers with.
 */
interface SnapshotCodec {

    /** The engine's own envelope for [page], validated by the engine first. */
    fun encode(page: PageStateDto): String

    /** The page an envelope carries, or the engine's refusal. */
    fun decode(snapshot: String): SnapshotDecode
}

/** What the engine answered for a stored envelope. */
sealed interface SnapshotDecode {

    /** The page the envelope carries. */
    data class Decoded(val page: PageStateDto) : SnapshotDecode

    /** The engine refused the envelope, in its own words. */
    data class Refused(val reason: String) : SnapshotDecode
}
