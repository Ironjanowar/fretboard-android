package dev.ironjanowar.fretboard.session

import dev.ironjanowar.fretboard.core.InstrumentDto
import dev.ironjanowar.fretboard.core.InstrumentStateDto
import dev.ironjanowar.fretboard.core.PageEventDto
import dev.ironjanowar.fretboard.core.PageStateDto
import dev.ironjanowar.fretboard.core.TuningDto
import dev.ironjanowar.fretboard.core.applyPageEvent
import dev.ironjanowar.fretboard.core.changeTuningString
import dev.ironjanowar.fretboard.core.detectTuningPreset
import dev.ironjanowar.fretboard.core.openTuningDraft
import dev.ironjanowar.fretboard.core.selectTuningPreset
import dev.ironjanowar.fretboard.core.tuningNotes

/**
 * The engine operations a tuning draft needs.
 *
 * The draft lifecycle is Kotlin's (open, edit, apply, cancel), but every musical
 * value is the engine's: the draft's pitches, the preset label it detects, the
 * note names it names, the resolved pitch of an edited string and the committed
 * page. The port exists so the lifecycle can be pinned on the JVM, where the
 * arm64 native library cannot be loaded; the production implementation is
 * [BindingTuningEngine] and computes nothing.
 */
interface TuningEngine {
    /** The draft a committed page opens, or `null` on the piano. */
    fun openDraft(state: PageStateDto): TuningDto?

    /** The draft with the named preset applied; an unknown name comes back unchanged. */
    fun selectPreset(instrument: InstrumentDto, draft: TuningDto, preset: String): TuningDto

    /** The draft with one string's note edited; an invalid string or note comes back unchanged. */
    fun changeString(
        instrument: InstrumentDto,
        draft: TuningDto,
        string: String,
        note: String,
    ): TuningDto

    /** The engine's own label for these exact pitches — a preset name, or `Custom`. */
    fun detectPreset(instrument: InstrumentDto, draft: TuningDto): String

    /** The engine's note names of a tuning, in physical string order. */
    fun notes(draft: TuningDto): List<String>

    /** The page that committed tuning produces. */
    fun commit(state: PageStateDto, draft: TuningDto): PageStateDto
}

/**
 * The production port: the pinned bindings, called as they are.
 *
 * Nothing here decides a pitch, a preset name or a note: each call hands the
 * engine's own answer straight back, so no value on screen can be one the engine
 * did not give.
 */
object BindingTuningEngine : TuningEngine {

    override fun openDraft(state: PageStateDto): TuningDto? = openTuningDraft(state)

    override fun selectPreset(
        instrument: InstrumentDto,
        draft: TuningDto,
        preset: String,
    ): TuningDto = selectTuningPreset(instrument, draft, preset)

    override fun changeString(
        instrument: InstrumentDto,
        draft: TuningDto,
        string: String,
        note: String,
    ): TuningDto = changeTuningString(instrument, draft, string, note)

    override fun detectPreset(instrument: InstrumentDto, draft: TuningDto): String =
        detectTuningPreset(instrument, draft)

    override fun notes(draft: TuningDto): List<String> = tuningNotes(draft)

    override fun commit(state: PageStateDto, draft: TuningDto): PageStateDto = applyPageEvent(
        state,
        PageEventDto.CommitTuning(draft),
    )
}

/**
 * One open tuning draft: the engine's own value plus the engine's own reading of
 * it.
 *
 * `preset` and `notes` are re-read from the engine after every edit, so the
 * sheet never labels a draft from a client-side table: a tuning that matches no
 * preset shows the engine's own `Custom`, not a guess.
 */
data class TuningDraft(
    /** The instrument the draft was opened for; a draft is only applied to it. */
    val instrument: InstrumentDto,
    /** The draft's tuning value: the engine's, never a client-assembled one. */
    val tuning: TuningDto,
    /** The engine's label for these exact pitches. */
    val preset: String,
    /** The engine's note names, in physical string order. */
    val notes: List<String>,
)

/**
 * One row of the string editor.
 *
 * The baseline's order is the physical one, labelled the way it labels them
 * (`modals.ex`: `string_count..1` with `string_idx = string_count - string_num`),
 * so the first row on screen is the instrument's last physical string, labelled
 * `String N`, and its token is the physical index the engine's edit expects.
 */
data class TuningRow(val stringIndex: Int, val label: String) {
    /** The decimal text the engine's `change_tuning_string` reads as the index. */
    val token: String get() = stringIndex.toString()
}

/** The editor rows of an instrument with [stringCount] strings. */
fun tuningRows(stringCount: Int): List<TuningRow> =
    (0 until stringCount).map { index -> TuningRow(index, "String ${stringCount - index}") }

/**
 * The fixed-reference tuning draft lifecycle.
 *
 * The draft is a value: it never mutates the committed page, and the committed
 * page never changes until the engine's own `CommitTuning` event is applied.
 * Editing resolves against the draft's reference preset inside the engine, so
 * repeated edits cannot drift and no nearest-pitch rule is implemented here.
 */
class DraftCoordinator(private val engine: TuningEngine) {

    /**
     * Open (or reopen) the draft from the committed page.
     *
     * Reopening always starts from the committed values, which is why a
     * cancelled edit can never leak into the next open. The piano opens no draft:
     * its tuning modal does not exist.
     */
    fun open(state: PageStateDto): TuningDraft? {
        val instrument = state.frettedInstrument() ?: return null
        val tuning = engine.openDraft(state) ?: return null
        return revised(instrument, tuning)
    }

    /** Select a preset: the engine's answer, unchanged when it does not know the name. */
    fun selectPreset(draft: TuningDraft, preset: String): TuningDraft =
        revised(draft.instrument, engine.selectPreset(draft.instrument, draft.tuning, preset))

    /**
     * Edit one string's note.
     *
     * [stringIndex] is the physical index; the engine answers the unchanged
     * draft for an index the instrument does not have or a note outside its
     * chromatic scale, so an invalid edit is dropped by the engine, not patched
     * up here.
     */
    fun changeString(draft: TuningDraft, stringIndex: Int, note: String): TuningDraft =
        revised(
            draft.instrument,
            engine.changeString(
                draft.instrument,
                draft.tuning,
                stringIndex.toString(),
                note,
            ),
        )

    /**
     * Apply the draft to [state], or `null` when the draft is stale.
     *
     * A draft belongs to the instrument it was opened for: if the page has since
     * changed instrument it is not committed to the replacement, which would
     * write the wrong tuning into a different instrument's page.
     */
    fun apply(state: PageStateDto, draft: TuningDraft): PageStateDto? {
        if (state.frettedInstrument() != draft.instrument) return null
        return engine.commit(state, draft.tuning)
    }

    /** Wrap a tuning the engine just answered in the engine's own reading of it. */
    private fun revised(instrument: InstrumentDto, tuning: TuningDto): TuningDraft =
        TuningDraft(
            instrument = instrument,
            tuning = tuning,
            preset = engine.detectPreset(instrument, tuning),
            notes = engine.notes(tuning),
        )
}

/** The instrument of a fretted page, or `null` for the keyboard. */
fun PageStateDto.frettedInstrument(): InstrumentDto? =
    (instrument as? InstrumentStateDto.Fretted)?.instrument

/**
 * The application's one draft coordinator: the lifecycle is pure and the engine
 * port is the pinned binding.
 */
val TUNING_DRAFTS: DraftCoordinator = DraftCoordinator(BindingTuningEngine)
