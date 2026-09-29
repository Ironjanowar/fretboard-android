package dev.ironjanowar.fretboard.session

import dev.ironjanowar.fretboard.core.ChordDto
import dev.ironjanowar.fretboard.core.ChordModeDto
import dev.ironjanowar.fretboard.core.InstrumentDto
import dev.ironjanowar.fretboard.core.InstrumentStateDto
import dev.ironjanowar.fretboard.core.KeySuggestionDto
import dev.ironjanowar.fretboard.core.PageEventDto
import dev.ironjanowar.fretboard.core.PageStateDto
import dev.ironjanowar.fretboard.core.ProgressionGroupDto
import dev.ironjanowar.fretboard.core.TuningDto
import dev.ironjanowar.fretboard.core.applyPageEvent
import dev.ironjanowar.fretboard.core.changeTuningString
import dev.ironjanowar.fretboard.core.detectTuningPreset
import dev.ironjanowar.fretboard.core.diatonicChords
import dev.ironjanowar.fretboard.core.openTuningDraft
import dev.ironjanowar.fretboard.core.progressionChords
import dev.ironjanowar.fretboard.core.progressions
import dev.ironjanowar.fretboard.core.selectTuningPreset
import dev.ironjanowar.fretboard.core.tuningNotes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

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

// ------------------------------------------------------- key and progression

/**
 * The engine operations a key or progression draft needs.
 *
 * A14 keeps the draft lifecycle in Kotlin (open, edit, preview, apply, cancel)
 * while every musical value stays the engine's: a key's diatonic chords, a
 * progression's own chord list and the catalog the picker lists. The port exists
 * so the lifecycle can be pinned on the JVM, where the arm64 native library
 * cannot be loaded; the production implementation is
 * [BindingKeyProgressionEngine] and computes nothing.
 */
interface KeyProgressionEngine {
    /** The key draft's preview: the diatonic chords of a key in the draft's mode. */
    suspend fun keyPreview(tonic: String, scale: String, mode: ChordModeDto): List<ChordDto>

    /** The progression draft's preview: one chord per degree, repeats included. */
    suspend fun progressionPreview(tonic: String, progression: String): List<ChordDto>

    /** The progression catalog, grouped as the picker shows it, in the frozen order. */
    suspend fun progressionCatalog(): List<ProgressionGroupDto>
}

/**
 * The production port: the pinned bindings, called as they are, off the main
 * thread.
 *
 * Each call is the engine's own entry point and its own answer is handed back:
 * no chord is computed here and no catalog is curated here.
 */
object BindingKeyProgressionEngine : KeyProgressionEngine {

    override suspend fun keyPreview(tonic: String, scale: String, mode: ChordModeDto): List<ChordDto> =
        withContext(Dispatchers.Default) { diatonicChords(tonic, scale, mode) }

    override suspend fun progressionPreview(tonic: String, progression: String): List<ChordDto> =
        withContext(Dispatchers.Default) { progressionChords(tonic, progression) }

    override suspend fun progressionCatalog(): List<ProgressionGroupDto> =
        withContext(Dispatchers.Default) { progressions() }
}

/**
 * The plan's fresh key draft: opening the modal starts at C major triad (A14).
 *
 * These are wire tokens and an engine enum, not musical values: the client picks
 * the plan's own starting point and the engine answers the preview for it.
 */
const val FRESH_KEY_TONIC: String = "C"
const val FRESH_KEY_SCALE: String = "major"
val FRESH_KEY_MODE: ChordModeDto = ChordModeDto.TRIAD

/**
 * The plan's fresh progression draft: opening the modal starts at
 * `pop_i_v_vi_iv` in C (A14).
 */
const val FRESH_PROGRESSION_TONIC: String = "C"
const val FRESH_PROGRESSION: String = "pop_i_v_vi_iv"

/** The two chord modes the key sheet offers, in the order the sheet shows them. */
val CHORD_MODES: List<ChordModeDto> = listOf(ChordModeDto.TRIAD, ChordModeDto.SEVENTH)

/** The English label of a chord mode choice. */
fun chordModeLabel(mode: ChordModeDto): String = when (mode) {
    ChordModeDto.TRIAD -> "Triad"
    ChordModeDto.SEVENTH -> "Seventh"
}

/**
 * One open key draft: the user's three choices plus the engine's own preview of
 * them.
 *
 * `preview` is the engine's `diatonic_chords` answer for exactly these three
 * fields, so the sheet never computes a chord: it shows the list the engine
 * answered for the key the draft holds.
 */
data class KeyDraft(
    val tonic: String,
    val scale: String,
    val mode: ChordModeDto,
    val preview: List<ChordDto>,
)

/**
 * One open progression draft: the tonic and the catalog identifier, plus the
 * engine's own chord list for them.
 *
 * `preview` has one entry per degree, repeats included, so a progression that
 * plays the same chord twice shows both occurrences — the apply commits the same
 * list rather than merging it.
 */
data class ProgressionDraft(
    val tonic: String,
    val progression: String,
    val preview: List<ChordDto>,
)

