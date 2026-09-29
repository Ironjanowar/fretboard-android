package dev.ironjanowar.fretboard.ui.surface

import dev.ironjanowar.fretboard.core.KeyboardSurfaceDto
import dev.ironjanowar.fretboard.core.NoteFillDto
import dev.ironjanowar.fretboard.core.PageEventDto

/**
 * One drawn key: the engine's own key plus where the client puts it.
 *
 * `pitch`, `note`, `memberships` and `fill` are the engine's own values, taken
 * from its keyboard surface and never recomputed. `black`, `leftDp`, `widthDp`
 * and `heightDp` are the client's layout: the physical place and size of the
 * key on the drawn keyboard.
 */
data class PianoKey(
    /** The engine's absolute pitch of the key. */
    val pitch: Int,
    /** The engine's own note name of the key. */
    val note: String,
    /** Whether the client draws this key among the black ones. */
    val black: Boolean,
    /** The engine's colour slots of every active chord that claims the key. */
    val memberships: List<ULong>,
    /** The engine's answer for what fills the key. */
    val fill: NoteFillDto,
    /** The key's left edge, from the keyboard's left edge. */
    val leftDp: Float,
    /** The key's width. */
    val widthDp: Float,
    /** The key's height; the white keys are taller than the black ones. */
    val heightDp: Float,
)

/**
 * The keyboard's geometry and its one hit grid.
 *
 * Kotlin owns where a key sits and how big it is; the engine owns the key, its
 * note and what claims it. The same [layout] list is used by the drawing, the
 * pointer hit test and the accessibility bounds, so the three cannot drift
 * apart: a point owns exactly one key, or none at all.
 *
 * ## Why the key colour is a client layout here
 *
 * The design asks the engine for key metadata (its contract's `KeyboardKey`
 * names a white/black kind and a lower-white anchor). The pinned artifact's
 * `KeyboardKeyDto` carries the key's pitch, note, memberships and fill and
 * nothing else, so the two facts a piano needs in order to be *drawn* — which
 * keys are black, and where a black key sits over the whites — are the client's
 * layout, exactly as the fretboard's inlay frets (`FretboardGeometry`) are.
 *
 * The colour is read from the engine's own note spelling, which is sharp-only
 * by the domain's own contract, and never from pitch arithmetic: a key the
 * engine spells `C#` is one of the black keys. Nothing here is a musical answer
 * — no pitch is computed, no interval, no mark: the marks are the engine's
 * `memberships`/`fill`, and a tap carries back the engine's own `pitch`.
 *
 * Proportions are the pinned web keyboard's (`lib/fretboard_web/components/
 * piano_keyboard.ex`): white 36x160, black 20x100, black left offsets as a
 * fraction of the white width. They are scaled by [SCALE] so a black key is at
 * least a 48dp target; the keyboard scrolls horizontally instead of shrinking.
 */
object PianoGeometry {

    /** The minimum touch target every key must reach (A12). */
    const val MIN_TARGET_DP: Float = 48f

    /** The web's proportions, in its own SVG units. */
    private const val WEB_WHITE_WIDTH = 36f
    private const val WEB_WHITE_HEIGHT = 160f
    private const val WEB_BLACK_WIDTH = 20f
    private const val WEB_BLACK_HEIGHT = 100f

    /**
     * The uniform scale applied to the web proportions.
     *
     * A black key is 20 web units wide, so the scale must be at least
     * `48 / 20 = 2.4` for the narrowest key to be a 48dp target. The offsets are
     * not distorted: every key grows by the same factor, which is what the
     * design asks ("uniformly enlarge those proportions").
     */
    const val SCALE: Float = 2.4f

    /** The drawn width of a white key. */
    val WHITE_WIDTH_DP: Float = WEB_WHITE_WIDTH * SCALE

    /** The drawn height of a white key. */
    val WHITE_HEIGHT_DP: Float = WEB_WHITE_HEIGHT * SCALE

    /** The drawn width of a black key; at [SCALE] this is exactly 48dp. */
    val BLACK_WIDTH_DP: Float = WEB_BLACK_WIDTH * SCALE

    /** The drawn height of a black key. */
    val BLACK_HEIGHT_DP: Float = WEB_BLACK_HEIGHT * SCALE