/**
 * What the progression sheet's catalog picker shows.
 *
 * The catalog is never a client list: it is the engine's own grouped catalog
 * (`progressions()`), in the engine's own order. Like the tuning sheet's preset
 * picker it has three states and no fourth: the engine's groups, the engine's
 * "none", or the engine's refusal.
 */
sealed interface ProgressionCatalogState {
    /** The engine lists no progressions at all. */
    data object NotApplicable : ProgressionCatalogState

    /** The engine's own groups, in the engine's own order, unfiltered. */
    data class Ready(val groups: List<ProgressionGroupDto>) : ProgressionCatalogState

    /** The engine refused or could not be reached; the reason is shown, not hidden. */
    data class Refused(val reason: String) : ProgressionCatalogState
}

/** The plan's fresh key draft, before the engine's preview arrives. */
fun freshKeyDraft(): KeyDraft = KeyDraft(
    tonic = FRESH_KEY_TONIC,
    scale = FRESH_KEY_SCALE,
    mode = FRESH_KEY_MODE,
    preview = emptyList(),
)

/** The plan's fresh progression draft, before the engine's preview arrives. */
fun freshProgressionDraft(): ProgressionDraft = ProgressionDraft(
    tonic = FRESH_PROGRESSION_TONIC,
    progression = FRESH_PROGRESSION,
    preview = emptyList(),
)

/**
 * The key and progression draft lifecycle, with every musical value from the
 * engine.
 *
 * The drafts are values: opening one reads the engine's preview for the plan's
 * fresh starting point, editing one asks the engine for a new preview, and
 * neither ever touches the committed page — the page changes only when the
 * engine's own `CommitKeys` / `CommitProgression` event is applied by the
 * session (A14's replacement rule), which is why this class has no commit path
 * and never assembles a page.
 */
class KeyProgressionDrafts(private val engine: KeyProgressionEngine) {

    /**
     * Open a fresh key draft.
     *
     * Reopening always starts from the plan's fresh C major triad, so a cancelled
     * or edited draft can never leak into the next open.
     */
    suspend fun openKey(): KeyDraft = previewKey(
        tonic = FRESH_KEY_TONIC,
        scale = FRESH_KEY_SCALE,
        mode = FRESH_KEY_MODE,
    )

    /**
     * Edit a key draft and re-read the preview from the engine.
     *
     * The three fields are the sheet's choice; the chord list is the engine's
     * answer for them, so an edit changes only the draft and never the page.
     */
    suspend fun previewKey(tonic: String, scale: String, mode: ChordModeDto): KeyDraft =
        KeyDraft(
            tonic = tonic,
            scale = scale,
            mode = mode,
            preview = engine.keyPreview(tonic, scale, mode),
        )

    /** Open a fresh progression draft: the plan's `pop_i_v_vi_iv` in C. */
    suspend fun openProgression(): ProgressionDraft = previewProgression(
        tonic = FRESH_PROGRESSION_TONIC,
        progression = FRESH_PROGRESSION,
    )

    /** Select a progression from the picker and re-read its chords from the engine. */
    suspend fun previewProgression(tonic: String, progression: String): ProgressionDraft =
        ProgressionDraft(
            tonic = tonic,
            progression = progression,
            preview = engine.progressionPreview(tonic, progression),
        )

    /** The engine's grouped progression catalog, or an explicit refusal. */
    suspend fun catalog(): ProgressionCatalogState = try {
        val groups = engine.progressionCatalog()
        if (groups.isEmpty()) {
            ProgressionCatalogState.NotApplicable
        } else {
            ProgressionCatalogState.Ready(groups)
        }
    } catch (failure: Throwable) {
        ProgressionCatalogState.Refused(engineFailure(failure))
    }

    /**
     * The engine's own key-commit event for this draft.
     *
     * The client hands the three fields over and the engine replaces the page's
     * chords with that key's diatonic chords in that mode, clears the highlight
     * and keeps everything else; the replacement rule is the engine's, so it is
     * never re-implemented here.
     */
    fun keyEvent(draft: KeyDraft): PageEventDto = PageEventDto.CommitKeys(
        tonic = draft.tonic,
        scale = draft.scale,
        mode = draft.mode,
    )

    /**
     * The engine's own progression-commit event for this draft.
     *
     * The engine replaces the chords with the progression's own list, repeats
     * and all, and clears the highlight.
     */
    fun progressionEvent(draft: ProgressionDraft): PageEventDto = PageEventDto.CommitProgression(
        tonic = draft.tonic,
        progression = draft.progression,
    )

    /**
     * The engine's own event for applying a *suggested* key.
     *
     * Deliberately the mode-free `CommitSuggestedKeys`: the engine infers the
     * mode from the chords already on the page, so an apply from the keys panel
     * never carries the key sheet's previous mode.
     */
    fun suggestedKeyEvent(suggestion: KeySuggestionDto): PageEventDto =
        PageEventDto.CommitSuggestedKeys(
            tonic = suggestion.tonic,
            scale = suggestion.scale,
        )
}