    /**
     * The black keys' names and their left offset, as a fraction of the white
     * width, measured from the left edge of the lower white key.
     *
     * These are the pinned web offsets (`@black_offsets`), keyed by the engine's
     * own sharp name. The keys of this map *are* the black-key names: a key
     * whose note is one of them is drawn black, which is how the classification
     * reads the engine's spelling instead of doing pitch arithmetic.
     */
    val BLACK_OFFSETS: Map<String, Float> = mapOf(
        "C#" to 0.62f,
        "D#" to 0.81f,
        "F#" to 0.58f,
        "G#" to 0.71f,
        "A#" to 0.86f,
    )

    /** True when the engine's own note name is one of the black keys. */
    fun isBlack(note: String): Boolean = note in BLACK_OFFSETS

    /**
     * Lay the engine's keys out, in the engine's own order.
     *
     * The engine answers the keys in ascending pitch order, one per pitch of its
     * frozen range, which is exactly the left-to-right order of a keyboard: the
     * first key it names is the leftmost white key. Every white key takes the
     * next white slot; every black key sits over the boundary of the white key
     * just before it, at its own offset. The engine's order is preserved, never
     * re-sorted or reversed.
     */
    fun layout(surface: KeyboardSurfaceDto): List<PianoKey> {
        var whiteCount = 0
        return surface.keys.map { key ->
            val note = key.note
            val black = isBlack(note)
            val left: Float
            val width: Float
            val height: Float
            if (black) {
                // The far left edge of the white key this black key sits over:
                // the last white key that was laid out before it.
                val lowerWhite = (whiteCount - 1).coerceAtLeast(0)
                left = (lowerWhite + BLACK_OFFSETS.getValue(note)) * WHITE_WIDTH_DP
                width = BLACK_WIDTH_DP
                height = BLACK_HEIGHT_DP
            } else {
                left = whiteCount * WHITE_WIDTH_DP
                whiteCount += 1
                width = WHITE_WIDTH_DP
                height = WHITE_HEIGHT_DP
            }
            PianoKey(
                pitch = key.pitch.toInt() and 0xFF,
                note = note,
                black = black,
                memberships = key.memberships,
                fill = key.fill,
                leftDp = left,
                widthDp = width,
                heightDp = height,
            )
        }
    }

    /** The scrollable width of a laid-out keyboard. */
    fun surfaceWidthDp(layout: List<PianoKey>): Float =
        layout.count { !it.black } * WHITE_WIDTH_DP

    /**
     * The key a point owns, or `null` outside the keyboard.
     *
     * Ownership is half-open: the key's left and top edges belong to it, its
     * right and bottom edges belong to the next one. Black keys are drawn over
     * the whites, so they are tested first and win inside their own rectangle;
     * below a black key's bottom edge the white key it overlaps owns the point.
     * The white regions therefore exclude the black overlays, exactly as the
     * drawing shows them.
     */
    fun keyAt(layout: List<PianoKey>, xDp: Float, yDp: Float): PianoKey? {
        if (xDp < 0f || yDp < 0f) return null
        layout.firstOrNull { it.black && owns(it, xDp, yDp) }?.let { return it }
        return layout.firstOrNull { !it.black && owns(it, xDp, yDp) }
    }

    private fun owns(key: PianoKey, xDp: Float, yDp: Float): Boolean =
        xDp >= key.leftDp &&
            xDp < key.leftDp + key.widthDp &&
            yDp >= 0f &&
            yDp < key.heightDp

    /**
     * The event one tap produces, or `null` when the tap owns no key.
     *
     * The client's whole share of a piano tap is deciding which key it landed on
     * and sending the engine that key's own pitch: one event, the tapped pitch
     * and nothing else. The engine's reducer decides whether it adds or removes
     * the key, and refuses a pitch outside its frozen range.
     */
    fun tapEvent(layout: List<PianoKey>, xDp: Float, yDp: Float): PageEventDto? =
        keyAt(layout, xDp, yDp)?.let { key -> PageEventDto.TogglePianoKey(key.pitch.toUByte()) }

    /**
     * The event for one pitch, used by the accessibility action.
     *
     * A pitch the engine did not answer as a key produces no event: the tap is a
     * no-op rather than an impossible key sent to the engine.
     */
    fun toggleEvent(layout: List<PianoKey>, pitch: Int): PageEventDto? {
        val key = layout.firstOrNull { it.pitch == pitch } ?: return null
        return PageEventDto.TogglePianoKey(key.pitch.toUByte())
    }
}
